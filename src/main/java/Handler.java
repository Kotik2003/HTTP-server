import java.util.function.Function;

/**
 * Интерфейс обработчика.
 * На входе Request, на выходе Response.
 */
public interface Handler extends Function<Request, Response> {

}
