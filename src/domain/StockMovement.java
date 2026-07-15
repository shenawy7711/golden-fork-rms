package domain;

import domain.enums.MovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An append-only stock ledger row (table: stock_movement).
 * stock_item.quantity_on_hand is the running sum of these; po_item_id is set for PO receipts.
 */
public class StockMovement {
    private long movementId;
    private int stockItemId;
    private Integer poItemId;         // nullable (set for receipts)
    private MovementType movementType;
    private BigDecimal quantityChange;   // signed delta
    private LocalDateTime movedAt;
    private int movedBy;

    public long getMovementId() { return movementId; }
    public void setMovementId(long movementId) { this.movementId = movementId; }

    public int getStockItemId() { return stockItemId; }
    public void setStockItemId(int stockItemId) { this.stockItemId = stockItemId; }

    public Integer getPoItemId() { return poItemId; }
    public void setPoItemId(Integer poItemId) { this.poItemId = poItemId; }

    public MovementType getMovementType() { return movementType; }
    public void setMovementType(MovementType movementType) { this.movementType = movementType; }

    public BigDecimal getQuantityChange() { return quantityChange; }
    public void setQuantityChange(BigDecimal quantityChange) { this.quantityChange = quantityChange; }

    public LocalDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(LocalDateTime movedAt) { this.movedAt = movedAt; }

    public int getMovedBy() { return movedBy; }
    public void setMovedBy(int movedBy) { this.movedBy = movedBy; }
}
