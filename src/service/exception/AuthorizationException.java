package service.exception;

/** The caller's role is not permitted this operation (FR-02, BR-03). Business-layer RBAC denial. */
public class AuthorizationException extends RmsException {
    public AuthorizationException(String message) { super(message); }
    public AuthorizationException(String message, Throwable cause) { super(message, cause); }
}
