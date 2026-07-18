package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.PurchaseOrder;
import domain.PurchaseOrderItem;
import domain.StockItem;
import domain.Supplier;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Purchasing (T064, FR-20, FR-21) over {@code PurchasingService}. Raising a PO changes no stock
 * (BR-23); receiving a delivery raises on-hand atomically per line (BR-24). Receipt quantities are
 * entered inline in the right-hand grid and submitted together.
 */
public final class PurchasingController implements ContextAware {

    // --- create PO ---
    @FXML private ComboBox<Supplier> supplierCombo;
    @FXML private TextField expectedDateField;
    @FXML private ComboBox<StockItem> lineItemCombo;
    @FXML private TextField lineQtyField;
    @FXML private TextField lineCostField;
    @FXML private Button addLineButton;
    @FXML private TableView<PurchaseOrderItem> draftTable;
    @FXML private TableColumn<PurchaseOrderItem, String> draftItemColumn;
    @FXML private TableColumn<PurchaseOrderItem, String> draftQtyColumn;
    @FXML private TableColumn<PurchaseOrderItem, String> draftCostColumn;
    @FXML private Button removeDraftButton;
    @FXML private Button createPoButton;

    // --- receive ---
    @FXML private TableView<PurchaseOrder> poTable;
    @FXML private TableColumn<PurchaseOrder, String> poNumberColumn;
    @FXML private TableColumn<PurchaseOrder, String> poSupplierColumn;
    @FXML private TableColumn<PurchaseOrder, String> poStatusColumn;
    @FXML private TableView<ReceiveRow> receiveTable;
    @FXML private TableColumn<ReceiveRow, String> recItemColumn;
    @FXML private TableColumn<ReceiveRow, String> recOrderedColumn;
    @FXML private TableColumn<ReceiveRow, String> recReceivedColumn;
    @FXML private TableColumn<ReceiveRow, String> recNowColumn;
    @FXML private Button receiveButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private final ObservableList<StockItem> stockItems = FXCollections.observableArrayList();
    private java.util.List<StockItem> activeItems = new java.util.ArrayList<>();
    private final ObservableList<PurchaseOrderItem> draftLines = FXCollections.observableArrayList();
    private final ObservableList<PurchaseOrder> orders = FXCollections.observableArrayList();
    private final ObservableList<ReceiveRow> receiveRows = FXCollections.observableArrayList();
    private final Map<Integer, String> supplierNames = new HashMap<>();
    private final Map<Integer, String> itemNames = new HashMap<>();

    /** Editable view-model row for the receive grid. */
    public static final class ReceiveRow {
        private final PurchaseOrderItem line;
        private final SimpleStringProperty receiveNow = new SimpleStringProperty("");
        ReceiveRow(PurchaseOrderItem line) { this.line = line; }
        public SimpleStringProperty receiveNowProperty() { return receiveNow; }
    }

