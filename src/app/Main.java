package app;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * The JavaFX entry point (T023). Builds the {@link AppContext}, hands the stage to a
 * {@link Navigator}, and shows the login screen.
 *
 * <p>This is the only place with a {@code main}; everything below {@code controller} stays free of
 * {@code javafx.*} (Principle I).
 *
 * <p>Run in dev with {@code mvn exec:java}, or {@code java -jar target/golden-fork-rms.jar} after
 * {@code mvn package}. Both need a Full JDK 8 (JavaFX comes from its bundled {@code jfxrt.jar}).
 */
public final class Main extends Application {

    private static final String TITLE = "Golden Fork RMS";
    private static final double MIN_WIDTH = 980;
    private static final double MIN_HEIGHT = 640;

    @Override
    public void start(Stage stage) {
        AppContext context;
        try {
            context = AppContext.bootstrap();
        } catch (RuntimeException e) {
            // Almost always a missing or wrong config/db.properties. There is no usable app without
            // it, so say what to fix and stop rather than open a window that fails on first click.
            fatal("Golden Fork RMS cannot start.\n\n" + e.getMessage());
            return;
        }

        stage.setTitle(TITLE);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        new Navigator(stage, context).showAuth();

        stage.show();
    }

    private static void fatal(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(TITLE);
        alert.setHeaderText("Startup failed");
        alert.getDialogPane().setPrefWidth(560);
        alert.showAndWait();
        javafx.application.Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
