package service.security;

import domain.User;
import domain.enums.RoleName;

/**
 * An authenticated session: the signed-in user and their role. Immutable; created by
 * {@code AuthService.login} and consumed by {@link RbacGuard}. No {@code javafx.*} (Principle I),
 * so it is reused unchanged by the Phase-2 web tier.
 */
public final class Session {

    private final User user;

    public Session(User user) {
        if (user == null) throw new IllegalArgumentException("session requires a user");
        this.user = user;
    }

    public User getUser() { return user; }

    public int getUserId() { return user.getUserId(); }

    public RoleName getRole() { return user.getRole(); }

    /** Convenience: does this session's role hold the given permission? */
    public boolean has(Permission permission) {
        return permission.isGrantedTo(user.getRole());
    }
}
