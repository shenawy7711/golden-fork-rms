package service;

import config.AppConfig;
import dao.ConnectionFactory;
import dao.DiningTableDAO;
import dao.ReservationDAO;
import domain.DiningTable;
import domain.Reservation;
import domain.User;
import domain.enums.ReservationStatus;
import domain.enums.RoleName;
import domain.enums.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Session;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link ReservationService} double-booking prevention (FR-26; BR-27) — TDD §5.3.
 *
 * <p>The overlap rule uses half-open intervals, so back-to-back bookings are allowed while any real
 * time collision on the same table is refused. Cancelled/Completed bookings never block because the
 * DAO returns only active (Booked/Seated) rows to the check. DAOs are mocked — this asserts the
 * algorithm and the create-time guards, not SQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService — no double-booking (BR-27)")
class ReservationServiceTest {

    @Mock private ConnectionFactory connections;
    @Mock private ReservationDAO reservationDAO;
    @Mock private DiningTableDAO tableDAO;

    private ReservationService service;
    private Session cashier;

    private static final int TABLE = 4;

    @BeforeEach
    void setUp() {
        TableService tableService = new TableService(tableDAO);
        service = new ReservationService(connections, reservationDAO, tableDAO, tableService,
            AppConfig::defaults); // 90-minute slots
        User user = new User();
        user.setUserId(9);
        user.setUsername("cashier");
        user.setRole(RoleName.CASHIER);
        cashier = new Session(user);
    }

    private static Reservation booking(int id, String start, int minutes) {
        Reservation r = new Reservation();
        r.setReservationId(id);
        r.setTableId(TABLE);
        r.setReservationDatetime(LocalDateTime.parse(start));
        r.setDurationMinutes(minutes);
        r.setStatus(ReservationStatus.BOOKED);
        return r;
    }

    @Nested
    @DisplayName("hasOverlap")
    class Overlap {

        @Test
        @DisplayName("two bookings sharing time on the same table overlap")
        void overlapping() {
            when(reservationDAO.findActiveByTable(TABLE))
                .thenReturn(Collections.singletonList(booking(1, "2026-08-01T19:00", 90)));
            // 19:30–21:00 overlaps 19:00–20:30
            assertTrue(service.hasOverlap(TABLE, LocalDateTime.parse("2026-08-01T19:30"), 90, 0));
        }

        @Test
        @DisplayName("back-to-back bookings (one ends as the next begins) do not overlap")
        void backToBack() {
            when(reservationDAO.findActiveByTable(TABLE))
                .thenReturn(Collections.singletonList(booking(1, "2026-08-01T19:00", 90)));
            // 20:30–22:00 starts exactly when 19:00–20:30 ends
            assertFalse(service.hasOverlap(TABLE, LocalDateTime.parse("2026-08-01T20:30"), 90, 0));
        }

        @Test
        @DisplayName("a booking never overlaps itself when excluded")
        void excludesSelf() {
            when(reservationDAO.findActiveByTable(TABLE))
                .thenReturn(Collections.singletonList(booking(7, "2026-08-01T19:00", 90)));
            assertFalse(service.hasOverlap(TABLE, LocalDateTime.parse("2026-08-01T19:00"), 90, 7));
        }

        @Test
        @DisplayName("no active bookings (all cancelled/completed) means no overlap")
        void noneActive() {
            when(reservationDAO.findActiveByTable(TABLE)).thenReturn(Collections.emptyList());
            assertFalse(service.hasOverlap(TABLE, LocalDateTime.parse("2026-08-01T19:00"), 90, 0));
        }

        @Test
        @DisplayName("a distant booking on the same table does not overlap")
        void distant() {
            when(reservationDAO.findActiveByTable(TABLE)).thenReturn(Arrays.asList(
                booking(1, "2026-08-01T12:00", 90),
                booking(2, "2026-08-01T21:00", 90)));
            assertFalse(service.hasOverlap(TABLE, LocalDateTime.parse("2026-08-01T15:00"), 90, 0));
        }
    }

    @Nested
    @DisplayName("create guards")
    class Create {

        private DiningTable table(int capacity) {
            DiningTable t = new DiningTable();
            t.setTableId(TABLE);
            t.setLabel("T4");
            t.setCapacity(capacity);
            t.setStatus(TableStatus.FREE);
            return t;
        }

        private Reservation validRequest() {
            Reservation r = new Reservation();
            r.setTableId(TABLE);
            r.setCustomerName("Dana Smith");
            r.setContactPhone("0123456789");
            r.setReservationDatetime(LocalDateTime.now().plusDays(1));
            r.setPartySize(2);
            return r;
        }

        @Test
        @DisplayName("an overlapping window is refused as a conflict")
        void overlapRejected() {
            when(tableDAO.findById(TABLE)).thenReturn(table(4));
            when(reservationDAO.findActiveByTable(TABLE)).thenReturn(Collections.singletonList(
                booking(1, LocalDateTime.now().plusDays(1).toString().substring(0, 16), 90)));
            Reservation request = validRequest();
            request.setReservationDatetime(LocalDateTime.parse(
                LocalDateTime.now().plusDays(1).toString().substring(0, 16)));
            assertThrows(ConflictException.class, () -> service.create(cashier, request, false));
        }

        @Test
        @DisplayName("a past date-time is refused")
        void pastRejected() {
            Reservation request = validRequest();
            request.setReservationDatetime(LocalDateTime.now().minusHours(1));
            assertThrows(ValidationException.class, () -> service.create(cashier, request, false));
        }

        @Test
        @DisplayName("no contact details are refused")
        void missingContactRejected() {
            Reservation request = validRequest();
            request.setContactPhone(null);
            request.setContactEmail(null);
            assertThrows(ValidationException.class, () -> service.create(cashier, request, false));
        }

        @Test
        @DisplayName("a party over capacity is refused without an override")
        void overCapacityRejected() {
            lenient().when(tableDAO.findById(TABLE)).thenReturn(table(2));
            Reservation request = validRequest();
            request.setPartySize(6);
            assertThrows(ValidationException.class, () -> service.create(cashier, request, false));
        }
    }
}
