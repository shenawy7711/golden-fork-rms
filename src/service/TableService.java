package service;

import dao.DiningTableDAO;
import domain.DiningTable;
import domain.enums.TableStatus;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Validation;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Dining tables and their live status (FR-08, FR-09; BR-11, BR-12).
 *
 * <p>Defining and deleting tables requires {@code DEFINE_TABLES} (Manager/Administrator); changing
 * a status requires {@code UPDATE_TABLE_STATUS}, which every role holds — a cashier marks a table
 * clean. Both are checked in the business layer (BR-03).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class TableService {

    static final String DUPLICATE_LABEL = "A table with that label already exists.";

    /**
     * The table state machine (BR-12), exactly as data-model.md §Table defines it:
     * {@code Free → Occupied} (open dine-in, FR-10); {@code Reserved → Occupied} (seat, FR-25);
     * {@code Occupied → Needs Cleaning} (close order, FR-17); {@code Needs Cleaning → Free} (staff
     * mark clean); {@code Free ↔ Reserved} (create/cancel/complete reservation, FR-24/25).
     *
     * <p>Everything else is rejected. Notably {@code Occupied → Free} is not a transition: a table
     * that hosted guests passes through Needs Cleaning, which is what makes FR-17's turnover
     * visible on the floor view.
     */
    private static final Map<TableStatus, EnumSet<TableStatus>> TRANSITIONS = buildTransitions();

    private static Map<TableStatus, EnumSet<TableStatus>> buildTransitions() {
        Map<TableStatus, EnumSet<TableStatus>> map = new EnumMap<>(TableStatus.class);
        map.put(TableStatus.FREE, EnumSet.of(TableStatus.OCCUPIED, TableStatus.RESERVED));
        map.put(TableStatus.RESERVED, EnumSet.of(TableStatus.OCCUPIED, TableStatus.FREE));
        map.put(TableStatus.OCCUPIED, EnumSet.of(TableStatus.NEEDS_CLEANING));
        map.put(TableStatus.NEEDS_CLEANING, EnumSet.of(TableStatus.FREE));
        return map;
    }

    private final DiningTableDAO tableDAO;

    public TableService(DiningTableDAO tableDAO) {
        this.tableDAO = tableDAO;
    }

    /** Every table, by label — the floor view (FR-09). Unguarded: all roles see the floor. */
    public List<DiningTable> listTables() {
        return tableDAO.findAll();
    }

    public List<DiningTable> listByStatus(TableStatus status) {
        return tableDAO.findByStatus(status);
    }

    public DiningTable findById(int tableId) {
        return tableDAO.findById(tableId);
    }

    /**
     * Creates or updates a table (FR-08).
     *
     * @throws ConflictException if the label is taken (BR-11)
     * @throws ValidationException if the label or capacity breaches Appendix A
     */
    public DiningTable defineTable(Session session, DiningTable table) {
        RbacGuard.require(session, Permission.DEFINE_TABLES);
        if (table == null) {
            throw new ValidationException("No table details were supplied.");
        }
        if (!Validation.hasLength(table.getLabel(), 1, 10)) {
            throw new ValidationException("Enter 1–10 characters for the table label.");
        }
        if (table.getCapacity() < 1) {
            throw new ValidationException("Enter a whole number ≥ 1 for the capacity.");
        }

        String label = table.getLabel().trim();
        DiningTable existing = tableDAO.findByLabel(label);
        if (existing != null && existing.getTableId() != table.getTableId()) {
            throw new ConflictException(DUPLICATE_LABEL);
        }

        table.setLabel(label);
        if (table.getTableId() == 0) {
            if (table.getStatus() == null) {
                table.setStatus(TableStatus.FREE);
            }
            tableDAO.insert(table);
        } else {
            // Label and capacity only — a status change goes through changeStatus so the state
            // machine cannot be sidestepped by saving the form (BR-12).
            tableDAO.update(table);
        }
        return table;
    }

    /**
     * Removes a table that is free and un-booked (FR-08).
     *
     * @throws ConflictException if it is occupied, holds an open order, or has a live future booking
     */
    public void deleteTable(Session session, int tableId) {
        RbacGuard.require(session, Permission.DEFINE_TABLES);
        DiningTable table = tableDAO.findById(tableId);
        if (table == null) {
            throw new ValidationException("That table no longer exists.");
        }
        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new ConflictException("This table is occupied and cannot be deleted.");
        }
        if (tableDAO.hasOpenOrder(tableId)) {
            throw new ConflictException("This table has an open order and cannot be deleted.");
        }
        if (tableDAO.hasFutureReservation(tableId, LocalDateTime.now())) {
            throw new ConflictException("This table has upcoming reservations and cannot be deleted.");
        }
        tableDAO.delete(tableId);
    }

    /**
     * Moves a table through the state machine (FR-09, BR-12). Called by staff from the floor view
     * and by {@code OrderService}/{@code ReservationService} for automatic transitions.
     *
     * @throws ValidationException if the transition is not one the state machine defines
     */
    public void changeStatus(Session session, int tableId, TableStatus newStatus) {
        RbacGuard.require(session, Permission.UPDATE_TABLE_STATUS);
        if (newStatus == null) {
            throw new ValidationException("Select a status.");
        }
        DiningTable table = tableDAO.findById(tableId);
        if (table == null) {
            throw new ValidationException("That table no longer exists.");
        }
        requireLegalTransition(table.getStatus(), newStatus);
        tableDAO.updateStatus(tableId, newStatus);
    }

    /**
     * {@link #changeStatus} for a caller that already holds a transaction — the automatic
     * transitions in {@code OrderService.finalise} (BR-19) must commit with the rest of the order.
     */
    public void changeStatus(Connection connection, Session session, int tableId, TableStatus newStatus)
            throws SQLException {
        RbacGuard.require(session, Permission.UPDATE_TABLE_STATUS);
        DiningTable table = tableDAO.findById(connection, tableId);
        if (table == null) {
            throw new ValidationException("That table no longer exists.");
        }
        requireLegalTransition(table.getStatus(), newStatus);
        tableDAO.updateStatus(connection, tableId, newStatus);
    }

    /** True when {@code from → to} is a defined transition. Same-state is a no-op, so allowed. */
    public static boolean isLegalTransition(TableStatus from, TableStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        EnumSet<TableStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    private static void requireLegalTransition(TableStatus from, TableStatus to) {
        if (!isLegalTransition(from, to)) {
            throw new ValidationException(
                "A table cannot go from " + from.dbValue() + " to " + to.dbValue() + ".");
        }
    }
}
