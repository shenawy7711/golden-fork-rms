package dao;

import domain.MenuItem;
import domain.enums.Availability;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code menu_item} rows (FR-06, FR-07).
 *
 * <p>Parameterised prepared statements only (Principle VII). Name-unique-within-category (the
 * {@code uq_item_per_category} constraint) is checked by {@code MenuService} via
 * {@link #findByNameInCategory} so the user gets the FRD's message rather than a raw SQL error.
 */
public final class MenuItemDAO {

    private static final String SELECT_BASE =
        "SELECT item_id, category_id, name, price, availability, description FROM menu_item";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY name";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE item_id = ?";

    private static final String SELECT_BY_CATEGORY =
        SELECT_BASE + " WHERE category_id = ? ORDER BY name";

    private static final String SELECT_BY_NAME_IN_CATEGORY =
        SELECT_BASE + " WHERE category_id = ? AND name = ?";

    // Order entry offers Available items only (FR-07); an Unavailable item keeps its row and its
    // history but leaves the POS.
    private static final String SELECT_ORDERABLE =
        SELECT_BASE + " WHERE availability = ? ORDER BY name";

    private static final String INSERT =
        "INSERT INTO menu_item (category_id, name, price, availability, description) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE menu_item SET category_id = ?, name = ?, price = ?, availability = ?, description = ? "
        + "WHERE item_id = ?";

    private static final String UPDATE_AVAILABILITY =
        "UPDATE menu_item SET availability = ? WHERE item_id = ?";

    private static final String DELETE = "DELETE FROM menu_item WHERE item_id = ?";

    private static final String EXISTS_ON_ORDER =
        "SELECT 1 FROM order_item WHERE item_id = ? LIMIT 1";

    private final ConnectionFactory connections;

    public MenuItemDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<MenuItem> findAll() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ALL)) {
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu items.", e);
        }
    }

    /** The item with this id, or {@code null} when absent. */
    public MenuItem findById(int itemId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, itemId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu item.", e);
        }
    }

    public MenuItem findById(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<MenuItem> findByCategory(int categoryId) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_CATEGORY)) {
            ps.setInt(1, categoryId);
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu items.", e);
        }
    }

    /** The item with this name inside this category, or {@code null} — the BR-08 uniqueness probe. */
    public MenuItem findByNameInCategory(int categoryId, String name) {
        try (Connection connection = connections.getConnection()) {
            return findByNameInCategory(connection, categoryId, name);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu item.", e);
        }
    }

    public MenuItem findByNameInCategory(Connection connection, int categoryId, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_NAME_IN_CATEGORY)) {
            ps.setInt(1, categoryId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Items the POS may sell right now: Available only (FR-07). */
    public List<MenuItem> findOrderable() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ORDERABLE)) {
            ps.setString(1, Availability.AVAILABLE.dbValue());
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the orderable items.", e);
        }
    }

    public int insert(MenuItem item) {
        try (Connection connection = connections.getConnection()) {
            return insert(connection, item);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the menu item.", e);
        }
    }

    public int insert(Connection connection, MenuItem item) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getName());
            ps.setBigDecimal(3, item.getPrice());
            ps.setString(4, item.getAvailability().dbValue());
            ps.setString(5, item.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setItemId(keys.getInt(1));
                }
            }
            return item.getItemId();
        }
    }

    /**
     * Updates the item. A price change here affects future order lines only — existing lines hold
     * their own unit-price snapshot, so finalised bills are never re-priced (BR-09, BR-15).
     */
    public void update(MenuItem item) {
        try (Connection connection = connections.getConnection()) {
            update(connection, item);
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the menu item.", e);
        }
    }

    public void update(Connection connection, MenuItem item) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getName());
            ps.setBigDecimal(3, item.getPrice());
            ps.setString(4, item.getAvailability().dbValue());
            ps.setString(5, item.getDescription());
            ps.setInt(6, item.getItemId());
            ps.executeUpdate();
        }
    }

    /** The "86 an item" toggle (FR-07, BR-10). */
    public void updateAvailability(int itemId, Availability availability) {
        try (Connection connection = connections.getConnection()) {
            updateAvailability(connection, itemId, availability);
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the item's availability.", e);
        }
    }

    public void updateAvailability(Connection connection, int itemId, Availability availability) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_AVAILABILITY)) {
            ps.setString(1, availability.dbValue());
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
    }

    /**
     * Hard-deletes the item. Only valid when it appears on no order — one that does is marked
     * Unavailable instead (BR-10), a decision {@code MenuService} makes.
     */
    public void delete(int itemId) {
        try (Connection connection = connections.getConnection()) {
            delete(connection, itemId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not delete the menu item.", e);
        }
    }

    public void delete(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        }
    }

    /** True when the item appears on any order — i.e. deleting it would break history (BR-10). */
    public boolean existsOnAnyOrder(int itemId) {
        try (Connection connection = connections.getConnection()) {
            return existsOnAnyOrder(connection, itemId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not check the item's order history.", e);
        }
    }

    public boolean existsOnAnyOrder(Connection connection, int itemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_ON_ORDER)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<MenuItem> mapAll(PreparedStatement ps) throws SQLException {
        List<MenuItem> items = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(map(rs));
            }
        }
        return items;
    }

    private static MenuItem map(ResultSet rs) throws SQLException {
        MenuItem item = new MenuItem();
        item.setItemId(rs.getInt("item_id"));
        item.setCategoryId(rs.getInt("category_id"));
        item.setName(rs.getString("name"));
        item.setPrice(rs.getBigDecimal("price"));
        item.setAvailability(Availability.fromDb(rs.getString("availability")));
        item.setDescription(rs.getString("description"));
        return item;
    }
}
