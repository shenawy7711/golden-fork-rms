package domain;

import java.math.BigDecimal;

/** A purchase-order line (table: purchase_order_item). received_qty is 0..ordered_qty. */
public class PurchaseOrderItem {
    private int poItemId;
    private int poId;
    private int stockItemId;
    private BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private BigDecimal unitCost;   // nullable

    public int getPoItemId() { return poItemId; }
    public void setPoItemId(int poItemId) { this.poItemId = poItemId; }

    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }

    public int getStockItemId() { return stockItemId; }
    public void setStockItemId(int stockItemId) { this.stockItemId = stockItemId; }

    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }

    public BigDecimal getReceivedQty() { return receivedQty; }
    public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
