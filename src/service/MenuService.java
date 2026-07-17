package service;

import dao.MenuCategoryDAO;
import dao.MenuItemDAO;
import domain.MenuCategory;
import domain.MenuItem;
import domain.enums.Availability;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Money;
import util.Validation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Menu categories and items (FR-05, FR-06, FR-07; BR-08, BR-09, BR-10).
 *
 * <p>Writes require {@code MANAGE_MENU} (Manager/Administrator) and are refused for a Cashier even
 * if the interface were bypassed (BR-03). The read models are unguarded: the POS needs the item
 * list to take an order, which every role may do.
 *
 * <p><b>Price changes never reach a finalised bill.</b> Editing an item's price changes what future
 * order lines cost; each existing line already holds its own unit-price snapshot (BR-09, BR-15).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class MenuService {

    static final String DUPLICATE_CATEGORY = "A category with that name already exists.";
    static final String DUPLICATE_ITEM = "An item with that name already exists in this category.";
    static final String CATEGORY_NOT_EMPTY =
        "This category still contains items. Move or remove them before deleting it.";

    private final MenuCategoryDAO categoryDAO;
    private final MenuItemDAO itemDAO;

    public MenuService(MenuCategoryDAO categoryDAO, MenuItemDAO itemDAO) {
        this.categoryDAO = categoryDAO;
        this.itemDAO = itemDAO;
    }

    // --- Categories (FR-05) -----------------------------------------------------

    /** Every category, in display order. Unguarded: order entry needs it. */
    public List<MenuCategory> listCategories() {
        return categoryDAO.findAll();
    }

    /**
     * Creates or updates a category (FR-05).
     *
     * @throws ConflictException if the name is taken (BR-08)
     */
    public MenuCategory saveCategory(Session session, MenuCategory category) {
        RbacGuard.require(session, Permission.MANAGE_MENU);
        if (category == null) {
            throw new ValidationException("No category details were supplied.");
        }
        if (!Validation.hasLength(category.getName(), 2, 50)) {
            throw new ValidationException("Enter 2–50 characters for the category name.");
        }
        if (category.getDisplayOrder() != null && category.getDisplayOrder() < 0) {
            throw new ValidationException("Enter a non-negative display order.");
        }

        String name = category.getName().trim();
        MenuCategory existing = categoryDAO.findByName(name);
        if (existing != null && existing.getCategoryId() != category.getCategoryId()) {
            throw new ConflictException(DUPLICATE_CATEGORY);
        }

        category.setName(name);
        if (category.getCategoryId() == 0) {
            categoryDAO.insert(category);
        } else {
            categoryDAO.update(category);
        }
        return category;
    }

    /**
     * Removes an empty category (FR-05).
     *
     * @throws ConflictException if it still holds items — they would otherwise lose their category
     */
    public void deleteCategory(Session session, int categoryId) {
        RbacGuard.require(session, Permission.MANAGE_MENU);
        if (categoryDAO.findById(categoryId) == null) {
            throw new ValidationException("That category no longer exists.");
        }
        if (categoryDAO.countItems(categoryId) > 0) {
            throw new ConflictException(CATEGORY_NOT_EMPTY);
        }
        categoryDAO.delete(categoryId);
    }

    // --- Items (FR-06, FR-07) ---------------------------------------------------

    /** Every item, available or not — the management view. */
    public List<MenuItem> listItems() {
        return itemDAO.findAll();
    }

    public List<MenuItem> listItemsInCategory(int categoryId) {
        return itemDAO.findByCategory(categoryId);
    }

    /** Items the POS may sell right now: Available only (FR-07). Unguarded — order entry needs it. */
    public List<MenuItem> listOrderableItems() {
        return itemDAO.findOrderable();
    }

    /**
     * Creates or updates an item (FR-06).
     *
     * @throws ConflictException if the name is taken within the category (BR-08)
     * @throws ValidationException if a field breaches Appendix A
     */
    public MenuItem saveItem(Session session, MenuItem item) {
        RbacGuard.require(session, Permission.MANAGE_MENU);
        if (item == null) {
            throw new ValidationException("No item details were supplied.");
        }
        if (!Validation.hasLength(item.getName(), 2, 80)) {
            throw new ValidationException("Enter 2–80 characters for the item name.");
        }
        if (!Validation.isNonNegative(item.getPrice())) {
            throw new ValidationException("Enter a valid non-negative price.");
        }
        if (item.getDescription() != null && item.getDescription().length() > 255) {
            throw new ValidationException("The description may be at most 255 characters.");
        }
        if (categoryDAO.findById(item.getCategoryId()) == null) {
            throw new ValidationException("Select a category for this item.");
        }

        String name = item.getName().trim();
        MenuItem existing = itemDAO.findByNameInCategory(item.getCategoryId(), name);
        if (existing != null && existing.getItemId() != item.getItemId()) {
            throw new ConflictException(DUPLICATE_ITEM);
        }

        item.setName(name);
        // One rounding step to the stored DECIMAL(10,2) scale (BR-13, BR-16).
        item.setPrice(Money.round(item.getPrice()));
        if (item.getAvailability() == null) {
            item.setAvailability(Availability.AVAILABLE);
        }

        if (item.getItemId() == 0) {
            itemDAO.insert(item);
        } else {
            itemDAO.update(item);
        }
        return item;
    }

    /**
     * Toggles whether the POS offers the item (FR-07, BR-10). Marking it Unavailable leaves its
     * record and history intact and does not touch orders already holding it.
     */
    public void setAvailability(Session session, int itemId, Availability availability) {
        RbacGuard.require(session, Permission.MANAGE_MENU);
        if (availability == null) {
            throw new ValidationException("Select an availability.");
        }
        if (itemDAO.findById(itemId) == null) {
            throw new ValidationException("That menu item no longer exists.");
        }
        itemDAO.updateAvailability(itemId, availability);
    }

    /**
     * Removes an item, or — when it appears on any order — marks it Unavailable instead so the
     * order history keeps pointing at a real item (BR-10).
     *
     * @return true if the row was deleted, false if it was discontinued (soft-deleted) instead
     */
    public boolean deleteItem(Session session, int itemId) {
        RbacGuard.require(session, Permission.MANAGE_MENU);
        if (itemDAO.findById(itemId) == null) {
            throw new ValidationException("That menu item no longer exists.");
        }
        if (itemDAO.existsOnAnyOrder(itemId)) {
            itemDAO.updateAvailability(itemId, Availability.UNAVAILABLE);
            return false;
        }
        itemDAO.delete(itemId);
        return true;
    }

    /** The price currently on the item, or {@code null} when it no longer exists. */
    public BigDecimal currentPrice(int itemId) {
        MenuItem item = itemDAO.findById(itemId);
        return item == null ? null : item.getPrice();
    }
}
