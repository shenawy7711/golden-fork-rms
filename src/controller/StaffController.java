package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.Staff;
import domain.enums.Status;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import service.exception.RmsException;

/**
 * Staff records (T070, FR-23) over {@code StaffService}. A staff record is distinct from a login and
 * is deactivated, never deleted, so history is kept (BR-05, BR-26).
 */
public final class StaffController implements ContextAware {

    @FXML private TableView<Staff> staffTable;
    @FXML private TableColumn<Staff, String> nameColumn;
    @FXML private TableColumn<Staff, String> positionColumn;
    @FXML private TableColumn<Staff, String> phoneColumn;
    @FXML private TableColumn<Staff, String> emailColumn;
    @FXML private TableColumn<Staff, String> statusColumn;

    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private TextField positionField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deactivateButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;
    private final ObservableList<Staff> staff = FXCollections.observableArrayList();
    private Staff editing;

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        positionColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPosition()));
        phoneColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        emailColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().dbValue()));
        staffTable.setItems(staff);

        staffTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, selected) -> showInForm(selected));

        saveButton.setOnAction(e -> save());
        newButton.setOnAction(e -> startNew());
        deactivateButton.setOnAction(e -> toggleActive());
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNew();
        reload();
    }

    private void reload() {
        run(() -> staff.setAll(context.staffService().listStaff(context.session())));
    }

    private void startNew() {
        editing = null;
        formTitle.setText("New staff member");
        nameField.clear();
        positionField.clear();
        phoneField.clear();
        emailField.clear();
        staffTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void showInForm(Staff s) {
        if (s == null) return;
        editing = s;
        formTitle.setText("Edit " + s.getFullName());
        nameField.setText(s.getFullName());
        positionField.setText(s.getPosition());
        phoneField.setText(s.getPhone());
        emailField.setText(s.getEmail());
        hideMessage();
    }

    private void save() {
        run(() -> {
            Staff s = new Staff();
            if (editing != null) {
                s.setStaffId(editing.getStaffId());
                s.setStatus(editing.getStatus());
                s.setUserId(editing.getUserId());
            } else {
                s.setStatus(Status.ACTIVE);
            }
            s.setFullName(nameField.getText());
            s.setPosition(positionField.getText());
            s.setPhone(blankToNull(phoneField.getText()));
            s.setEmail(blankToNull(emailField.getText()));
            context.staffService().save(context.session(), s);
            startNew();
            reload();
        });
    }

    private void toggleActive() {
        Staff selected = staffTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a staff member first.");
            return;
        }
        run(() -> {
            if (selected.getStatus() == Status.ACTIVE) {
                context.staffService().deactivate(context.session(), selected.getStaffId());
            } else {
                context.staffService().reactivate(context.session(), selected.getStaffId());
            }
            startNew();
            reload();
        });
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

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
}
