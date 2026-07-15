package service.exception;

/** A data-access failure (wraps a SQLException). Signals a rollback; no partial data is kept (NFR-03). */
public class PersistenceException extends RmsException {
    public PersistenceException(String message) { super(message); }
    public PersistenceException(String message, Throwable cause) { super(message, cause); }
}
