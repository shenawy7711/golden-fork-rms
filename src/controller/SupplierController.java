package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.Supplier;
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
 * Supplier management (T062, FR-19) over {@code SupplierService}. Suppliers are deactivated, never
 * deleted, so purchase-order history keeps a valid vendor (BR-05, BR-22).
 */
public final class SupplierController implements ContextAware {

    @FXML private TableView<Supplier> supplierTable;
    @FXML private TableColumn<Supplier, String> nameColumn;
    @FXML private TableColumn<Supplier, String> contactColumn;
    @FXML private TableColumn<Supplier, String> phoneColumn;
    @FXML private TableColumn<Supplier, String> emailColumn;
    @FXML private TableColumn<Supplier, String> statusColumn;

    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private TextField contactField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField addressField;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deactivateButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;
    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private Supplier editing;

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        contactColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContactPerson()));
        phoneColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        emailColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().dbValue()));
        supplierTable.setItems(suppliers);

        supplierTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, selected) -> showInForm(selected));

        saveButton.setOnAction(e -> save());
        newButton.setOnAction(e -> startNew());
        deactivateButton.setOnAction(e -> deactivateSelected());
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNew();
        reload();
    }

    private void reload() {
        run(() -> suppliers.setAll(context.supplierService().listSuppliers()));
    }

    private void startNew() {
        editing = null;
        formTitle.setText("New supplier");
        nameField.clear();
        contactField.clear();
        phoneField.clear();
        emailField.clear();
        addressField.clear();
        supplierTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void showInForm(Supplier s) {
        if (s == null) return;
        editing = s;
        formTitle.setText("Edit " + s.getName());
        nameField.setText(s.getName());
        contactField.setText(s.getContactPerson());
        phoneField.setText(s.getPhone());
        emailField.setText(s.getEmail());
        addressField.setText(s.getAddress());
        hideMessage();
    }

    private void save() {
        run(() -> {
            Supplier s = new Supplier();
            if (editing != null) {
                s.setSupplierId(editing.getSupplierId());
                s.setStatus(editing.getStatus());
            } else {
                s.setStatus(Status.ACTIVE);
            }
            s.setName(nameField.getText());
            s.setContactPerson(blankToNull(contactField.getText()));
            s.setPhone(blankToNull(phoneField.getText()));
            s.setEmail(blankToNull(emailField.getText()));
            s.setAddress(blankToNull(addressField.getText()));
            context.supplierService().save(context.session(), s);
            startNew();
            reload();
        });
    }

    private void deactivateSelected() {
        Supplier selected = supplierTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a supplier first.");
            return;
        }
        run(() -> {
            if (selected.getStatus() == Status.ACTIVE) {
                context.supplierService().deactivate(context.session(), selected.getSupplierId());
            } else {
                context.supplierService().reactivate(context.session(), selected.getSupplierId());
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
