package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import config.AppConfig;
import domain.PaymentMethod;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administrator-only reference/system data (T035a, FR-31) over {@code SystemConfigService} — never
 * the DAO. Appendix A validation lives in the service; this class shows the message it throws.
 */
public final class SystemConfigController implements ContextAware {

    /** Plain-English notes per key, so the table explains itself without the FRD to hand. */
    private static final Map<String, String> NOTES = notes();
    private static final Map<String, String> RULES = rules();

    private static Map<String, String> notes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(AppConfig.KEY_TAX_RATE, "Tax applied to orders. A decimal fraction: 0.1400 = 14%.");
        map.put(AppConfig.KEY_IDLE_TIMEOUT_MIN, "Minutes of inactivity before automatic sign-out.");
        map.put(AppConfig.KEY_LOGIN_MAX_ATTEMPTS, "Failed sign-ins allowed before a username is throttled.");
        map.put(AppConfig.KEY_RESERVATION_SLOT_MINUTES, "How long a reservation holds its table.");
        map.put(AppConfig.KEY_DISCOUNT_APPROVAL_THRESHOLD, "Discount amount above which a manager must approve.");
        return map;
    }

    private static Map<String, String> rules() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(AppConfig.KEY_TAX_RATE, "Between 0 and 1, up to four decimals (0.1400 = 14%).");
        map.put(AppConfig.KEY_IDLE_TIMEOUT_MIN, "A whole number of minutes. 0 disables auto sign-out.");
        map.put(AppConfig.KEY_LOGIN_MAX_ATTEMPTS, "A whole number, at least 1.");
        map.put(AppConfig.KEY_RESERVATION_SLOT_MINUTES, "A whole number of minutes, at least 1.");
        map.put(AppConfig.KEY_DISCOUNT_APPROVAL_THRESHOLD, "A non-negative amount, e.g. 20.00.");
        return map;
    }

    /** One row of the settings table. */
    public static final class ConfigRow {
        private final String key;
        private final String value;

        ConfigRow(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String key() { return key; }
        public String value() { return value; }
        public String note() { return NOTES.getOrDefault(key, ""); }
    }

    @FXML private TableView<ConfigRow> configTable;
    @FXML private TableColumn<ConfigRow, String> keyColumn;
    @FXML private TableColumn<ConfigRow, String> valueColumn;
    @FXML private TableColumn<ConfigRow, String> descriptionColumn;

    @FXML private TableView<PaymentMethod> paymentMethodTable;
    @FXML private TableColumn<PaymentMethod, String> methodColumn;

    @FXML private Label editTitle;
    @FXML private TextField keyField;
    @FXML private TextField valueField;
    @FXML private Label ruleHint;
    @FXML private Label messageLabel;
    @FXML private Button saveButton;
    @FXML private Button refreshButton;

    private AppContext context;

    private final ObservableList<ConfigRow> rows = FXCollections.observableArrayList();
    private final ObservableList<PaymentMethod> methods = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        keyColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().key()));
        valueColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().value()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().note()));
        configTable.setItems(rows);

        methodColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMethodName()));
        paymentMethodTable.setItems(methods);

        configTable.getSelectionModel().selectedItemProperty()
            .addListener((observable, old, selected) -> showInForm(selected));

        saveButton.setOnAction(event -> save());
        refreshButton.setOnAction(event -> reload());
        setEditingEnabled(false);
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
    }

    private void reload() {
        run(() -> {
            ConfigRow selected = configTable.getSelectionModel().getSelectedItem();
            Map<String, String> values = context.systemConfigService().getAll(context.session());

            rows.clear();
            // Ordered by the notes map rather than the database, so the list reads the same way
            // every time regardless of insert order.
            for (String key : NOTES.keySet()) {
                if (values.containsKey(key)) {
                    rows.add(new ConfigRow(key, values.get(key)));
                }
            }
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (!NOTES.containsKey(entry.getKey())) {
                    rows.add(new ConfigRow(entry.getKey(), entry.getValue()));
                }
            }

            methods.setAll(context.systemConfigService().listPaymentMethods(context.session()));

            if (selected != null) {
                reselectByKey(selected.key());
            }
        });
    }

    private void reselectByKey(String key) {
        for (ConfigRow row : rows) {
            if (row.key().equals(key)) {
                configTable.getSelectionModel().select(row);
                return;
            }
        }
    }

    private void showInForm(ConfigRow row) {
        if (row == null) {
            setEditingEnabled(false);
            return;
        }
        editTitle.setText("Change " + row.key());
        keyField.setText(row.key());
        valueField.setText(row.value());
        ruleHint.setText(RULES.getOrDefault(row.key(), ""));
        setEditingEnabled(true);
        hideMessage();
    }

    private void setEditingEnabled(boolean enabled) {
        valueField.setDisable(!enabled);
        saveButton.setDisable(!enabled);
        if (!enabled) {
            editTitle.setText("Change a setting");
            keyField.clear();
            valueField.clear();
            ruleHint.setText("Select a setting to change.");
        }
    }

    private void save() {
        run(() -> {
            context.systemConfigService().update(context.session(), keyField.getText(), valueField.getText());
            reload();
        });
    }

    /** Service messages are already the FRD's wording, so they are shown as-is. */
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
