package service;

import dao.LoginEventDAO;
import dao.RoleDAO;
import dao.UserDAO;
import domain.Role;
import domain.User;
import domain.enums.RoleName;
import domain.enums.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.exception.AuthorizationException;
import service.exception.ConflictException;
import service.exception.ValidationException;
import service.security.Session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserService} business rules (FR-03): unique username (BR-04) and the last-active-
 * Administrator protection (BR-06), which must never allow zero active Administrators.
 *
 * <p>The DAOs are mocked — these assert the service's decisions, not SQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — account rules (FR-03; BR-04, BR-06)")
class UserServiceTest {

    @Mock private UserDAO userDAO;
    @Mock private RoleDAO roleDAO;
    @Mock private LoginEventDAO loginEventDAO;

    private UserService service;
    private Session adminSession;

    private static User user(int id, String username, RoleName role, Status status) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setFullName("Test User");
        user.setRole(role);
        user.setRoleId(role.ordinal() + 1);
        user.setStatus(status);
        return user;
    }

    @BeforeEach
    void setUp() {
        service = new UserService(userDAO, roleDAO, loginEventDAO);
        adminSession = new Session(user(1, "admin", RoleName.ADMINISTRATOR, Status.ACTIVE));
    }

    private void stubRoleLookup(RoleName roleName) {
        Role role = new Role();
        role.setRoleId(roleName.ordinal() + 1);
        role.setRoleName(roleName);
        when(roleDAO.findByName(roleName)).thenReturn(role);
    }

    // --- BR-04: unique username -------------------------------------------------

    @Test
    @DisplayName("create rejects a username that already exists (BR-04)")
    void createRejectsDuplicateUsername() {
        when(userDAO.findByUsername("jsmith")).thenReturn(user(7, "jsmith", RoleName.CASHIER, Status.ACTIVE));

        User candidate = user(0, "jsmith", RoleName.CASHIER, Status.ACTIVE);
        ConflictException thrown = assertThrows(ConflictException.class,
            () -> service.create(adminSession, candidate, "passw0rd"));

        assertEquals("Username already exists.", thrown.getMessage());
        verify(userDAO, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("create stores the password hashed, never the plain text (BR-02)")
    void createHashesPassword() {
        when(userDAO.findByUsername("newbie")).thenReturn(null);
        stubRoleLookup(RoleName.CASHIER);

        User candidate = user(0, "newbie", RoleName.CASHIER, Status.ACTIVE);
        service.create(adminSession, candidate, "passw0rd");

        assertNotEquals("passw0rd", candidate.getPasswordHash());
        verify(userDAO).insert(candidate);
    }

    @Test
    @DisplayName("create rejects a password that breaches the Appendix A policy")
    void createRejectsWeakPassword() {
        User candidate = user(0, "newbie", RoleName.CASHIER, Status.ACTIVE);

        assertThrows(ValidationException.class,
            () -> service.create(adminSession, candidate, "short1"));      // under 8 chars
        assertThrows(ValidationException.class,
            () -> service.create(adminSession, candidate, "alllettersonly"));  // no digit

        verify(userDAO, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("update rejects a username already taken by a different account (BR-04)")
    void updateRejectsDuplicateUsername() {
        when(userDAO.findById(5)).thenReturn(user(5, "old", RoleName.CASHIER, Status.ACTIVE));
        when(userDAO.findByUsername("taken")).thenReturn(user(9, "taken", RoleName.CASHIER, Status.ACTIVE));

        User edited = user(5, "taken", RoleName.CASHIER, Status.ACTIVE);
        assertThrows(ConflictException.class, () -> service.update(adminSession, edited));

        verify(userDAO, never()).update(any(User.class));
    }

    @Test
    @DisplayName("update allows an account to keep its own username")
    void updateAllowsSameUsername() {
        when(userDAO.findById(5)).thenReturn(user(5, "same", RoleName.CASHIER, Status.ACTIVE));
        when(userDAO.findByUsername("same")).thenReturn(user(5, "same", RoleName.CASHIER, Status.ACTIVE));
        stubRoleLookup(RoleName.CASHIER);

        User edited = user(5, "same", RoleName.CASHIER, Status.ACTIVE);
        service.update(adminSession, edited);

        verify(userDAO).update(edited);
    }

    // --- BR-06: never zero active Administrators --------------------------------

    @Test
    @DisplayName("deactivate refuses the last active Administrator (BR-06)")
    void deactivateRefusesLastAdmin() {
        when(userDAO.findById(1)).thenReturn(user(1, "admin", RoleName.ADMINISTRATOR, Status.ACTIVE));
        when(userDAO.countActiveAdministrators()).thenReturn(1);

        assertThrows(ValidationException.class, () -> service.deactivate(adminSession, 1));

        verify(userDAO, never()).updateStatus(anyInt(), any(Status.class));
    }

    @Test
    @DisplayName("deactivate allows an Administrator when another active one remains (BR-06)")
    void deactivateAllowsWhenAnotherAdminRemains() {
        when(userDAO.findById(2)).thenReturn(user(2, "admin2", RoleName.ADMINISTRATOR, Status.ACTIVE));
        when(userDAO.countActiveAdministrators()).thenReturn(2);

        service.deactivate(adminSession, 2);

        verify(userDAO).updateStatus(2, Status.INACTIVE);
    }

    @Test
    @DisplayName("delete refuses the last active Administrator (BR-06)")
    void deleteRefusesLastAdmin() {
        when(userDAO.findById(1)).thenReturn(user(1, "admin", RoleName.ADMINISTRATOR, Status.ACTIVE));
        when(userDAO.countActiveAdministrators()).thenReturn(1);

        assertThrows(ValidationException.class, () -> service.delete(adminSession, 1));

        verify(userDAO, never()).delete(anyInt());
    }

    @Test
    @DisplayName("demoting the last active Administrator off the role is refused too (BR-06)")
    void updateRefusesDemotingLastAdmin() {
        when(userDAO.findById(1)).thenReturn(user(1, "admin", RoleName.ADMINISTRATOR, Status.ACTIVE));
        when(userDAO.findByUsername("admin")).thenReturn(user(1, "admin", RoleName.ADMINISTRATOR, Status.ACTIVE));
        when(userDAO.countActiveAdministrators()).thenReturn(1);

        User demoted = user(1, "admin", RoleName.MANAGER, Status.ACTIVE);
        assertThrows(ValidationException.class, () -> service.update(adminSession, demoted));

        verify(userDAO, never()).update(any(User.class));
    }

    // --- BR-05: history is preserved --------------------------------------------

    @Test
    @DisplayName("delete refuses an account with session history, offering deactivation (BR-05)")
    void deleteRefusesAccountWithHistory() {
        when(userDAO.findById(4)).thenReturn(user(4, "cashier", RoleName.CASHIER, Status.ACTIVE));
        when(loginEventDAO.existsForUser(4)).thenReturn(true);

        ConflictException thrown = assertThrows(ConflictException.class,
            () -> service.delete(adminSession, 4));

        assertEquals("This account has activity history and cannot be deleted. Deactivate it instead.",
            thrown.getMessage());
        verify(userDAO, never()).delete(anyInt());
    }

    @Test
    @DisplayName("delete removes an account with no history (BR-05)")
    void deleteRemovesAccountWithoutHistory() {
        when(userDAO.findById(4)).thenReturn(user(4, "cashier", RoleName.CASHIER, Status.ACTIVE));
        when(loginEventDAO.existsForUser(4)).thenReturn(false);

        service.delete(adminSession, 4);

        verify(userDAO).delete(4);
    }

    // --- BR-03: the guard stands even here --------------------------------------

    @Test
    @DisplayName("a Manager is refused account management even calling the service directly (BR-03)")
    void managerCannotManageUsers() {
        Session manager = new Session(user(3, "manager", RoleName.MANAGER, Status.ACTIVE));
        User candidate = user(0, "newbie", RoleName.CASHIER, Status.ACTIVE);

        assertThrows(AuthorizationException.class, () -> service.list(manager));
        assertThrows(AuthorizationException.class, () -> service.create(manager, candidate, "passw0rd"));
        assertThrows(AuthorizationException.class, () -> service.deactivate(manager, 4));
        assertThrows(AuthorizationException.class, () -> service.delete(manager, 4));

        verify(userDAO, never()).insert(any(User.class));
        verify(userDAO, never()).delete(anyInt());
    }
}
