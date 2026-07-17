package service;

import domain.enums.TableStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static domain.enums.TableStatus.FREE;
import static domain.enums.TableStatus.NEEDS_CLEANING;
import static domain.enums.TableStatus.OCCUPIED;
import static domain.enums.TableStatus.RESERVED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table state machine (FR-09, BR-12) exactly as data-model.md §Table defines it.
 *
 * <p>Asserts the whole 4×4 grid rather than a few examples: the rule is "only defined transitions
 * are allowed", so what is *rejected* carries as much weight as what is allowed.
 */
@DisplayName("TableService — table state machine (FR-09, BR-12)")
class TableServiceTest {

    private static void assertLegal(TableStatus from, TableStatus to) {
        assertTrue(TableService.isLegalTransition(from, to),
            from + " → " + to + " should be allowed");
    }

    private static void assertIllegal(TableStatus from, TableStatus to) {
        assertFalse(TableService.isLegalTransition(from, to),
            from + " → " + to + " should be rejected");
    }

    @Test
    @DisplayName("the five defined transitions are allowed")
    void definedTransitionsAllowed() {
        assertLegal(FREE, OCCUPIED);              // open a dine-in order (FR-10)
        assertLegal(RESERVED, OCCUPIED);          // seat a booking (FR-25)
        assertLegal(OCCUPIED, NEEDS_CLEANING);    // close the order (FR-17)
        assertLegal(NEEDS_CLEANING, FREE);        // staff mark it cleaned
        assertLegal(FREE, RESERVED);              // create a booking (FR-24)
        assertLegal(RESERVED, FREE);              // cancel/complete a booking (FR-25)
    }

    @Test
    @DisplayName("an occupied table cannot jump straight to free — it must be cleaned first (FR-17)")
    void occupiedCannotSkipCleaning() {
        assertIllegal(OCCUPIED, FREE);
    }

    @Test
    @DisplayName("an occupied table cannot be reserved out from under its guests")
    void occupiedCannotBeReserved() {
        assertIllegal(OCCUPIED, RESERVED);
    }

    @Test
    @DisplayName("a table awaiting cleaning cannot be seated or booked")
    void needsCleaningOnlyGoesFree() {
        assertIllegal(NEEDS_CLEANING, OCCUPIED);
        assertIllegal(NEEDS_CLEANING, RESERVED);
    }

    @Test
    @DisplayName("a free or reserved table never lands in cleaning — nobody has eaten yet")
    void nothingElseEntersCleaning() {
        assertIllegal(FREE, NEEDS_CLEANING);
        assertIllegal(RESERVED, NEEDS_CLEANING);
    }

    @Test
    @DisplayName("staying put is a no-op, not an illegal transition")
    void sameStateIsAllowed() {
        for (TableStatus status : TableStatus.values()) {
            assertLegal(status, status);
        }
    }

    @Test
    @DisplayName("a null on either side is never legal")
    void nullsRejected() {
        assertIllegal(null, FREE);
        assertIllegal(FREE, null);
        assertIllegal(null, null);
    }

    @Test
    @DisplayName("the full grid: exactly the defined transitions and no others")
    void gridMatchesTheStateModel() {
        for (TableStatus from : TableStatus.values()) {
            for (TableStatus to : TableStatus.values()) {
                if (from == to) continue;
                boolean expected =
                       (from == FREE && (to == OCCUPIED || to == RESERVED))
                    || (from == RESERVED && (to == OCCUPIED || to == FREE))
                    || (from == OCCUPIED && to == NEEDS_CLEANING)
                    || (from == NEEDS_CLEANING && to == FREE);

                if (expected) {
                    assertLegal(from, to);
                } else {
                    assertIllegal(from, to);
                }
            }
        }
    }
}
