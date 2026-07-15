package domain;

import domain.enums.Status;

import java.math.BigDecimal;

/**
 * An inventory item (table: stock_item). quantity_on_hand is system-maintained and
 * changes only via recorded stock movements (BR-21); low when on-hand <= reorder level (BR-25).
 */
public class StockItem {
    private int stockItemId;
    private String name;
    private String unitOfMeasure;
    private BigDecimal reorderLevel;
    private BigDecimal quantityOnHand;
    private Status status;

    public int getStockItemId() { return stockItemId; }
    public void setStockItemId(int stockItemId) { this.stockItemId = stockItemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }

    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(BigDecimal quantityOnHand) { this.quantityOnHand = quantityOnHand; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
