package service;

import dao.SupplierDAO;
import domain.Supplier;
import domain.enums.Status;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Validation;

import java.util.List;

/**
 * Suppliers (FR-19; BR-05, BR-22).
 *
 * <p>Writes require {@code MANAGE_SUPPLIERS} (Manager/Administrator), checked in the business layer
 * (BR-03). A supplier is never hard-deleted — {@link #deactivate} sets it Inactive so any purchase
 * order that references it keeps a valid vendor (BR-05, BR-22).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class SupplierService {

    static final String DUPLICATE_NAME = "A supplier with that name already exists.";

    private final SupplierDAO supplierDAO;

    public SupplierService(SupplierDAO supplierDAO) {
        this.supplierDAO = supplierDAO;
    }

    /** Every supplier, by name. Unguarded read — purchasing needs the active list. */
    public List<Supplier> listSuppliers() {
        return supplierDAO.findAll();
    }

    public List<Supplier> listActive() {
        return supplierDAO.findActive();
    }

    public Supplier findById(int supplierId) {
        return supplierDAO.findById(supplierId);
    }

    /**
     * Creates or updates a supplier (FR-19).
     *
     * @throws ConflictException if the name is taken (BR-22)
     * @throws ValidationException if a field breaches Appendix A
     */
    public Supplier save(Session session, Supplier supplier) {
        RbacGuard.require(session, Permission.MANAGE_SUPPLIERS);
        if (supplier == null) {
            throw new ValidationException("No supplier details were supplied.");
        }
        if (!Validation.hasLength(supplier.getName(), 2, 100)) {
            throw new ValidationException("Enter 2–100 characters for the supplier name.");
        }
        if (supplier.getPhone() != null && !supplier.getPhone().trim().isEmpty()
            && !Validation.isValidPhone(supplier.getPhone())) {
            throw new ValidationException("Enter a valid phone number.");
        }
        if (supplier.getEmail() != null && !supplier.getEmail().trim().isEmpty()
            && !Validation.isValidEmail(supplier.getEmail())) {
            throw new ValidationException("Enter a valid email address.");
        }

        String name = supplier.getName().trim();
        Supplier existing = supplierDAO.findByName(name);
        if (existing != null && existing.getSupplierId() != supplier.getSupplierId()) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        supplier.setName(name);
        if (supplier.getStatus() == null) {
            supplier.setStatus(Status.ACTIVE);
        }
        if (supplier.getSupplierId() == 0) {
            supplierDAO.insert(supplier);
        } else {
            supplierDAO.update(supplier);
        }
        return supplier;
    }

    /**
     * Deactivates a supplier (FR-19, BR-05). A supplier is kept, not deleted, so its purchase-order
     * history stays intact.
     */
    public void deactivate(Session session, int supplierId) {
        RbacGuard.require(session, Permission.MANAGE_SUPPLIERS);
        Supplier supplier = supplierDAO.findById(supplierId);
        if (supplier == null) {
            throw new ValidationException("That supplier no longer exists.");
        }
        supplierDAO.updateStatus(supplierId, Status.INACTIVE);
    }

    /** Reactivates a previously deactivated supplier. */
    public void reactivate(Session session, int supplierId) {
        RbacGuard.require(session, Permission.MANAGE_SUPPLIERS);
        Supplier supplier = supplierDAO.findById(supplierId);
        if (supplier == null) {
            throw new ValidationException("That supplier no longer exists.");
        }
        supplierDAO.updateStatus(supplierId, Status.ACTIVE);
    }
}
