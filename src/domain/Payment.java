package domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A recorded payment, one per finalised order (table: payment). Recorded, not processed. */
public class Payment {
    private int paymentId;
    private int orderId;
    private int methodId;
    private BigDecimal amount;
    private BigDecimal amountTendered;   // nullable (cash)
    private BigDecimal changeGiven;      // nullable (cash)
    private LocalDateTime paidAt;

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getMethodId() { return methodId; }
    public void setMethodId(int methodId) { this.methodId = methodId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getAmountTendered() { return amountTendered; }
    public void setAmountTendered(BigDecimal amountTendered) { this.amountTendered = amountTendered; }

    public BigDecimal getChangeGiven() { return changeGiven; }
    public void setChangeGiven(BigDecimal changeGiven) { this.changeGiven = changeGiven; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
