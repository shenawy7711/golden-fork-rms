package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.DiningTable;
import domain.MenuItem;
import domain.Order;
import domain.OrderItem;
import domain.PaymentMethod;
import domain.enums.DiscountType;
import domain.enums.OrderType;
import domain.enums.TableStatus;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import service.exception.RmsException;
import service.exception.ValidationException;
import util.Money;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The point-of-sale screen (T052, FR-10…FR-17) over {@code OrderService}, {@code BillingService},
 * and {@code ReceiptService}.
 *
 * <p>Every figure shown here — subtotal, discount, tax, total — is read back from the {@code Order}
 * that {@code OrderService}/{@code BillingService} return; the controller computes nothing itself
 * (Principle III). Finalisation is one atomic service call; on success the receipt PDF is written and
 * opened (FR-16).
 */
public final class OrderController implements ContextAware {

    @FXML private ComboBox<OrderType> typeCombo;
    @FXML private ComboBox<DiningTable> tableCombo;
    @FXML private Button openButton;
    @FXML private ListView<Order> openOrdersList;
    @FXML private Button resumeButton;

    @FXML private Label orderHeader;
    @FXML private ListView<MenuItem> itemsList;
    @FXML private TextField quantityField;
    @FXML private Button addButton;

    @FXML private TableView<OrderItem> linesTable;
    @FXML private TableColumn<OrderItem, String> lineNameColumn;
    @FXML private TableColumn<OrderItem, String> lineQtyColumn;
    @FXML private TableColumn<OrderItem, String> linePriceColumn;
    @FXML private TableColumn<OrderItem, String> lineTotalColumn;
    @FXML private Button removeLineButton;

    @FXML private ComboBox<DiscountType> discountTypeCombo;
    @FXML private TextField discountValueField;
    @FXML private Button applyDiscountButton;

    @FXML private Label subtotalLabel;
    @FXML private Label discountLabel;
    @FXML private Label taxLabel;
    @FXML private Label totalLabel;

    @FXML private ComboBox<PaymentMethod> methodCombo;
    @FXML private TextField tenderedField;
    @FXML private Button finaliseButton;
    @FXML private Button voidButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;

    private final ObservableList<OrderType> types = FXCollections.observableArrayList(OrderType.values());
    private final ObservableList<DiningTable> freeTables = FXCollections.observableArrayList();
    private final ObservableList<Order> openOrders = FXCollections.observableArrayList();
    private final ObservableList<MenuItem> orderableItems = FXCollections.observableArrayList();
    private final ObservableList<OrderItem> lines = FXCollections.observableArrayList();
    private final ObservableList<DiscountType> discountTypes =
        FXCollections.observableArrayList(DiscountType.values());
    private final ObservableList<PaymentMethod> methods = FXCollections.observableArrayList();

    private final Map<Integer, String> itemNames = new HashMap<>();

    private Order currentOrder;

