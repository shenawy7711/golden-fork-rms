package service;

import dao.LoginEventDAO;
import dao.RoleDAO;
import dao.UserDAO;
import domain.Role;
import domain.User;
import domain.enums.RoleName;
import domain.enums.Status;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.PasswordHasher;
import service.security.RbacGuard;
import service.security.Session;
import util.Validation;

import java.util.List;

/**
 * Administrator-only account management (FR-03; BR-04, BR-05, BR-06).
 *
 * <p>Every method calls {@link RbacGuard#require} before acting, so a role-forbidden call is
 * refused even if the interface were bypassed (BR-03). No {@code javafx.*} (Principle I).
 */
public final class UserService {

    static final String DUPLICATE_USERNAME = "Username already exists.";
    static final String LAST_ADMIN =
        "This is the last active Administrator. Promote another user to Administrator first.";

    private final UserDAO userDAO;
    private final RoleDAO roleDAO;
    private final LoginEventDAO loginEventDAO;

    public UserService(UserDAO userDAO, RoleDAO roleDAO, LoginEventDAO loginEventDAO) {
        this.userDAO = userDAO;
        this.roleDAO = roleDAO;
        this.loginEventDAO = loginEventDAO;
    }

    /** Every account, active and inactive (FR-03). */
    public List<User> list(Session session) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        return userDAO.findAll();
    }

    /**
     * Creates an account that can sign in immediately (FR-03).
     *
     * @throws ConflictException if the username is taken (BR-04)
     * @throws ValidationException if a field or the password breaches Appendix A
     */
    public User create(Session session, User user, String initialPassword) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        validateFields(user);
        if (!Validation.isValidPassword(initialPassword)) {
            throw new ValidationException(Validation.PASSWORD_POLICY_HINT);
        }
        String username = user.getUsername().trim();
        if (userDAO.findByUsername(username) != null) {
            throw new ConflictException(DUPLICATE_USERNAME);
        }

        user.setUsername(username);
        user.setFullName(user.getFullName().trim());
        user.setRoleId(resolveRoleId(user.getRole()));
        user.setStatus(user.getStatus() == null ? Status.ACTIVE : user.getStatus());
        user.setPasswordHash(PasswordHasher.hash(initialPassword));   // never the plain text (BR-02)
        userDAO.insert(user);
        return user;
    }

    /**
     * Edits full name, role, and status. Role changes take effect on the user's next login, since
     * an existing {@link Session} holds the role it was created with.
     *
     * @throws ValidationException if this would deactivate or demote the last active Administrator (BR-06)
     */
    public User update(Session session, User user) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        validateFields(user);

        User existing = userDAO.findById(user.getUserId());
        if (existing == null) {
            throw new ValidationException("That user account no longer exists.");
        }

        String username = user.getUsername().trim();
        User byName = userDAO.findByUsername(username);
        if (byName != null && byName.getUserId() != user.getUserId()) {
            throw new ConflictException(DUPLICATE_USERNAME);
        }

        // Losing the last admin has two routes — deactivating them, or moving them off the
        // Administrator role. Both are checked here; the DAO enforces neither (BR-06).
        boolean wasActiveAdmin = existing.getRole() == RoleName.ADMINISTRATOR
            && existing.getStatus() == Status.ACTIVE;
        boolean staysActiveAdmin = user.getRole() == RoleName.ADMINISTRATOR
            && user.getStatus() == Status.ACTIVE;
        if (wasActiveAdmin && !staysActiveAdmin) {
            requireAnotherActiveAdmin();
        }

        user.setUsername(username);
        user.setFullName(user.getFullName().trim());
        user.setRoleId(resolveRoleId(user.getRole()));
        userDAO.update(user);
        return user;
    }

    /** Sets a new password for an account, stored hashed (BR-02). */
    public void changePassword(Session session, int userId, String newPassword) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        if (!Validation.isValidPassword(newPassword)) {
            throw new ValidationException(Validation.PASSWORD_POLICY_HINT);
        }
        if (userDAO.findById(userId) == null) {
            throw new ValidationException("That user account no longer exists.");
        }
        userDAO.updatePasswordHash(userId, PasswordHasher.hash(newPassword));
    }

    /**
     * Blocks sign-in while preserving the account and its history (BR-05).
     *
     * @throws ValidationException if this is the last active Administrator (BR-06)
     */
    public void deactivate(Session session, int userId) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new ValidationException("That user account no longer exists.");
        }
        if (user.getStatus() == Status.INACTIVE) return;   // already there; nothing to do
        if (user.getRole() == RoleName.ADMINISTRATOR) {
            requireAnotherActiveAdmin();
        }
        userDAO.updateStatus(userId, Status.INACTIVE);
    }

    /** Restores sign-in for a deactivated account. */
    public void activate(Session session, int userId) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        if (userDAO.findById(userId) == null) {
            throw new ValidationException("That user account no longer exists.");
        }
        userDAO.updateStatus(userId, Status.ACTIVE);
    }

    /**
     * Hard-deletes an account, permitted only when it has no history (BR-05). An account with
     * recorded sessions must be deactivated instead, so the staff-activity report (FR-29) keeps
     * referring to a real user.
     *
     * @throws ConflictException if history exists — the caller offers deactivation instead
     * @throws ValidationException if this is the last active Administrator (BR-06)
     */
    public void delete(Session session, int userId) {
        RbacGuard.require(session, Permission.MANAGE_USERS);
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new ValidationException("That user account no longer exists.");
        }
        if (user.getRole() == RoleName.ADMINISTRATOR && user.getStatus() == Status.ACTIVE) {
            requireAnotherActiveAdmin();
        }
        if (loginEventDAO.existsForUser(userId)) {
            throw new ConflictException(
                "This account has activity history and cannot be deleted. Deactivate it instead.");
        }
        userDAO.delete(userId);
    }

    // BR-06: there must always be at least one active Administrator left besides the one being
    // deactivated, demoted, or deleted.
    private void requireAnotherActiveAdmin() {
        if (userDAO.countActiveAdministrators() <= 1) {
            throw new ValidationException(LAST_ADMIN);
        }
    }

    private int resolveRoleId(RoleName roleName) {
        Role role = roleDAO.findByName(roleName);
        if (role == null) {
            throw new ValidationException("Unknown role: " + roleName);
        }
        return role.getRoleId();
    }

    // Appendix A: username 3-50, full name 2-100, role required.
    private static void validateFields(User user) {
        if (user == null) {
            throw new ValidationException("No user details were supplied.");
        }
        if (!Validation.hasLength(user.getUsername(), 3, 50)) {
            throw new ValidationException("Enter 3–50 characters for the username.");
        }
        if (!Validation.hasLength(user.getFullName(), 2, 100)) {
            throw new ValidationException("Enter 2–100 characters for the full name.");
        }
        if (user.getRole() == null) {
            throw new ValidationException("Select a role.");
        }
    }
}
