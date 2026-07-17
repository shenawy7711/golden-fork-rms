package dao;

import domain.User;
import domain.enums.RoleName;
import domain.enums.Status;
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
 * Reads and writes {@code user_account} rows, joining {@code role} so the caller gets the
 * {@link RoleName} needed for RBAC without a second query (FR-03).
 *
 * <p>Parameterised prepared statements only (Principle VII). This DAO enforces no business rules:
 * unique-username (BR-04) and last-active-Administrator (BR-06) live in {@code UserService}, which
 * uses {@link #findByUsername} and {@link #countActiveAdministrators} to decide.
 */
public final class UserDAO {

    private static final String SELECT_BASE =
        "SELECT u.user_id, u.username, u.password_hash, u.full_name, u.role_id, "
        + "u.status, u.created_at, r.role_name "
        + "FROM user_account u JOIN role r ON r.role_id = u.role_id";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE u.user_id = ?";

    // username collates utf8mb4_unicode_ci, so plain equality is already case-insensitive AND
    // uses the unique index. LOWER(username) = LOWER(?) would match too, but forces a full scan.
    private static final String SELECT_BY_USERNAME = SELECT_BASE + " WHERE u.username = ?";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY u.username";

    private static final String INSERT =
        "INSERT INTO user_account (username, password_hash, full_name, role_id, status, created_at) "
        + "VALUES (?, ?, ?, ?, ?, ?)";

    // password_hash is updated only by the dedicated statement below, so a profile edit can never
    // blank someone's credentials by passing a null hash.
    private static final String UPDATE =
        "UPDATE user_account SET username = ?, full_name = ?, role_id = ?, status = ? WHERE user_id = ?";

    private static final String UPDATE_PASSWORD =
        "UPDATE user_account SET password_hash = ? WHERE user_id = ?";

    private static final String UPDATE_STATUS =
        "UPDATE user_account SET status = ? WHERE user_id = ?";

    private static final String DELETE = "DELETE FROM user_account WHERE user_id = ?";

    private static final String COUNT_ACTIVE_ADMINS =
        "SELECT COUNT(*) FROM user_account u JOIN role r ON r.role_id = u.role_id "
        + "WHERE r.role_name = ? AND u.status = ?";

    private final ConnectionFactory connections;

    public UserDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** The user with this id, or {@code null} when absent. */
    public User findById(int userId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, userId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the user account.", e);
        }
    }

    public User findById(Connection connection, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** The user with this username (case-insensitive), or {@code null} when absent. */
    public User findByUsername(String username) {
        try (Connection connection = connections.getConnection()) {
            return findByUsername(connection, username);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the user account.", e);
        }
    }

    public User findByUsername(Connection connection, String username) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Every account, active and inactive, ordered by username. */
    public List<User> findAll() {
        try (Connection connection = connections.getConnection()) {
            return findAll(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the user accounts.", e);
        }
    }

    public List<User> findAll(Connection connection) throws SQLException {
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs));
            }
        }
        return users;
    }

    /** Inserts the account and returns its generated id; also sets it on {@code user}. */
    public int insert(User user) {
        try (Connection connection = connections.getConnection()) {
            return insert(connection, user);
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the user account.", e);
        }
    }

    public int insert(Connection connection, User user) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setInt(4, user.getRoleId());
            ps.setString(5, user.getStatus().dbValue());
            LocalDateTime createdAt = user.getCreatedAt() == null ? LocalDateTime.now() : user.getCreatedAt();
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getInt(1));
                }
            }
            user.setCreatedAt(createdAt);
            return user.getUserId();
        }
    }

    /** Updates username, full name, role, and status. Leaves the password hash untouched. */
    public void update(User user) {
        try (Connection connection = connections.getConnection()) {
            update(connection, user);
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the user account.", e);
        }
    }

    public void update(Connection connection, User user) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setInt(3, user.getRoleId());
            ps.setString(4, user.getStatus().dbValue());
            ps.setInt(5, user.getUserId());
            ps.executeUpdate();
        }
    }

    /** Replaces the stored hash. The caller supplies an already-hashed value, never plain text (BR-02). */
    public void updatePasswordHash(int userId, String passwordHash) {
        try (Connection connection = connections.getConnection()) {
            updatePasswordHash(connection, userId, passwordHash);
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the password.", e);
        }
    }

    public void updatePasswordHash(Connection connection, int userId, String passwordHash) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_PASSWORD)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /** Sets Active/Inactive — the soft-delete that preserves history (BR-05). */
    public void updateStatus(int userId, Status status) {
        try (Connection connection = connections.getConnection()) {
            updateStatus(connection, userId, status);
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the account status.", e);
        }
    }

    public void updateStatus(Connection connection, int userId, Status status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Hard-deletes the row. Only valid for an account with no history — an account referenced by
     * {@code login_event} is deactivated instead (BR-05), a decision {@code UserService} makes.
     */
    public void delete(int userId) {
        try (Connection connection = connections.getConnection()) {
            delete(connection, userId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not delete the user account.", e);
        }
    }

    public void delete(Connection connection, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** How many active Administrators exist — the input to the BR-06 last-admin check. */
    public int countActiveAdministrators() {
        try (Connection connection = connections.getConnection()) {
            return countActiveAdministrators(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not count the active administrators.", e);
        }
    }

    public int countActiveAdministrators(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(COUNT_ACTIVE_ADMINS)) {
            ps.setString(1, RoleName.ADMINISTRATOR.dbValue());
            ps.setString(2, Status.ACTIVE.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setRoleId(rs.getInt("role_id"));
        user.setRole(RoleName.fromDb(rs.getString("role_name")));
        user.setStatus(Status.fromDb(rs.getString("status")));
        Timestamp createdAt = rs.getTimestamp("created_at");
        user.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return user;
    }
}
