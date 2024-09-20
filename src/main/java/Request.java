import java.util.HashMap;
import java.util.Map;

/**
 * Запрос. Формируется в процессе чтения из канала и
 * затем направляется в обработчик.
 */
public class Request {
    private String method;

    private String path;

    private final Map<String, String> query = new HashMap<>();

    private Map<String, String> headers = new HashMap<>();

    private byte[] body = new byte[0];

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQuery() {
        return query;
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

    public void addQuery(String key, String value) {
        query.put(key, value);
    }
}
