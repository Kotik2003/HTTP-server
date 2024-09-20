import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Ответ сервера, генерируемый обработчиком.
 */
public class Response {
    // Текст для всех http-кодов
    public static final Map<Integer, String> STATUS_CODES =
            Stream.<SimpleEntry<Integer, String>>of(
                            new SimpleEntry<>(100, "Continue"),
                            new SimpleEntry<>(101, "Switching Protocols"),
                            new SimpleEntry<>(102, "Processing"),
                            new SimpleEntry<>(103, "Early Hints"),
                            new SimpleEntry<>(200, "OK"),
                            new SimpleEntry<>(201, "Created"),
                            new SimpleEntry<>(202, "Accepted"),
                            new SimpleEntry<>(203, "Non-Authoritative Information"),
                            new SimpleEntry<>(204, "No Content"),
                            new SimpleEntry<>(205, "Reset Content"),
                            new SimpleEntry<>(206, "Partial Content"),
                            new SimpleEntry<>(207, "Multi-Status"),
                            new SimpleEntry<>(208, "Already Reported"),
                            new SimpleEntry<>(226, "IM Used"),
                            new SimpleEntry<>(300, "Multiple Choices"),
                            new SimpleEntry<>(301, "Moved Permanently"),
                            new SimpleEntry<>(302, "Found"),
                            new SimpleEntry<>(303, "See Other"),
                            new SimpleEntry<>(304, "Not Modified"),
                            new SimpleEntry<>(305, "Use Proxy"),
                            new SimpleEntry<>(306, "(Unused)"),
                            new SimpleEntry<>(307, "Temporary Redirect"),
                            new SimpleEntry<>(308, "Permanent Redirect"),
                            new SimpleEntry<>(400, "Bad Request"),
                            new SimpleEntry<>(401, "Unauthorized"),
                            new SimpleEntry<>(402, "Payment Required"),
                            new SimpleEntry<>(403, "Forbidden"),
                            new SimpleEntry<>(404, "Not Found"),
                            new SimpleEntry<>(405, "Method Not Allowed"),
                            new SimpleEntry<>(406, "Not Acceptable"),
                            new SimpleEntry<>(407, "Proxy Authentication Required"),
                            new SimpleEntry<>(408, "Request Timeout"),
                            new SimpleEntry<>(409, "Conflict"),
                            new SimpleEntry<>(410, "Gone"),
                            new SimpleEntry<>(411, "Length Required"),
                            new SimpleEntry<>(412, "Precondition Failed"),
                            new SimpleEntry<>(413, "Payload Too Large"),
                            new SimpleEntry<>(414, "URI Too Long"),
                            new SimpleEntry<>(415, "Unsupported Media Type"),
                            new SimpleEntry<>(416, "Range Not Satisfiable"),
                            new SimpleEntry<>(417, "Expectation Failed"),
                            new SimpleEntry<>(421, "Misdirected Request"),
                            new SimpleEntry<>(422, "Unprocessable Entity"),
                            new SimpleEntry<>(423, "Locked"),
                            new SimpleEntry<>(424, "Failed Dependency"),
                            new SimpleEntry<>(425, "Too Early"),
                            new SimpleEntry<>(426, "Upgrade Required"),
                            new SimpleEntry<>(427, "Unassigned"),
                            new SimpleEntry<>(428, "Precondition Required"),
                            new SimpleEntry<>(429, "Too Many Requests"),
                            new SimpleEntry<>(430, "Unassigned"),
                            new SimpleEntry<>(431, "Request Header Fields Too Large"),
                            new SimpleEntry<>(451, "Unavailable For Legal Reasons"),
                            new SimpleEntry<>(500, "Internal Server Error"),
                            new SimpleEntry<>(501, "Not Implemented"),
                            new SimpleEntry<>(502, "Bad Gateway"),
                            new SimpleEntry<>(503, "Service Unavailable"),
                            new SimpleEntry<>(504, "Gateway Timeout"),
                            new SimpleEntry<>(505, "HTTP Version Not Supported"),
                            new SimpleEntry<>(506, "Variant Also Negotiates"),
                            new SimpleEntry<>(507, "Insufficient Storage"),
                            new SimpleEntry<>(508, "Loop Detected"),
                            new SimpleEntry<>(509, "Unassigned"),
                            new SimpleEntry<>(510, "Not Extended"),
                            new SimpleEntry<>(511, "Network Authentication Required"))
                    .collect(toMap(SimpleEntry::getKey, SimpleEntry::getValue));

    static final String CR_LF = "\r\n";
    static final String PROTO = "HTTP/1.1";

    // Код ответа
    private final int statusCode;

    // Первая строка ответа
    private final String status;

    // Заголовки
    private Map<String, String> headers = new HashMap<>();

    // Тело
    private byte[] body = new byte[0];

    /**
     * Конструктор
     * @param statusCode
     */
    public Response(int statusCode) {
        this.statusCode = statusCode;
        status = String.format("%s %d %s",
                PROTO, statusCode, STATUS_CODES.get(statusCode));
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusLine() {
        return status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public byte[] getBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append(status).append(CR_LF);
        if (!headers.containsKey("Content-Length")) {
            headers.put("Content-Length", String.format("%d", body.length));
        }
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", "text/html");
        }
        for (Map.Entry<String, String> kv : headers.entrySet()) {
            String header = String.format("%s: %s", kv.getKey(), kv.getValue());
            sb.append(header).append(CR_LF);
        }
        sb.append(CR_LF);

        byte[] headersBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        byte[] bytes = new byte[headersBytes.length + body.length];

        System.arraycopy(headersBytes, 0, bytes, 0, headersBytes.length);
        System.arraycopy(body, 0, bytes, headersBytes.length, body.length);

        return bytes;
    }
}
