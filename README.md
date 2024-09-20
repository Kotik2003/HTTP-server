## HTTPServer - Простая реализация HTTP-сервера

Этот проект представляет собой простой фреймворк для протокола HTTP/1.1. Он реализует TCP-сервер на основе ServerSocketChannel (java.nio), который позволяет обрабатывать одновременно несколько подключений в одном потоке. Поддерживаются все методы и коды ответов протокола HTTP/1.1, а также запросы с параметрами (например, ?ke1=value1&key2=value2)

### Структура проекта

```
├── src/                            # Каталог исходного кода
│   └── main/java/                  # Исходный код фреймворка
│       ├── HTTPServer.java         # TCP-сервер на основе ServerSocketChannel
│       ├── Request.java            # HTTP-запрос
│       ├── Response.java           # HTTP-ответ
│       ├── Connection.java         # Операции ввода-вывода для отдельного соединения
│       └── Handler.java            # Интерфейса обработчика запросов
├── test/                           # Каталог тестового кода
│   └── main/java/                  # Исходный код тестов
│       └── HTTPServerTest.java     # Тестирование взаимодействия сервера и клиента
├── pom.xml                         # Файл конфигурации Maven-проекта
└── README.md                       # Документация проекта
```

### Быстрый старт

Ниже показан пример реализации Handler, обрабатывающего все основные запросы: 

```java
Handler handler = request -> {
    Response response;
    if (request.getMethod().equals("GET")) {
        // Пример обработки запроса с параметрами.
        // Возвращает разный ответ в зависимости от наличия в запросе 
        // параметра test (?test или ?test=)
        response = new Response(200);
        if (request.getQuery().containsKey("test")) {
            response.addHeader("Content-Type", "text/html");
            response.setBody("This is the test!".getBytes(StandardCharsets.UTF_8));
        } else {
            response.addHeader("Content-Type", "text/plain");
            response.setBody("Hello world!".getBytes(StandardCharsets.UTF_8));
        }
    } else if (request.getMethod().equals("POST")) {
        // Просто возвращаем назад тело запроса
        response = new Response(201);        
        response.setBody(request.getBody());
    } else if (request.getMethod().equals("PUT")) {
        // Просто возвращаем назад тело запроса
        response = new Response(200);        
        response.setBody(request.getBody());
    } else if (request.getMethod().equals("DELETE")) {
        response = new Response(204);
    } else {
        // 405 Method Not Allowed
        response = new Response(405);
    }
    return response;
};
```

Ниже показан пример кода для создания простейшего HTTP-сервера.

```java
HTTPServer server = new HTTPServer("localhost", 8080);
server.addListener("/home", "GET", handler);
server.addListener("/home", "POST", handler);
server.addListener("/home", "PUT", handler);
server.addListener("/home", "DELETE", handler);
server.start();
// ...
// Где-то в другом потоке:
server.stop();
```

Метод `start` сервера запускает бесконечный цикл, поэтому может потребоваться запустить его в отдельном потоке, например, так:

```java
new Thread(server::start).start();
```

Обработчики можно добавлять динамически, уже после запуска сервера.

Примеры запросов, которые обработаются в заданной конфигурации:

```
GET /home
GET /home?q=value
GET /home?test
POST /home
PUT /home
DELETE /home
```