    @FXML
    private void initialize() {
        typeCombo.setItems(types);
        typeCombo.setValue(OrderType.DINE_IN);
        tableCombo.setItems(freeTables);
        openOrdersList.setItems(openOrders);
        itemsList.setItems(orderableItems);
        discountTypeCombo.setItems(discountTypes);
        discountTypeCombo.setValue(DiscountType.NONE);
        methodCombo.setItems(methods);

        configureTextRenderers();

        lineNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            itemNames.getOrDefault(cell.getValue().getItemId(), "Item #" + cell.getValue().getItemId())));
        lineQtyColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            String.valueOf(cell.getValue().getQuantity())));
        linePriceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            money(cell.getValue().getUnitPrice())));
        lineTotalColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            money(cell.getValue().getLineTotal())));
        linesTable.setItems(lines);

        typeCombo.valueProperty().addListener((obs, old, type) ->
            tableCombo.setDisable(type != OrderType.DINE_IN));

        openButton.setOnAction(e -> openOrder());
        resumeButton.setOnAction(e -> resumeSelected());
        addButton.setOnAction(e -> addItem());
        removeLineButton.setOnAction(e -> removeSelectedLine());
        applyDiscountButton.setOnAction(e -> applyDiscount());
        finaliseButton.setOnAction(e -> finalise());
        voidButton.setOnAction(e -> voidOrder());
        refreshButton.setOnAction(e -> reloadReference());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reloadReference();
        showNoOrder();
    }

    private void reloadReference() {
        run(() -> {
            freeTables.setAll(context.tableService().listByStatus(TableStatus.FREE));
            openOrders.setAll(context.orderService().listOpenOrders());
            orderableItems.setAll(context.menuService().listOrderableItems());
            methods.setAll(context.paymentMethodDAO().findAll());
            rebuildItemNames();
            if (!methods.isEmpty() && methodCombo.getValue() == null) {
                methodCombo.setValue(methods.get(0));
            }
        });
    }

    private void rebuildItemNames() {
        itemNames.clear();
        for (MenuItem item : context.menuService().listItems()) {
            itemNames.put(item.getItemId(), item.getName());
        }
    }

    // --- open / resume ----------------------------------------------------------

    private void openOrder() {
        run(() -> {
            OrderType type = typeCombo.getValue();
            Integer tableId = null;
            if (type == OrderType.DINE_IN) {
                DiningTable table = tableCombo.getValue();
                if (table == null) {
                    throw new ValidationException("Select a table for a dine-in order.");
                }
                tableId = table.getTableId();
            }
            currentOrder = context.orderService().openOrder(context.session(), type, tableId);
            reloadReference();
            loadCurrentOrder();
        });
    }

    private void resumeSelected() {
        Order selected = openOrdersList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select an open order to resume.");
            return;
        }
        currentOrder = selected;
        loadCurrentOrder();
    }

    private void loadCurrentOrder() {
        if (currentOrder == null) {
            showNoOrder();
            return;
        }
        currentOrder = context.orderService().findOrder(currentOrder.getOrderId());
        lines.setAll(context.orderService().listLines(currentOrder.getOrderId()));
        orderHeader.setText("Order " + currentOrder.getOrderNumber() + " · "
            + currentOrder.getOrderType().dbValue()
            + (currentOrder.getTableId() == null ? "" : " · Table " + currentOrder.getTableId()));
        discountTypeCombo.setValue(currentOrder.getDiscountType());
        discountValueField.setText(currentOrder.getDiscountValue() == null
            ? "" : currentOrder.getDiscountValue().toPlainString());
        setOrderControlsDisabled(false);
        refreshTotals();
        hideMessage();
    }

    private void showNoOrder() {
        currentOrder = null;
        lines.clear();
        orderHeader.setText("No order open");
        subtotalLabel.setText(money(Money.ZERO));
        discountLabel.setText(money(Money.ZERO));
        taxLabel.setText(money(Money.ZERO));
        totalLabel.setText(money(Money.ZERO));
        setOrderControlsDisabled(true);
    }

    private void setOrderControlsDisabled(boolean disabled) {
        addButton.setDisable(disabled);
        removeLineButton.setDisable(disabled);
        applyDiscountButton.setDisable(disabled);
        finaliseButton.setDisable(disabled);
        voidButton.setDisable(disabled);
    }

    // --- lines ------------------------------------------------------------------

    private void addItem() {
        MenuItem item = itemsList.getSelectionModel().getSelectedItem();
        if (item == null) {
            showMessage("Select an item to add.");
            return;
        }
        run(() -> {
            int qty = parseQuantity(quantityField.getText());
            currentOrder = context.orderService().addLine(context.session(), currentOrder.getOrderId(),
                item.getItemId(), qty);
            quantityField.clear();
            reloadLinesAndTotals();
        });
    }

    private void removeSelectedLine() {
        OrderItem line = linesTable.getSelectionModel().getSelectedItem();
        if (line == null) {
            showMessage("Select a line to remove.");
            return;
        }
        run(() -> {
            currentOrder = context.orderService().removeLine(context.session(), line.getOrderItemId());
            reloadLinesAndTotals();
        });
    }

    private void applyDiscount() {
        run(() -> {
            DiscountType type = discountTypeCombo.getValue();
            BigDecimal value = parseDiscount(discountValueField.getText(), type);
            currentOrder = context.orderService().applyDiscount(context.session(),
                currentOrder.getOrderId(), type, value);
            refreshTotals();
        });
    }

    private void reloadLinesAndTotals() {
        lines.setAll(context.orderService().listLines(currentOrder.getOrderId()));
        refreshTotals();
    }

    private void refreshTotals() {
        subtotalLabel.setText(money(currentOrder.getSubtotal()));
        discountLabel.setText(currentOrder.getDiscountAmount() == null
            ? money(Money.ZERO) : "-" + money(currentOrder.getDiscountAmount()));
        BigDecimal rate = currentOrder.getTaxRate() == null ? BigDecimal.ZERO : currentOrder.getTaxRate();
        taxLabel.setText("Tax (" + rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
            + "%): " + money(currentOrder.getTaxAmount()));
        totalLabel.setText(money(currentOrder.getTotal()));
    }

    // --- finalise / void --------------------------------------------------------

    private void finalise() {
        if (currentOrder == null) return;
        PaymentMethod method = methodCombo.getValue();
        if (method == null) {
            showMessage("Select a payment method.");
            return;
        }
        run(() -> {
            BigDecimal tendered = parseTendered(tenderedField.getText());
            Order finalised = context.orderService().finalise(context.session(),
                currentOrder.getOrderId(), method.getMethodId(), tendered);
            int orderId = finalised.getOrderId();
            String number = finalised.getOrderNumber();
            tenderedField.clear();
            showNoOrder();
            reloadReference();
            showMessage("Order " + number + " paid — total " + money(finalised.getTotal()) + ".");
            printReceipt(orderId, number);
        });
    }

    private void voidOrder() {
        if (currentOrder == null) return;
        if (!confirm("Void order " + currentOrder.getOrderNumber() + "?",
            "The order is cancelled and the table released. This cannot be undone.")) {
            return;
        }
        run(() -> {
            context.orderService().voidOrder(context.session(), currentOrder.getOrderId());
            showNoOrder();
            reloadReference();
            showMessage("Order voided.");
        });
    }

    /** Writes the receipt PDF and opens it in the system viewer (FR-16). */
    private void printReceipt(int orderId, String number) {
        try {
            byte[] pdf = context.receiptService().generate(orderId);
            File file = new File(System.getProperty("java.io.tmpdir"), "receipt-" + number + ".pdf");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(pdf);
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                showMessage("Receipt saved to " + file.getAbsolutePath());
            }
        } catch (RmsException e) {
            showMessage(e.getMessage());
        } catch (Exception e) {
            showMessage("The receipt was generated but could not be opened automatically.");
        }
    }

    // --- rendering & parsing ----------------------------------------------------

    private void configureTextRenderers() {
        tableCombo.setCellFactory(v -> tableCell());
        tableCombo.setButtonCell(tableCell());
        itemsList.setCellFactory(v -> itemCell());
        methodCombo.setCellFactory(v -> methodCell());
        methodCombo.setButtonCell(methodCell());
        openOrdersList.setCellFactory(v -> orderCell());
    }

    private static javafx.scene.control.ListCell<DiningTable> tableCell() {
        return new javafx.scene.control.ListCell<DiningTable>() {
            @Override protected void updateItem(DiningTable t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.getLabel() + " (seats " + t.getCapacity() + ")");
            }
        };
    }

    private javafx.scene.control.ListCell<MenuItem> itemCell() {
        return new javafx.scene.control.ListCell<MenuItem>() {
            @Override protected void updateItem(MenuItem i, boolean empty) {
                super.updateItem(i, empty);
                setText(empty || i == null ? null : i.getName() + "  ·  " + money(i.getPrice()));
            }
        };
    }

    private static javafx.scene.control.ListCell<PaymentMethod> methodCell() {
        return new javafx.scene.control.ListCell<PaymentMethod>() {
            @Override protected void updateItem(PaymentMethod m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.getMethodName());
            }
        };
    }

    private javafx.scene.control.ListCell<Order> orderCell() {
        return new javafx.scene.control.ListCell<Order>() {
            @Override protected void updateItem(Order o, boolean empty) {
                super.updateItem(o, empty);
                setText(empty || o == null ? null : o.getOrderNumber() + " · " + o.getOrderType().dbValue()
                    + (o.getTableId() == null ? "" : " · T" + o.getTableId()));
            }
        };
    }

    private static int parseQuantity(String text) {
        if (text == null || text.trim().isEmpty()) return 1;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a whole number for the quantity.");
        }
    }

    private static BigDecimal parseDiscount(String text, DiscountType type) {
        if (type == null || type == DiscountType.NONE) return BigDecimal.ZERO;
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid discount value.");
        }
    }

    private static BigDecimal parseTendered(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a valid amount tendered.");
        }
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : Money.round(v).toPlainString();
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

    private static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }
}
