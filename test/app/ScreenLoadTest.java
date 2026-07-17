package app;

import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves every FXML screen actually loads and binds to its controller.
 *
 * <p>Worth its own test because these failures are invisible to the compiler: a mistyped
 * {@code fx:id}, a control declared in FXML but missing from the controller, or a bad
 * {@code fx:controller} class name all compile cleanly and only throw when the screen is opened.
 *
 * <p>Loading runs each controller's {@code initialize()} but not {@code init(...)}, so no database
 * or session is involved.
 */
@DisplayName("FXML screens load and bind to their controllers")
class ScreenLoadTest {

    @BeforeAll
    static void initToolkit() {
        // Constructing a JFXPanel boots the JavaFX toolkit, which FXMLLoader needs in order to
        // instantiate controls. Cheaper than starting a full Application.
        new JFXPanel();
    }

    private static void assertLoads(String fxmlPath) throws Exception {
        URL resource = ScreenLoadTest.class.getResource(fxmlPath);
        assertNotNull(resource, fxmlPath + " is missing from the classpath");

        FXMLLoader loader = new FXMLLoader(resource);
        assertNotNull(loader.load(), fxmlPath + " loaded as null");
        assertNotNull(loader.getController(), fxmlPath + " has no controller");
    }

    @Test
    @DisplayName("auth.fxml (T032)")
    void authLoads() throws Exception {
        assertLoads("/view/auth.fxml");
    }

    @Test
    @DisplayName("dashboard.fxml (T033)")
    void dashboardLoads() throws Exception {
        assertLoads("/view/dashboard.fxml");
    }

    @Test
    @DisplayName("users.fxml (T034)")
    void usersLoads() throws Exception {
        assertLoads("/view/users.fxml");
    }

    @Test
    @DisplayName("system-config.fxml (T035a)")
    void systemConfigLoads() throws Exception {
        assertLoads("/view/system-config.fxml");
    }

    @Test
    @DisplayName("menu.fxml (T041)")
    void menuLoads() throws Exception {
        assertLoads("/view/menu.fxml");
    }

    @Test
    @DisplayName("tables.fxml (T042)")
    void tablesLoads() throws Exception {
        assertLoads("/view/tables.fxml");
    }

    @Test
    @DisplayName("orders.fxml (T052)")
    void ordersLoads() throws Exception {
        assertLoads("/view/orders.fxml");
    }

    @Test
    @DisplayName("suppliers.fxml (T062)")
    void suppliersLoads() throws Exception {
        assertLoads("/view/suppliers.fxml");
    }

    @Test
    @DisplayName("inventory.fxml (T063)")
    void inventoryLoads() throws Exception {
        assertLoads("/view/inventory.fxml");
    }

    @Test
    @DisplayName("purchasing.fxml (T064)")
    void purchasingLoads() throws Exception {
        assertLoads("/view/purchasing.fxml");
    }

    @Test
    @DisplayName("staff.fxml (T070)")
    void staffLoads() throws Exception {
        assertLoads("/view/staff.fxml");
    }

    @Test
    @DisplayName("reservations.fxml (T071)")
    void reservationsLoads() throws Exception {
        assertLoads("/view/reservations.fxml");
    }

    @Test
    @DisplayName("reports.fxml (T074)")
    void reportsLoads() throws Exception {
        assertLoads("/view/reports.fxml");
    }

    @Test
    @DisplayName("app.css is on the classpath where Navigator expects it (T003)")
    void stylesheetIsPresent() {
        assertNotNull(ScreenLoadTest.class.getResource("/view/css/app.css"),
            "app.css must be at /view/css/app.css for Navigator to apply it");
    }

    @Test
    @DisplayName("every Screen marked implemented has an FXML that exists")
    void implementedScreensHaveFxml() {
        for (Screen screen : Screen.values()) {
            if (!screen.isImplemented()) continue;
            assertNotNull(ScreenLoadTest.class.getResource(screen.fxml()),
                screen + " is marked implemented but " + screen.fxml() + " is missing");
        }
    }
}
