package service;

import dao.ConnectionFactory;
import dao.PurchaseOrderDAO;
import dao.PurchaseOrderItemDAO;
import dao.StockItemDAO;
import dao.StockMovementDAO;
import dao.SupplierDAO;
import domain.PurchaseOrder;
import domain.PurchaseOrderItem;
import domain.StockMovement;
import domain.Supplier;
import domain.User;
import domain.enums.PoStatus;
import domain.enums.RoleName;
import domain.enums.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.exception.ValidationException;
import service.security.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PurchasingService} receipt rules (FR-21; BR-24, NFR-03).
 *
 * <p>Asserts the atomic-receive decisions: the ledger row, the on-hand bump, and the received-qty
 * bump move together per line and the PO status is recomputed; an over-receipt is refused before any
 * stock is touched. The {@link ConnectionFactory} is stubbed to run the transactional lambda inline
 * against a mock {@link Connection}; the DAOs are mocked (this tests decisions, not SQL).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchasingService — atomic receive (BR-24)")
class PurchasingServiceTest {

    @Mock private ConnectionFactory connections;
    @Mock private Connection connection;
    @Mock private SupplierDAO supplierDAO;
    @Mock private StockItemDAO stockItemDAO;
    @Mock private StockMovementDAO stockMovementDAO;
    @Mock private PurchaseOrderDAO purchaseOrderDAO;
    @Mock private PurchaseOrderItemDAO purchaseOrderItemDAO;

    private PurchasingService service;
    private Session manager;

    private static final int PO_ID = 10;

    @BeforeEach
    void setUp() {
        service = new PurchasingService(connections, supplierDAO, stockItemDAO, stockMovementDAO,
            purchaseOrderDAO, purchaseOrderItemDAO);
        User user = new User();
        user.setUserId(3);
        user.setUsername("manager");
        user.setRole(RoleName.MANAGER);
        manager = new Session(user);
    }

    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        when(connections.inTransaction(any(ConnectionFactory.TransactionalWork.class)))
            .thenAnswer(inv -> ((ConnectionFactory.TransactionalWork<Object>) inv.getArgument(0))
                .execute(connection));
    }

    private static PurchaseOrder orderedPo() {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoId(PO_ID);
        po.setPoNumber("PO000010");
        po.setSupplierId(1);
        po.setStatus(PoStatus.ORDERED);
        return po;
    }

    private static PurchaseOrderItem line(int poItemId, int stockItemId, String ordered, String received) {
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setPoItemId(poItemId);
        line.setPoId(PO_ID);
        line.setStockItemId(stockItemId);
        line.setOrderedQty(new BigDecimal(ordered));
        line.setReceivedQty(new BigDecimal(received));
        return line;
    }

    @Nested
    @DisplayName("receiveDelivery")
    class Receive {

        @Test
        @DisplayName("full receipt writes a ledger row + on-hand + received per line and closes the PO")
        void fullReceipt() throws Exception {
            runTransactionsInline();
            when(purchaseOrderDAO.findById(connection, PO_ID)).thenReturn(orderedPo());
            when(purchaseOrderItemDAO.findByPo(connection, PO_ID)).thenReturn(Arrays.asList(
                line(101, 500, "10", "0"),
                line(102, 501, "4", "0")));

            Map<Integer, BigDecimal> received = new HashMap<>();
            received.put(101, new BigDecimal("10"));
            received.put(102, new BigDecimal("4"));

            PurchaseOrder result = service.receiveDelivery(manager, PO_ID, received);

            verify(stockMovementDAO, times(2)).insert(eq(connection), any(StockMovement.class));
            verify(stockItemDAO).addOnHand(connection, 500, new BigDecimal("10"));
            verify(stockItemDAO).addOnHand(connection, 501, new BigDecimal("4"));
            verify(purchaseOrderItemDAO).addReceived(connection, 101, new BigDecimal("10"));
            verify(purchaseOrderItemDAO).addReceived(connection, 102, new BigDecimal("4"));
            verify(purchaseOrderDAO).updateStatus(connection, PO_ID, PoStatus.RECEIVED);
            assertEquals(PoStatus.RECEIVED, result.getStatus());
        }

        @Test
        @DisplayName("a partial receipt leaves the PO Partially Received")
        void partialReceipt() throws Exception {
            runTransactionsInline();
            when(purchaseOrderDAO.findById(connection, PO_ID)).thenReturn(orderedPo());
            when(purchaseOrderItemDAO.findByPo(connection, PO_ID)).thenReturn(Arrays.asList(
                line(101, 500, "10", "0"),
                line(102, 501, "4", "0")));

            Map<Integer, BigDecimal> received = Collections.singletonMap(101, new BigDecimal("6"));

            PurchaseOrder result = service.receiveDelivery(manager, PO_ID, received);

            verify(stockItemDAO).addOnHand(connection, 500, new BigDecimal("6"));
            verify(stockItemDAO, never()).addOnHand(eq(connection), eq(501), any());
            verify(purchaseOrderDAO).updateStatus(connection, PO_ID, PoStatus.PARTIALLY_RECEIVED);
            assertEquals(PoStatus.PARTIALLY_RECEIVED, result.getStatus());
        }

        @Test
        @DisplayName("receiving more than outstanding is refused before any stock moves")
        void overReceiptRejected() throws Exception {
            runTransactionsInline();
            when(purchaseOrderDAO.findById(connection, PO_ID)).thenReturn(orderedPo());
            when(purchaseOrderItemDAO.findByPo(connection, PO_ID)).thenReturn(Collections.singletonList(
                line(101, 500, "10", "7"))); // only 3 outstanding

            Map<Integer, BigDecimal> received = Collections.singletonMap(101, new BigDecimal("5"));

            assertThrows(ValidationException.class,
                () -> service.receiveDelivery(manager, PO_ID, received));
            verify(stockItemDAO, never()).addOnHand(any(), anyInt(), any());
            verify(stockMovementDAO, never()).insert(any(), any());
            verify(purchaseOrderDAO, never()).updateStatus(any(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("createPO")
    class Create {

        @Test
        @DisplayName("rejects an inactive supplier")
        void inactiveSupplierRejected() {
            Supplier inactive = new Supplier();
            inactive.setSupplierId(1);
            inactive.setName("Old Foods");
            inactive.setStatus(Status.INACTIVE);
            when(supplierDAO.findById(1)).thenReturn(inactive);

            PurchaseOrder header = new PurchaseOrder();
            header.setSupplierId(1);

            assertThrows(ValidationException.class,
                () -> service.createPO(manager, header, Collections.singletonList(line(0, 500, "5", "0"))));
        }

        @Test
        @DisplayName("rejects a PO with no lines")
        void noLinesRejected() {
            PurchaseOrder header = new PurchaseOrder();
            header.setSupplierId(1);

            assertThrows(ValidationException.class,
                () -> service.createPO(manager, header, Collections.emptyList()));
        }
    }
}
