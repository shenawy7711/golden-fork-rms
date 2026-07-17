package service;

import config.AppConfig;
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
import domain.User;
import domain.enums.Availability;
import domain.enums.DiscountType;
import domain.enums.OrderStatus;
import domain.enums.OrderType;
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

import java.math.BigDecimal;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OrderService} finalisation rules (FR-15, FR-17; BR-19; SC-004, SC-005).
 *
 * <p>These assert the service's <em>decisions</em> around the atomic transaction, not SQL: the
 * payment, the {@code Paid/Closed} flip, the stored figures, and the table release move together, and
 * a zero-item, non-Open, under-tendered, or already-finalised order is refused before any payment is
 * written. The {@link ConnectionFactory} is stubbed to run the transactional lambda directly against
 * a mock {@link Connection}; {@link BillingService} is the real money engine and {@link TableService}
 * is real over a mocked DAO, so the figures on the finalised bill are genuinely computed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — atomic finalise & immutability (BR-19)")
class OrderServiceTest {

    @Mock private ConnectionFactory connections;
    @Mock private Connection connection;
    @Mock private OrderDAO orderDAO;
    @Mock private OrderItemDAO orderItemDAO;
    @Mock private PaymentDAO paymentDAO;
    @Mock private PaymentMethodDAO paymentMethodDAO;
    @Mock private MenuItemDAO menuItemDAO;
    @Mock private DiningTableDAO tableDAO;

    private OrderService service;
    private Session cashier;

    private static final int ORDER_ID = 1;
    private static final int TABLE_ID = 5;
    private static final int CASH_METHOD = 1;

    @BeforeEach
    void setUp() {
        BillingService billing = new BillingService(AppConfig::defaults); // 14% tax, threshold 20.00
        TableService tableService = new TableService(tableDAO);
        service = new OrderService(connections, orderDAO, orderItemDAO, paymentDAO, paymentMethodDAO,
            menuItemDAO, tableDAO, tableService, billing);

        User user = new User();
        user.setUserId(7);
        user.setUsername("cashier");
        user.setRole(RoleName.CASHIER);
        cashier = new Session(user);
    }

