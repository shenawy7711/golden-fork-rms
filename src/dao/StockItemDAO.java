package dao;

import domain.StockItem;
import domain.enums.Status;
import service.exception.PersistenceException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code stock_item} rows (FR-18, FR-22).
 *
 * <p>Parameterised prepared statements only (Principle VII). {@code quantity_on_hand} is never set
 * to an arbitrary value through this DAO — it moves only via {@link #addOnHand}, the signed delta
 * that {@code InventoryService}/{@code PurchasingService} apply in the same transaction as the
 * {@code stock_movement} ledger row (BR-21). {@link #findLowStock} backs the reorder alert (BR-25).
 */
public final class StockItemDAO {

    private static final String SELECT_BASE =
        "SELECT stock_item_id, name, unit_of_measure, reorder_level, quantity_on_hand, status "
        + "FROM stock_item";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY name";

    private static final String SELECT_ACTIVE = SELECT_BASE + " WHERE status = 'Active' ORDER BY name";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE stock_item_id = ?";

    private static final String SELECT_BY_NAME = SELECT_BASE + " WHERE name = ?";

    private static final String SELECT_LOW_STOCK =
        SELECT_BASE + " WHERE status = 'Active' AND quantity_on_hand <= reorder_level ORDER BY name";

    private static final String INSERT =
        "INSERT INTO stock_item (name, unit_of_measure, reorder_level, quantity_on_hand, status) "
        + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE stock_item SET name = ?, unit_of_measure = ?, reorder_level = ?, status = ? "
        + "WHERE stock_item_id = ?";

    private static final String UPDATE_STATUS =
        "UPDATE stock_item SET status = ? WHERE stock_item_id = ?";

    private static final String ADD_ON_HAND =
        "UPDATE stock_item SET quantity_on_hand = quantity_on_hand + ? WHERE stock_item_id = ?";

    private final ConnectionFactory connections;

    public StockItemDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<StockItem> findAll() {
        return query(SELECT_ALL);
    }

    public List<StockItem> findActive() {
        return query(SELECT_ACTIVE);
    }

    public List<StockItem> findLowStock() {
        return query(SELECT_LOW_STOCK);
    }

    public StockItem findById(int stockItemId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, stockItemId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the stock item.", e);
        }
    }

    public StockItem findById(Connection connection, int stockItemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, stockItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public StockItem findByName(String name) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the stock item.", e);
        }
    }

    public int insert(StockItem item) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getUnitOfMeasure());
            ps.setBigDecimal(3, item.getReorderLevel());
            ps.setBigDecimal(4, item.getQuantityOnHand());
            ps.setString(5, (item.getStatus() == null ? Status.ACTIVE : item.getStatus()).dbValue());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setStockItemId(keys.getInt(1));
                }
            }
            return item.getStockItemId();
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the stock item.", e);
        }
    }

    /** Updates the editable fields — but never {@code quantity_on_hand}, which moves only via ledger. */
    public void update(StockItem item) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getUnitOfMeasure());
            ps.setBigDecimal(3, item.getReorderLevel());
            ps.setString(4, (item.getStatus() == null ? Status.ACTIVE : item.getStatus()).dbValue());
            ps.setInt(5, item.getStockItemId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the stock item.", e);
        }
    }

    public void updateStatus(int stockItemId, Status status) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, stockItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the stock item's status.", e);
        }
    }

    /**
     * Adds a signed delta to on-hand, in the caller's transaction. The {@code chk_onhand_nonneg}
     * constraint rejects a negative result, so an over-large adjustment is refused by the database
     * and the whole transaction rolls back (BR-21).
     */
    public void addOnHand(Connection connection, int stockItemId, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ADD_ON_HAND)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, stockItemId);
            ps.executeUpdate();
        }
    }

    private List<StockItem> query(String sql) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<StockItem> items = new ArrayList<>();
            while (rs.next()) {
                items.add(map(rs));
            }
            return items;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the stock items.", e);
        }
    }

    private static StockItem map(ResultSet rs) throws SQLException {
        StockItem item = new StockItem();
        item.setStockItemId(rs.getInt("stock_item_id"));
        item.setName(rs.getString("name"));
        item.setUnitOfMeasure(rs.getString("unit_of_measure"));
        item.setReorderLevel(rs.getBigDecimal("reorder_level"));
        item.setQuantityOnHand(rs.getBigDecimal("quantity_on_hand"));
        item.setStatus(Status.fromDb(rs.getString("status")));
        return item;
    }
}
