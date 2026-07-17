package service;

import dao.ConnectionFactory;
import dao.PurchaseOrderDAO;
import dao.PurchaseOrderItemDAO;
import dao.StockItemDAO;
import dao.StockMovementDAO;
import dao.SupplierDAO;
import domain.PurchaseOrder;
import domain.PurchaseOrderItem;
import domain.StockItem;
import domain.StockMovement;
import domain.Supplier;
import domain.enums.MovementType;
import domain.enums.PoStatus;
import domain.enums.Status;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Purchase orders and delivery receipt (FR-20, FR-21; BR-23, BR-24).
 *
 * <p>Writes require {@code MANAGE_PURCHASING} (Manager/Administrator), checked in the business layer
 * (BR-03). Raising a PO records intent and <b>changes no stock</b> (BR-23). Receiving a delivery is
 * the atomic counterpart (TDD §5.4): for each line it appends a {@code Receipt} ledger row, raises
 * on-hand, and raises the line's received quantity, then recomputes the PO status — all in one
 * transaction, so a failure leaves both stock and PO exactly as they were (BR-24, NFR-03).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class PurchasingService {

    private final ConnectionFactory connections;
    private final SupplierDAO supplierDAO;
    private final StockItemDAO stockItemDAO;
    private final StockMovementDAO stockMovementDAO;
    private final PurchaseOrderDAO purchaseOrderDAO;
    private final PurchaseOrderItemDAO purchaseOrderItemDAO;

    public PurchasingService(ConnectionFactory connections, SupplierDAO supplierDAO,
                             StockItemDAO stockItemDAO, StockMovementDAO stockMovementDAO,
                             PurchaseOrderDAO purchaseOrderDAO, PurchaseOrderItemDAO purchaseOrderItemDAO) {
        this.connections = connections;
        this.supplierDAO = supplierDAO;
        this.stockItemDAO = stockItemDAO;
        this.stockMovementDAO = stockMovementDAO;
        this.purchaseOrderDAO = purchaseOrderDAO;
        this.purchaseOrderItemDAO = purchaseOrderItemDAO;
    }

    public List<PurchaseOrder> listOrders() {
        return purchaseOrderDAO.findAll();
    }

    public PurchaseOrder findById(int poId) {
        return purchaseOrderDAO.findById(poId);
    }

    public List<PurchaseOrderItem> listLines(int poId) {
        return purchaseOrderItemDAO.findByPo(poId);
    }

    /**
     * Raises a purchase order against an active supplier (FR-20, BR-23). Creating it does not touch
     * stock — that happens only on receipt.
     *
     * @param header a PurchaseOrder carrying the supplier id and optional expected date
     * @param lines  ≥ 1 lines, each with a stock item and an ordered quantity > 0
     * @throws ValidationException if the supplier is inactive, there are no lines, a quantity ≤ 0, or
     *                             the expected date is in the past
     */
    public PurchaseOrder createPO(Session session, PurchaseOrder header, List<PurchaseOrderItem> lines) {
        RbacGuard.require(session, Permission.MANAGE_PURCHASING);
        if (header == null) {
            throw new ValidationException("No purchase-order details were supplied.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Add at least one line to the purchase order.");
        }
        for (PurchaseOrderItem line : lines) {
            if (line.getOrderedQty() == null || line.getOrderedQty().signum() <= 0) {
                throw new ValidationException("Each line needs an ordered quantity greater than zero.");
            }
        }
        if (header.getExpectedDate() != null && header.getExpectedDate().isBefore(LocalDate.now())) {
            throw new ValidationException("The expected date cannot be in the past.");
        }

        Supplier supplier = supplierDAO.findById(header.getSupplierId());
        if (supplier == null) {
            throw new ValidationException("Select a supplier.");
        }
        if (supplier.getStatus() != Status.ACTIVE) {
            throw new ValidationException("That supplier is inactive.");
        }

        return connections.inTransaction(connection -> {
            header.setPoNumber("PO" + String.format("%06d", purchaseOrderDAO.nextSequence(connection)));
            header.setStatus(PoStatus.ORDERED);
            header.setCreatedBy(session.getUserId());
            header.setOrderedAt(LocalDateTime.now());
            purchaseOrderDAO.insert(connection, header);

            for (PurchaseOrderItem line : lines) {
                line.setPoId(header.getPoId());
                line.setReceivedQty(BigDecimal.ZERO);
                purchaseOrderItemDAO.insert(connection, line);
            }
            return header;
        });
    }

    /**
     * Records a delivery against a PO (FR-21, BR-24). The map keys are {@code po_item_id}s, the
     * values the quantity received now (a partial delivery omits or zeroes the rest). Atomic per
     * TDD §5.4: ledger + on-hand + received_qty + PO status all commit together.
     *
     * @throws ValidationException if the PO is closed, nothing is being received, or a line's
     *                             received quantity exceeds what remains outstanding
     */
    public PurchaseOrder receiveDelivery(Session session, int poId, Map<Integer, BigDecimal> receivedByLine) {
        RbacGuard.require(session, Permission.MANAGE_PURCHASING);
        if (receivedByLine == null || receivedByLine.isEmpty()) {
            throw new ValidationException("Enter the quantities received.");
        }

        return connections.inTransaction(connection -> {
            PurchaseOrder po = purchaseOrderDAO.findById(connection, poId);
            if (po == null) {
                throw new ValidationException("That purchase order no longer exists.");
            }
            if (po.getStatus() == PoStatus.CANCELLED || po.getStatus() == PoStatus.RECEIVED) {
                throw new ConflictException("This purchase order is closed to further receipts.");
            }

            List<PurchaseOrderItem> lines = purchaseOrderItemDAO.findByPo(connection, poId);
            boolean anyReceived = false;

            for (PurchaseOrderItem line : lines) {
                BigDecimal receiving = receivedByLine.get(line.getPoItemId());
                if (receiving == null || receiving.signum() == 0) {
                    continue;
                }
                if (receiving.signum() < 0) {
                    throw new ValidationException("A received quantity cannot be negative.");
                }
                BigDecimal outstanding = line.getOrderedQty().subtract(line.getReceivedQty());
                if (receiving.compareTo(outstanding) > 0) {
                    throw new ValidationException(
                        "Cannot receive more than the outstanding quantity on a line.");
                }

                StockMovement movement = new StockMovement();
                movement.setStockItemId(line.getStockItemId());
                movement.setPoItemId(line.getPoItemId());
                movement.setMovementType(MovementType.RECEIPT);
                movement.setQuantityChange(receiving);
                movement.setMovedAt(LocalDateTime.now());
                movement.setMovedBy(session.getUserId());
                stockMovementDAO.insert(connection, movement);

                stockItemDAO.addOnHand(connection, line.getStockItemId(), receiving);
                purchaseOrderItemDAO.addReceived(connection, line.getPoItemId(), receiving);
                line.setReceivedQty(line.getReceivedQty().add(receiving));
                anyReceived = true;
            }

            if (!anyReceived) {
                throw new ValidationException("Enter at least one quantity to receive.");
            }

            PoStatus newStatus = allFullyReceived(lines) ? PoStatus.RECEIVED : PoStatus.PARTIALLY_RECEIVED;
            purchaseOrderDAO.updateStatus(connection, poId, newStatus);
            po.setStatus(newStatus);
            return po;
        });
    }

    private static boolean allFullyReceived(List<PurchaseOrderItem> lines) {
        for (PurchaseOrderItem line : lines) {
            if (line.getReceivedQty().compareTo(line.getOrderedQty()) < 0) {
                return false;
            }
        }
        return true;
    }
}
