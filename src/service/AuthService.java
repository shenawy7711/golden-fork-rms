package service;

import dao.LoginEventDAO;
import dao.UserDAO;
import domain.User;
import domain.enums.LoginEventType;
import domain.enums.Status;
import service.exception.AuthorizationException;
import service.exception.ConflictException;
import service.security.PasswordHasher;
import service.security.Session;
import util.Validation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sign-in and sign-out (FR-01, FR-04; BR-01, BR-02, BR-07).
 *
 * <p>No {@code javafx.*} (Principle I) — the Phase-2 web tier reuses this unchanged.
 *
 * <p><b>Generic failure by design.</b> Every rejected sign-in — unknown username, wrong password,
 * or a deactivated account — produces the same {@link AuthorizationException} message. Telling the
 * caller which one failed would let an attacker enumerate valid usernames (FR-01 acceptance
 * criteria). Plain-text passwords are never logged or echoed (BR-02).
 */
public final class AuthService {

    /** The single message for every failed sign-in (FR-01). */
    static final String INVALID_CREDENTIALS = "Invalid username or password";

    /**
     * How long a username stays throttled once it hits the attempt limit.
     *
     * <p>The FRD (FR-01) requires the login be "temporarily throttled" after
     * {@code login_max_attempts} consecutive failures but does not state for how long, so this is
     * an implementation decision: long enough to make brute force impractical, short enough that a
     * cashier who fat-fingered their password mid-service is not locked out for the shift.
     */
    static final Duration THROTTLE_WINDOW = Duration.ofMinutes(5);

    private final UserDAO userDAO;
    private final LoginEventDAO loginEventDAO;
    private final int maxAttempts;
    private final OpenOrderCheck openOrderCheck;

    // Consecutive failures per username. In-memory and per-process: the schema has no column for
    // it, and a throttle that resets on restart is the documented trade-off rather than a silent one.
    private final Map<String, FailureRecord> failures = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts consecutive failures before throttling — {@code login_max_attempts} from
     *                    {@code AppConfig} (default 5, BR-07)
     */
    public AuthService(UserDAO userDAO, LoginEventDAO loginEventDAO, int maxAttempts) {
        this(userDAO, loginEventDAO, maxAttempts, session -> false);
    }

    /**
     * @param openOrderCheck answers "does this session hold an unsaved Open order?" — supplied by
     *                       {@code OrderService} once US1 exists; defaults to "no" until then
     */
    public AuthService(UserDAO userDAO, LoginEventDAO loginEventDAO, int maxAttempts,
                       OpenOrderCheck openOrderCheck) {
        this.userDAO = userDAO;
        this.loginEventDAO = loginEventDAO;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.openOrderCheck = openOrderCheck;
    }

    /**
     * Authenticates a user and returns their {@link Session} (FR-01).
     *
     * @throws AuthorizationException with the generic message for any invalid credential, an
     *                                inactive account, or a throttled username
     * @throws service.exception.PersistenceException if the data store is unreachable — the caller
     *                                shows "Unable to sign in — service unavailable"; no session
     */
    public Session login(String username, String password) {
        if (Validation.isBlank(username) || Validation.isBlank(password)) {
            throw new AuthorizationException(INVALID_CREDENTIALS);
        }
        String key = username.trim().toLowerCase();
        if (isThrottled(key)) {
            throw new AuthorizationException(INVALID_CREDENTIALS);
        }

        User user = userDAO.findByUsername(username.trim());

        // Verify even when the user is absent or inactive, against a throwaway hash, so that a
        // failed sign-in takes the same time regardless of the reason. Skipping the hash for an
        // unknown username returns visibly faster and leaks which usernames exist.
        String storedHash = (user == null) ? PasswordHasher.dummyHash() : user.getPasswordHash();
        boolean passwordMatches = PasswordHasher.verify(password, storedHash);

        if (user == null || !passwordMatches || user.getStatus() != Status.ACTIVE) {
            recordFailure(key);
            throw new AuthorizationException(INVALID_CREDENTIALS);
        }

        failures.remove(key);
        loginEventDAO.record(user.getUserId(), LoginEventType.LOGIN);
        return new Session(user);
    }

    /**
     * Ends the session and records the LOGOUT event (FR-04).
     *
     * @throws ConflictException if an unsaved Open order is held — it must be finalised, parked, or
     *                           discarded first, so no partial financial data is orphaned (BR-07)
     */
    public void logout(Session session) {
        if (session == null) return;
        if (openOrderCheck.hasUnsavedOpenOrder(session)) {
            throw new ConflictException(
                "You have an unsaved open order. Finalise, park, or discard it before signing out.");
        }
        loginEventDAO.record(session.getUserId(), LoginEventType.LOGOUT);
    }

    private boolean isThrottled(String key) {
        FailureRecord record = failures.get(key);
        if (record == null || record.count < maxAttempts) return false;
        if (Duration.between(record.lastFailure, LocalDateTime.now()).compareTo(THROTTLE_WINDOW) >= 0) {
            failures.remove(key);   // window elapsed — start clean
            return false;
        }
        return true;
    }

    private void recordFailure(String key) {
        failures.merge(key, new FailureRecord(1, LocalDateTime.now()),
            (existing, fresh) -> new FailureRecord(existing.count + 1, fresh.lastFailure));
    }

    /** Consecutive failures for one username and when the last one happened. */
    private static final class FailureRecord {
        final int count;
        final LocalDateTime lastFailure;

        FailureRecord(int count, LocalDateTime lastFailure) {
            this.count = count;
            this.lastFailure = lastFailure;
        }
    }

    /**
     * Whether a session holds an unsaved Open order (BR-07). Implemented by {@code OrderService}
     * in US1; until then {@link AuthService} defaults it to "no" so logout works.
     */
    @FunctionalInterface
    public interface OpenOrderCheck {
        boolean hasUnsavedOpenOrder(Session session);
    }
}
