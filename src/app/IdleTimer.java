package app;

import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Auto-logout after a period of inactivity (FR-04 edge case, T075).
 *
 * <p>A {@link PauseTransition} counts down the configured idle window; any mouse or key event on the
 * scene restarts it. When it elapses, {@code onTimeout} runs on the JavaFX thread — the
 * {@link Navigator} uses that to end the session and return to the login screen. The timer runs only
 * while a dashboard is shown and is stopped the moment the session ends, so the login screen itself
 * never counts down.
 */
final class IdleTimer {

    private final Scene scene;
    private final PauseTransition idle;
    private final javafx.event.EventHandler<MouseEvent> onMouse;
    private final javafx.event.EventHandler<KeyEvent> onKey;
    private boolean running;

    IdleTimer(Scene scene, int idleMinutes, Runnable onTimeout) {
        this.scene = scene;
        // A non-positive configured value would mean "never idle out"; clamp to a sane floor so a
        // misconfigured system_config row cannot disable the guard entirely.
        int minutes = idleMinutes > 0 ? idleMinutes : 15;
        this.idle = new PauseTransition(Duration.minutes(minutes));
        this.idle.setOnFinished(event -> {
            running = false;
            onTimeout.run();
        });
        this.onMouse = event -> reset();
        this.onKey = event -> reset();
    }

    /** Begins watching for inactivity and resetting on interaction. */
    void start() {
        if (running) return;
        running = true;
        scene.addEventFilter(MouseEvent.ANY, onMouse);
        scene.addEventFilter(KeyEvent.ANY, onKey);
        idle.playFromStart();
    }

    /** Stops watching and detaches the interaction listeners. */
    void stop() {
        if (!running) return;
        running = false;
        idle.stop();
        scene.removeEventFilter(MouseEvent.ANY, onMouse);
        scene.removeEventFilter(KeyEvent.ANY, onKey);
    }

    private void reset() {
        if (running) {
            idle.playFromStart();
        }
    }
}
