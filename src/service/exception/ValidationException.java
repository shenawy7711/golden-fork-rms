package service.exception;

/** Input failed a field/business validation rule (FRD Appendix A). Maps to a field-specific message. */
public class ValidationException extends RmsException {
    public ValidationException(String message) { super(message); }
    public ValidationException(String message, Throwable cause) { super(message, cause); }
}
