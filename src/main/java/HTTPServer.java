import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Logger;

record ListenerPair(String endPoint, String method) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListenerPair listener)) return false;
        return Objects.equals(endPoint, listener.endPoint) && Objects.equals(method, listener.method);
    }
}

// HTTP/1.1 сервер
public class HTTPServer {
    private static final Logger LOGGER = Logger.getLogger(HTTPServer.class.getName());

    private final InetSocketAddress inetSocketAddress;

    // Карта соединений
    private final Map<SocketChannel, Connection> connections = new HashMap<>();

    // Карта обработчиков
    private final Map<ListenerPair, Handler> listeners = new HashMap<>();

    private Selector selector;

    private ServerSocketChannel serverSocketChannel;

    private boolean running = false;

    /**
     * Конструктор
     *
     * @param address
     * @param port
     */
    public HTTPServer(String address, int port) {
        inetSocketAddress = new InetSocketAddress(address, port);
    }

    /**
     * Добавляет обработчик
     *
     * @param endPoint
     * @param method
     * @param handler
     */
    public void addListener(String endPoint, String method, Handler handler) {
        ListenerPair listener = new ListenerPair(endPoint, method);
        listeners.put(listener, handler);
    }

    /**
     * Запускает сервер
     */
    public void start() {
        try {
            init();
            loop();
        } catch (Exception e) {
            LOGGER.severe("Unexpected error occurred. Stopping server.");
            LOGGER.severe(e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Останавливает сервер
     */
    public void stop() {
        if (running) {
            running = false;
            try {
                LOGGER.info("Stopping server.");
                selector.close();
                serverSocketChannel.close();
                for (Connection connection : connections.values()) {
                    try {
                        connection.close();
                    } catch (IOException e) {
                        LOGGER.warning("Error during closing connection. Ignoring.");
                        LOGGER.warning(e.getMessage());
                    }
                }
            } catch (IOException e) {
                LOGGER.warning("Error during stopping server. Ignoring.");
                LOGGER.warning(e.getMessage());
            }
        }
    }

    // Инициализация
    private void init() throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(inetSocketAddress);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // register a simple graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOGGER.info("Got shutdown signal.");
                HTTPServer.this.stop();
            } catch (Exception e) {
                LOGGER.warning("Error during shutdown. Forced shutdown.");
                LOGGER.warning(e.getMessage());
            } finally {
                LOGGER.info("Stopped.");
            }
        }));

        LOGGER.info("Server is now listening on port: " + inetSocketAddress.getPort());
    }

    // Основной цикл
    private void loop() {
        running = true;
        while (running) {
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    try {
                        if (!key.isValid()) {
                            continue;
                        }
                        if (key.isAcceptable()) {
                            accept();
                        } else if (key.isReadable()) {
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (ClosedSelectorException e) {
                LOGGER.severe("Selector is closed. Stopping server.");
                return;
            } catch (IOException e) {
                LOGGER.severe("Unexpected error occurred. Stopping server.");
                LOGGER.severe(e.getMessage());
                return;
            }
        }
    }

    // Прием входящих соединений
    private void accept() throws IOException {
        SocketChannel channel = serverSocketChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        connections.put(channel, new Connection(channel));
        LOGGER.info("Connection accepted: " + channel + ", active connections: " + connections.size());
    }

    // Чтение
    private void read(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        Connection connection = connections.get(clientChannel);

        Response response = null;
        try {
            connection.read();
            if (connection.getState() == Connection.State.READY_WRITE) {
                Request request = connection.getRequest();
                ListenerPair listener = new ListenerPair(request.getPath(), request.getMethod());
                Handler handler = listeners.getOrDefault(listener, null);

                if (handler != null) {
                    response = handler.apply(request);
                } else {
                    // 404 Not Found
                    response = new Response(404);
                }
            } else {
                // keep reading
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (Connection.RequestException e) {
            response = new Response(e.getErrorCode());
        }

        if (response != null) {
            key.interestOps(SelectionKey.OP_WRITE);
            // write response
            connection.sendResponse(response);
        }
    }

    // Запись
    private void write(SelectionKey key) throws IOException {
        try (SocketChannel clientChannel = (SocketChannel) key.channel()) {
            Connection connection = connections.get(clientChannel);
            connection.write();
            closeChannel(key);
        }
    }

    // Закрывает соединение
    private void closeChannel(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        connections.remove(channel);
        key.cancel();
        LOGGER.info("Closing connection for channel: " + channel + ", active connections: " + connections.size());

        try {
            channel.close();
        } catch (IOException e) {
            LOGGER.warning("Error during closing channel: " + channel);
            LOGGER.warning(e.getMessage());
        }
    }
}
