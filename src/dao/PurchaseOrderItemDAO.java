package dao;

import domain.PurchaseOrderItem;
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
 * Reads and writes {@code purchase_order_item} lines (FR-20, FR-21).
 *
 * <p>Parameterised prepared statements only (Principle VII). {@link #addReceived} bumps a line's
 * {@code received_qty} inside the receive transaction; the {@code chk_received_range} constraint
 * (0 ≤ received ≤ ordered) is the database's backstop against over-receipt (BR-24).
 */
public final class PurchaseOrderItemDAO {

    private static final String SELECT_BY_PO =
        "SELECT po_item_id, po_id, stock_item_id, ordered_qty, received_qty, unit_cost "
        + "FROM purchase_order_item WHERE po_id = ? ORDER BY po_item_id";

    private static final String INSERT =
        "INSERT INTO purchase_order_item (po_id, stock_item_id, ordered_qty, received_qty, unit_cost) "
        + "VALUES (?, ?, ?, ?, ?)";

    private static final String ADD_RECEIVED =
        "UPDATE purchase_order_item SET received_qty = received_qty + ? WHERE po_item_id = ?";

    private final ConnectionFactory connections;

    public PurchaseOrderItemDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<PurchaseOrderItem> findByPo(int poId) {
        try (Connection connection = connections.getConnection()) {
            return findByPo(connection, poId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the purchase-order lines.", e);
        }
    }

    public List<PurchaseOrderItem> findByPo(Connection connection, int poId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_PO)) {
            ps.setInt(1, poId);
            List<PurchaseOrderItem> lines = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(map(rs));
                }
            }
            return lines;
        }
    }

    public int insert(Connection connection, PurchaseOrderItem line) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, line.getPoId());
            ps.setInt(2, line.getStockItemId());
            ps.setBigDecimal(3, line.getOrderedQty());
            ps.setBigDecimal(4, line.getReceivedQty() == null ? BigDecimal.ZERO : line.getReceivedQty());
            if (line.getUnitCost() == null) {
                ps.setNull(5, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(5, line.getUnitCost());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    line.setPoItemId(keys.getInt(1));
                }
            }
            return line.getPoItemId();
        }
    }

    /** Adds to a line's received quantity, in the caller's transaction (BR-24). */
    public void addReceived(Connection connection, int poItemId, BigDecimal delta) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ADD_RECEIVED)) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, poItemId);
            ps.executeUpdate();
        }
    }

    private static PurchaseOrderItem map(ResultSet rs) throws SQLException {
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setPoItemId(rs.getInt("po_item_id"));
        line.setPoId(rs.getInt("po_id"));
        line.setStockItemId(rs.getInt("stock_item_id"));
        line.setOrderedQty(rs.getBigDecimal("ordered_qty"));
        line.setReceivedQty(rs.getBigDecimal("received_qty"));
        line.setUnitCost(rs.getBigDecimal("unit_cost"));
        return line;
    }
}
