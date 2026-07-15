package domain;

import domain.enums.Availability;

import java.math.BigDecimal;

/** A sellable menu item (table: menu_item). Price snapshots onto order lines at order time (BR-15). */
public class MenuItem {
    private int itemId;
    private int categoryId;
    private String name;
    private BigDecimal price;
    private Availability availability;
    private String description;   // nullable

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Availability getAvailability() { return availability; }
    public void setAvailability(Availability availability) { this.availability = availability; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
