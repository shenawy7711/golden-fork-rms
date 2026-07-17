package service;

import dao.ConnectionFactory;
import dao.DiningTableDAO;
import dao.MenuItemDAO;
import dao.OrderDAO;
import dao.OrderItemDAO;
import dao.PaymentDAO;
import dao.PaymentMethodDAO;
import domain.DiningTable;
import domain.MenuItem;
import domain.Order;
import domain.OrderItem;
import domain.Payment;
import domain.PaymentMethod;
import domain.enums.Availability;
import domain.enums.DiscountType;
import domain.enums.OrderStatus;
import domain.enums.OrderType;
import domain.enums.TableStatus;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The order lifecycle — entry, editing, and the atomic finalise-to-paid transaction
 * (FR-10, FR-11, FR-15, FR-17; BR-14, BR-15, BR-19).
 *
 * <p>All figures come from {@link BillingService}; this service never does money arithmetic of its
 * own (Principle III). Every mutation runs inside a {@link ConnectionFactory#inTransaction}
 * transaction so a partial write is impossible: {@link #finalise} inserts the payment, flips the
 * order to {@code Paid/Closed}, stores the final figures, and releases the table in one commit —
 * any failure leaves the order Open with no payment (BR-19, NFR-03).
 *
 * <p>Writing requires {@code CREATE_ORDER} (all roles); the guard is in the business layer, so a
 * bypassed UI cannot open or finalise an order (BR-03). No {@code javafx.*} (Principle I).
 */
public final class OrderService {

    private final ConnectionFactory connections;
    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final PaymentDAO paymentDAO;
    private final PaymentMethodDAO paymentMethodDAO;
    private final MenuItemDAO menuItemDAO;
    private final DiningTableDAO tableDAO;
    private final TableService tableService;
    private final BillingService billing;

    public OrderService(ConnectionFactory connections, OrderDAO orderDAO, OrderItemDAO orderItemDAO,
                        PaymentDAO paymentDAO, PaymentMethodDAO paymentMethodDAO, MenuItemDAO menuItemDAO,
                        DiningTableDAO tableDAO, TableService tableService, BillingService billing) {
        this.connections = connections;
        this.orderDAO = orderDAO;
        this.orderItemDAO = orderItemDAO;
        this.paymentDAO = paymentDAO;
        this.paymentMethodDAO = paymentMethodDAO;
        this.menuItemDAO = menuItemDAO;
        this.tableDAO = tableDAO;
        this.tableService = tableService;
        this.billing = billing;
    }

    // --- Reads -------------------------------------------------------------------

    /** Every Open order — the "resume order" list on the POS. Unguarded read. */
    public List<Order> listOpenOrders() {
        return orderDAO.findOpen();
    }

    public Order findOrder(int orderId) {
        return orderDAO.findById(orderId);
    }

    /** The lines on an order, in entry order. */
    public List<OrderItem> listLines(int orderId) {
        return orderItemDAO.findByOrder(orderId);
    }

    // --- FR-10: open ------------------------------------------------------------

    /**
     * Opens a new Open order (FR-10, BR-14). A dine-in order needs a table that is Free or Reserved
     * and holds no other open order; the table moves to Occupied in the same transaction. A takeaway
     * order takes no table.
     *
     * @throws ConflictException if the table already has an open order or is not free/reserved
     */
    public Order openOrder(Session session, OrderType type, Integer tableId) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        if (type == null) {
            throw new ValidationException("Choose dine-in or takeaway.");
        }
        if (type == OrderType.DINE_IN && tableId == null) {
            throw new ValidationException("Select a table for a dine-in order.");
        }
        if (type == OrderType.TAKEAWAY) {
            tableId = null;
        }

        final Integer resolvedTableId = tableId;
        return connections.inTransaction(connection -> {
            if (type == OrderType.DINE_IN) {
                DiningTable table = tableDAO.findById(connection, resolvedTableId);
                if (table == null) {
                    throw new ValidationException("That table no longer exists.");
                }
                if (table.getStatus() != TableStatus.FREE && table.getStatus() != TableStatus.RESERVED) {
                    throw new ConflictException("That table is not available.");
                }
                if (tableDAO.hasOpenOrder(connection, resolvedTableId)) {
                    throw new ConflictException("That table already has an open order.");
                }
            }

            Order order = new Order();
            order.setOrderNumber("ORD" + String.format("%06d", orderDAO.nextSequence(connection)));
            order.setOrderType(type);
            order.setTableId(resolvedTableId);
            order.setStatus(OrderStatus.OPEN);
            order.setCreatedBy(session.getUserId());
            order.setCreatedAt(LocalDateTime.now());
            order.setSubtotal(Money.ZERO);
            order.setDiscountType(DiscountType.NONE);
            order.setDiscountValue(Money.ZERO);
            order.setDiscountAmount(Money.ZERO);
            order.setTaxRate(billing.currentTaxRate());
            order.setTaxAmount(Money.ZERO);
            order.setTotal(Money.ZERO);
            orderDAO.insert(connection, order);

            if (type == OrderType.DINE_IN) {
                tableService.changeStatus(connection, session, resolvedTableId, TableStatus.OCCUPIED);
            }
            return order;
        });
    }

    // --- FR-11: edit lines ------------------------------------------------------

    /**
     * Adds {@code quantity} of an Available item to an Open order, snapshotting the item's current
     * price onto the line (FR-11, BR-15). Adding an item already on the order increases its line.
     *
     * @throws ValidationException if the order is not Open, the item is unavailable, or quantity < 1
     */
    public Order addLine(Session session, int orderId, int itemId, int quantity) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        if (quantity < 1) {
            throw new ValidationException("Enter a quantity of at least 1.");
        }
        return connections.inTransaction(connection -> {
            Order order = requireOpenOrder(connection, orderId);

            MenuItem item = menuItemDAO.findById(connection, itemId);
            if (item == null) {
                throw new ValidationException("That menu item no longer exists.");
            }
            if (item.getAvailability() != Availability.AVAILABLE) {
                throw new ValidationException("That item is not available.");
            }

            OrderItem existing = findLineForItem(connection, orderId, itemId);
            if (existing == null) {
                OrderItem line = new OrderItem();
                line.setOrderId(orderId);
                line.setItemId(itemId);
                line.setQuantity(quantity);
                line.setUnitPrice(Money.round(item.getPrice()));
                line.setLineTotal(billing.lineTotal(line));
                orderItemDAO.insert(connection, line);
            } else {
                // Keep the original snapshot price; only the quantity grows.
                existing.setQuantity(existing.getQuantity() + quantity);
                existing.setLineTotal(billing.lineTotal(existing));
                orderItemDAO.update(connection, existing);
            }

            return recomputeAndStore(connection, order);
        });
    }

    /**
     * Removes a line from an Open order (FR-11) and re-derives the figures.
     *
     * @throws ValidationException if the order is not Open
     */
    public Order removeLine(Session session, int orderItemId) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        return connections.inTransaction(connection -> {
            OrderItem line = orderItemDAO.findById(connection, orderItemId);
            if (line == null) {
                throw new ValidationException("That line is no longer on the order.");
            }
            Order order = requireOpenOrder(connection, line.getOrderId());
            orderItemDAO.delete(connection, orderItemId);
            return recomputeAndStore(connection, order);
        });
    }

    // --- FR-13: discount --------------------------------------------------------

    /**
     * Applies a discount to an Open order and stores the re-derived figures (FR-13). The cap and the
     * approval-threshold check live in {@link BillingService}; this method persists the result.
     *
     * @throws service.exception.AuthorizationException if the discount needs manager approval and the
     *         session lacks {@code APPROVE_DISCOUNT}
     */
    public Order applyDiscount(Session session, int orderId, DiscountType type, BigDecimal value) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        return connections.inTransaction(connection -> {
            Order order = requireOpenOrder(connection, orderId);
            List<OrderItem> lines = orderItemDAO.findByOrder(connection, orderId);
            order.setSubtotal(billing.computeSubtotal(lines));
            billing.applyDiscount(session, order, type, value);
            billing.computeTaxAndTotal(order);
            orderDAO.updateFigures(connection, order);
            return order;
        });
    }

    // --- FR-10 (lifecycle): void ------------------------------------------------

    /**
     * Cancels an Open order before payment and releases its table hold (FR-10). A finalised order can
     * never be voided.
     *
     * @throws ValidationException if the order is not Open
     */
    public void voidOrder(Session session, int orderId) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        connections.inTransaction(connection -> {
            Order order = requireOpenOrder(connection, orderId);
            orderDAO.updateStatus(connection, orderId, OrderStatus.CANCELLED);
            releaseTable(connection, session, order);
        });
    }

    // --- FR-15, FR-17: finalise (atomic) ----------------------------------------

    /**
     * Finalises an Open order into a paid, closed bill — the one atomic money transaction (FR-15,
     * FR-17; BR-19). In a single commit it re-derives every figure at the current tax rate
     * (snapshotting the rate onto the order), records the 1:1 payment, flips the order to
     * {@code Paid/Closed}, and sends a dine-in table to Needs Cleaning. Any failure rolls the whole
     * thing back, leaving the order Open with no payment (NFR-03).
     *
     * @param amountTendered cash tendered, or {@code null} for exact/non-cash — when supplied it must
     *                       be at least the total, and the change is recorded
     * @throws ValidationException if the order has no lines, is not Open, or the method is unknown
     * @throws ConflictException  if the order was finalised concurrently
     */
    public Order finalise(Session session, int orderId, int methodId, BigDecimal amountTendered) {
        RbacGuard.require(session, Permission.CREATE_ORDER);
        return connections.inTransaction(connection -> {
            Order order = requireOpenOrder(connection, orderId);

            List<OrderItem> lines = orderItemDAO.findByOrder(connection, orderId);
            if (lines.isEmpty()) {
                throw new ValidationException("Add at least one item before taking payment.");
            }

            PaymentMethod method = paymentMethodDAO.findById(connection, methodId);
            if (method == null) {
                throw new ValidationException("Select a payment method.");
            }

            // Re-derive from the lines and snapshot the tax rate onto the order (BR-18).
            billing.recompute(order, lines);
            BigDecimal total = order.getTotal();

            BigDecimal tendered = null;
            BigDecimal change = null;
            if (amountTendered != null) {
                tendered = Money.round(amountTendered);
                change = Money.subtract(tendered, total);
                if (Money.isNegative(change)) {
                    throw new ValidationException("The amount tendered is less than the total due.");
                }
            }

            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setMethodId(methodId);
            payment.setAmount(total);
            payment.setAmountTendered(tendered);
            payment.setChangeGiven(change);
            payment.setPaidAt(LocalDateTime.now());
            paymentDAO.insert(connection, payment);

            order.setStatus(OrderStatus.PAID_CLOSED);
            order.setClosedAt(payment.getPaidAt());
            int updated = orderDAO.updateFinalised(connection, order);
            if (updated == 0) {
                // The AND status='Open' guard matched nothing — someone finalised it first.
                throw new ConflictException("This order has already been finalised.");
            }

            releaseTable(connection, session, order);
            return order;
        });
    }

    // --- helpers ----------------------------------------------------------------

    private Order requireOpenOrder(Connection connection, int orderId) throws SQLException {
        Order order = orderDAO.findById(connection, orderId);
        if (order == null) {
            throw new ValidationException("That order no longer exists.");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new ValidationException("This order is closed and can no longer be changed.");
        }
        return order;
    }

    private OrderItem findLineForItem(Connection connection, int orderId, int itemId) throws SQLException {
        for (OrderItem line : orderItemDAO.findByOrder(connection, orderId)) {
            if (line.getItemId() == itemId) {
                return line;
            }
        }
        return null;
    }

    /** Recomputes every figure from the order's current lines and persists them. */
    private Order recomputeAndStore(Connection connection, Order order) throws SQLException {
        List<OrderItem> lines = orderItemDAO.findByOrder(connection, order.getOrderId());
        billing.recompute(order, lines);
        orderDAO.updateFigures(connection, order);
        return order;
    }

    /** A dine-in order releases its table on void (→ Free) or finalise (→ Needs Cleaning). */
    private void releaseTable(Connection connection, Session session, Order order) throws SQLException {
        if (order.getOrderType() != OrderType.DINE_IN || order.getTableId() == null) {
            return;
        }
        DiningTable table = tableDAO.findById(connection, order.getTableId());
        if (table == null) {
            return;
        }
        if (order.getStatus() == OrderStatus.PAID_CLOSED) {
            tableService.changeStatus(connection, session, order.getTableId(), TableStatus.NEEDS_CLEANING);
        } else if (table.getStatus() == TableStatus.OCCUPIED) {
            // A voided order that had already occupied the table: Occupied → Needs Cleaning → Free
            // is the only legal path, but a void means no service happened, so free it directly.
            tableService.changeStatus(connection, session, order.getTableId(), TableStatus.NEEDS_CLEANING);
            tableService.changeStatus(connection, session, order.getTableId(), TableStatus.FREE);
        }
    }
}
