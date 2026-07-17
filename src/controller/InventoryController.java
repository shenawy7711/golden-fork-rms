package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.StockItem;
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
import service.exception.ValidationException;

import java.math.BigDecimal;

/**
 * Stock items with low-stock flagging (T063, FR-18, FR-22) over {@code InventoryService}. On-hand is
 * shown but never edited directly here — it moves only through the "Adjust" action, which writes a
 * ledger row and updates on-hand atomically (BR-21).
 */
public final class InventoryController implements ContextAware {

    @FXML private TableView<StockItem> itemTable;
    @FXML private TableColumn<StockItem, String> nameColumn;
    @FXML private TableColumn<StockItem, String> unitColumn;
    @FXML private TableColumn<StockItem, String> reorderColumn;
    @FXML private TableColumn<StockItem, String> onHandColumn;
    @FXML private TableColumn<StockItem, String> statusColumn;
    @FXML private TableColumn<StockItem, String> lowColumn;

    @FXML private Label formTitle;
    @FXML private TextField nameField;
    @FXML private TextField unitField;
    @FXML private TextField reorderField;
    @FXML private TextField openingField;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deactivateButton;

    @FXML private TextField adjustField;
    @FXML private Button adjustButton;

    @FXML private Button lowStockButton;
    @FXML private Button allButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;
    private final ObservableList<StockItem> items = FXCollections.observableArrayList();
    private StockItem editing;
    private boolean showingLowOnly;

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        unitColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnitOfMeasure()));
        reorderColumn.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getReorderLevel())));
        onHandColumn.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getQuantityOnHand())));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().dbValue()));
        lowColumn.setCellValueFactory(c -> new SimpleStringProperty(isLow(c.getValue()) ? "Low" : ""));
        StatusPill.apply(statusColumn);
        StatusPill.apply(lowColumn);
        itemTable.setItems(items);

        itemTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, selected) -> showInForm(selected));

        saveButton.setOnAction(e -> save());
        newButton.setOnAction(e -> startNew());
        deactivateButton.setOnAction(e -> toggleActive());
        adjustButton.setOnAction(e -> adjust());
        lowStockButton.setOnAction(e -> { showingLowOnly = true; reload(); });
        allButton.setOnAction(e -> { showingLowOnly = false; reload(); });
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNew();
        reload();
    }

    private void reload() {
        run(() -> {
            if (showingLowOnly) {
                items.setAll(context.inventoryService().lowStockItems(context.session()));
            } else {
                items.setAll(context.inventoryService().listItems());
            }
        });
    }

    private static boolean isLow(StockItem item) {
        return item.getStatus() == Status.ACTIVE
            && item.getQuantityOnHand() != null && item.getReorderLevel() != null
            && item.getQuantityOnHand().compareTo(item.getReorderLevel()) <= 0;
    }

    private void startNew() {
        editing = null;
        formTitle.setText("New stock item");
        nameField.clear();
        unitField.clear();
        reorderField.clear();
        openingField.clear();
        openingField.setDisable(false);
        itemTable.getSelectionModel().clearSelection();
        hideMessage();
    }

    private void showInForm(StockItem item) {
        if (item == null) return;
        editing = item;
        formTitle.setText("Edit " + item.getName());
        nameField.setText(item.getName());
        unitField.setText(item.getUnitOfMeasure());
        reorderField.setText(plain(item.getReorderLevel()));
        openingField.setText(plain(item.getQuantityOnHand()));
        // On-hand is not editable after creation; it moves via adjustments only.
        openingField.setDisable(true);
        hideMessage();
    }

    private void save() {
        run(() -> {
            StockItem item = new StockItem();
            if (editing != null) {
                item.setStockItemId(editing.getStockItemId());
                item.setStatus(editing.getStatus());
                item.setQuantityOnHand(editing.getQuantityOnHand());
            } else {
                item.setStatus(Status.ACTIVE);
                item.setQuantityOnHand(parseDecimal(openingField.getText(), "opening quantity", true));
            }
            item.setName(nameField.getText());
            item.setUnitOfMeasure(unitField.getText());
            item.setReorderLevel(parseDecimal(reorderField.getText(), "reorder level", false));
            context.inventoryService().save(context.session(), item);
            startNew();
            reload();
        });
    }

    private void toggleActive() {
        StockItem selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a stock item first.");
            return;
        }
        run(() -> {
            if (selected.getStatus() == Status.ACTIVE) {
                context.inventoryService().deactivate(context.session(), selected.getStockItemId());
            } else {
                context.inventoryService().reactivate(context.session(), selected.getStockItemId());
            }
            startNew();
            reload();
        });
    }

    private void adjust() {
        StockItem selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a stock item to adjust.");
            return;
        }
        run(() -> {
            BigDecimal delta = parseSignedDecimal(adjustField.getText());
            context.inventoryService().adjustStock(context.session(), selected.getStockItemId(), delta);
            adjustField.clear();
            reload();
        });
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseDecimal(String text, String label, boolean allowBlankAsZero) {
        if (text == null || text.trim().isEmpty()) {
            if (allowBlankAsZero) return BigDecimal.ZERO;
            throw new ValidationException("Enter a " + label + ".");
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid number for the " + label + ".");
        }
    }

    private static BigDecimal parseSignedDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter a quantity (use a minus sign to remove stock).");
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid quantity to adjust.");
        }
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
