package dao;

import domain.Supplier;
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
 * Reads and writes {@code supplier} rows (FR-19).
 *
 * <p>Parameterised prepared statements only (Principle VII). A supplier referenced by any purchase
 * order is deactivated rather than deleted (BR-05, BR-22); {@link #hasPurchaseOrders} is the probe
 * {@code SupplierService} uses to decide.
 */
public final class SupplierDAO {

    private static final String SELECT_BASE =
        "SELECT supplier_id, name, contact_person, phone, email, address, status FROM supplier";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY name";

    private static final String SELECT_ACTIVE = SELECT_BASE + " WHERE status = 'Active' ORDER BY name";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE supplier_id = ?";

    private static final String SELECT_BY_NAME = SELECT_BASE + " WHERE name = ?";

    private static final String INSERT =
        "INSERT INTO supplier (name, contact_person, phone, email, address, status) "
        + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE supplier SET name = ?, contact_person = ?, phone = ?, email = ?, address = ?, "
        + "status = ? WHERE supplier_id = ?";

    private static final String UPDATE_STATUS =
        "UPDATE supplier SET status = ? WHERE supplier_id = ?";

    private static final String EXISTS_PO =
        "SELECT 1 FROM purchase_order WHERE supplier_id = ? LIMIT 1";

    private final ConnectionFactory connections;

    public SupplierDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<Supplier> findAll() {
        return query(SELECT_ALL);
    }

    public List<Supplier> findActive() {
        return query(SELECT_ACTIVE);
    }

    public Supplier findById(int supplierId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, supplierId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the supplier.", e);
        }
    }

    public Supplier findById(Connection connection, int supplierId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Supplier findByName(String name) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BY_NAME)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the supplier.", e);
        }
    }

    public int insert(Supplier supplier) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, supplier);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    supplier.setSupplierId(keys.getInt(1));
                }
            }
            return supplier.getSupplierId();
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the supplier.", e);
        }
    }

    public void update(Supplier supplier) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            bind(ps, supplier);
            ps.setInt(7, supplier.getSupplierId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the supplier.", e);
        }
    }

    public void updateStatus(int supplierId, Status status) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, supplierId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Could not change the supplier's status.", e);
        }
    }

    /** True when any purchase order references this supplier — deactivate instead of delete (BR-22). */
    public boolean hasPurchaseOrders(int supplierId) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(EXISTS_PO)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not check the supplier's purchase orders.", e);
        }
    }

    private List<Supplier> query(String sql) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Supplier> suppliers = new ArrayList<>();
            while (rs.next()) {
                suppliers.add(map(rs));
            }
            return suppliers;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the suppliers.", e);
        }
    }

    private static void bind(PreparedStatement ps, Supplier s) throws SQLException {
        ps.setString(1, s.getName());
        ps.setString(2, s.getContactPerson());
        ps.setString(3, s.getPhone());
        ps.setString(4, s.getEmail());
        ps.setString(5, s.getAddress());
        ps.setString(6, (s.getStatus() == null ? Status.ACTIVE : s.getStatus()).dbValue());
    }

    private static Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setName(rs.getString("name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setStatus(Status.fromDb(rs.getString("status")));
        return s;
    }
}
