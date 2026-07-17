package app;

import javafx.scene.text.Font;

/**
 * Registers the app's bundled display face (Playfair Display) with the JavaFX font system before any
 * scene is built, so {@code app.css} can reference the family by name. Called once at startup.
 *
 * <p>The face ships in {@code /view/fonts/} rather than relying on a system install, so the editorial
 * serif renders identically on every machine (NFR-08 offline operation — no web fonts).
 */
final class Fonts {

    private static boolean loaded;

    private Fonts() {}

    /** Loads the bundled fonts once; later calls are no-ops. Failures fall back to the CSS serif. */
    static void load() {
        if (loaded) return;
        loaded = true;
        register("/view/fonts/PlayfairDisplay.ttf");
    }

    private static void register(String resource) {
        try {
            Font.loadFont(Fonts.class.getResourceAsStream(resource), 12);
        } catch (RuntimeException ignored) {
            // A missing/again-registered font just falls back to Georgia via the CSS family list.
        }
    }
}