    /** Runs the value-returning transactional lambda directly against the mock connection. */
    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        when(connections.inTransaction(any(ConnectionFactory.TransactionalWork.class)))
            .thenAnswer(inv -> ((ConnectionFactory.TransactionalWork<Object>) inv.getArgument(0))
                .execute(connection));
    }

    private static Order openDineInOrder() {
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        order.setOrderNumber("ORD000001");
        order.setOrderType(OrderType.DINE_IN);
        order.setTableId(TABLE_ID);
        order.setStatus(OrderStatus.OPEN);
        order.setCreatedBy(7);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscountType(DiscountType.NONE);
        order.setDiscountValue(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxRate(new BigDecimal("0.1400"));
        order.setTaxAmount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return order;
    }

    private static OrderItem line(int qty, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setItemId(100);
        item.setQuantity(qty);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private static PaymentMethod cash() {
        PaymentMethod method = new PaymentMethod();
        method.setMethodId(CASH_METHOD);
        method.setMethodName("Cash");
        return method;
    }

    private static DiningTable occupiedTable() {
        DiningTable table = new DiningTable();
        table.setTableId(TABLE_ID);
        table.setLabel("T5");
        table.setCapacity(4);
        table.setStatus(TableStatus.OCCUPIED);
        return table;
    }

    @Nested
    @DisplayName("finalise")
    class Finalise {

        @Test
        @DisplayName("commits payment + Paid/Closed + stored figures + table release together")
        void atomicSuccess() throws Exception {
            runTransactionsInline();
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(openDineInOrder());
            when(orderItemDAO.findByOrder(connection, ORDER_ID))
                .thenReturn(java.util.Arrays.asList(line(2, "12.50"))); // subtotal 25.00
            when(paymentMethodDAO.findById(connection, CASH_METHOD)).thenReturn(cash());
            when(orderDAO.updateFinalised(eq(connection), any(Order.class))).thenReturn(1);
            when(tableDAO.findById(connection, TABLE_ID)).thenReturn(occupiedTable());

            Order result = service.finalise(cashier, ORDER_ID, CASH_METHOD, new BigDecimal("30.00"));

            // 25.00 + 14% tax (3.50) = 28.50
            assertEquals(OrderStatus.PAID_CLOSED, result.getStatus());
            assertEquals(new BigDecimal("25.00"), result.getSubtotal());
            assertEquals(new BigDecimal("3.50"), result.getTaxAmount());
            assertEquals(new BigDecimal("28.50"), result.getTotal());

            Payment recorded = captureInsertedPayment();
            assertEquals(new BigDecimal("28.50"), recorded.getAmount());
            assertEquals(new BigDecimal("1.50"), recorded.getChangeGiven()); // 30.00 − 28.50
            verify(orderDAO).updateFinalised(eq(connection), any(Order.class));
            verify(tableDAO).updateStatus(connection, TABLE_ID, TableStatus.NEEDS_CLEANING);
        }

        @Test
        @DisplayName("rejects a zero-item order and writes no payment")
        void zeroItemsRejected() throws Exception {
            runTransactionsInline();
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(openDineInOrder());
            when(orderItemDAO.findByOrder(connection, ORDER_ID)).thenReturn(java.util.Collections.emptyList());

            assertThrows(ValidationException.class,
                () -> service.finalise(cashier, ORDER_ID, CASH_METHOD, null));
            verify(paymentDAO, never()).insert(any(), any());
            verify(orderDAO, never()).updateFinalised(any(), any());
        }

        @Test
        @DisplayName("rejects tendered below total before writing a payment")
        void underTenderedRejected() throws Exception {
            runTransactionsInline();
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(openDineInOrder());
            when(orderItemDAO.findByOrder(connection, ORDER_ID))
                .thenReturn(java.util.Arrays.asList(line(2, "12.50")));
            when(paymentMethodDAO.findById(connection, CASH_METHOD)).thenReturn(cash());

            assertThrows(ValidationException.class,
                () -> service.finalise(cashier, ORDER_ID, CASH_METHOD, new BigDecimal("10.00")));
            verify(paymentDAO, never()).insert(any(), any());
            verify(orderDAO, never()).updateFinalised(any(), any());
        }

        @Test
        @DisplayName("refuses to finalise an order that is no longer Open")
        void alreadyClosedRejected() throws Exception {
            runTransactionsInline();
            Order closed = openDineInOrder();
            closed.setStatus(OrderStatus.PAID_CLOSED);
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(closed);

            assertThrows(ValidationException.class,
                () -> service.finalise(cashier, ORDER_ID, CASH_METHOD, null));
            verify(paymentDAO, never()).insert(any(), any());
        }

        @Test
        @DisplayName("surfaces a concurrent finalise (0 rows updated) as a conflict")
        void concurrentFinaliseConflicts() throws Exception {
            runTransactionsInline();
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(openDineInOrder());
            when(orderItemDAO.findByOrder(connection, ORDER_ID))
                .thenReturn(java.util.Arrays.asList(line(1, "8.00")));
            when(paymentMethodDAO.findById(connection, CASH_METHOD)).thenReturn(cash());
            when(orderDAO.updateFinalised(eq(connection), any(Order.class))).thenReturn(0);

            assertThrows(ConflictException.class,
                () -> service.finalise(cashier, ORDER_ID, CASH_METHOD, null));
            verify(tableDAO, never()).updateStatus(any(), anyInt(), any());
        }

        private Payment captureInsertedPayment() throws Exception {
            org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
            verify(paymentDAO).insert(eq(connection), captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("immutability of a finalised order")
    class Immutability {

        @Test
        @DisplayName("addLine is refused once the order is Paid/Closed")
        void addLineRejectedAfterFinalise() throws Exception {
            runTransactionsInline();
            Order closed = openDineInOrder();
            closed.setStatus(OrderStatus.PAID_CLOSED);
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(closed);

            MenuItem item = new MenuItem();
            item.setItemId(100);
            item.setAvailability(Availability.AVAILABLE);
            item.setPrice(new BigDecimal("5.00"));

            assertThrows(ValidationException.class,
                () -> service.addLine(cashier, ORDER_ID, 100, 1));
            verify(orderItemDAO, never()).insert(any(), any());
        }

        @Test
        @DisplayName("applyDiscount is refused once the order is Paid/Closed")
        void discountRejectedAfterFinalise() throws Exception {
            runTransactionsInline();
            Order closed = openDineInOrder();
            closed.setStatus(OrderStatus.PAID_CLOSED);
            when(orderDAO.findById(connection, ORDER_ID)).thenReturn(closed);

            assertThrows(ValidationException.class,
                () -> service.applyDiscount(cashier, ORDER_ID, DiscountType.PERCENTAGE, new BigDecimal("10")));
            verify(orderDAO, never()).updateFigures(any(), any());
        }
    }
}
