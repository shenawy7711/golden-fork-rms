package dao;

import domain.Staff;
import domain.enums.Status;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code staff} rows (FR-23).
 *
 * <p>Parameterised prepared statements only (Principle VII). A staff record is distinct from a
 * login: {@code user_id} is nullable, so a member may exist without an account (BR-26). Records are
 * deactivated, not deleted, to preserve history (BR-05).
 */
public final class StaffDAO {

    private static final String SELECT_BASE =
        "SELECT staff_id, full_name, position, phone, email, status, user_id FROM staff";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY full_name";

    private static final String SELECT_ACTIVE = SELECT_BASE + " WHERE status = 'Active' ORDER BY full_name";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE staff_id = ?";

    private static final String INSERT =
        "INSERT INTO staff (full_name, position, phone, email, status, user_id) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE staff SET full_name = ?, position = ?, phone = ?, email = ?, status = ?, user_id = ? "
        + "WHERE staff_id = ?";

    private static final String UPDATE_STATUS = "UPDATE staff SET status = ? WHERE staff_id = ?";

    private final ConnectionFactory connections;

    public StaffDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<Staff> findAll() {
        return query(SELECT_ALL);
    }

    public List<Staff> findActive() {
        return query(SELECT_ACTIVE);
    }

    public Staff findById(int staffId) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the staff record.", e);
        }
    }

    public int insert(Staff staff) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, staff);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    staff.setStaffId(keys.getInt(1));
                }
            }
            return staff.getStaffId();
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the staff record.", e);
        }
    }

    public void update(Staff staff) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            bind(ps, staff);
            ps.setInt(7, staff.getStaffId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the staff record.", e);
        }
    }

    public void updateStatus(int staffId, Status status) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, staffId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the staff record's status.", e);
        }
    }

    private List<Staff> query(String sql) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Staff> staff = new ArrayList<>();
            while (rs.next()) {
                staff.add(map(rs));
            }
            return staff;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the staff records.", e);
        }
    }

    private static void bind(PreparedStatement ps, Staff s) throws SQLException {
        ps.setString(1, s.getFullName());
        ps.setString(2, s.getPosition());
        ps.setString(3, s.getPhone());
        ps.setString(4, s.getEmail());
        ps.setString(5, (s.getStatus() == null ? Status.ACTIVE : s.getStatus()).dbValue());
        if (s.getUserId() == null) {
            ps.setNull(6, java.sql.Types.INTEGER);
        } else {
            ps.setInt(6, s.getUserId());
        }
    }

    private static Staff map(ResultSet rs) throws SQLException {
        Staff s = new Staff();
        s.setStaffId(rs.getInt("staff_id"));
        s.setFullName(rs.getString("full_name"));
        s.setPosition(rs.getString("position"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setStatus(Status.fromDb(rs.getString("status")));
        int userId = rs.getInt("user_id");
        s.setUserId(rs.wasNull() ? null : userId);
        return s;
    }
}
