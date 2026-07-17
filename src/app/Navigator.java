package app;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Owns the stage and the scene, and swaps what is on screen (T023).
 *
 * <p>Two moves: {@link #showAuth()} and {@link #showDashboard()} replace the whole scene root,
 * while {@link #navigateTo(Screen)} swaps only the dashboard's centre content so the topbar, nav,
 * and status bar stay put.
 *
 * <p>{@code app.css} is applied once to the scene, so every screen loaded into it inherits the
 * styling without each FXML having to reference the stylesheet.
 */
public final class Navigator {

    private static final String STYLESHEET = "/view/css/app.css";
    private static final String AUTH_FXML = "/view/auth.fxml";
    private static final String DASHBOARD_FXML = "/view/dashboard.fxml";

    private final Stage stage;
    private final AppContext context;

    private Scene scene;
    private DashboardHost dashboardHost;
    private IdleTimer idleTimer;

    public Navigator(Stage stage, AppContext context) {
        this.stage = stage;
        this.context = context;
    }

    /** Shows the login screen, discarding any dashboard and its session (FR-01). */
    public void showAuth() {
        stopIdleTimer();
        context.setSession(null);
        dashboardHost = null;
        setRoot(load(AUTH_FXML));
    }

    /** Shows the dashboard shell for the signed-in session and lands on its first screen (FR-02). */
    public void showDashboard() {
        Parent dashboard = load(DASHBOARD_FXML);
        setRoot(dashboard);
        startIdleTimer();
    }

    /**
     * Starts the inactivity auto-logout for the signed-in session (FR-04). The window comes from
     * {@code idle_timeout_min} in {@code system_config} (default 15). On timeout the session ends and
     * the login screen returns with a notice.
     */
    private void startIdleTimer() {
        stopIdleTimer();
        idleTimer = new IdleTimer(scene, context.config().idleTimeoutMinutes(), this::onIdleTimeout);
        idleTimer.start();
    }

    private void stopIdleTimer() {
        if (idleTimer != null) {
            idleTimer.stop();
            idleTimer = null;
        }
    }

    private void onIdleTimeout() {
        // Record the logout when we can; an idle sign-out must proceed even if that write fails, so
        // the session never lingers after the timeout.
        try {
            if (context.session() != null) {
                context.authService().logout(context.session());
            }
        } catch (RuntimeException ignored) {
            // e.g. an unsaved open order blocks a clean logout — the auto-logout still proceeds.
        }
        showAuth();
        Alert notice = new Alert(Alert.AlertType.INFORMATION,
            "You were signed out after a period of inactivity.");
        notice.setTitle("Golden Fork RMS");
        notice.setHeaderText("Session ended");
        notice.show();
    }

    /**
     * Swaps the dashboard's centre content (T023). Silently does nothing when no dashboard is
     * hosting — {@link #showDashboard()} must have run first.
     */
    public void navigateTo(Screen screen) {
        if (dashboardHost == null) return;
        dashboardHost.setContent(screen, load(screen.fxml()));
    }

    /**
     * Registered by {@code DashboardController} so {@link #navigateTo} knows where to put content.
     *
     * @param onContentChanged run after each swap, or {@code null}
     */
    public void setContentHost(StackPane contentHost, Runnable onContentChanged) {
        this.dashboardHost = (screen, content) -> {
            contentHost.getChildren().setAll(content);
            if (onContentChanged != null) onContentChanged.run();
        };
    }

    public Stage stage() { return stage; }

    /**
     * Loads an FXML, wiring the controller with {@link ContextAware#init} when it asks for it.
     *
     * @throws IllegalStateException if the FXML is missing or malformed — a packaging error the
     *                               user cannot act on, so it fails loudly rather than silently
     */
    private Parent load(String fxmlPath) {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalStateException("Missing screen resource: " + fxmlPath);
        }
        try {
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof ContextAware) {
                ((ContextAware) controller).init(context, this);
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load screen: " + fxmlPath, e);
        }
    }

    private void setRoot(Parent root) {
        if (scene == null) {
            scene = new Scene(root);
            applyStylesheet(scene);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }

    private void applyStylesheet(Scene target) {
        URL css = getClass().getResource(STYLESHEET);
        if (css == null) {
            // Styling is cosmetic — a missing stylesheet should not stop the app from running.
            System.err.println("[view] Stylesheet not found on the classpath: " + STYLESHEET);
            return;
        }
        target.getStylesheets().add(css.toExternalForm());
    }

    /** The dashboard shell's contract with the navigator: somewhere to put a screen. */
    interface DashboardHost {
        void setContent(Screen screen, Parent content);
    }
}
