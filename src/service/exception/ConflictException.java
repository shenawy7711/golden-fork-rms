package service.exception;

/**
 * The operation conflicts with existing state: a uniqueness clash (duplicate username,
 * category/table/supplier/stock name), a reservation overlap (BR-27), an illegal state
 * transition, or a table that already has an open order.
 */
public class ConflictException extends RmsException {
    public ConflictException(String message) { super(message); }
    public ConflictException(String message, Throwable cause) { super(message, cause); }
}
