package dao;

import domain.PaymentMethod;
import domain.enums.Status;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code payment_method} reference rows (FR-15).
 *
 * <p>Parameterised prepared statements only (Principle VII). Payment methods are reference data
 * administered through {@code SystemConfigService}; the POS reads them here to record which method
 * a finalised order was paid with.
 */
public final class PaymentMethodDAO {

    private static final String SELECT_BASE =
        "SELECT method_id, method_name, status FROM payment_method";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY method_id";

    private static final String SELECT_ACTIVE = SELECT_BASE + " WHERE status = 'Active' ORDER BY method_id";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE method_id = ?";

    private static final String COUNT_ACTIVE =
        "SELECT COUNT(*) FROM payment_method WHERE status = 'Active'";

    private static final String UPDATE_STATUS =
        "UPDATE payment_method SET status = ? WHERE method_id = ?";

    private final ConnectionFactory connections;

    public PaymentMethodDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<PaymentMethod> findAll() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ALL)) {
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payment methods.", e);
        }
    }

    /** Active methods only — what the POS offers at payment time (FR-15). */
    public List<PaymentMethod> findActive() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE)) {
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payment methods.", e);
        }
    }

    /** How many methods are currently Active — used to keep at least one (FR-15). */
    public int countActive() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(COUNT_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new PersistenceException("Could not count the active payment methods.", e);
        }
    }

    public void setStatus(int methodId, Status status) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, methodId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the payment method's status.", e);
        }
    }

    /** The method with this id, or {@code null} when absent. */
    public PaymentMethod findById(int methodId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, methodId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payment method.", e);
        }
    }

    public PaymentMethod findById(Connection connection, int methodId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, methodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private static List<PaymentMethod> mapAll(PreparedStatement ps) throws SQLException {
        List<PaymentMethod> methods = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                methods.add(map(rs));
            }
        }
        return methods;
    }

    private static PaymentMethod map(ResultSet rs) throws SQLException {
        PaymentMethod method = new PaymentMethod();
        method.setMethodId(rs.getInt("method_id"));
        method.setMethodName(rs.getString("method_name"));
        method.setStatus(Status.fromDb(rs.getString("status")));
        return method;
    }
}
