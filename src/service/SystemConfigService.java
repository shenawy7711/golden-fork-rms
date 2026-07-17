package service;

import config.AppConfig;
import dao.PaymentMethodDAO;
import dao.SystemConfigDAO;
import domain.PaymentMethod;
import domain.enums.Status;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Administrator-only reference/system data (FR-31; BR-31, BR-03, BR-09, BR-18).
 *
 * <p>Every method calls {@code RbacGuard.require(CONFIGURE_SYSTEM)} first, so a Manager or Cashier
 * is refused even if the interface were bypassed (BR-03). Each write records the acting
 * administrator and a timestamp for audit (BR-31).
 *
 * <p><b>Changing the tax rate never alters a finalised bill.</b> This service stores the rate in
 * force; {@code OrderService} snapshots it onto the order at finalisation, and the receipt and
 * reports read that stored figure (BR-18, BR-09). A rate change applies to future finalisations
 * only.
 *
 * <p>No {@code javafx.*} (Principle I).
 */
public final class SystemConfigService {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final SystemConfigDAO systemConfigDAO;
    private final PaymentMethodDAO paymentMethodDAO;

    public SystemConfigService(SystemConfigDAO systemConfigDAO, PaymentMethodDAO paymentMethodDAO) {
        this.systemConfigDAO = systemConfigDAO;
        this.paymentMethodDAO = paymentMethodDAO;
    }

    /** Every configuration entry (FR-31). */
    public Map<String, String> getAll(Session session) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        return systemConfigDAO.findAllValues();
    }

    /** One configuration value, or {@code null} when the key is absent (FR-31). */
    public String get(Session session, String key) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        return systemConfigDAO.findValue(key);
    }

    /** The tunables as typed values — the same view {@code ReferenceDataLoader} builds at boot. */
    public AppConfig getConfig(Session session) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        return AppConfig.from(systemConfigDAO.findAllValues());
    }

    /**
     * Validates per Appendix A and persists the value with {@code updated_by}/{@code updated_at}
     * (FR-31, BR-31).
     *
     * @throws ValidationException if the key is unknown or the value breaches Appendix A
     */
    public void update(Session session, String key, String value) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        if (key == null || key.trim().isEmpty()) {
            throw new ValidationException("Select a setting to change.");
        }
        String normalisedKey = key.trim();
        String normalisedValue = value == null ? "" : value.trim();
        validate(normalisedKey, normalisedValue);
        systemConfigDAO.upsertValue(normalisedKey, normalisedValue, session.getUserId());
    }

    /** The payment-method reference list (FR-15, FR-31). */
    public List<PaymentMethod> listPaymentMethods(Session session) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        return systemConfigDAO.listPaymentMethods();
    }

    /**
     * Activates or deactivates a payment method (FR-15, FR-31). A method is never hard-deleted — a
     * finalised payment may reference it — so it is flipped Inactive to retire it. <b>At least one
     * method must stay Active</b> so an order can always be paid; deactivating the last one is
     * refused.
     *
     * @throws ValidationException if the method does not exist
     * @throws ConflictException  if deactivating would leave no active method
     */
    public void setPaymentMethodActive(Session session, int methodId, boolean active) {
        RbacGuard.require(session, Permission.CONFIGURE_SYSTEM);
        PaymentMethod method = paymentMethodDAO.findById(methodId);
        if (method == null) {
            throw new ValidationException("That payment method no longer exists.");
        }
        if (!active && method.getStatus() == Status.ACTIVE && paymentMethodDAO.countActive() <= 1) {
            throw new ConflictException("At least one payment method must stay active.");
        }
        paymentMethodDAO.setStatus(methodId, active ? Status.ACTIVE : Status.INACTIVE);
    }

    // Appendix A, "System constants" rows. Each key has its own rule; an unknown key is rejected
    // rather than stored, so a typo cannot silently create a setting nothing reads.
    private static void validate(String key, String value) {
        switch (key) {
            case AppConfig.KEY_TAX_RATE:
                BigDecimal rate = decimal(value, "Enter a tax rate between 0 and 100%.");
                if (rate.signum() < 0 || rate.compareTo(ONE) > 0) {
                    throw new ValidationException("Enter a tax rate between 0 and 100%.");
                }
                if (rate.scale() > 4) {
                    throw new ValidationException("A tax rate may have at most four decimal places.");
                }
                break;

            case AppConfig.KEY_DISCOUNT_APPROVAL_THRESHOLD:
                BigDecimal threshold = decimal(value, "Enter a non-negative value.");
                if (threshold.signum() < 0) {
                    throw new ValidationException("Enter a non-negative value.");
                }
                break;

            case AppConfig.KEY_IDLE_TIMEOUT_MIN:
                // 0 is valid here and disables auto-logout (FRD FR-31 table).
                if (integer(value, "Enter a whole number of minutes (0 disables auto-logout).") < 0) {
                    throw new ValidationException("Enter a whole number of minutes (0 disables auto-logout).");
                }
                break;

            case AppConfig.KEY_LOGIN_MAX_ATTEMPTS:
                if (integer(value, "Enter a whole number of attempts (at least 1).") < 1) {
                    throw new ValidationException("Enter a whole number of attempts (at least 1).");
                }
                break;

            case AppConfig.KEY_RESERVATION_SLOT_MINUTES:
                if (integer(value, "Enter a whole number of minutes (at least 1).") < 1) {
                    throw new ValidationException("Enter a whole number of minutes (at least 1).");
                }
                break;

            default:
                throw new ValidationException("Unknown setting: " + key);
        }
    }

    private static BigDecimal decimal(String value, String message) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(message);
        }
    }

    private static int integer(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(message);
        }
    }
}
