package dao;

import domain.StockMovement;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends {@code stock_movement} ledger rows (FR-18, FR-21).
 *
 * <p>Parameterised prepared statements only (Principle VII). The ledger is append-only: every
 * change to a stock item's on-hand is recorded here as a signed delta, written in the same
 * transaction as the {@code stock_item.quantity_on_hand} update so the running sum always
 * reconciles (BR-21, BR-24).
 */
public final class StockMovementDAO {

    private static final String INSERT =
        "INSERT INTO stock_movement (stock_item_id, po_item_id, movement_type, quantity_change, "
        + "moved_at, moved_by) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ITEM =
        "SELECT movement_id, stock_item_id, po_item_id, movement_type, quantity_change, moved_at, "
        + "moved_by FROM stock_movement WHERE stock_item_id = ? ORDER BY moved_at DESC, movement_id DESC";

    private final ConnectionFactory connections;

    public StockMovementDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public long insert(Connection connection, StockMovement movement) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, movement.getStockItemId());
            if (movement.getPoItemId() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, movement.getPoItemId());
            }
            ps.setString(3, movement.getMovementType().dbValue());
            ps.setBigDecimal(4, movement.getQuantityChange());
            ps.setTimestamp(5, Timestamp.valueOf(movement.getMovedAt()));
            ps.setInt(6, movement.getMovedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    movement.setMovementId(keys.getLong(1));
                }
            }
            return movement.getMovementId();
        }
    }

    /** The movement history for one item, most recent first — the audit trail behind its on-hand. */
    public List<StockMovement> findByItem(int stockItemId) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_ITEM)) {
            ps.setInt(1, stockItemId);
            List<StockMovement> movements = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    movements.add(map(rs));
                }
            }
            return movements;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the stock movements.", e);
        }
    }

    private static StockMovement map(ResultSet rs) throws SQLException {
        StockMovement m = new StockMovement();
        m.setMovementId(rs.getLong("movement_id"));
        m.setStockItemId(rs.getInt("stock_item_id"));
        int poItemId = rs.getInt("po_item_id");
        m.setPoItemId(rs.wasNull() ? null : poItemId);
        m.setMovementType(domain.enums.MovementType.fromDb(rs.getString("movement_type")));
        m.setQuantityChange(rs.getBigDecimal("quantity_change"));
        Timestamp movedAt = rs.getTimestamp("moved_at");
        m.setMovedAt(movedAt == null ? null : movedAt.toLocalDateTime());
        m.setMovedBy(rs.getInt("moved_by"));
        return m;
    }
}
