package service;

import config.AppConfig;
import dao.ConnectionFactory;
import dao.DiningTableDAO;
import dao.ReservationDAO;
import domain.DiningTable;
import domain.Reservation;
import domain.enums.ReservationStatus;
import domain.enums.TableStatus;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.DateTimeUtil;
import util.Validation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Table reservations (FR-24, FR-25, FR-26; BR-27, BR-28, BR-29).
 *
 * <p>Writes require {@code MANAGE_RESERVATION} (all roles), checked in the business layer (BR-03).
 * The core guarantee is <b>no double-booking</b>: {@link #hasOverlap} rejects a new booking whose
 * window overlaps any active (Booked/Seated) booking on the same table, using the half-open
 * interval rule from {@code DateTimeUtil} so back-to-back slots are allowed (BR-27). Creating,
 * seating, and closing a booking move the table through its own state machine in the same
 * transaction.
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class ReservationService {

    private final ConnectionFactory connections;
    private final ReservationDAO reservationDAO;
    private final DiningTableDAO tableDAO;
    private final TableService tableService;
    private final Supplier<AppConfig> config;

    public ReservationService(ConnectionFactory connections, ReservationDAO reservationDAO,
                              DiningTableDAO tableDAO, TableService tableService,
                              Supplier<AppConfig> config) {
        this.connections = connections;
        this.reservationDAO = reservationDAO;
        this.tableDAO = tableDAO;
        this.tableService = tableService;
        this.config = config;
    }

    public List<Reservation> listReservations() {
        return reservationDAO.findAll();
    }

    public List<Reservation> listUpcoming() {
        return reservationDAO.findUpcoming();
    }

    public Reservation findById(int reservationId) {
        return reservationDAO.findById(reservationId);
    }

    /**
     * True if a proposed window collides with an existing active booking on the same table (FR-26,
     * BR-27). Cancelled, Completed, and No-Show bookings never block. {@code excludeReservationId}
     * lets a booking ignore itself when it is being moved.
     */
    public boolean hasOverlap(int tableId, LocalDateTime start, int durationMinutes, int excludeReservationId) {
        for (Reservation existing : reservationDAO.findActiveByTable(tableId)) {
            if (existing.getReservationId() == excludeReservationId) {
                continue;
            }
            if (DateTimeUtil.overlaps(start, durationMinutes,
                    existing.getReservationDatetime(), existing.getDurationMinutes())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a booking (FR-24, FR-26). The window defaults to the configured slot length when the
     * reservation carries no duration. On success the booking is Booked and, if the table is Free, it
     * is marked Reserved.
     *
     * @param overrideCapacity accept a party larger than the table's capacity (BR-28) when true;
     *                         otherwise an over-capacity party is refused
     * @throws ValidationException if a field is invalid, the date is not in the future, or the party
     *                             exceeds capacity without an override
     * @throws ConflictException   if the window overlaps an existing booking (BR-27)
     */
    public Reservation create(Session session, Reservation reservation, boolean overrideCapacity) {
        RbacGuard.require(session, Permission.MANAGE_RESERVATION);
        if (reservation == null) {
            throw new ValidationException("No reservation details were supplied.");
        }
        if (!Validation.hasLength(reservation.getCustomerName(), 2, 100)) {
            throw new ValidationException("Enter 2–100 characters for the customer name.");
        }
        if (!Validation.hasAtLeastOneContact(reservation.getContactPhone(), reservation.getContactEmail())) {
            throw new ValidationException("Enter at least one contact — a phone number or an email.");
        }
        if (reservation.getContactPhone() != null && !reservation.getContactPhone().trim().isEmpty()
            && !Validation.isValidPhone(reservation.getContactPhone())) {
            throw new ValidationException("Enter a valid phone number.");
        }
        if (reservation.getContactEmail() != null && !reservation.getContactEmail().trim().isEmpty()
            && !Validation.isValidEmail(reservation.getContactEmail())) {
            throw new ValidationException("Enter a valid email address.");
        }
        if (!DateTimeUtil.isFuture(reservation.getReservationDatetime())) {
            throw new ValidationException("The reservation must be for a future date and time.");
        }
        if (reservation.getPartySize() < 1) {
            throw new ValidationException("Enter a party size of at least 1.");
        }
        if (reservation.getDurationMinutes() <= 0) {
            reservation.setDurationMinutes(config.get().reservationSlotMinutes());
        }

        DiningTable table = tableDAO.findById(reservation.getTableId());
        if (table == null) {
            throw new ValidationException("Select a table for the reservation.");
        }
        if (reservation.getPartySize() > table.getCapacity() && !overrideCapacity) {
            throw new ValidationException("The party is larger than the table seats "
                + table.getCapacity() + ". Confirm to book anyway.");
        }
        if (hasOverlap(reservation.getTableId(), reservation.getReservationDatetime(),
                reservation.getDurationMinutes(), 0)) {
            throw new ConflictException("That table is already booked for an overlapping time.");
        }

        return connections.inTransaction(connection -> {
            reservation.setStatus(ReservationStatus.BOOKED);
            reservation.setCreatedBy(session.getUserId());
            reservationDAO.insert(connection, reservation);

            DiningTable current = tableDAO.findById(connection, reservation.getTableId());
            if (current != null && current.getStatus() == TableStatus.FREE) {
                tableService.changeStatus(connection, session, reservation.getTableId(), TableStatus.RESERVED);
            }
            return reservation;
        });
    }

    /** Booked → Seated; the table becomes Occupied so an order can open (FR-25, BR-29). */
    public Reservation seat(Session session, int reservationId) {
        return transition(session, reservationId, ReservationStatus.BOOKED, ReservationStatus.SEATED,
            (connection, reservation) -> {
                DiningTable table = tableDAO.findById(connection, reservation.getTableId());
                if (table != null
                    && (table.getStatus() == TableStatus.RESERVED || table.getStatus() == TableStatus.FREE)) {
                    tableService.changeStatus(connection, session, reservation.getTableId(), TableStatus.OCCUPIED);
                }
            });
    }

    /** Seated → Completed; a seated table goes to Needs Cleaning (FR-25, BR-29). */
    public Reservation complete(Session session, int reservationId) {
        return transition(session, reservationId, ReservationStatus.SEATED, ReservationStatus.COMPLETED,
            (connection, reservation) -> releaseHold(connection, session, reservation));
    }

    /** Booked or Seated → Cancelled; releases the table hold (FR-25, BR-29). */
    public Reservation cancel(Session session, int reservationId) {
        RbacGuard.require(session, Permission.MANAGE_RESERVATION);
        return connections.inTransaction(connection -> {
            Reservation reservation = reservationDAO.findById(connection, reservationId);
            if (reservation == null) {
                throw new ValidationException("That reservation no longer exists.");
            }
            if (reservation.getStatus() != ReservationStatus.BOOKED
                && reservation.getStatus() != ReservationStatus.SEATED) {
                throw new ValidationException("Only a booked or seated reservation can be cancelled.");
            }
            reservationDAO.updateStatus(connection, reservationId, ReservationStatus.CANCELLED);
            reservation.setStatus(ReservationStatus.CANCELLED);
            releaseHold(connection, session, reservation);
            return reservation;
        });
    }

    /** Booked → No-Show; frees the reserved hold (FR-25, BR-29). */
    public Reservation markNoShow(Session session, int reservationId) {
        return transition(session, reservationId, ReservationStatus.BOOKED, ReservationStatus.NO_SHOW,
            (connection, reservation) -> releaseHold(connection, session, reservation));
    }

    // --- helpers ----------------------------------------------------------------

    @FunctionalInterface
    private interface SideEffect {
        void apply(java.sql.Connection connection, Reservation reservation) throws java.sql.SQLException;
    }

    private Reservation transition(Session session, int reservationId, ReservationStatus from,
                                   ReservationStatus to, SideEffect sideEffect) {
        RbacGuard.require(session, Permission.MANAGE_RESERVATION);
        return connections.inTransaction(connection -> {
            Reservation reservation = reservationDAO.findById(connection, reservationId);
            if (reservation == null) {
                throw new ValidationException("That reservation no longer exists.");
            }
            if (reservation.getStatus() != from) {
                throw new ValidationException("A " + reservation.getStatus().dbValue()
                    + " reservation cannot become " + to.dbValue() + ".");
            }
            reservationDAO.updateStatus(connection, reservationId, to);
            reservation.setStatus(to);
            sideEffect.apply(connection, reservation);
            return reservation;
        });
    }

    /**
     * Frees a table after a booking closes: a seated table (Occupied) goes to Needs Cleaning; a
     * merely reserved table returns to Free once no other active booking still holds it.
     */
    private void releaseHold(java.sql.Connection connection, Session session, Reservation reservation)
            throws java.sql.SQLException {
        DiningTable table = tableDAO.findById(connection, reservation.getTableId());
        if (table == null) {
            return;
        }
        if (table.getStatus() == TableStatus.OCCUPIED) {
            tableService.changeStatus(connection, session, reservation.getTableId(), TableStatus.NEEDS_CLEANING);
        } else if (table.getStatus() == TableStatus.RESERVED
            && reservationDAO.findActiveByTable(connection, reservation.getTableId()).isEmpty()) {
            tableService.changeStatus(connection, session, reservation.getTableId(), TableStatus.FREE);
        }
    }
}
