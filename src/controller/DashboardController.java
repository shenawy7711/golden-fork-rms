package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import app.Screen;
import domain.DiningTable;
import domain.Order;
import domain.Reservation;
import domain.StockItem;
import domain.enums.OrderType;
import domain.enums.TableStatus;
import domain.report.ReportDocument;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import service.exception.ConflictException;
import service.exception.RmsException;
import service.security.Permission;
import service.security.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dashboard shell (T033, FR-02): topbar, role-filtered side nav, content host, status bar.
 *
 * <p>The nav is built from {@link Screen} filtered by the session's permissions, so a Cashier is
 * never shown a Management entry. That is presentation only — every service behind these screens
 * calls {@code RbacGuard.require} itself, so reaching one another way still fails (BR-03).
 */
public final class DashboardController implements ContextAware {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private ScrollPane navScroll;
    @FXML private VBox navBox;
    @FXML private StackPane contentHost;
    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private Button logoutButton;
    @FXML private Label statusLabel;
    @FXML private Label loggedInLabel;
    @FXML private Label dbStatusLabel;

    private AppContext context;
    private Navigator navigator;

    private final List<Button> navButtons = new ArrayList<>();
    private Button homeButton;

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        this.navigator = navigator;

        Session session = context.session();
        if (session == null) {
            // Shouldn't happen — the dashboard is only reachable after login — but never render a
            // shell with no identity behind it (BR-01).
            navigator.showAuth();
            return;
        }

        navigator.setContentHost(contentHost, this::clearNavSelection);

        populateUserChip(session);
        populateStatusBar(session);
        buildNav(session);

        logoutButton.setOnAction(event -> attemptLogout());

