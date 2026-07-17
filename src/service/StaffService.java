package service;

import dao.StaffDAO;
import domain.Staff;
import domain.enums.Status;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;
import util.Validation;

import java.util.List;

/**
 * Staff records (FR-23; BR-05, BR-26).
 *
 * <p>Writes require {@code MANAGE_STAFF} (Manager/Administrator), checked in the business layer
 * (BR-03). A staff record is <b>distinct from a login account</b> — it may exist with no
 * {@code user_id} at all (BR-26) — and is deactivated, never deleted, so history is kept (BR-05).
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class StaffService {

    private final StaffDAO staffDAO;

    public StaffService(StaffDAO staffDAO) {
        this.staffDAO = staffDAO;
    }

    public List<Staff> listStaff(Session session) {
        RbacGuard.require(session, Permission.MANAGE_STAFF);
        return staffDAO.findAll();
    }

    public List<Staff> listActive(Session session) {
        RbacGuard.require(session, Permission.MANAGE_STAFF);
        return staffDAO.findActive();
    }

    public Staff findById(int staffId) {
        return staffDAO.findById(staffId);
    }

    /**
     * Creates or updates a staff record (FR-23).
     *
     * @throws ValidationException if a field breaches Appendix A
     */
    public Staff save(Session session, Staff staff) {
        RbacGuard.require(session, Permission.MANAGE_STAFF);
        if (staff == null) {
            throw new ValidationException("No staff details were supplied.");
        }
        if (!Validation.hasLength(staff.getFullName(), 2, 100)) {
            throw new ValidationException("Enter 2–100 characters for the full name.");
        }
        if (!Validation.hasLength(staff.getPosition(), 1, 50)) {
            throw new ValidationException("Enter a position.");
        }
        if (staff.getPhone() != null && !staff.getPhone().trim().isEmpty()
            && !Validation.isValidPhone(staff.getPhone())) {
            throw new ValidationException("Enter a valid phone number.");
        }
        if (staff.getEmail() != null && !staff.getEmail().trim().isEmpty()
            && !Validation.isValidEmail(staff.getEmail())) {
            throw new ValidationException("Enter a valid email address.");
        }

        staff.setFullName(staff.getFullName().trim());
        staff.setPosition(staff.getPosition().trim());
        if (staff.getStatus() == null) {
            staff.setStatus(Status.ACTIVE);
        }
        if (staff.getStaffId() == 0) {
            staffDAO.insert(staff);
        } else {
            staffDAO.update(staff);
        }
        return staff;
    }

    /** Deactivates a staff record (FR-23, BR-05) — hidden from active rosters, history retained. */
    public void deactivate(Session session, int staffId) {
        RbacGuard.require(session, Permission.MANAGE_STAFF);
        if (staffDAO.findById(staffId) == null) {
            throw new ValidationException("That staff record no longer exists.");
        }
        staffDAO.updateStatus(staffId, Status.INACTIVE);
    }

    public void reactivate(Session session, int staffId) {
        RbacGuard.require(session, Permission.MANAGE_STAFF);
        if (staffDAO.findById(staffId) == null) {
            throw new ValidationException("That staff record no longer exists.");
        }
        staffDAO.updateStatus(staffId, Status.ACTIVE);
    }
}
