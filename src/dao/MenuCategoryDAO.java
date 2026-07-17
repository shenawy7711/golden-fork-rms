package dao;

import domain.MenuCategory;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code menu_category} rows (FR-05).
 *
 * <p>Parameterised prepared statements only (Principle VII). Business rules (unique name BR-08,
 * blocking the delete of a non-empty category) live in {@code MenuService}, which uses
 * {@link #findByName} and {@link #countItems} to decide.
 */
public final class MenuCategoryDAO {

    private static final String SELECT_BASE =
        "SELECT category_id, name, display_order FROM menu_category";

    // display_order is nullable; NULLs sort last so unordered categories fall to the bottom
    // rather than jumping to the top.
    private static final String SELECT_ALL =
        SELECT_BASE + " ORDER BY display_order IS NULL, display_order, name";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE category_id = ?";

    private static final String SELECT_BY_NAME = SELECT_BASE + " WHERE name = ?";

    private static final String INSERT =
        "INSERT INTO menu_category (name, display_order) VALUES (?, ?)";

    private static final String UPDATE =
        "UPDATE menu_category SET name = ?, display_order = ? WHERE category_id = ?";

    private static final String DELETE = "DELETE FROM menu_category WHERE category_id = ?";

    private static final String COUNT_ITEMS =
        "SELECT COUNT(*) FROM menu_item WHERE category_id = ?";

    private final ConnectionFactory connections;

    public MenuCategoryDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** Every category, in display order. */
    public List<MenuCategory> findAll() {
        try (Connection connection = connections.getConnection()) {
            return findAll(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu categories.", e);
        }
    }

    public List<MenuCategory> findAll(Connection connection) throws SQLException {
        List<MenuCategory> categories = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(map(rs));
            }
        }
        return categories;
    }

    /** The category with this id, or {@code null} when absent. */
    public MenuCategory findById(int categoryId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, categoryId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu category.", e);
        }
    }

    public MenuCategory findById(Connection connection, int categoryId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The category with this name (case-insensitive per the column collation), or {@code null}. */
    public MenuCategory findByName(String name) {
        try (Connection connection = connections.getConnection()) {
            return findByName(connection, name);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the menu category.", e);
        }
    }

    public MenuCategory findByName(Connection connection, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Inserts and returns the generated id; also sets it on {@code category}. */
    public int insert(MenuCategory category) {
        try (Connection connection = connections.getConnection()) {
            return insert(connection, category);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the menu category.", e);
        }
    }

    public int insert(Connection connection, MenuCategory category) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            setNullableInt(ps, 2, category.getDisplayOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setCategoryId(keys.getInt(1));
                }
            }
            return category.getCategoryId();
        }
    }

    public void update(MenuCategory category) {
        try (Connection connection = connections.getConnection()) {
            update(connection, category);
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the menu category.", e);
        }
    }

    public void update(Connection connection, MenuCategory category) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, category.getName());
            setNullableInt(ps, 2, category.getDisplayOrder());
            ps.setInt(3, category.getCategoryId());
            ps.executeUpdate();
        }
    }

    /** Removes the category. {@code MenuService} checks it is empty first (FR-05). */
    public void delete(int categoryId) {
        try (Connection connection = connections.getConnection()) {
            delete(connection, categoryId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not delete the menu category.", e);
        }
    }

    public void delete(Connection connection, int categoryId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, categoryId);
            ps.executeUpdate();
        }
    }

    /** How many items sit in this category — the input to the "no non-empty delete" rule (FR-05). */
    public int countItems(int categoryId) {
        try (Connection connection = connections.getConnection()) {
            return countItems(connection, categoryId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not count the items in the category.", e);
        }
    }

    public int countItems(Connection connection, int categoryId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(COUNT_ITEMS)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static MenuCategory map(ResultSet rs) throws SQLException {
        MenuCategory category = new MenuCategory();
        category.setCategoryId(rs.getInt("category_id"));
        category.setName(rs.getString("name"));
        int displayOrder = rs.getInt("display_order");
        category.setDisplayOrder(rs.wasNull() ? null : displayOrder);
        return category;
    }
}