        showHome();
    }

    private void populateUserChip(Session session) {
        String fullName = session.getUser().getFullName();
        userNameLabel.setText(fullName);
        userRoleLabel.setText(session.getRole().dbValue());
        avatarLabel.setText(initials(fullName));
    }

    private void populateStatusBar(Session session) {
        statusLabel.setText("Ready");
        loggedInLabel.setText("Logged in " + LocalTime.now().format(TIME));
        // The session exists, which means a connection succeeded during login.
        dbStatusLabel.setText("DB: connected");
    }

    /** Builds the three nav sections from {@link Screen}, keeping only what the role may reach. */
    private void buildNav(Session session) {
        navBox.getChildren().clear();
        navButtons.clear();

        homeButton = navItem("Dashboard", "D", this::showHome);

        Map<Screen.Section, List<Screen>> permitted = new LinkedHashMap<>();
        for (Screen screen : Screen.values()) {
            if (!session.has(screen.permission())) continue;
            permitted.computeIfAbsent(screen.section(), key -> new ArrayList<>()).add(screen);
        }

        for (Screen.Section section : Screen.Section.values()) {
            List<Screen> screens = permitted.get(section);
            boolean isOperations = section == Screen.Section.OPERATIONS;
            if ((screens == null || screens.isEmpty()) && !isOperations) continue;

            VBox group = new VBox(2);
            Label title = new Label(section.title().toUpperCase());
            title.getStyleClass().add("nav-section-title");
            group.getChildren().add(title);

            // "Dashboard" is the shell's own home rather than a Screen, so it is added by hand.
            if (isOperations) {
                group.getChildren().add(homeButton);
            }
            if (screens != null) {
                for (Screen screen : screens) {
                    group.getChildren().add(navItem(screen.title(), screen.chip(), () -> open(screen)));
                }
            }
            navBox.getChildren().add(group);
        }
    }

    private Button navItem(String text, String chip, Runnable action) {
        Label chipLabel = new Label(chip);
        chipLabel.getStyleClass().add("nav-chip");

        Button button = new Button(text);
        button.getStyleClass().add("nav-item");
        button.setGraphic(chipLabel);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            select(button);
            action.run();
        });
        navButtons.add(button);
        return button;
    }

    private void select(Button selected) {
        for (Button button : navButtons) {
            button.getStyleClass().remove("active");
        }
        if (selected != null && !selected.getStyleClass().contains("active")) {
            selected.getStyleClass().add("active");
        }
    }

    private void clearNavSelection() {
        // Navigator swapped the centre; nothing to do beyond keeping the highlight honest.
    }

    private void open(Screen screen) {
        if (!screen.isImplemented()) {
            info(screen.title() + " is not available yet.",
                "This screen arrives with its module in a later phase.");
            return;
        }
        navigator.navigateTo(screen);
    }

    private void showHome() {
        select(homeButton);
        contentHost.getChildren().setAll(buildHome());
    }

    /**
     * The greeting, a live stat grid, quick actions, and two at-a-glance panels — every figure is
     * read from the services the signed-in role may reach, so a Cashier sees the operations tiles
     * while a Manager/Administrator also sees sales, stock, and staff. A screen the role can't reach
     * is simply left out rather than shown as a locked or invented number.
     */
    private Parent buildHome() {
        Session session = context.session();

        Label greeting = new Label(greetingFor(LocalTime.now()) + ", " + firstName(session.getUser().getFullName()));
        greeting.getStyleClass().addAll("h1", "heading");
        Label sub = new Label("Here is what is happening at Golden Fork right now.");
        sub.getStyleClass().add("greeting-sub");

        Label date = new Label(LocalDateTime.now().format(DATE));
        date.getStyleClass().add("date");
        Label shift = new Label("Signed in · " + LocalTime.now().format(TIME));
        shift.getStyleClass().add("date-sub");

        VBox left = new VBox(4, greeting, sub);
        VBox right = new VBox(2, date, shift);
        right.setAlignment(Pos.CENTER_RIGHT);
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox header = new HBox(left, headSpacer, right);
        header.setAlignment(Pos.CENTER_LEFT);

        FlowPane stats = new FlowPane(18, 18);
        for (Region card : buildStatCards(session)) {
            stats.getChildren().add(card);
        }

        VBox content = new VBox(26, header, stats);
        content.getStyleClass().add("content");

        Region actions = buildQuickActions(session);
        if (actions != null) content.getChildren().add(actions);

        HBox panels = buildGlancePanels(session);
        if (panels != null) content.getChildren().add(panels);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("app-root");
        return scroll;
    }

    /** The live stat cards, filtered to what the role may see. */
    private List<Region> buildStatCards(Session session) {
        List<Region> cards = new ArrayList<>();

        // Open orders — every role.
        try {
            List<Order> open = context.orderService().listOpenOrders();
            long dineIn = open.stream().filter(o -> o.getOrderType() == OrderType.DINE_IN).count();
            long takeaway = open.size() - dineIn;
            cards.add(statCard("Open orders", String.valueOf(open.size()),
                dineIn + " dine-in · " + takeaway + " takeaway", "#2F4C7A"));
        } catch (RmsException ignored) { /* skip a tile rather than break the dashboard */ }

        // Tables free — every role.
        try {
            List<DiningTable> tables = context.tableService().listTables();
            long free = tables.stream().filter(t -> t.getStatus() == TableStatus.FREE).count();
            long occupied = tables.stream().filter(t -> t.getStatus() == TableStatus.OCCUPIED).count();
            long reserved = tables.stream().filter(t -> t.getStatus() == TableStatus.RESERVED).count();
            cards.add(statCard("Tables free", free + " / " + tables.size(),
                occupied + " occupied · " + reserved + " reserved", "#3F9E6A"));
        } catch (RmsException ignored) { }

        // Reservations today — every role.
        try {
            LocalDate today = LocalDate.now();
            List<Reservation> upcoming = context.reservationService().listUpcoming();
            long todays = upcoming.stream()
                .filter(r -> r.getReservationDatetime() != null
                    && r.getReservationDatetime().toLocalDate().equals(today)).count();
            long stillAhead = upcoming.stream()
                .filter(r -> r.getReservationDatetime() != null
                    && r.getReservationDatetime().isAfter(LocalDateTime.now())).count();
            cards.add(statCard("Reservations today", String.valueOf(todays),
                stillAhead + " still upcoming", "#C8912E"));
        } catch (RmsException ignored) { }

        // Today's sales — VIEW_REPORTS.
        if (session.has(Permission.VIEW_REPORTS)) {
            try {
                LocalDate today = LocalDate.now();
                ReportDocument sales = context.reportService().salesReport(session, today, today, null);
                cards.add(statCard("Today's sales", summaryValue(sales, "Total sales: ", "0.00"),
                    summaryValue(sales, "Orders: ", "0") + " orders", "#16294A"));
            } catch (RmsException ignored) { }
        }

        // Low stock — MANAGE_STOCK.
        if (session.has(Permission.MANAGE_STOCK)) {
            try {
                int low = context.inventoryService().lowStockItems(session).size();
                cards.add(statCard("Low stock", String.valueOf(low), "items at or below reorder", "#C8912E"));
            } catch (RmsException ignored) { }
        }

        // Staff on file — MANAGE_STAFF.
        if (session.has(Permission.MANAGE_STAFF)) {
            try {
                int staff = context.staffService().listActive(session).size();
                cards.add(statCard("Active staff", String.valueOf(staff), "on the roster", "#6B7C93"));
            } catch (RmsException ignored) { }
        }

        return cards;
    }

    private Region statCard(String title, String value, String sub, String iconHex) {
        Label label = new Label(title);
        label.getStyleClass().add("label");

        Region icon = new Region();
        icon.getStyleClass().add("stat-icon");
        icon.setStyle("-fx-background-color: " + iconHex + "; -fx-background-radius: 8px;");
        icon.setMinSize(28, 28);
        icon.setMaxSize(28, 28);

        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox top = new HBox(label, headSpacer, icon);
        top.setAlignment(Pos.CENTER_LEFT);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("value");

        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("sub");
        subLabel.setWrapText(true);

        VBox card = new VBox(8, top, valueLabel, subLabel);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(232);
        card.setMinWidth(200);
        return card;
    }

    /** The quick-action row — only the destinations the role can actually reach. */
    private Region buildQuickActions(Session session) {
        List<Button> buttons = new ArrayList<>();
        addAction(buttons, session, Screen.ORDERS, "New order", true);
        addAction(buttons, session, Screen.TABLES, "Table floor", false);
        addAction(buttons, session, Screen.RESERVATIONS, "New reservation", false);
        addAction(buttons, session, Screen.MENU, "Add menu item", false);
        addAction(buttons, session, Screen.REPORTS, "Run report", false);
        if (buttons.isEmpty()) return null;

        Label heading = new Label("QUICK ACTIONS");
        heading.getStyleClass().add("section-label");
        FlowPane row = new FlowPane(12, 12);
        row.getChildren().addAll(buttons);
        return new VBox(12, heading, row);
    }

    private void addAction(List<Button> into, Session session, Screen screen, String text, boolean primary) {
        if (!session.has(screen.permission()) || !screen.isImplemented()) return;
        Button button = new Button(text);
        button.getStyleClass().add("qa");
        if (primary) button.getStyleClass().add("primary");
        button.setOnAction(event -> openFromHome(screen));
        into.add(button);
    }

    /** Opens a screen from the dashboard and lights the matching nav entry. */
    private void openFromHome(Screen screen) {
        for (Button button : navButtons) {
            if (screen.title().equals(button.getText())) {
                select(button);
                break;
            }
        }
        navigator.navigateTo(screen);
    }

    /** "Tables at a glance" and, for stock-permitted roles, "Low stock alerts". */
    private HBox buildGlancePanels(Session session) {
        List<Region> panels = new ArrayList<>();

        try {
            List<DiningTable> tables = context.tableService().listTables();
            tables.sort(TableController.byLabel());
            if (!tables.isEmpty()) panels.add(tablesGlancePanel(tables));
        } catch (RmsException ignored) { }

        if (session.has(Permission.MANAGE_STOCK)) {
            try {
                panels.add(lowStockPanel(context.inventoryService().lowStockItems(session)));
            } catch (RmsException ignored) { }
        }

        if (panels.isEmpty()) return null;
        HBox row = new HBox(20);
        for (Region panel : panels) {
            HBox.setHgrow(panel, Priority.ALWAYS);
            panel.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().add(panel);
        }
        return row;
    }

    private Region tablesGlancePanel(List<DiningTable> tables) {
        Label title = new Label("Tables at a glance");
        title.getStyleClass().add("h2");
        Button open = new Button("Open table floor");
        open.getStyleClass().add("link");
        open.setOnAction(event -> openFromHome(Screen.TABLES));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(title, spacer, open);
        head.setAlignment(Pos.CENTER_LEFT);

        FlowPane grid = new FlowPane(12, 12);
        int shown = 0;
        for (DiningTable table : tables) {
            if (shown++ >= 8) break;
            grid.getChildren().add(tableTile(table));
        }

        VBox panel = new VBox(14, head, grid);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Region tableTile(DiningTable table) {
        Label dot = new Label("●");
        dot.getStyleClass().add(statusStyle(table.getStatus()));
        Label id = new Label(table.getLabel());
        id.getStyleClass().add("id");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(id, spacer, dot);
        top.setAlignment(Pos.CENTER_LEFT);

        Label detail = new Label(table.getStatus().dbValue() + " · seats " + table.getCapacity());
        detail.getStyleClass().add("detail");

        VBox tile = new VBox(4, top, detail);
        tile.getStyleClass().add("table-tile");
        tile.setPrefWidth(120);
        tile.setOnMouseClicked(event -> openFromHome(Screen.TABLES));
        return tile;
    }

    private Region lowStockPanel(List<StockItem> lowItems) {
        Label title = new Label("Low stock alerts");
        title.getStyleClass().add("h2");
        Button view = new Button("View inventory");
        view.getStyleClass().add("link");
        view.setOnAction(event -> openFromHome(Screen.INVENTORY));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(title, spacer, view);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(10);
        if (lowItems.isEmpty()) {
            Label none = new Label("Everything is above its reorder level.");
            none.getStyleClass().add("greeting-sub");
            rows.getChildren().add(none);
        } else {
            int shown = 0;
            for (StockItem item : lowItems) {
                if (shown++ >= 4) break;
                rows.getChildren().add(lowStockRow(item));
            }
        }

        VBox panel = new VBox(14, head, rows);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Region lowStockRow(StockItem item) {
        Label name = new Label(item.getName());
        name.getStyleClass().add("h2");
        Label detail = new Label("On hand " + plain(item.getQuantityOnHand()) + " " + item.getUnitOfMeasure()
            + " · reorder " + plain(item.getReorderLevel()));
        detail.getStyleClass().add("greeting-sub");
        VBox text = new VBox(2, name, detail);

        Label pill = new Label("Low");
        pill.getStyleClass().addAll("pill", "warn");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, text, spacer, pill);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String statusStyle(TableStatus status) {
        switch (status) {
            case FREE: return "st-free";
            case OCCUPIED: return "st-occupied";
            case RESERVED: return "st-reserved";
            case NEEDS_CLEANING: return "st-cleaning";
            default: return "st-free";
        }
    }

    private static String plain(java.math.BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /** Pulls a value out of a report's summary lines, e.g. "Total sales: 4210.00" → "4210.00". */
    private static String summaryValue(ReportDocument doc, String prefix, String fallback) {
        for (String line : doc.getSummary()) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return fallback;
    }

    private void attemptLogout() {
        try {
            context.authService().logout(context.session());
            navigator.showAuth();
        } catch (ConflictException openOrder) {
            // BR-07: an unsaved open order must be resolved first, so the session stays.
            info("Cannot sign out yet", openOrder.getMessage());
        }
    }

    private static void info(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private static String greetingFor(LocalTime now) {
        if (now.getHour() < 12) return "Good morning";
        if (now.getHour() < 18) return "Good afternoon";
        return "Good evening";
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "there";
        return fullName.trim().split("\\s+")[0];
    }

    private static String initials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }
}
