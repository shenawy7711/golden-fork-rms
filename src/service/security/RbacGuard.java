package service.security;

import service.exception.AuthorizationException;

/**
 * Business-layer access control (Principle IV, FR-02, BR-03). Every protected service operation
 * calls {@link #require} BEFORE acting, independent of whether the UI hid the control — a
 * role-forbidden call is rejected even if the interface were bypassed, and the attempt is recorded.
 */
public final class RbacGuard {

    private RbacGuard() {}

    /**
     * Ensures the session's role holds the permission, else throws {@link AuthorizationException}
     * and records the denied attempt.
     */
    public static void require(Session session, Permission permission) {
        if (session == null) {
            throw new AuthorizationException("You must be signed in to perform this action.");
        }
        if (!session.has(permission)) {
            recordDenial(session, permission);
            throw new AuthorizationException("You do not have permission to perform this action.");
        }
    }

    // Denied attempts are logged for audit (FR-02). Wired to a persistent audit sink later;
    // never logs passwords or other sensitive data.
    private static void recordDenial(Session session, Permission permission) {
        System.err.println("[RBAC] denied user=" + session.getUserId()
            + " role=" + session.getRole() + " permission=" + permission);
    }
}
