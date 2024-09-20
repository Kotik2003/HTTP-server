import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Реализация чтения-записи в соединении (канале).
 * Это класс для внутреннего использования.
 * Читает из канала, пока полностью не прочтет запрос, и
 * затем формирует объект типа Request.
 * После того как получит ответ в методе sendResponse,
 * записывает байты ответа в канал.
 */
class Connection {
    // Чтение производится порциями по 8 КБ
    static final int READ_BUFFER_SIZE = 8192;

    // Допустимые методы
    private static final Set<String> HTTP_METHODS = Stream.of(
            "GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "CONNECT", "OPTIONS", "TRACE"
    ).collect(Collectors.toUnmodifiableSet());

    private final SocketChannel channel;

    // Промежуточное место хранения заголовков
    private final List<String> lines = new ArrayList<>();

    // Буфер чтения
    private ByteBuffer readBuffer;

    // Буфер записи
    private ByteBuffer writeBuffer;

    // Состояние
    private State state;

    private Request request;

    /**
     * Конструктор
     *
     * @param channel
     */
    public Connection(SocketChannel channel) {
        this.channel = channel;
        readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        state = State.READ_HEADERS;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public State getState() {
        return state;
    }

    /**
     * Возвращает полностью прочитанный запрос, иначе null.
     *
     * @return
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Записывает байты ответа в канал.
     *
     * @param response
     */
    public void sendResponse(Response response) {
        // convert to bytes
        byte[] bytes = response.getBytes();
        writeBuffer = ByteBuffer.wrap(bytes);
    }

    /**
     * Чтение запроса порциями по 8 КБ
     *
     * @throws IOException
     * @throws RequestException
     */
    public void read() throws IOException, RequestException {
        int read;
        while ((read = channel.read(readBuffer)) > 0) {
            if (state == State.READ_HEADERS) {
                int limit = readBuffer.position();
                readBuffer.rewind();
                readBuffer.limit(limit);
                if (!readLines()) {
                    readBuffer.compact();
                }
            }
        }
        if (read == 0 && readBuffer.position() == readBuffer.limit()) {
            state = State.READY_WRITE;
            request.setBody(readBuffer.array());
        }
        if (read < 0) {
            throw new IOException("End of input stream. Connection is closed by the client");
        }
    }

    /**
     * Запись ответа в канал.
     *
     * @throws IOException
     */
    public void write() throws IOException {
        int write = channel.write(writeBuffer);
        if (write < 0) {
            throw new IOException("End of output stream. Connection is closed by the client");
        }
        state = State.READY_CLOSE;
    }

    /**
     * Разбор заголовков по достижении пустой строки.
     *
     * @throws RequestException исключение, содержащее http-код ошибки
     */
    private void parseHeaders() throws RequestException {
        request = new Request();
        if (lines.isEmpty()) {
            // 400 Bad request
            throw new RequestException(400);
        }
        String[] startLine = lines.get(0).split("\\s");
        if (startLine.length != 3) {
            // 400 Bad request
            throw new RequestException(400);
        }
        if (!HTTP_METHODS.contains(startLine[0])) {
            // 400 Bad request
            throw new RequestException(400);
        }

        if (!startLine[2].equals("HTTP/1.1")) {
            // 505 HTTP Version Not Supported
            throw new RequestException(505);
        }

        // Split to path and query
        String path = startLine[1];
        if (path.contains("?")) {
            String query = path.substring(path.indexOf("?") + 1);
            path = path.substring(0, path.indexOf("?"));

            String[] params = query.split("&");
            for (String param : params) {
                String[] kv = param.split("=");
                String key = kv[0];
                String value = "";
                if (kv.length == 2) {
                    value = kv[1];
                }
                request.addQuery(key, value);
            }
        }

        // Путь и метод
        request.setMethod(startLine[0]);
        request.setPath(path);

        // Заголовки
        for (int i = 1; i < lines.size(); ++i) {
            String line = lines.get(i);
            int idx = line.indexOf(":");
            if (idx == -1) {
                throw new RequestException(400);
            }
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            request.addHeader(key, val);
        }

        // Размер тела определяется заголовком "Content-Length"
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(request.getHeaders().getOrDefault("Content-Length", "0"));
        } catch (Exception e) {
            throw new RequestException(400);
        }
        if (contentLength == 0) {
            state = State.READY_WRITE;
        } else {
            state = State.READ_BODY;
            ByteBuffer newBuffer = ByteBuffer.allocate(contentLength);
            newBuffer.put(readBuffer);
            readBuffer = newBuffer;
        }
    }

    /**
     * Чтение строк, заканчивающихся CR-LF.
     *
     * @return true, если достигнута пустая строка перед телом,
     * иначе false.
     * @throws RequestException
     */
    private boolean readLines() throws RequestException {
        int pos = readBuffer.position();
        int cur = pos;
        while (cur < readBuffer.limit() - 1 && state == State.READ_HEADERS) {
            byte first = readBuffer.get(cur);
            byte second = readBuffer.get(cur + 1);

            if (first == '\r' && second == '\n') {
                // one line read
                if (cur == pos) {
                    // last empty line read
                    readBuffer.position(cur + 2);
                    parseHeaders();
                    return true;
                } else {
                    byte[] bytes = new byte[cur - pos];
                    readBuffer.get(pos, bytes);
                    String str = new String(bytes, StandardCharsets.UTF_8);
                    lines.add(str);
                }
                pos = cur + 2;
                cur = pos;
            } else {
                cur++;
            }
        }
        if (cur != pos && pos == readBuffer.position()) {
            // Line too long
            throw new RequestException(431);
        }
        readBuffer.position(pos);
        return false;
    }

    public void close() throws IOException {
        channel.close();
    }

    public enum State {
        READ_HEADERS,
        READ_BODY,
        READY_WRITE,
        READY_CLOSE
    }

    /**
     * Исключение, содержащее http-код ошибки
     */
    public static class RequestException extends Exception {
        private final int errorCode;

        public RequestException(int errorCode) {
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }
}
