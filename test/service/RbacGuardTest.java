package service;

import domain.User;
import domain.enums.RoleName;
import domain.enums.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import service.exception.AuthorizationException;
import service.security.Permission;
import service.security.RbacGuard;
import service.security.Session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Constitution RBAC gate (Principle IV, FR-02, BR-03): asserts the FRD §2.4 Role–Permission Matrix
 * exactly. These run against {@link RbacGuard} directly — the business-layer check that stands
 * whether or not the interface hid the control.
 */
@DisplayName("RbacGuard — FRD §2.4 Role–Permission Matrix")
class RbacGuardTest {

    private static Session sessionFor(RoleName role) {
        User user = new User();
        user.setUserId(1);
        user.setUsername("test-" + role.name().toLowerCase());
        user.setFullName("Test User");
        user.setRole(role);
        user.setStatus(Status.ACTIVE);
        return new Session(user);
    }

    private static void assertDenied(RoleName role, Permission permission) {
        assertThrows(AuthorizationException.class,
            () -> RbacGuard.require(sessionFor(role), permission),
            role + " must be denied " + permission);
    }

    private static void assertAllowed(RoleName role, Permission permission) {
        assertDoesNotThrow(
            () -> RbacGuard.require(sessionFor(role), permission),
            role + " must be allowed " + permission);
    }

    @Nested
    @DisplayName("Cashier")
    class CashierMatrix {

        @Test
        @DisplayName("is denied the management functions (FR-02 acceptance criteria)")
        void deniedManagementFunctions() {
            assertDenied(RoleName.CASHIER, Permission.MANAGE_MENU);
            assertDenied(RoleName.CASHIER, Permission.MANAGE_STOCK);
            assertDenied(RoleName.CASHIER, Permission.VIEW_REPORTS);
            assertDenied(RoleName.CASHIER, Permission.MANAGE_USERS);
        }

        @Test
        @DisplayName("is denied approving a discount above the threshold (BR-17)")
        void deniedDiscountApproval() {
            assertDenied(RoleName.CASHIER, Permission.APPROVE_DISCOUNT);
        }

        @Test
        @DisplayName("keeps its front-line operations")
        void allowedFrontLineOperations() {
            assertAllowed(RoleName.CASHIER, Permission.LOGIN);
            assertAllowed(RoleName.CASHIER, Permission.CREATE_ORDER);
            assertAllowed(RoleName.CASHIER, Permission.APPLY_DISCOUNT);
            assertAllowed(RoleName.CASHIER, Permission.UPDATE_TABLE_STATUS);
            assertAllowed(RoleName.CASHIER, Permission.MANAGE_RESERVATION);
        }
    }

    @Nested
    @DisplayName("Manager")
    class ManagerMatrix {

        @Test
        @DisplayName("is denied user-account and system administration")
        void deniedAdministration() {
            assertDenied(RoleName.MANAGER, Permission.MANAGE_USERS);
            assertDenied(RoleName.MANAGER, Permission.CONFIGURE_SYSTEM);
        }

        @Test
        @DisplayName("holds every operational permission (permissions are cumulative)")
        void allowedOperations() {
            for (Permission permission : Permission.values()) {
                if (permission == Permission.MANAGE_USERS || permission == Permission.CONFIGURE_SYSTEM) {
                    continue;
                }
                assertAllowed(RoleName.MANAGER, permission);
            }
        }
    }

    @Nested
    @DisplayName("Administrator")
    class AdministratorMatrix {

        @Test
        @DisplayName("is allowed every permission")
        void allowedEverything() {
            for (Permission permission : Permission.values()) {
                assertAllowed(RoleName.ADMINISTRATOR, permission);
            }
        }
    }

    @Test
    @DisplayName("cumulative grants: Administrator ⊇ Manager ⊇ Cashier")
    void grantsAreCumulative() {
        for (Permission permission : Permission.values()) {
            if (Permission.grantedTo(RoleName.CASHIER).contains(permission)) {
                assertAllowed(RoleName.MANAGER, permission);
            }
            if (Permission.grantedTo(RoleName.MANAGER).contains(permission)) {
                assertAllowed(RoleName.ADMINISTRATOR, permission);
            }
        }
    }

    @Test
    @DisplayName("no session is refused — no function without a valid session (BR-01)")
    void nullSessionDenied() {
        assertThrows(AuthorizationException.class,
            () -> RbacGuard.require(null, Permission.CREATE_ORDER));
    }
}
