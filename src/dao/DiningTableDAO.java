package dao;

import domain.DiningTable;
import domain.enums.OrderStatus;
import domain.enums.ReservationStatus;
import domain.enums.TableStatus;
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
 * Reads and writes {@code dining_table} rows (FR-08, FR-09).
 *
 * <p>Parameterised prepared statements only (Principle VII). The table state machine (BR-12) is
 * enforced by {@code TableService}; this DAO writes whatever status it is given.
 */
public final class DiningTableDAO {

    private static final String SELECT_BASE =
        "SELECT table_id, label, capacity, status FROM dining_table";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY label";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE table_id = ?";

    private static final String SELECT_BY_LABEL = SELECT_BASE + " WHERE label = ?";

    private static final String SELECT_BY_STATUS = SELECT_BASE + " WHERE status = ? ORDER BY label";

    private static final String INSERT =
        "INSERT INTO dining_table (label, capacity, status) VALUES (?, ?, ?)";

    private static final String UPDATE =
        "UPDATE dining_table SET label = ?, capacity = ? WHERE table_id = ?";

    private static final String UPDATE_STATUS =
        "UPDATE dining_table SET status = ? WHERE table_id = ?";

    private static final String DELETE = "DELETE FROM dining_table WHERE table_id = ?";

    private static final String EXISTS_OPEN_ORDER =
        "SELECT 1 FROM orders WHERE table_id = ? AND status = ? LIMIT 1";

    // "Future reservation" means one still live (Booked or Seated) that has not yet passed. A
    // Cancelled/Completed/No-Show booking holds nothing and must not block a delete (BR-27).
    private static final String EXISTS_FUTURE_RESERVATION =
        "SELECT 1 FROM reservation WHERE table_id = ? AND reservation_datetime >= ? "
        + "AND status IN (?, ?) LIMIT 1";

    private final ConnectionFactory connections;

    public DiningTableDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<DiningTable> findAll() {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_ALL)) {
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the dining tables.", e);
        }
    }

    /** The table with this id, or {@code null} when absent. */
    public DiningTable findById(int tableId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, tableId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the dining table.", e);
        }
    }

    public DiningTable findById(Connection connection, int tableId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The table with this label (case-insensitive per collation), or {@code null} — the BR-11 probe. */
    public DiningTable findByLabel(String label) {
        try (Connection connection = connections.getConnection()) {
            return findByLabel(connection, label);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the dining table.", e);
        }
    }

    public DiningTable findByLabel(Connection connection, String label) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_LABEL)) {
            ps.setString(1, label);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<DiningTable> findByStatus(TableStatus status) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_STATUS)) {
            ps.setString(1, status.dbValue());
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the dining tables.", e);
        }
    }

    public int insert(DiningTable table) {
        try (Connection connection = connections.getConnection()) {
            return insert(connection, table);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the dining table.", e);
        }
    }

    public int insert(Connection connection, DiningTable table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, table.getLabel());
            ps.setInt(2, table.getCapacity());
            ps.setString(3, (table.getStatus() == null ? TableStatus.FREE : table.getStatus()).dbValue());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    table.setTableId(keys.getInt(1));
                }
            }
            return table.getTableId();
        }
    }

    /** Updates label and capacity. Status moves only through {@link #updateStatus} (BR-12). */
    public void update(DiningTable table) {
        try (Connection connection = connections.getConnection()) {
            update(connection, table);
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the dining table.", e);
        }
    }

    public void update(Connection connection, DiningTable table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, table.getLabel());
            ps.setInt(2, table.getCapacity());
            ps.setInt(3, table.getTableId());
            ps.executeUpdate();
        }
    }

    public void updateStatus(int tableId, TableStatus status) {
        try (Connection connection = connections.getConnection()) {
            updateStatus(connection, tableId, status);
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the table's status.", e);
        }
    }

    public void updateStatus(Connection connection, int tableId, TableStatus status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, tableId);
            ps.executeUpdate();
        }
    }

    public void delete(int tableId) {
        try (Connection connection = connections.getConnection()) {
            delete(connection, tableId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not delete the dining table.", e);
        }
    }

    public void delete(Connection connection, int tableId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, tableId);
            ps.executeUpdate();
        }
    }

    /** True when an Open order sits on this table — it cannot be deleted meanwhile (FR-08). */
    public boolean hasOpenOrder(int tableId) {
        try (Connection connection = connections.getConnection()) {
            return hasOpenOrder(connection, tableId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not check the table's orders.", e);
        }
    }

    public boolean hasOpenOrder(Connection connection, int tableId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_OPEN_ORDER)) {
            ps.setInt(1, tableId);
            ps.setString(2, OrderStatus.OPEN.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** True when a live booking still holds this table from {@code from} onward (FR-08). */
    public boolean hasFutureReservation(int tableId, LocalDateTime from) {
        try (Connection connection = connections.getConnection()) {
            return hasFutureReservation(connection, tableId, from);
        } catch (SQLException e) {
            throw new PersistenceException("Could not check the table's reservations.", e);
        }
    }

    public boolean hasFutureReservation(Connection connection, int tableId, LocalDateTime from) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_FUTURE_RESERVATION)) {
            ps.setInt(1, tableId);
            ps.setTimestamp(2, Timestamp.valueOf(from));
            ps.setString(3, ReservationStatus.BOOKED.dbValue());
            ps.setString(4, ReservationStatus.SEATED.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<DiningTable> mapAll(PreparedStatement ps) throws SQLException {
        List<DiningTable> tables = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(map(rs));
            }
        }
        return tables;
    }

    private static DiningTable map(ResultSet rs) throws SQLException {
        DiningTable table = new DiningTable();
        table.setTableId(rs.getInt("table_id"));
        table.setLabel(rs.getString("label"));
        table.setCapacity(rs.getInt("capacity"));
        table.setStatus(TableStatus.fromDb(rs.getString("status")));
        return table;
    }
}
