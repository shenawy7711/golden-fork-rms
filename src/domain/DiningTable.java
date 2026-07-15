package domain;

import domain.enums.TableStatus;

/** A dining table (table: dining_table). */
public class DiningTable {
    private int tableId;
    private String label;
    private int capacity;
    private TableStatus status;

    public int getTableId() { return tableId; }
    public void setTableId(int tableId) { this.tableId = tableId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public TableStatus getStatus() { return status; }
    public void setStatus(TableStatus status) { this.status = status; }
}
