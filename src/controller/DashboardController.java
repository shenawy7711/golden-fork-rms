package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import app.Screen;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import service.exception.ConflictException;
import service.security.Session;

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
     * The greeting and the stat tiles.
     *
     * <p>The tiles are deliberately empty of figures: open orders, free tables, reservations, and
     * today's sales all come from services that arrive with US1/US3/US5. Showing invented numbers
     * here would look exactly like real data, so each tile says what it is waiting for instead.
     */
    private Parent buildHome() {
        Session session = context.session();

        Label greeting = new Label(greetingFor(LocalTime.now()) + ", " + firstName(session.getUser().getFullName()));
        greeting.getStyleClass().addAll("h1", "heading");

        Label sub = new Label("Here is what is happening at Golden Fork right now.");
        sub.getStyleClass().add("greeting-sub");

        Label date = new Label(LocalDateTime.now().format(DATE));
        date.getStyleClass().add("date");

        Label shift = new Label("Shift open · " + LocalTime.now().format(TIME));
        shift.getStyleClass().add("date-sub");

        VBox left = new VBox(4, greeting, sub);
        VBox right = new VBox(2, date, shift);
        right.setStyle("-fx-alignment: center-right;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(left, spacer, right);

        HBox statsTop = new HBox(18,
            statCard("Open orders", "Arrives with the POS module (US1)"),
            statCard("Tables free", "Arrives with the tables module (US3)"));
        HBox statsBottom = new HBox(18,
            statCard("Reservations today", "Arrives with the reservations module (US5)"),
            statCard("Today's sales", "Arrives with the POS module (US1)"));

        VBox content = new VBox(24, header, statsTop, statsBottom);
        content.getStyleClass().add("content");
        return content;
    }

    private Region statCard(String title, String waitingFor) {
        Label label = new Label(title);
        label.getStyleClass().add("label");

        Label value = new Label("—");
        value.getStyleClass().add("value");

        Label sub = new Label(waitingFor);
        sub.getStyleClass().add("sub");
        sub.setWrapText(true);

        VBox card = new VBox(6, label, value, sub);
        card.getStyleClass().add("stat-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
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
