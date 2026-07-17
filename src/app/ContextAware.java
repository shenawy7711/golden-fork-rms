package app;

/**
 * Implemented by controllers that need the shared services or the navigator. {@link Navigator}
 * calls {@link #init} on a freshly loaded controller before its screen is shown.
 *
 * <p>An explicit interface rather than reflection or an FXML controller factory: the wiring is
 * visible in the controller itself, and a missing dependency is a compile error instead of a
 * start-up surprise.
 */
public interface ContextAware {

    /**
     * Supplies the shared context and navigator. Called after {@code initialize()} and before the
     * screen is displayed, so implementations can populate their controls here.
     */
    void init(AppContext context, Navigator navigator);
}
