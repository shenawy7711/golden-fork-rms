package config;

import dao.SystemConfigDAO;
import domain.PaymentMethod;

import java.util.List;

/**
 * Loads reference/system data from the database at start-up: the tunables in {@code system_config}
 * (as an {@link AppConfig}) and the fixed {@code payment_method} list (FR-31, FR-15).
 *
 * <p>Reads go through {@link SystemConfigDAO} rather than raw SQL here, so all statements stay in
 * the DAO layer (Principle VII).
 *
 * <p>{@link #loadOrDefaults()} is what the app shell calls: configuration should not be the reason
 * the application refuses to start, so an unreachable database yields the documented defaults and
 * lets the first real DAO call report the connection problem properly.
 */
public final class ReferenceDataLoader {

    private final SystemConfigDAO systemConfigDAO;

    public ReferenceDataLoader(SystemConfigDAO systemConfigDAO) {
        this.systemConfigDAO = systemConfigDAO;
    }

    /** Loads the tunables; propagates a {@code PersistenceException} if the database is unreachable. */
    public AppConfig load() {
        return AppConfig.from(systemConfigDAO.findAllValues());
    }

    /** Loads the tunables, falling back to {@link AppConfig#defaults()} if they cannot be read. */
    public AppConfig loadOrDefaults() {
        try {
            return load();
        } catch (RuntimeException e) {
            System.err.println("[config] Could not load system_config; using defaults: " + e.getMessage());
            return AppConfig.defaults();
        }
    }

    /** The fixed payment-method reference list (FR-15). */
    public List<PaymentMethod> paymentMethods() {
        return systemConfigDAO.listPaymentMethods();
    }
}
