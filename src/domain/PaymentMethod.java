package domain;

/** A payment method reference entry (table: payment_method). */
public class PaymentMethod {
    private int methodId;
    private String methodName;

    public int getMethodId() { return methodId; }
    public void setMethodId(int methodId) { this.methodId = methodId; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
}