    @FXML
    private void initialize() {
        supplierCombo.setItems(suppliers);
        lineItemCombo.setItems(stockItems);
        supplierCombo.setButtonCell(supplierCell());
        supplierCombo.setCellFactory(v -> supplierCell());
        lineItemCombo.setButtonCell(itemCell());
        lineItemCombo.setCellFactory(v -> itemCell());

        draftItemColumn.setCellValueFactory(c -> new SimpleStringProperty(
            itemNames.getOrDefault(c.getValue().getStockItemId(), "#" + c.getValue().getStockItemId())));
        draftQtyColumn.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().getOrderedQty())));
        draftCostColumn.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getUnitCost() == null ? "" : plain(c.getValue().getUnitCost())));
        draftTable.setItems(draftLines);

        poNumberColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPoNumber()));
        poSupplierColumn.setCellValueFactory(c -> new SimpleStringProperty(
            supplierNames.getOrDefault(c.getValue().getSupplierId(), "#" + c.getValue().getSupplierId())));
        poStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().dbValue()));
        poTable.setItems(orders);
        poTable.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, selected) -> loadReceiveRows(selected));

        recItemColumn.setCellValueFactory(c -> new SimpleStringProperty(
            itemNames.getOrDefault(c.getValue().line.getStockItemId(), "#" + c.getValue().line.getStockItemId())));
        recOrderedColumn.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().line.getOrderedQty())));
        recReceivedColumn.setCellValueFactory(c -> new SimpleStringProperty(plain(c.getValue().line.getReceivedQty())));
        recNowColumn.setCellValueFactory(c -> c.getValue().receiveNow);
        recNowColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        recNowColumn.setOnEditCommit(e -> e.getRowValue().receiveNow.set(e.getNewValue()));
        receiveTable.setEditable(true);
        receiveTable.setItems(receiveRows);

        // A supplier sells their own catalogue: picking one narrows the item list to it,
        // and switching mid-draft clears lines that belonged to the previous supplier.
        supplierCombo.valueProperty().addListener((obs, old, chosen) -> {
            if (old != null && chosen != null && old.getSupplierId() != chosen.getSupplierId()
                && !draftLines.isEmpty()) {
                draftLines.clear();
                showMessage("Draft lines cleared — they belonged to " + old.getName() + ".");
            }
            refreshCatalogue(chosen);
        });
        lineItemCombo.setPromptText("Pick a supplier first…");
        lineItemCombo.setDisable(true);
        addLineButton.setDisable(true);

        addLineButton.setOnAction(e -> addDraftLine());
        removeDraftButton.setOnAction(e -> removeDraftLine());
        createPoButton.setOnAction(e -> createPo());
        receiveButton.setOnAction(e -> receiveDelivery());
        refreshButton.setOnAction(e -> reload());
    }

    /** Narrows the line-item combo to the chosen supplier's catalogue. */
    private void refreshCatalogue(Supplier chosen) {
        stockItems.clear();
        lineItemCombo.setValue(null);
        boolean none = chosen == null;
        lineItemCombo.setDisable(none);
        addLineButton.setDisable(none);
        if (none) return;
        for (StockItem item : activeItems) {
            if (item.getSupplierId() != null && item.getSupplierId() == chosen.getSupplierId()) {
                stockItems.add(item);
            }
        }
        lineItemCombo.setPromptText(stockItems.isEmpty()
            ? "No items assigned to " + chosen.getName()
            : "Choose an item…");
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
    }

    private void reload() {
        run(() -> {
            suppliers.setAll(context.supplierService().listActive());
            activeItems = context.inventoryService().listActive();
            refreshCatalogue(supplierCombo.getValue());
            supplierNames.clear();
            for (Supplier s : context.supplierService().listSuppliers()) {
                supplierNames.put(s.getSupplierId(), s.getName());
            }
            itemNames.clear();
            for (StockItem i : context.inventoryService().listItems()) {
                itemNames.put(i.getStockItemId(), i.getName());
            }
            orders.setAll(context.purchasingService().listOrders());
        });
    }

    // --- create PO --------------------------------------------------------------

    private void addDraftLine() {
        StockItem item = lineItemCombo.getValue();
        if (item == null) {
            showMessage("Select a stock item for the line.");
            return;
        }
        run(() -> {
            BigDecimal qty = parsePositive(lineQtyField.getText(), "ordered quantity");
            PurchaseOrderItem line = new PurchaseOrderItem();
            line.setStockItemId(item.getStockItemId());
            line.setOrderedQty(qty);
            line.setReceivedQty(BigDecimal.ZERO);
            if (lineCostField.getText() != null && !lineCostField.getText().trim().isEmpty()) {
                line.setUnitCost(parseDecimal(lineCostField.getText(), "unit cost"));
            }
            draftLines.add(line);
            lineQtyField.clear();
            lineCostField.clear();
        });
    }

    private void removeDraftLine() {
        PurchaseOrderItem selected = draftTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            draftLines.remove(selected);
        }
    }

    private void createPo() {
        Supplier supplier = supplierCombo.getValue();
        if (supplier == null) {
            showMessage("Select a supplier.");
            return;
        }
        if (draftLines.isEmpty()) {
            showMessage("Add at least one line.");
            return;
        }
        run(() -> {
            PurchaseOrder header = new PurchaseOrder();
            header.setSupplierId(supplier.getSupplierId());
            if (expectedDateField.getText() != null && !expectedDateField.getText().trim().isEmpty()) {
                header.setExpectedDate(parseDate(expectedDateField.getText()));
            }
            PurchaseOrder created = context.purchasingService().createPO(context.session(), header,
                new java.util.ArrayList<>(draftLines));
            draftLines.clear();
            expectedDateField.clear();
            reload();
            showMessage("Created " + created.getPoNumber() + ".");
        });
    }

    // --- receive ----------------------------------------------------------------

    private void loadReceiveRows(PurchaseOrder po) {
        receiveRows.clear();
        if (po == null) return;
        for (PurchaseOrderItem line : context.purchasingService().listLines(po.getPoId())) {
            receiveRows.add(new ReceiveRow(line));
        }
    }

    private void receiveDelivery() {
        PurchaseOrder po = poTable.getSelectionModel().getSelectedItem();
        if (po == null) {
            showMessage("Select a purchase order to receive against.");
            return;
        }
        run(() -> {
            Map<Integer, BigDecimal> received = new HashMap<>();
            for (ReceiveRow row : receiveRows) {
                String raw = row.receiveNow.get();
                if (raw == null || raw.trim().isEmpty()) continue;
                received.put(row.line.getPoItemId(), parseDecimal(raw, "received quantity"));
            }
            if (received.isEmpty()) {
                throw new ValidationException("Enter at least one received quantity.");
            }
            context.purchasingService().receiveDelivery(context.session(), po.getPoId(), received);
            reload();
            loadReceiveRows(context.purchasingService().findById(po.getPoId()));
            showMessage("Delivery recorded for " + po.getPoNumber() + ".");
        });
    }

    // --- helpers ----------------------------------------------------------------

    private static ListCell<Supplier> supplierCell() {
        return new ListCell<Supplier>() {
            @Override protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
            }
        };
    }

    private ListCell<StockItem> itemCell() {
        return new ListCell<StockItem>() {
            @Override protected void updateItem(StockItem i, boolean empty) {
                super.updateItem(i, empty);
                setText(empty || i == null ? null : i.getName() + " (" + i.getUnitOfMeasure() + ")");
            }
        };
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parsePositive(String text, String label) {
        BigDecimal v = parseDecimal(text, label);
        if (v.signum() <= 0) {
            throw new ValidationException("Enter a " + label + " greater than zero.");
        }
        return v;
    }

    private static BigDecimal parseDecimal(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter a " + label + ".");
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid " + label + ".");
        }
    }

    private static java.time.LocalDate parseDate(String text) {
        try {
            return java.time.LocalDate.parse(text.trim());
        } catch (Exception e) {
            throw new ValidationException("Enter the expected date as YYYY-MM-DD.");
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
