package app;

import config.AppConfig;
import config.ReferenceDataLoader;
import dao.ConnectionFactory;
import dao.DiningTableDAO;
import dao.LoginEventDAO;
import dao.MenuCategoryDAO;
import dao.MenuItemDAO;
import dao.OrderDAO;
import dao.OrderItemDAO;
import dao.PaymentDAO;
import dao.PaymentMethodDAO;
import dao.PurchaseOrderDAO;
import dao.PurchaseOrderItemDAO;
import dao.ReservationDAO;
import dao.RoleDAO;
import dao.StaffDAO;
import dao.StockItemDAO;
import dao.StockMovementDAO;
import dao.SupplierDAO;
import dao.SystemConfigDAO;
import dao.UserDAO;
import service.AuthService;
import service.BillingService;
import service.InventoryService;
import service.MenuService;
import service.OrderService;
import service.PurchasingService;
import service.ReceiptService;
import service.ReservationService;
import service.StaffService;
import service.SupplierService;
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

    private final ReferenceDataLoader referenceDataLoader;
    private final AuthService authService;
    private final UserService userService;
    private final SystemConfigService systemConfigService;
    private final MenuService menuService;
    private final TableService tableService;
    private final BillingService billingService;
    private final OrderService orderService;
    private final ReceiptService receiptService;
    private final PaymentMethodDAO paymentMethodDAO;
    private final SupplierService supplierService;
    private final InventoryService inventoryService;
    private final PurchasingService purchasingService;
    private final StaffService staffService;
    private final ReservationService reservationService;

    /**
     * The tunables in force. Not final: an administrator's FR-31 edit replaces it via
     * {@link #refreshConfig()}, and {@link BillingService} reads it through a supplier so the new
     * tax rate reaches the next finalisation rather than waiting for a restart.
     */
    private volatile AppConfig config;

    private Session session;

    private AppContext(ReferenceDataLoader referenceDataLoader, AppConfig config,
                       AuthService authService, UserService userService,
                       SystemConfigService systemConfigService, MenuService menuService,
                       TableService tableService, BillingService billingService,
                       OrderService orderService, ReceiptService receiptService,
                       PaymentMethodDAO paymentMethodDAO, SupplierService supplierService,
                       InventoryService inventoryService, PurchasingService purchasingService,
                       StaffService staffService, ReservationService reservationService) {
        this.referenceDataLoader = referenceDataLoader;
        this.config = config;
        this.authService = authService;
        this.userService = userService;
        this.systemConfigService = systemConfigService;
        this.menuService = menuService;
        this.tableService = tableService;
        this.billingService = billingService;
        this.orderService = orderService;
        this.receiptService = receiptService;
        this.paymentMethodDAO = paymentMethodDAO;
        this.supplierService = supplierService;
        this.inventoryService = inventoryService;
        this.purchasingService = purchasingService;
        this.staffService = staffService;
        this.reservationService = reservationService;
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
        OrderDAO orderDAO = new OrderDAO(connections);
        OrderItemDAO orderItemDAO = new OrderItemDAO(connections);
        PaymentDAO paymentDAO = new PaymentDAO(connections);
        PaymentMethodDAO paymentMethodDAO = new PaymentMethodDAO(connections);
        SupplierDAO supplierDAO = new SupplierDAO(connections);
        StockItemDAO stockItemDAO = new StockItemDAO(connections);
        StockMovementDAO stockMovementDAO = new StockMovementDAO(connections);
        PurchaseOrderDAO purchaseOrderDAO = new PurchaseOrderDAO(connections);
        PurchaseOrderItemDAO purchaseOrderItemDAO = new PurchaseOrderItemDAO(connections);
        StaffDAO staffDAO = new StaffDAO(connections);
        ReservationDAO reservationDAO = new ReservationDAO(connections);

        ReferenceDataLoader referenceDataLoader = new ReferenceDataLoader(systemConfigDAO);
        AppConfig config = referenceDataLoader.loadOrDefaults();

        // Built before the context so it can be handed in; the supplier below closes over the
        // context field, which refreshConfig() updates.
        AppContext[] holder = new AppContext[1];
        BillingService billingService = new BillingService(() -> holder[0].config());
        TableService tableService = new TableService(diningTableDAO);

        AppContext context = new AppContext(
            referenceDataLoader,
            config,
            new AuthService(userDAO, loginEventDAO, config.loginMaxAttempts()),
            new UserService(userDAO, roleDAO, loginEventDAO),
            new SystemConfigService(systemConfigDAO),
            new MenuService(menuCategoryDAO, menuItemDAO),
            tableService,
            billingService,
            new OrderService(connections, orderDAO, orderItemDAO, paymentDAO, paymentMethodDAO,
                menuItemDAO, diningTableDAO, tableService, billingService),
            new ReceiptService(orderDAO, orderItemDAO, paymentDAO, paymentMethodDAO, menuItemDAO, userDAO),
            paymentMethodDAO,
            new SupplierService(supplierDAO),
            new InventoryService(connections, stockItemDAO, stockMovementDAO),
            new PurchasingService(connections, supplierDAO, stockItemDAO, stockMovementDAO,
                purchaseOrderDAO, purchaseOrderItemDAO),
            new StaffService(staffDAO),
            new ReservationService(connections, reservationDAO, diningTableDAO, tableService,
                () -> holder[0].config()));
        holder[0] = context;
        return context;
    }

    /**
     * Re-reads the tunables after an administrator changes one (FR-31), so the new tax rate or
     * threshold applies to subsequent orders without a restart. Called by
     * {@code SystemConfigController} once a save succeeds.
     */
    public void refreshConfig() {
        this.config = referenceDataLoader.loadOrDefaults();
    }

    public AppConfig config() { return config; }

    public AuthService authService() { return authService; }

    public UserService userService() { return userService; }

    public SystemConfigService systemConfigService() { return systemConfigService; }

    public MenuService menuService() { return menuService; }

    public TableService tableService() { return tableService; }

    public BillingService billingService() { return billingService; }

    public OrderService orderService() { return orderService; }

    public ReceiptService receiptService() { return receiptService; }

    public PaymentMethodDAO paymentMethodDAO() { return paymentMethodDAO; }

    public SupplierService supplierService() { return supplierService; }

    public InventoryService inventoryService() { return inventoryService; }

    public PurchasingService purchasingService() { return purchasingService; }

    public StaffService staffService() { return staffService; }

    public ReservationService reservationService() { return reservationService; }

    /** The signed-in session, or {@code null} before login / after logout. */
    public Session session() { return session; }

    public void setSession(Session session) { this.session = session; }
}
