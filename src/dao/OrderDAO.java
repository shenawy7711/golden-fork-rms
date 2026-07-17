package dao;

import domain.Order;
import domain.enums.DiscountType;
import domain.enums.OrderStatus;
import domain.enums.OrderType;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code orders} header rows (FR-10, FR-15, FR-17).
 *
 * <p>Parameterised prepared statements only (Principle VII). An order is inserted Open with a
 * unique {@code order_number}; {@link #updateFinalised} writes the stored final figures and the
 * {@code Paid/Closed} status in the same transaction as the payment (BR-19), so a finalised bill is
 * never left half-written. The stored figures are authoritative forever — reports and receipts read
 * them back rather than recomputing (BR-20, BR-30).
 */
public final class OrderDAO {

    private static final String SELECT_BASE =
        "SELECT order_id, order_number, order_type, table_id, status, created_by, created_at, "
        + "closed_at, subtotal, discount_type, discount_value, discount_amount, tax_rate, "
        + "tax_amount, total FROM orders";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE order_id = ?";

    private static final String SELECT_OPEN = SELECT_BASE + " WHERE status = 'Open' ORDER BY created_at";

    private static final String SELECT_BY_RANGE =
        SELECT_BASE + " WHERE created_at >= ? AND created_at < ? ORDER BY created_at";

    private static final String SELECT_FINALISED_BY_RANGE =
        SELECT_BASE + " WHERE status = 'Paid/Closed' AND closed_at >= ? AND closed_at < ? "
        + "ORDER BY closed_at";

    private static final String NEXT_SEQUENCE =
        "SELECT COALESCE(MAX(order_id), 0) + 1 AS next_seq FROM orders";

    private static final String INSERT =
        "INSERT INTO orders (order_number, order_type, table_id, status, created_by, created_at, "
        + "subtotal, discount_type, discount_value, discount_amount, tax_rate, tax_amount, total) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_FIGURES =
        "UPDATE orders SET subtotal = ?, discount_type = ?, discount_value = ?, discount_amount = ?, "
        + "tax_rate = ?, tax_amount = ?, total = ? WHERE order_id = ?";

    private static final String UPDATE_FINALISED =
        "UPDATE orders SET status = ?, closed_at = ?, subtotal = ?, discount_type = ?, "
        + "discount_value = ?, discount_amount = ?, tax_rate = ?, tax_amount = ?, total = ? "
        + "WHERE order_id = ? AND status = 'Open'";

    private static final String UPDATE_STATUS =
        "UPDATE orders SET status = ? WHERE order_id = ?";

    private final ConnectionFactory connections;

    public OrderDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** The next order sequence number — {@code MAX(order_id)+1}, used to mint {@code order_number}. */
    public int nextSequence(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(NEXT_SEQUENCE);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("next_seq") : 1;
        }
    }

    public Order findById(int orderId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, orderId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the order.", e);
        }
    }

    public Order findById(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Every Open order — the POS "resume order" list. */
    public List<Order> findOpen() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_OPEN)) {
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the open orders.", e);
        }
    }

    /** Orders created in {@code [from, to)} — for reporting over a date range. */
    public List<Order> findByCreatedRange(LocalDateTime from, LocalDateTime to) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_RANGE)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the orders.", e);
        }
    }

    /** Finalised orders closed in {@code [from, to)} — the reconcilable basis for sales reports (BR-30). */
    public List<Order> findFinalisedByRange(LocalDateTime from, LocalDateTime to) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_FINALISED_BY_RANGE)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the finalised orders.", e);
        }
    }

    public int insert(Connection connection, Order order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, order.getOrderNumber());
            ps.setString(2, order.getOrderType().dbValue());
            if (order.getTableId() == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, order.getTableId());
            }
            ps.setString(4, (order.getStatus() == null ? OrderStatus.OPEN : order.getStatus()).dbValue());
            ps.setInt(5, order.getCreatedBy());
            ps.setTimestamp(6, Timestamp.valueOf(order.getCreatedAt()));
            ps.setBigDecimal(7, order.getSubtotal());
            ps.setString(8, (order.getDiscountType() == null ? DiscountType.NONE : order.getDiscountType()).dbValue());
            ps.setBigDecimal(9, order.getDiscountValue());
            ps.setBigDecimal(10, order.getDiscountAmount());
            ps.setBigDecimal(11, order.getTaxRate());
            ps.setBigDecimal(12, order.getTaxAmount());
            ps.setBigDecimal(13, order.getTotal());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    order.setOrderId(keys.getInt(1));
                }
            }
            return order.getOrderId();
        }
    }

    /** Rewrites the running figures on an Open order after a line change. */
    public void updateFigures(Connection connection, Order order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_FIGURES)) {
            ps.setBigDecimal(1, order.getSubtotal());
            ps.setString(2, (order.getDiscountType() == null ? DiscountType.NONE : order.getDiscountType()).dbValue());
            ps.setBigDecimal(3, order.getDiscountValue());
            ps.setBigDecimal(4, order.getDiscountAmount());
            ps.setBigDecimal(5, order.getTaxRate());
            ps.setBigDecimal(6, order.getTaxAmount());
            ps.setBigDecimal(7, order.getTotal());
            ps.setInt(8, order.getOrderId());
            ps.executeUpdate();
        }
    }

    /**
     * Writes the finalised figures and flips the order to {@code Paid/Closed} — but only if it is
     * still {@code Open}. The {@code AND status = 'Open'} guard means a double-finalise touches zero
     * rows; the caller treats a zero row count as "already finalised" and rolls the transaction back
     * (BR-19).
     *
     * @return the number of rows updated (1 on success, 0 if the order was no longer Open)
     */
    public int updateFinalised(Connection connection, Order order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_FINALISED)) {
            ps.setString(1, OrderStatus.PAID_CLOSED.dbValue());
            ps.setTimestamp(2, Timestamp.valueOf(order.getClosedAt()));
            ps.setBigDecimal(3, order.getSubtotal());
            ps.setString(4, (order.getDiscountType() == null ? DiscountType.NONE : order.getDiscountType()).dbValue());
            ps.setBigDecimal(5, order.getDiscountValue());
            ps.setBigDecimal(6, order.getDiscountAmount());
            ps.setBigDecimal(7, order.getTaxRate());
            ps.setBigDecimal(8, order.getTaxAmount());
            ps.setBigDecimal(9, order.getTotal());
            ps.setInt(10, order.getOrderId());
            return ps.executeUpdate();
        }
    }

    /** Sets an order's status directly — used to void an Open order (Open → Cancelled). */
    public void updateStatus(Connection connection, int orderId, OrderStatus status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    private static List<Order> mapAll(PreparedStatement ps) throws SQLException {
        List<Order> orders = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(map(rs));
            }
        }
        return orders;
    }

    private static Order map(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setOrderId(rs.getInt("order_id"));
        order.setOrderNumber(rs.getString("order_number"));
        order.setOrderType(OrderType.fromDb(rs.getString("order_type")));
        int tableId = rs.getInt("table_id");
        order.setTableId(rs.wasNull() ? null : tableId);
        order.setStatus(OrderStatus.fromDb(rs.getString("status")));
        order.setCreatedBy(rs.getInt("created_by"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        order.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp closedAt = rs.getTimestamp("closed_at");
        order.setClosedAt(closedAt == null ? null : closedAt.toLocalDateTime());
        order.setSubtotal(rs.getBigDecimal("subtotal"));
        order.setDiscountType(DiscountType.fromDb(rs.getString("discount_type")));
        order.setDiscountValue(rs.getBigDecimal("discount_value"));
        order.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        order.setTaxRate(rs.getBigDecimal("tax_rate"));
        order.setTaxAmount(rs.getBigDecimal("tax_amount"));
        order.setTotal(rs.getBigDecimal("total"));
        return order;
    }
}
