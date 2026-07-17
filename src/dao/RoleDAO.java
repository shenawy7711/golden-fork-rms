package dao;

import domain.Role;
import domain.enums.RoleName;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code role} reference list (FR-02). Roles are a fixed set seeded by
 * {@code db/seed.sql} — this DAO is read-only.
 *
 * <p>Parameterised prepared statements only (Principle VII).
 */
public final class RoleDAO {

    private static final String SELECT_ALL =
        "SELECT role_id, role_name FROM role ORDER BY role_id";

    private static final String SELECT_BY_NAME =
        "SELECT role_id, role_name FROM role WHERE role_name = ?";

    private final ConnectionFactory connections;

    public RoleDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    /** Every role, ordered by id. */
    public List<Role> findAll() {
        try (Connection connection = connections.getConnection()) {
            return findAll(connection);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the roles.", e);
        }
    }

    public List<Role> findAll(Connection connection) throws SQLException {
        List<Role> roles = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.add(map(rs));
            }
        }
        return roles;
    }

    /** The role with this name, or {@code null} when absent. */
    public Role findByName(RoleName roleName) {
        try (Connection connection = connections.getConnection()) {
            return findByName(connection, roleName);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the role '" + roleName + "'.", e);
        }
    }

    public Role findByName(Connection connection, RoleName roleName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_NAME)) {
            ps.setString(1, roleName.dbValue());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private static Role map(ResultSet rs) throws SQLException {
        Role role = new Role();
        role.setRoleId(rs.getInt("role_id"));
        role.setRoleName(RoleName.fromDb(rs.getString("role_name")));
        return role;
    }
}
