package dao;

import domain.PurchaseOrder;
import domain.enums.PoStatus;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code purchase_order} header rows (FR-20, FR-21).
 *
 * <p>Parameterised prepared statements only (Principle VII). Creating a PO does not touch stock
 * (BR-23); the status is advanced by {@link #updateStatus} inside the atomic receive transaction as
 * deliveries arrive (BR-24).
 */
public final class PurchaseOrderDAO {

    private static final String SELECT_BASE =
        "SELECT po_id, po_number, supplier_id, status, created_by, ordered_at, expected_date "
        + "FROM purchase_order";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY ordered_at DESC";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE po_id = ?";

    private static final String NEXT_SEQUENCE =
        "SELECT COALESCE(MAX(po_id), 0) + 1 AS next_seq FROM purchase_order";

    private static final String INSERT =
        "INSERT INTO purchase_order (po_number, supplier_id, status, created_by, ordered_at, "
        + "expected_date) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS =
        "UPDATE purchase_order SET status = ? WHERE po_id = ?";

    private final ConnectionFactory connections;

    public PurchaseOrderDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<PurchaseOrder> findAll() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<PurchaseOrder> orders = new ArrayList<>();
            while (rs.next()) {
                orders.add(map(rs));
            }
            return orders;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the purchase orders.", e);
        }
    }

    public PurchaseOrder findById(int poId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, poId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the purchase order.", e);
        }
    }

    public PurchaseOrder findById(Connection connection, int poId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int nextSequence(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(NEXT_SEQUENCE);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("next_seq") : 1;
        }
    }

    public int insert(Connection connection, PurchaseOrder order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, order.getPoNumber());
            ps.setInt(2, order.getSupplierId());
            ps.setString(3, (order.getStatus() == null ? PoStatus.ORDERED : order.getStatus()).dbValue());
            ps.setInt(4, order.getCreatedBy());
            ps.setTimestamp(5, Timestamp.valueOf(order.getOrderedAt()));
            if (order.getExpectedDate() == null) {
                ps.setNull(6, java.sql.Types.DATE);
            } else {
                ps.setDate(6, Date.valueOf(order.getExpectedDate()));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    order.setPoId(keys.getInt(1));
                }
            }
            return order.getPoId();
        }
    }

    public void updateStatus(Connection connection, int poId, PoStatus status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, poId);
            ps.executeUpdate();
        }
    }

    private static PurchaseOrder map(ResultSet rs) throws SQLException {
        PurchaseOrder order = new PurchaseOrder();
        order.setPoId(rs.getInt("po_id"));
        order.setPoNumber(rs.getString("po_number"));
        order.setSupplierId(rs.getInt("supplier_id"));
        order.setStatus(PoStatus.fromDb(rs.getString("status")));
        order.setCreatedBy(rs.getInt("created_by"));
        Timestamp orderedAt = rs.getTimestamp("ordered_at");
        order.setOrderedAt(orderedAt == null ? null : orderedAt.toLocalDateTime());
        Date expected = rs.getDate("expected_date");
        order.setExpectedDate(expected == null ? null : expected.toLocalDate());
        return order;
    }
}
