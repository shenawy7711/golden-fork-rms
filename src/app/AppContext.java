package app;

import config.AppConfig;
import config.ReferenceDataLoader;
import dao.ConnectionFactory;
import dao.DiningTableDAO;
import dao.LoginEventDAO;
import dao.MenuCategoryDAO;
import dao.MenuItemDAO;
import dao.RoleDAO;
import dao.SystemConfigDAO;
import dao.UserDAO;
import service.AuthService;
import service.MenuService;
import service.SystemConfigService;
import service.TableService;
import service.UserService;
import service.security.Session;

/**
 * The application's composition root: builds the DAOs and services once and hands them to whatever
 * needs them. Controllers receive this via {@link ContextAware} rather than constructing their own
 * dependencies, so there is a single place where wiring lives.
 *
 * <p>This class is the only one that knows how the object graph fits together — the services
 * themselves stay framework-free and reusable by the Phase-2 web tier (Principles I, II).
 */
public final class AppContext {

    private final AppConfig config;
    private final AuthService authService;
    private final UserService userService;
    private final SystemConfigService systemConfigService;
    private final MenuService menuService;
    private final TableService tableService;

    private Session session;

    private AppContext(AppConfig config, AuthService authService, UserService userService,
                       SystemConfigService systemConfigService, MenuService menuService,
                       TableService tableService) {
        this.config = config;
        this.authService = authService;
        this.userService = userService;
        this.systemConfigService = systemConfigService;
        this.menuService = menuService;
        this.tableService = tableService;
    }

    /** Wires the graph from {@code config/db.properties} and the {@code system_config} table. */
    public static AppContext bootstrap() {
        ConnectionFactory connections = new ConnectionFactory();

        RoleDAO roleDAO = new RoleDAO(connections);
        UserDAO userDAO = new UserDAO(connections);
        LoginEventDAO loginEventDAO = new LoginEventDAO(connections);
        SystemConfigDAO systemConfigDAO = new SystemConfigDAO(connections);
        MenuCategoryDAO menuCategoryDAO = new MenuCategoryDAO(connections);
        MenuItemDAO menuItemDAO = new MenuItemDAO(connections);
        DiningTableDAO diningTableDAO = new DiningTableDAO(connections);

        AppConfig config = new ReferenceDataLoader(systemConfigDAO).loadOrDefaults();

        return new AppContext(
            config,
            new AuthService(userDAO, loginEventDAO, config.loginMaxAttempts()),
            new UserService(userDAO, roleDAO, loginEventDAO),
            new SystemConfigService(systemConfigDAO),
            new MenuService(menuCategoryDAO, menuItemDAO),
            new TableService(diningTableDAO));
    }

    public AppConfig config() { return config; }

    public AuthService authService() { return authService; }

    public UserService userService() { return userService; }

    public SystemConfigService systemConfigService() { return systemConfigService; }

    public MenuService menuService() { return menuService; }

    public TableService tableService() { return tableService; }

    /** The signed-in session, or {@code null} before login / after logout. */
    public Session session() { return session; }

    public void setSession(Session session) { this.session = session; }
}
