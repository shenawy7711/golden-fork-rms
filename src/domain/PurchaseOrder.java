package domain;

import domain.enums.PoStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A purchase-order header (table: purchase_order). Creating it does not change stock (BR-23). */
public class PurchaseOrder {
    private int poId;
    private String poNumber;
    private int supplierId;
    private PoStatus status;
    private int createdBy;
    private LocalDateTime orderedAt;
    private LocalDate expectedDate;   // nullable

    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public PoStatus getStatus() { return status; }
    public void setStatus(PoStatus status) { this.status = status; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

    public LocalDate getExpectedDate() { return expectedDate; }
    public void setExpectedDate(LocalDate expectedDate) { this.expectedDate = expectedDate; }
}
