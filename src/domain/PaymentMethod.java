package domain;

import domain.enums.Status;

/**
 * A payment method reference entry (table: payment_method). Activated/deactivated rather than
 * hard-deleted once referenced by a payment (FR-15, FR-31); at least one method stays Active.
 */
public class PaymentMethod {
    private int methodId;
    private String methodName;
    private Status status;

    public int getMethodId() { return methodId; }
    public void setMethodId(int methodId) { this.methodId = methodId; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
