package service;

import config.AppConfig;
import domain.Order;
import domain.OrderItem;
import domain.enums.DiscountType;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * The single money engine (Principle III; FR-12, FR-13, FR-14; BR-13, BR-16, BR-17, BR-18).
 *
 * <p>Every monetary figure in the system is produced here and nowhere else — controllers and views
 * never compute one. All arithmetic goes through {@link Money}, so each figure takes exactly one
 * HALF-UP rounding step to two decimals (BR-13, BR-16).
 *
 * <p><b>The tax rate is read fresh, then snapshotted.</b> The rate comes from a {@link Supplier} so
 * an administrator's change (FR-31) reaches the next finalisation without an app restart; once
 * {@code OrderService} finalises, the rate is written onto the order and the bill is fixed forever
 * (BR-18, BR-09).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class BillingService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final Supplier<AppConfig> config;

    public BillingService(Supplier<AppConfig> config) {
        this.config = config;
    }

    // --- FR-12: subtotal ---------------------------------------------------------

    /**
     * Σ(unit_price snapshot × quantity), rounded once (BR-13).
     *
     * <p>Uses each line's stored {@code unitPrice}, never the menu item's current price — that is
     * what keeps a finalised bill immune to a later price edit (BR-09, BR-15).
     */
    public BigDecimal computeSubtotal(List<OrderItem> lines) {
        if (lines == null || lines.isEmpty()) return Money.ZERO;
        BigDecimal subtotal = Money.ZERO;
        for (OrderItem line : lines) {
            subtotal = Money.add(subtotal, lineTotal(line));
        }
        return subtotal;
    }

    /** quantity × unit_price for one line, rounded once (BR-13). */
    public BigDecimal lineTotal(OrderItem line) {
        if (line == null || line.getUnitPrice() == null) return Money.ZERO;
        return Money.multiply(line.getUnitPrice(), BigDecimal.valueOf(line.getQuantity()));
    }

    // --- FR-13: discount ---------------------------------------------------------

    /**
     * The discount a type/value pair produces against a subtotal, <b>capped</b> so a bill can never
     * go negative (BR-17): a percentage is capped at 100%, a fixed amount at the subtotal itself.
     *
     * <p>Pure arithmetic — no permission check. {@link #applyDiscount} is the guarded entry point.
     *
     * <p><b>Capping, not rejecting.</b> The artifacts disagree here: TDD §8.3's worked example
     * ("subtotal 20.00, fixed 25.00 → discount capped at 20.00") and the constitution-gated case in
     * tasks.md T043 both call for capping, while contracts/pos-billing.md says an over-cap value
     * raises {@code ValidationException}. This follows the gated behaviour, which also matches
     * BR-17's own wording ("result never negative"). A negative value is still rejected — that is
     * not a cap but a nonsense input.
     *
     * @throws ValidationException if the value is negative
     */
    public BigDecimal computeDiscountAmount(BigDecimal subtotal, DiscountType type, BigDecimal value) {
        if (type == null || type == DiscountType.NONE) return Money.ZERO;
        if (value == null || Money.isNegative(value)) {
            throw new ValidationException("Enter a non-negative discount.");
        }

        BigDecimal base = subtotal == null ? Money.ZERO : subtotal;

        switch (type) {
            case PERCENTAGE:
                BigDecimal percent = Money.min(value, HUNDRED);   // cap at 100% (BR-17)
                // One rounding step: computing subtotal × (percent ÷ 100) would round the division
                // and then the product. Multiply first, divide once with the final scale.
                return Money.round(base.multiply(percent).divide(HUNDRED, Money.SCALE, Money.ROUNDING));

            case FIXED:
                return Money.min(Money.round(value), base);       // cap at the subtotal (BR-17)

            default:
                return Money.ZERO;
        }
    }

    /**
     * Applies a discount to an open order (FR-13, BR-17), writing {@code discountType},
     * {@code discountValue}, and the capped {@code discountAmount} onto it.
     *
     * <p>A discount whose <em>amount</em> exceeds {@code discount_approval_threshold} needs
     * {@code APPROVE_DISCOUNT} (Manager/Administrator). At or below the threshold a Cashier's own
     * {@code APPLY_DISCOUNT} is enough. The threshold is compared against the money coming off the
     * bill, not the percentage figure — 10% of a large order can exceed the threshold where 50% of
     * a small one does not.
     *
     * @throws service.exception.AuthorizationException if approval is required and the session lacks it
     */
    public Order applyDiscount(Session session, Order order, DiscountType type, BigDecimal value) {
        RbacGuard.require(session, Permission.APPLY_DISCOUNT);
        if (order == null) {
            throw new ValidationException("No order was supplied.");
        }

        BigDecimal amount = computeDiscountAmount(order.getSubtotal(), type, value);

        if (amount.compareTo(config.get().discountApprovalThreshold()) > 0) {
            RbacGuard.require(session, Permission.APPROVE_DISCOUNT);
        }

        order.setDiscountType(type == null ? DiscountType.NONE : type);
        order.setDiscountValue(value == null ? Money.ZERO : Money.round(value));
        order.setDiscountAmount(amount);
        return order;
    }

    /** True when a discount of this amount needs a manager's approval (BR-17). */
    public boolean requiresApproval(BigDecimal discountAmount) {
        return discountAmount != null
            && discountAmount.compareTo(config.get().discountApprovalThreshold()) > 0;
    }

    // --- FR-14: tax and total ----------------------------------------------------

    /** subtotal − discount, never below zero (BR-17). */
    public BigDecimal computeDiscountedBase(Order order) {
        BigDecimal subtotal = order.getSubtotal() == null ? Money.ZERO : order.getSubtotal();
        BigDecimal discount = order.getDiscountAmount() == null ? Money.ZERO : order.getDiscountAmount();
        return Money.max(Money.subtract(subtotal, discount), Money.ZERO);
    }

    /**
     * Computes tax and total at the given rate and writes all three figures onto the order
     * (FR-14; BR-16, BR-18): {@code tax = round((subtotal − discount) × rate)},
     * {@code total = (subtotal − discount) + tax}.
     *
     * @param taxRate the rate to apply as a decimal fraction (0.1400 = 14%) — the caller passes the
     *                order's stored snapshot when re-deriving a finalised bill, so a reprint can
     *                never pick up today's rate (BR-09)
     */
    public Order computeTaxAndTotal(Order order, BigDecimal taxRate) {
        if (order == null) {
            throw new ValidationException("No order was supplied.");
        }
        BigDecimal rate = taxRate == null ? Money.ZERO : taxRate;
        BigDecimal base = computeDiscountedBase(order);

        BigDecimal tax = Money.multiply(base, rate);
        order.setTaxRate(rate);
        order.setTaxAmount(tax);
        order.setTotal(Money.add(base, tax));
        return order;
    }

    /**
     * {@link #computeTaxAndTotal} at the rate currently in force — used while an order is open and
     * at the moment of finalisation, where the rate is snapshotted onto it (BR-18).
     */
    public Order computeTaxAndTotal(Order order) {
        return computeTaxAndTotal(order, currentTaxRate());
    }

    /** The tax rate in force right now (FR-31). Snapshotted onto an order at finalisation. */
    public BigDecimal currentTaxRate() {
        return config.get().taxRate();
    }

    /**
     * Recomputes every figure on an open order from its lines at the current tax rate — the single
     * call the POS makes after any change to the order.
     */
    public Order recompute(Order order, List<OrderItem> lines) {
        order.setSubtotal(computeSubtotal(lines));
        // The discount value stays as entered; its amount re-caps against the new subtotal, so
        // removing lines can never leave a discount larger than the bill (BR-17).
        order.setDiscountAmount(
            computeDiscountAmount(order.getSubtotal(), order.getDiscountType(), order.getDiscountValue()));
        return computeTaxAndTotal(order);
    }
}
