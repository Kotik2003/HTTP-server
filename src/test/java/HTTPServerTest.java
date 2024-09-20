import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HTTPServerTest {
    // Глобальный сервер для всех тестов
    private static HTTPServer server;

    // Вспомогательный метод для отправки запроса GET
    private HttpResponse<String> clientSendGet(String uri) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Вспомогательный метод для отправки запроса POST
    private HttpResponse<String> clientSendPost(String uri, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Вспомогательный метод для отправки запроса PUT
    private HttpResponse<String> clientSendPut(String uri, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Вспомогательный метод для отправки запроса DELETE
    private HttpResponse<String> clientSendDelete(String uri) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Обработчик для всех запросов.
    // Обрабатывает GET и POST, а для других возвращает код 405
    private Handler handler = request -> {
        Response response;
        if (request.getMethod().equals("GET")) {
            // Возвращает разный ответ в зависимости от наличия в запросе параметра test
            response = new Response(200);
            if (request.getQuery().containsKey("test")) {
                response.addHeader("Content-Type", "text/html");
                response.setBody("This is the test!".getBytes(StandardCharsets.UTF_8));
            } else {
                response.addHeader("Content-Type", "text/plain");
                response.setBody("Hello world!".getBytes(StandardCharsets.UTF_8));
            }
        } else if (request.getMethod().equals("POST")) {
            response = new Response(201);
            response.addHeader("Content-Type", request.getHeaders().get("Content-Type"));
            response.setBody(request.getBody());
        } else if (request.getMethod().equals("PUT")) {
            // Просто возвращаем назад тело запроса
            response = new Response(200);
            response.addHeader("Content-Type", request.getHeaders().get("Content-Type"));
            response.setBody(request.getBody());
        } else if (request.getMethod().equals("DELETE")) {
            response = new Response(204);
        } else {
            // 405 Method Not Allowed
            response = new Response(405);
        }
        return response;
    };

    // Запуск сервера перед тестами (в отдельном потоке)
    @BeforeAll
    public static void beforeAll() throws InterruptedException {
        server = new HTTPServer("localhost", 8080);
        new Thread(server::start).start();
        Thread.sleep(500);
    }

    // Остановка сервера
    @AfterAll
    public static void afterAll() {
        server.stop();
    }


    // Тестируем GET
    @Test
    public void test1() throws IOException, InterruptedException {
        server.addListener("/test1", "GET", handler);
        HttpResponse<String> response = clientSendGet("http://127.0.0.1:8080/test1?q1=1&q2=2");
        assertEquals(200, response.statusCode());
        assertEquals("Hello world!", response.body());
        assertEquals(String.format("%d", "Hello world!".length()),
                response.headers().map().get("Content-Length").get(0));
        assertEquals("text/plain",
                response.headers().map().get("Content-Type").get(0));
    }

    // Тестируем GET с параметром
    @Test
    public void test2() throws IOException, InterruptedException {
        server.addListener("/test2", "GET", handler);
        HttpResponse<String> response = clientSendGet("http://127.0.0.1:8080/test2?test");
        assertEquals(200, response.statusCode());
        assertEquals("This is the test!", response.body());
        assertEquals(String.format("%d", "This is the test!".length()),
                response.headers().map().get("Content-Length").get(0));
        assertEquals("text/html",
                response.headers().map().get("Content-Type").get(0));
    }

    // Тестируем POST
    @Test
    public void test3() throws IOException, InterruptedException {
        server.addListener("/test3", "POST", handler);
        HttpResponse<String> response = clientSendPost("http://127.0.0.1:8080/test3", "Hello world!");
        assertEquals(201, response.statusCode());
        assertEquals("Hello world!", response.body());
        assertEquals(String.format("%d", "Hello world!".length()),
                response.headers().map().get("Content-Length").get(0));
    }

    // Тестируем PUT
    @Test
    public void test4() throws IOException, InterruptedException {
        HttpResponse<String> response = clientSendPut("http://127.0.0.1:8080/test4", "Hello world!");
        // Обработчик не задан, код ответа должен быть 404
        assertEquals(404, response.statusCode());

        server.addListener("/test4", "PUT", handler);
        response = clientSendPut("http://127.0.0.1:8080/test4", "Hello world!");
        assertEquals(200, response.statusCode());
    }

    // Тестируем DELETE
    @Test
    public void test5() throws IOException, InterruptedException {
        HttpResponse<String> response = clientSendDelete("http://127.0.0.1:8080/test5");
        // Обработчик не задан, код ответа должен быть 404
        assertEquals(404, response.statusCode());

        server.addListener("/test5", "DELETE", handler);
        response = clientSendDelete("http://127.0.0.1:8080/test5");
        assertEquals(204, response.statusCode());
    }
}