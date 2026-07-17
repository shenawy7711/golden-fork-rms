package dao;

import domain.Reservation;
import domain.enums.ReservationStatus;
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
 * Reads and writes {@code reservation} rows (FR-24, FR-25, FR-26).
 *
 * <p>Parameterised prepared statements only (Principle VII). {@link #findActiveByTable} returns the
 * bookings that can block a new one — those still in {@code Booked} or {@code Seated}; the overlap
 * decision itself is made by {@code ReservationService} in Java against {@code DateTimeUtil} so the
 * TDD §5.3 half-open interval rule lives in one place (BR-27).
 */
public final class ReservationDAO {

    private static final String SELECT_BASE =
        "SELECT reservation_id, table_id, customer_name, contact_phone, contact_email, "
        + "reservation_datetime, duration_minutes, party_size, status, created_by FROM reservation";

    private static final String SELECT_ALL = SELECT_BASE + " ORDER BY reservation_datetime DESC";

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE reservation_id = ?";

    private static final String SELECT_ACTIVE_BY_TABLE =
        SELECT_BASE + " WHERE table_id = ? AND status IN ('Booked', 'Seated') ORDER BY reservation_datetime";

    private static final String SELECT_UPCOMING =
        SELECT_BASE + " WHERE status IN ('Booked', 'Seated') ORDER BY reservation_datetime";

    private static final String INSERT =
        "INSERT INTO reservation (table_id, customer_name, contact_phone, contact_email, "
        + "reservation_datetime, duration_minutes, party_size, status, created_by) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS =
        "UPDATE reservation SET status = ? WHERE reservation_id = ?";

    private final ConnectionFactory connections;

    public ReservationDAO(ConnectionFactory connections) {
        this.connections = connections;
    }

    public List<Reservation> findAll() {
        return query(SELECT_ALL);
    }

    public List<Reservation> findUpcoming() {
        return query(SELECT_UPCOMING);
    }

    public Reservation findById(int reservationId) {
        try (Connection connection = connections.getConnection()) {
            return findById(connection, reservationId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the reservation.", e);
        }
    }

    public Reservation findById(Connection connection, int reservationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_BY_ID)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Active (Booked/Seated) bookings on a table — the candidates a new booking is checked against. */
    public List<Reservation> findActiveByTable(int tableId) {
        try (Connection connection = connections.getConnection()) {
            return findActiveByTable(connection, tableId);
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the table's reservations.", e);
        }
    }

    public List<Reservation> findActiveByTable(Connection connection, int tableId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_ACTIVE_BY_TABLE)) {
            ps.setInt(1, tableId);
            List<Reservation> reservations = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reservations.add(map(rs));
                }
            }
            return reservations;
        }
    }

    public int insert(Connection connection, Reservation reservation) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, reservation.getTableId());
            ps.setString(2, reservation.getCustomerName());
            ps.setString(3, reservation.getContactPhone());
            ps.setString(4, reservation.getContactEmail());
            ps.setTimestamp(5, Timestamp.valueOf(reservation.getReservationDatetime()));
            ps.setInt(6, reservation.getDurationMinutes());
            ps.setInt(7, reservation.getPartySize());
            ps.setString(8, (reservation.getStatus() == null
                ? ReservationStatus.BOOKED : reservation.getStatus()).dbValue());
            ps.setInt(9, reservation.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    reservation.setReservationId(keys.getInt(1));
                }
            }
            return reservation.getReservationId();
        }
    }

    public void updateStatus(Connection connection, int reservationId, ReservationStatus status)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.dbValue());
            ps.setInt(2, reservationId);
            ps.executeUpdate();
        }
    }

    private List<Reservation> query(String sql) {
        try (Connection connection = connections.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Reservation> reservations = new ArrayList<>();
            while (rs.next()) {
                reservations.add(map(rs));
            }
            return reservations;
        } catch (SQLException e) {
            throw new PersistenceException("Could not read the reservations.", e);
        }
    }

    private static Reservation map(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setReservationId(rs.getInt("reservation_id"));
        r.setTableId(rs.getInt("table_id"));
        r.setCustomerName(rs.getString("customer_name"));
        r.setContactPhone(rs.getString("contact_phone"));
        r.setContactEmail(rs.getString("contact_email"));
        Timestamp when = rs.getTimestamp("reservation_datetime");
        r.setReservationDatetime(when == null ? null : when.toLocalDateTime());
        r.setDurationMinutes(rs.getInt("duration_minutes"));
        r.setPartySize(rs.getInt("party_size"));
        r.setStatus(ReservationStatus.fromDb(rs.getString("status")));
        r.setCreatedBy(rs.getInt("created_by"));
        return r;
    }
}
