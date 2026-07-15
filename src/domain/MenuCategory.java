package domain;

/** A menu grouping (table: menu_category). */
public class MenuCategory {
    private int categoryId;
    private String name;
    private Integer displayOrder;   // nullable

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
