package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import service.exception.AuthorizationException;
import service.exception.PersistenceException;
import service.security.Session;

/**
 * The login screen (T032, FR-01). Collects credentials, delegates to {@code AuthService}, and maps
 * its typed exceptions to the FRD's user-facing messages.
 *
 * <p>Holds no business rules: whether a credential is valid, whether an account is active, and
 * whether a username is throttled are all decided in the service (Principle I/IV). This class only
 * shows the answer.
 */
public final class AuthController implements ContextAware {

    /** Shown when the data store is unreachable (FR-01 acceptance criteria). */
    private static final String SERVICE_UNAVAILABLE = "Unable to sign in — service unavailable";

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordPlainField;
    @FXML private Button passwordToggle;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private AppContext context;
    private Navigator navigator;

    @FXML
    private void initialize() {
        // The masked and plain fields are two views of one value; keep them in step so toggling
        // visibility never loses or stales what was typed.
        passwordPlainField.textProperty().bindBidirectional(passwordField.textProperty());

        passwordToggle.setOnAction(event -> togglePasswordVisible());
        loginButton.setOnAction(event -> attemptLogin());

        // Any edit clears a stale failure message.
        usernameField.textProperty().addListener((observable, old, current) -> hideError());
        passwordField.textProperty().addListener((observable, old, current) -> hideError());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        this.navigator = navigator;
        usernameField.requestFocus();
    }

    private void togglePasswordVisible() {
        boolean showing = passwordPlainField.isVisible();
        setPasswordVisible(!showing);
    }

    private void setPasswordVisible(boolean visible) {
        passwordPlainField.setVisible(visible);
        passwordPlainField.setManaged(visible);
        passwordField.setVisible(!visible);
        passwordField.setManaged(!visible);
        passwordToggle.setText(visible ? "Hide" : "Show");
    }

    private void attemptLogin() {
        hideError();
        loginButton.setDisable(true);
        try {
            Session session = context.authService()
                .login(usernameField.getText(), passwordField.getText());

            context.setSession(session);
            passwordField.clear();          // never leave credentials in a control (BR-02)
            setPasswordVisible(false);
            navigator.showDashboard();

        } catch (AuthorizationException invalid) {
            // Already generic ("Invalid username or password") — the service does not reveal which
            // part failed, and neither should this.
            showError(invalid.getMessage());
        } catch (PersistenceException unavailable) {
            showError(SERVICE_UNAVAILABLE);
        } finally {
            loginButton.setDisable(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
