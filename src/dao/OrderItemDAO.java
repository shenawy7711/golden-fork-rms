package dao;

import domain.OrderItem;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code order_item} lines (FR-11).
 *
 * <p>Parameterised prepared statements only (Principle VII). Each line stores its {@code unit_price}
 * as a snapshot taken when the line was added, and its {@code line_total} as computed by
 * {@code BillingService}; a later menu price edit never rewrites an existing line (BR-15).
 */
public final class OrderItemDAO {

    private static final String SELECT_BY_ORDER =
        "SELECT order_item_id, order_id, item_id, quantity, unit_price, line_total "
        + "FROM order_item WHERE order_id = ? ORDER BY order_item_id";

    private static final String SELECT_BY_ID =
        "SELECT order_item_id, order_id, item_id, quantity, unit_price, line_total "
        + "FROM order_item WHERE order_item_id = ?";

    private static final String INSERT =
        "INSERT INTO order_item (order_id, item_id, quantity, unit_price, line_total) "
        + "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE order_item SET quantity = ?, unit_price = ?, line_total = ? WHERE order_item_id = ?";

    private static final String DELETE = "DELETE FROM order_item WHERE order_item_id = ?";

    private final ConnectionFactory connections;

    public OrderItemDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<OrderItem> findByOrder(int orderId) {
        try (Connection connection = connections.getConnection()) {
            return findByOrder(connection, orderId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the order lines.", e);
        }
    }

    public List<OrderItem> findByOrder(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ORDER)) {
            ps.setInt(1, orderId);
            return mapAll(ps);
        }
    }

    public OrderItem findById(Connection connection, int orderItemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, orderItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int insert(Connection connection, OrderItem line) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, line.getOrderId());
            ps.setInt(2, line.getItemId());
            ps.setInt(3, line.getQuantity());
            ps.setBigDecimal(4, line.getUnitPrice());
            ps.setBigDecimal(5, line.getLineTotal());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    line.setOrderItemId(keys.getInt(1));
                }
            }
            return line.getOrderItemId();
        }
    }

    /** Rewrites a line's quantity and recomputed total — e.g. adding to an existing line. */
    public void update(Connection connection, OrderItem line) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setInt(1, line.getQuantity());
            ps.setBigDecimal(2, line.getUnitPrice());
            ps.setBigDecimal(3, line.getLineTotal());
            ps.setInt(4, line.getOrderItemId());
            ps.executeUpdate();
        }
    }

    public void delete(Connection connection, int orderItemId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, orderItemId);
            ps.executeUpdate();
        }
    }

    private static List<OrderItem> mapAll(PreparedStatement ps) throws SQLException {
        List<OrderItem> lines = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lines.add(map(rs));
            }
        }
        return lines;
    }

    private static OrderItem map(ResultSet rs) throws SQLException {
        OrderItem line = new OrderItem();
        line.setOrderItemId(rs.getInt("order_item_id"));
        line.setOrderId(rs.getInt("order_id"));
        line.setItemId(rs.getInt("item_id"));
        line.setQuantity(rs.getInt("quantity"));
        line.setUnitPrice(rs.getBigDecimal("unit_price"));
        line.setLineTotal(rs.getBigDecimal("line_total"));
        return line;
    }
}
