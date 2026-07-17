package dao;

import domain.LoginEvent;
import domain.enums.LoginEventType;
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
 * Writes and reads the {@code login_event} session audit trail (FR-01, FR-04). The staff-activity
 * report (FR-29) reads it back over a date range.
 *
 * <p>Parameterised prepared statements only (Principle VII).
 */
public final class LoginEventDAO {

    private static final String SELECT_BASE =
        "SELECT event_id, user_id, event_type, event_time FROM login_event";

    private static final String INSERT =
        "INSERT INTO login_event (user_id, event_type, event_time) VALUES (?, ?, ?)";

    private static final String SELECT_BY_USER =
        SELECT_BASE + " WHERE user_id = ? ORDER BY event_time DESC";

    private static final String SELECT_BY_RANGE =
        SELECT_BASE + " WHERE event_time >= ? AND event_time < ? ORDER BY event_time";

    private static final String SELECT_BY_USER_AND_RANGE =
        SELECT_BASE + " WHERE user_id = ? AND event_time >= ? AND event_time < ? ORDER BY event_time";

    private static final String EXISTS_FOR_USER =
        "SELECT 1 FROM login_event WHERE user_id = ? LIMIT 1";

    private final ConnectionFactory connections;

    public LoginEventDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** Records a LOGIN or LOGOUT and returns the generated event id. */
    public long insert(LoginEvent event) {
        try (Connection connection = connections.getConnection()) {
            return insert(connection, event);
        } catch (SQLException e) {
            throw new PersistenceException("Could not record the session event.", e);
        }
    }

    public long insert(Connection connection, LoginEvent event) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime at = event.getEventTime() == null ? LocalDateTime.now() : event.getEventTime();
            ps.setInt(1, event.getUserId());
            ps.setString(2, event.getEventType().dbValue());
            ps.setTimestamp(3, Timestamp.valueOf(at));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    event.setEventId(keys.getLong(1));
                }
            }
            event.setEventTime(at);
            return event.getEventId();
        }
    }

    /** Convenience for the common case: record {@code type} for {@code userId} at now. */
    public long record(int userId, LoginEventType type) {
        LoginEvent event = new LoginEvent();
        event.setUserId(userId);
        event.setEventType(type);
        event.setEventTime(LocalDateTime.now());
        return insert(event);
    }

    public long record(Connection connection, int userId, LoginEventType type) throws SQLException {
        LoginEvent event = new LoginEvent();
        event.setUserId(userId);
        event.setEventType(type);
        event.setEventTime(LocalDateTime.now());
        return insert(connection, event);
    }

    /** Every event for a user, newest first. */
    public List<LoginEvent> findByUser(int userId) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_USER)) {
            ps.setInt(1, userId);
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the session history.", e);
        }
    }

    /**
     * Events in the half-open window {@code [from, toExclusive)}. The upper bound is exclusive so a
     * caller reporting on whole days passes the next midnight and includes everything on the last day.
     */
    public List<LoginEvent> findByRange(LocalDateTime from, LocalDateTime toExclusive) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_RANGE)) {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(toExclusive));
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the session history.", e);
        }
    }

    /** Events for one user within {@code [from, toExclusive)} — the FR-29 per-cashier breakdown. */
    public List<LoginEvent> findByUserAndRange(int userId, LocalDateTime from, LocalDateTime toExclusive) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_USER_AND_RANGE)) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(from));
            ps.setTimestamp(3, Timestamp.valueOf(toExclusive));
            return mapAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the session history.", e);
        }
    }

    /** True when the user has any recorded session — i.e. deleting them would destroy history (BR-05). */
    public boolean existsForUser(int userId) {
        try (Connection connection = connections.getConnection()) {
            return existsForUser(connection, userId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the session history.", e);
        }
    }

    public boolean existsForUser(Connection connection, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(EXISTS_FOR_USER)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static List<LoginEvent> mapAll(PreparedStatement ps) throws SQLException {
        List<LoginEvent> events = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(map(rs));
            }
        }
        return events;
    }

    private static LoginEvent map(ResultSet rs) throws SQLException {
        LoginEvent event = new LoginEvent();
        event.setEventId(rs.getLong("event_id"));
        event.setUserId(rs.getInt("user_id"));
        event.setEventType(LoginEventType.fromDb(rs.getString("event_type")));
        event.setEventTime(rs.getTimestamp("event_time").toLocalDateTime());
        return event;
    }
}
