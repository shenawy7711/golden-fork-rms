package service;

import dao.ConnectionFactory;
import dao.StockItemDAO;
import dao.StockMovementDAO;
import domain.StockItem;
import domain.StockMovement;
import domain.enums.MovementType;
import domain.enums.Status;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Validation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stock items and the ledger that maintains their on-hand quantity (FR-18, FR-22; BR-21, BR-25).
 *
 * <p>Writes require {@code MANAGE_STOCK} (Manager/Administrator), checked in the business layer
 * (BR-03). <b>On-hand is never set directly.</b> After an item is created, its quantity changes only
 * through {@link #adjustStock} — which writes a {@code stock_movement} ledger row and updates
 * {@code quantity_on_hand} in one transaction, so the running total always reconciles to the ledger
 * (BR-21, NFR-03).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class InventoryService {

    static final String DUPLICATE_NAME = "A stock item with that name already exists.";

    private final ConnectionFactory connections;
    private final StockItemDAO stockItemDAO;
    private final StockMovementDAO stockMovementDAO;

    public InventoryService(ConnectionFactory connections, StockItemDAO stockItemDAO,
                            StockMovementDAO stockMovementDAO) {
        this.connections = connections;
        this.stockItemDAO = stockItemDAO;
        this.stockMovementDAO = stockMovementDAO;
    }

    public List<StockItem> listItems() {
        return stockItemDAO.findAll();
    }

    public List<StockItem> listActive() {
        return stockItemDAO.findActive();
    }

    public StockItem findById(int stockItemId) {
        return stockItemDAO.findById(stockItemId);
    }

    /** The movement history for one item, most recent first (FR-22) — the ledger behind its on-hand. */
    public List<StockMovement> movementsFor(int stockItemId) {
        return stockMovementDAO.findByItem(stockItemId);
    }

    /**
     * Creates or updates a stock item (FR-18, BR-21). On create, the opening {@code quantity_on_hand}
     * is accepted; on update it is left untouched (it moves only via {@link #adjustStock}).
     *
     * @throws ConflictException if the name is taken
     * @throws ValidationException if a field breaches Appendix A
     */
    public StockItem save(Session session, StockItem item) {
        RbacGuard.require(session, Permission.MANAGE_STOCK);
        if (item == null) {
            throw new ValidationException("No stock item details were supplied.");
        }
        if (!Validation.hasLength(item.getName(), 2, 80)) {
            throw new ValidationException("Enter 2–80 characters for the item name.");
        }
        if (!Validation.hasLength(item.getUnitOfMeasure(), 1, 20)) {
            throw new ValidationException("Enter a unit of measure (e.g. kg, L, each).");
        }
        if (!Validation.isNonNegative(item.getReorderLevel())) {
            throw new ValidationException("Enter a non-negative reorder level.");
        }

        String name = item.getName().trim();
        StockItem existing = stockItemDAO.findByName(name);
        if (existing != null && existing.getStockItemId() != item.getStockItemId()) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        item.setName(name);
        if (item.getStatus() == null) {
            item.setStatus(Status.ACTIVE);
        }
        if (item.getStockItemId() == 0) {
            if (item.getQuantityOnHand() == null) {
                item.setQuantityOnHand(BigDecimal.ZERO);
            }
            if (!Validation.isNonNegative(item.getQuantityOnHand())) {
                throw new ValidationException("Enter a non-negative opening quantity.");
            }
            stockItemDAO.insert(item);
        } else {
            stockItemDAO.update(item);
        }
        return item;
    }

    /** Deactivates a stock item (FR-18, BR-05) — kept for its movement history, not deleted. */
    public void deactivate(Session session, int stockItemId) {
        RbacGuard.require(session, Permission.MANAGE_STOCK);
        if (stockItemDAO.findById(stockItemId) == null) {
            throw new ValidationException("That stock item no longer exists.");
        }
        stockItemDAO.updateStatus(stockItemId, Status.INACTIVE);
    }

    public void reactivate(Session session, int stockItemId) {
        RbacGuard.require(session, Permission.MANAGE_STOCK);
        if (stockItemDAO.findById(stockItemId) == null) {
            throw new ValidationException("That stock item no longer exists.");
        }
        stockItemDAO.updateStatus(stockItemId, Status.ACTIVE);
    }

    /**
     * Applies a manual stock adjustment (FR-18, BR-21) — a signed delta, positive to add or negative
     * to remove (wastage, count correction). Atomic: the ledger row and the on-hand update commit
     * together, or neither does. An adjustment that would drive on-hand below zero is refused by the
     * {@code chk_onhand_nonneg} constraint and rolls back (NFR-03).
     *
     * @throws ValidationException if the delta is zero or the item does not exist
     */
    public void adjustStock(Session session, int stockItemId, BigDecimal signedQty) {
        RbacGuard.require(session, Permission.MANAGE_STOCK);
        if (signedQty == null || signedQty.signum() == 0) {
            throw new ValidationException("Enter a non-zero quantity to adjust.");
        }
        connections.inTransaction(connection -> {
            StockItem item = stockItemDAO.findById(connection, stockItemId);
            if (item == null) {
                throw new ValidationException("That stock item no longer exists.");
            }

            StockMovement movement = new StockMovement();
            movement.setStockItemId(stockItemId);
            movement.setPoItemId(null);
            movement.setMovementType(MovementType.ADJUSTMENT);
            movement.setQuantityChange(signedQty);
            movement.setMovedAt(LocalDateTime.now());
            movement.setMovedBy(session.getUserId());
            stockMovementDAO.insert(connection, movement);

            stockItemDAO.addOnHand(connection, stockItemId, signedQty);
        });
    }

    /** Active items at or below their reorder level (FR-22, BR-25) — the low-stock alert. */
    public List<StockItem> lowStockItems(Session session) {
        RbacGuard.require(session, Permission.MANAGE_STOCK);
        return stockItemDAO.findLowStock();
    }
}
