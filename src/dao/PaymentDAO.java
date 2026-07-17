package dao;

import domain.Payment;
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
 * Reads and writes {@code payment} rows — one per finalised order (FR-15).
 *
 * <p>Parameterised prepared statements only (Principle VII). The {@code order_id} unique constraint
 * enforces the 1:1 relationship; the insert here is part of the finalisation transaction (BR-19),
 * so a payment is never written without its order also flipping to {@code Paid/Closed}.
 */
public final class PaymentDAO {

    private static final String SELECT_BY_ORDER =
        "SELECT payment_id, order_id, method_id, amount, amount_tendered, change_given, paid_at "
        + "FROM payment WHERE order_id = ?";

    private static final String SELECT_BY_RANGE =
        "SELECT payment_id, order_id, method_id, amount, amount_tendered, change_given, paid_at "
        + "FROM payment WHERE paid_at >= ? AND paid_at < ? ORDER BY paid_at";

    private static final String INSERT =
        "INSERT INTO payment (order_id, method_id, amount, amount_tendered, change_given, paid_at) "
        + "VALUES (?, ?, ?, ?, ?, ?)";

    private final ConnectionFactory connections;

    public PaymentDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** The payment for an order, or {@code null} if it is not yet finalised. */
    public Payment findByOrder(int orderId) {
        try (Connection connection = connections.getConnection()) {
            return findByOrder(connection, orderId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payment.", e);
        }
    }

    public Payment findByOrder(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ORDER)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Payments recorded in {@code [from, to)} — for reconciling a sales report by method. */
    public List<Payment> findByRange(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_RANGE)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
            List<Payment> payments = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    payments.add(map(rs));
                }
            }
            return payments;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payments.", e);
        }
    }

    public int insert(Connection connection, Payment payment) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payment.getOrderId());
            ps.setInt(2, payment.getMethodId());
            ps.setBigDecimal(3, payment.getAmount());
            if (payment.getAmountTendered() == null) {
                ps.setNull(4, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(4, payment.getAmountTendered());
            }
            if (payment.getChangeGiven() == null) {
                ps.setNull(5, java.sql.Types.DECIMAL);
            } else {
                ps.setBigDecimal(5, payment.getChangeGiven());
            }
            ps.setTimestamp(6, Timestamp.valueOf(payment.getPaidAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    payment.setPaymentId(keys.getInt(1));
                }
            }
            return payment.getPaymentId();
        }
    }

    private static Payment map(ResultSet rs) throws SQLException {
        Payment payment = new Payment();
        payment.setPaymentId(rs.getInt("payment_id"));
        payment.setOrderId(rs.getInt("order_id"));
        payment.setMethodId(rs.getInt("method_id"));
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setAmountTendered(rs.getBigDecimal("amount_tendered"));
        payment.setChangeGiven(rs.getBigDecimal("change_given"));
        Timestamp paidAt = rs.getTimestamp("paid_at");
        payment.setPaidAt(paidAt == null ? null : paidAt.toLocalDateTime());
        return payment;
    }
}
