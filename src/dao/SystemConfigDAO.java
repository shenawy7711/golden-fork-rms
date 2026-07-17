package dao;

import domain.PaymentMethod;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes reference/system data: the {@code system_config} key-value entries and the
 * {@code payment_method} reference list (FR-31).
 *
 * <p>Parameterised prepared statements only — no string-concatenated SQL (Principle VII).
 *
 * <p>Each operation comes in two forms: one taking a caller-supplied {@link Connection} (so it
 * enlists in an open transaction) and one that opens and closes its own.
 *
 * <p>Note: {@code payment_method} is a fixed reference set per data-model.md §2.10 — it carries no
 * {@code status} column, so methods are listed but not activated/deactivated here.
 */
public final class SystemConfigDAO {

    private static final String SELECT_ALL_CONFIG =
        "SELECT config_id, config_key, config_value, updated_by, updated_at FROM system_config";

    private static final String SELECT_CONFIG_BY_KEY =
        "SELECT config_value FROM system_config WHERE config_key = ?";

    // The unique key on config_key makes this an update-if-present, insert-if-absent in one round trip.
    private static final String UPSERT_CONFIG =
        "INSERT INTO system_config (config_key, config_value, updated_by, updated_at) VALUES (?, ?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), "
        + "updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)";

    private static final String SELECT_PAYMENT_METHODS =
        "SELECT method_id, method_name FROM payment_method ORDER BY method_id";

    private final ConnectionFactory connections;

    public SystemConfigDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** Every config entry, keyed by {@code config_key}. */
    public Map<String, String> findAllValues() {
        try (Connection connection = connections.getConnection()) {
            return findAllValues(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read system configuration.", e);
        }
    }

    public Map<String, String> findAllValues(Connection connection) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL_CONFIG);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.put(rs.getString("config_key"), rs.getString("config_value"));
            }
        }
        return values;
    }

    /** The raw value for a key, or {@code null} when the key is absent. */
    public String findValue(String key) {
        try (Connection connection = connections.getConnection()) {
            return findValue(connection, key);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the setting '" + key + "'.", e);
        }
    }

    public String findValue(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_CONFIG_BY_KEY)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Inserts or updates a key, recording who changed it and when (FR-31 audit).
     *
     * @param updatedBy the acting administrator's user id, or {@code null} for a system write
     */
    public void upsertValue(String key, String value, Integer updatedBy) {
        try (Connection connection = connections.getConnection()) {
            upsertValue(connection, key, value, updatedBy);
        } catch (SQLException e) {
            throw new PersistenceException("Could not save the setting '" + key + "'.", e);
        }
    }

    public void upsertValue(Connection connection, String key, String value, Integer updatedBy)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_CONFIG)) {
            ps.setString(1, key);
            ps.setString(2, value);
            if (updatedBy == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, updatedBy);
            }
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /** The fixed payment-method reference list (FR-15). */
    public List<PaymentMethod> listPaymentMethods() {
        try (Connection connection = connections.getConnection()) {
            return listPaymentMethods(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the payment methods.", e);
        }
    }

    public List<PaymentMethod> listPaymentMethods(Connection connection) throws SQLException {
        List<PaymentMethod> methods = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_PAYMENT_METHODS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PaymentMethod method = new PaymentMethod();
                method.setMethodId(rs.getInt("method_id"));
                method.setMethodName(rs.getString("method_name"));
                methods.add(method);
            }
        }
        return methods;
    }
}
