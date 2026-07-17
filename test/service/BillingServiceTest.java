package service;

import config.AppConfig;
import domain.Order;
import domain.OrderItem;
import domain.User;
import domain.enums.DiscountType;
import domain.enums.RoleName;
import domain.enums.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import service.exception.AuthorizationException;
import service.exception.ValidationException;
import service.security.Session;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constitution Billing gate (Principle III): the TDD §8.3 worked financial checks, which exist to
 * mitigate the BRD risk "Incorrect financial calculations". These MUST pass.
 *
 * <p>Comparisons use {@link BigDecimal#compareTo} via {@code assertSame0}, not {@code equals}:
 * {@code equals} also compares scale, so {@code 33.0} would fail against {@code 33.00} for a
 * reason that has nothing to do with the money being right.
 */
@DisplayName("BillingService — TDD §8.3 worked financial checks (BR-13, BR-16, BR-17, BR-18)")
class BillingServiceTest {

    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.1000");

    private Map<String, String> configValues;
    private BillingService billing;

    @BeforeEach
    void setUp() {
        configValues = new HashMap<>();
        configValues.put(AppConfig.KEY_TAX_RATE, "0.1000");
        configValues.put(AppConfig.KEY_DISCOUNT_APPROVAL_THRESHOLD, "20.00");
        // Read through a supplier so a test can change the rate mid-flight, exactly as an
        // administrator's FR-31 edit would.
        billing = new BillingService(() -> AppConfig.from(configValues));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
            () -> "expected " + expected + " but was " + actual);
    }

    private static OrderItem line(String unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        return item;
    }

    private static Order orderWithSubtotal(String subtotal) {
        Order order = new Order();
        order.setSubtotal(new BigDecimal(subtotal));
        order.setDiscountType(DiscountType.NONE);
        order.setDiscountAmount(BigDecimal.ZERO);
        return order;
    }

    private static Session sessionFor(RoleName role) {
        User user = new User();
        user.setUserId(1);
        user.setUsername("test");
        user.setFullName("Test User");
        user.setRole(role);
        user.setStatus(Status.ACTIVE);
        return new Session(user);
    }

    // --- TDD §8.3 row 1: subtotal ------------------------------------------------

    @Test
    @DisplayName("subtotal: 2 × 12.50 + 1 × 8.00 = 33.00 (BR-13)")
    void subtotalWorkedCase() {
        List<OrderItem> lines = Arrays.asList(line("12.50", 2), line("8.00", 1));
        assertAmount("33.00", billing.computeSubtotal(lines));
    }

    @Test
    @DisplayName("subtotal of no lines is 0.00, not an error")
    void subtotalOfEmptyOrder() {
        assertAmount("0.00", billing.computeSubtotal(Collections.<OrderItem>emptyList()));
        assertAmount("0.00", billing.computeSubtotal(null));
    }

    // --- TDD §8.3 row 2: percentage discount -------------------------------------

    @Test
    @DisplayName("% discount: 10% of 100.00 → discount 10.00, base 90.00 (BR-17)")
    void percentageDiscountWorkedCase() {
        Order order = orderWithSubtotal("100.00");
        billing.applyDiscount(sessionFor(RoleName.MANAGER), order, DiscountType.PERCENTAGE, new BigDecimal("10"));

        assertAmount("10.00", order.getDiscountAmount());
        assertAmount("90.00", billing.computeDiscountedBase(order));
    }

    // --- TDD §8.3 row 3: fixed discount cap --------------------------------------

    @Test
    @DisplayName("fixed cap: 25.00 against subtotal 20.00 → capped at 20.00, total ≥ 0 (BR-17)")
    void fixedDiscountIsCappedAtSubtotal() {
        assertAmount("20.00",
            billing.computeDiscountAmount(new BigDecimal("20.00"), DiscountType.FIXED, new BigDecimal("25.00")));
    }

    @Test
    @DisplayName("percentage is capped at 100 — a bill never goes negative (BR-17)")
    void percentageIsCappedAtOneHundred() {
        BigDecimal discount =
            billing.computeDiscountAmount(new BigDecimal("50.00"), DiscountType.PERCENTAGE, new BigDecimal("150"));

        assertAmount("50.00", discount);   // 150% capped to 100% of 50.00
    }

    @Test
    @DisplayName("a capped discount leaves a zero base, never a negative one (BR-17)")
    void cappedDiscountLeavesZeroNotNegative() {
        Order order = orderWithSubtotal("20.00");
        billing.applyDiscount(sessionFor(RoleName.MANAGER), order, DiscountType.FIXED, new BigDecimal("25.00"));

        assertAmount("0.00", billing.computeDiscountedBase(order));
        billing.computeTaxAndTotal(order);
        assertAmount("0.00", order.getTotal());
        assertTrue(order.getTotal().signum() >= 0, "total must never be negative");
    }

    @Test
    @DisplayName("a negative discount is rejected — that is nonsense input, not a cap")
    void negativeDiscountRejected() {
        assertThrows(ValidationException.class,
            () -> billing.computeDiscountAmount(new BigDecimal("50.00"), DiscountType.FIXED, new BigDecimal("-1")));
    }

    // --- TDD §8.3 row 4: tax and total -------------------------------------------

    @Test
    @DisplayName("tax & total: base 90.00 at 10% → tax 9.00, total 99.00 (BR-16, BR-18)")
    void taxAndTotalWorkedCase() {
        Order order = orderWithSubtotal("100.00");
        order.setDiscountType(DiscountType.PERCENTAGE);
        order.setDiscountValue(new BigDecimal("10"));
        order.setDiscountAmount(new BigDecimal("10.00"));

        billing.computeTaxAndTotal(order, TEN_PERCENT);

        assertAmount("9.00", order.getTaxAmount());
        assertAmount("99.00", order.getTotal());
    }

    @Test
    @DisplayName("the full worked flow: 100.00 − 10% + 10% tax = 99.00")
    void endToEndWorkedExample() {
        Order order = orderWithSubtotal("100.00");
        billing.applyDiscount(sessionFor(RoleName.MANAGER), order, DiscountType.PERCENTAGE, new BigDecimal("10"));
        billing.computeTaxAndTotal(order);

        assertAmount("100.00", order.getSubtotal());
        assertAmount("10.00", order.getDiscountAmount());
        assertAmount("9.00", order.getTaxAmount());
        assertAmount("99.00", order.getTotal());
    }

    // --- TDD §8.3 row 5: immutability (BR-09, BR-18) ------------------------------

    @Nested
    @DisplayName("a finalised bill is immune to later changes (BR-09, BR-18)")
    class Immutability {

        @Test
        @DisplayName("re-deriving at the order's stored rate ignores a later tax-rate change")
        void storedRateWinsOverCurrentRate() {
            // No discount here, so the taxable base is the full 100.00: tax 10.00, total 110.00.
            Order finalised = orderWithSubtotal("100.00");
            billing.computeTaxAndTotal(finalised, TEN_PERCENT);   // snapshots 10% onto the order
            BigDecimal totalAtFinalisation = finalised.getTotal();
            assertAmount("110.00", totalAtFinalisation);

            // An administrator raises the tax rate afterwards (FR-31).
            configValues.put(AppConfig.KEY_TAX_RATE, "0.2500");

            // Re-deriving the bill from its own snapshot must produce the same figures.
            billing.computeTaxAndTotal(finalised, finalised.getTaxRate());

            assertAmount("10.00", finalised.getTaxAmount());
            assertAmount("110.00", finalised.getTotal());
            assertAmount(totalAtFinalisation.toPlainString(), finalised.getTotal());
        }

        @Test
        @DisplayName("the new rate does reach the next order — the change is not ignored, just not retroactive")
        void newRateAppliesToFutureOrders() {
            configValues.put(AppConfig.KEY_TAX_RATE, "0.2500");

            Order fresh = orderWithSubtotal("100.00");
            billing.computeTaxAndTotal(fresh);

            assertAmount("25.00", fresh.getTaxAmount());
            assertAmount("125.00", fresh.getTotal());
        }

        @Test
        @DisplayName("subtotal uses the line's price snapshot, not the menu's current price (BR-15)")
        void subtotalUsesPriceSnapshot() {
            // The line holds 12.50 from when it was added; the menu item may since cost anything.
            List<OrderItem> lines = Collections.singletonList(line("12.50", 2));
            assertAmount("25.00", billing.computeSubtotal(lines));
        }
    }

    // --- BR-17: approval threshold ------------------------------------------------

    @Nested
    @DisplayName("discount approval threshold (BR-17)")
    class ApprovalThreshold {

        @Test
        @DisplayName("a Cashier may apply a discount at or below the threshold")
        void cashierMayDiscountBelowThreshold() {
            Order order = orderWithSubtotal("100.00");
            // 20% of 100.00 = 20.00, exactly the threshold — allowed.
            billing.applyDiscount(sessionFor(RoleName.CASHIER), order, DiscountType.PERCENTAGE, new BigDecimal("20"));

            assertAmount("20.00", order.getDiscountAmount());
        }

        @Test
        @DisplayName("a Cashier is refused a discount above the threshold")
        void cashierRefusedAboveThreshold() {
            Order order = orderWithSubtotal("100.00");
            // 20.01 > 20.00 threshold — needs APPROVE_DISCOUNT, which a Cashier lacks.
            assertThrows(AuthorizationException.class,
                () -> billing.applyDiscount(sessionFor(RoleName.CASHIER), order, DiscountType.FIXED,
                    new BigDecimal("20.01")));
        }

        @Test
        @DisplayName("a Manager may approve a discount above the threshold")
        void managerMayDiscountAboveThreshold() {
            Order order = orderWithSubtotal("100.00");
            billing.applyDiscount(sessionFor(RoleName.MANAGER), order, DiscountType.FIXED, new BigDecimal("50.00"));

            assertAmount("50.00", order.getDiscountAmount());
        }

        @Test
        @DisplayName("the threshold is measured against the money off the bill, not the percentage")
        void thresholdMeasuresAmountNotPercentage() {
            // 50% is a big percentage but only 5.00 off a 10.00 bill — under the threshold.
            Order small = orderWithSubtotal("10.00");
            billing.applyDiscount(sessionFor(RoleName.CASHIER), small, DiscountType.PERCENTAGE, new BigDecimal("50"));
            assertAmount("5.00", small.getDiscountAmount());

            // 10% is a small percentage but 100.00 off a 1000.00 bill — over it.
            Order large = orderWithSubtotal("1000.00");
            assertThrows(AuthorizationException.class,
                () -> billing.applyDiscount(sessionFor(RoleName.CASHIER), large, DiscountType.PERCENTAGE,
                    new BigDecimal("10")));
        }
    }

    // --- BR-13/BR-16: one rounding step per figure --------------------------------

    @Test
    @DisplayName("each figure takes a single HALF-UP rounding step (BR-13, BR-16)")
    void singleRoundingStepPerFigure() {
        // 3 × 0.335 = 1.005 → 1.01 HALF-UP, not 1.00 (which double-rounding via 0.34 would give).
        assertAmount("1.01", billing.computeSubtotal(Collections.singletonList(line("0.335", 3))));

        // 33.33% of 100.00 = 33.33 exactly, rounded once.
        assertAmount("33.33",
            billing.computeDiscountAmount(new BigDecimal("100.00"), DiscountType.PERCENTAGE, new BigDecimal("33.33")));
    }

    @Test
    @DisplayName("recompute re-caps a fixed discount when lines are removed (BR-17)")
    void recomputeRecapsDiscountAfterLineRemoval() {
        Order order = new Order();
        order.setDiscountType(DiscountType.FIXED);
        order.setDiscountValue(new BigDecimal("25.00"));

        // Started at 33.00 with a 25.00 discount; a line is removed and the bill drops to 8.00.
        billing.recompute(order, Collections.singletonList(line("8.00", 1)));

        assertAmount("8.00", order.getSubtotal());
        assertAmount("8.00", order.getDiscountAmount());   // re-capped down from 25.00
        assertAmount("0.00", order.getTotal());
    }
}
