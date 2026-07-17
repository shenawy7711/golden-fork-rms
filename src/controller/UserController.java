package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.User;
import domain.enums.RoleName;
import domain.enums.Status;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import service.exception.RmsException;

import java.util.Optional;

/**
 * Administrator-only account management (T034, FR-03) over {@code UserService} — never the DAO.
 *
 * <p>Every rule (unique username, password policy, last-admin protection) is decided in the
 * service; this class maps the typed exception it throws to the FRD's message. It deliberately
 * does not pre-check those rules itself: two copies of a rule drift apart, and the service's copy
 * is the one that protects the data.
 */
public final class UserController implements ContextAware {

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> fullNameColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> statusColumn;

    @FXML private Label formTitle;
    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private ComboBox<RoleName> roleCombo;
    @FXML private ComboBox<Status> statusCombo;
    @FXML private VBox passwordGroup;
    @FXML private Label passwordLabel;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button refreshButton;
    @FXML private Button activateButton;
    @FXML private Button deactivateButton;
    @FXML private Button deleteButton;
    @FXML private Button changePasswordButton;

    private AppContext context;

    private final ObservableList<User> users = FXCollections.observableArrayList();

    /** The account being edited, or {@code null} when the form is creating a new one. */
    private User editing;

    @FXML
    private void initialize() {
        usernameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
        fullNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFullName()));
        roleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRole().dbValue()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus().dbValue()));

        userTable.setItems(users);
        roleCombo.setItems(FXCollections.observableArrayList(RoleName.values()));
        statusCombo.setItems(FXCollections.observableArrayList(Status.values()));

        userTable.getSelectionModel().selectedItemProperty()
            .addListener((observable, old, selected) -> showInForm(selected));

        saveButton.setOnAction(event -> save());
        newButton.setOnAction(event -> startNew());
        refreshButton.setOnAction(event -> reload());
        activateButton.setOnAction(event -> activateSelected());
        deactivateButton.setOnAction(event -> deactivateSelected());
        deleteButton.setOnAction(event -> deleteSelected());
        changePasswordButton.setOnAction(event -> changePasswordForSelected());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNew();
        reload();
    }

    private void reload() {
        run(() -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            users.setAll(context.userService().list(context.session()));
            if (selected != null) {
                reselectById(selected.getUserId());
            }
        });
    }

    private void reselectById(int userId) {
        for (User user : users) {
            if (user.getUserId() == userId) {
                userTable.getSelectionModel().select(user);
                return;
            }
        }
    }

    private void startNew() {
        editing = null;
        formTitle.setText("New account");
        saveButton.setText("Create account");
        usernameField.clear();
        fullNameField.clear();
        passwordField.clear();
        roleCombo.setValue(RoleName.CASHIER);
        statusCombo.setValue(Status.ACTIVE);
        setPasswordGroupVisible(true);
        passwordLabel.setText("Initial password");
        userTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void showInForm(User user) {
        if (user == null) return;
        editing = user;
        formTitle.setText("Edit account");
        saveButton.setText("Save changes");
        usernameField.setText(user.getUsername());
        fullNameField.setText(user.getFullName());
        roleCombo.setValue(user.getRole());
        statusCombo.setValue(user.getStatus());
        passwordField.clear();
        // An edit never carries a password: changing one is its own explicit action.
        setPasswordGroupVisible(false);
        hideMessage();
    }

    private void setPasswordGroupVisible(boolean visible) {
        passwordGroup.setVisible(visible);
        passwordGroup.setManaged(visible);
    }

    private void save() {
        run(() -> {
            User candidate = new User();
            candidate.setUsername(usernameField.getText());
            candidate.setFullName(fullNameField.getText());
            candidate.setRole(roleCombo.getValue());
            candidate.setStatus(statusCombo.getValue());

            if (editing == null) {
                context.userService().create(context.session(), candidate, passwordField.getText());
                passwordField.clear();
                info("Account created", candidate.getUsername() + " can sign in now.");
            } else {
                candidate.setUserId(editing.getUserId());
                context.userService().update(context.session(), candidate);
                info("Account updated", "A role change takes effect at the user's next sign-in.");
            }
            startNew();
            reload();
        });
    }

    private void changePasswordForSelected() {
        User selected = requireSelection();
        if (selected == null) return;
        if (!passwordGroup.isVisible()) {
            setPasswordGroupVisible(true);
            passwordLabel.setText("New password for " + selected.getUsername());
            passwordField.requestFocus();
            return;
        }
        run(() -> {
            context.userService().changePassword(context.session(), selected.getUserId(), passwordField.getText());
            passwordField.clear();
            setPasswordGroupVisible(false);
            info("Password changed", selected.getUsername() + "'s password has been set.");
        });
    }

    private void activateSelected() {
        User selected = requireSelection();
        if (selected == null) return;
        run(() -> {
            context.userService().activate(context.session(), selected.getUserId());
            reload();
        });
    }

    private void deactivateSelected() {
        User selected = requireSelection();
        if (selected == null) return;
        run(() -> {
            context.userService().deactivate(context.session(), selected.getUserId());
            reload();
        });
    }

    private void deleteSelected() {
        User selected = requireSelection();
        if (selected == null) return;
        if (!confirm("Delete " + selected.getUsername() + "?",
                     "This cannot be undone. Accounts with activity history must be deactivated instead.")) {
            return;
        }
        run(() -> {
            context.userService().delete(context.session(), selected.getUserId());
            startNew();
            reload();
        });
    }

    private User requireSelection() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select an account first.");
        }
        return selected;
    }

    /**
     * Runs a service call, turning any {@link RmsException} into the inline message. The service's
     * message is already the FRD's wording, so it is shown as-is rather than reworded here.
     */
    private void run(Runnable action) {
        hideMessage();
        try {
            action.run();
        } catch (RmsException e) {
            showMessage(e.getMessage());
        }
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }

    private static void info(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }
}
