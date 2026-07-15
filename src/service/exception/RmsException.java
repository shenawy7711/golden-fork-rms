package service.exception;

/**
 * Base for the service layer's typed exceptions (Constitution VII).
 * Controllers catch these at the UI boundary and map them to the FRD's
 * user-facing messages. Unchecked so services stay uncluttered by throws clauses.
 */
public class RmsException extends RuntimeException {
    public RmsException(String message) { super(message); }
    public RmsException(String message, Throwable cause) { super(message, cause); }
}
