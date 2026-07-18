package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.StockItem;
import domain.StockMovement;
import domain.enums.MovementType;
import domain.enums.Status;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock items with low-stock flagging (T063, FR-18, FR-22) over {@code InventoryService}, laid out
 * as a searchable card list plus the navy detail side-panel from design mockup #4. On-hand is shown
 * but never edited directly here — it moves only through the "Adjust" action, which writes a ledger
 * row and updates on-hand atomically (BR-21).
 *
 * <p>The side-panel has two modes: <em>detail</em> (an item is selected — figures, recent
 * movements, adjust/edit actions) and <em>form</em> (creating or editing an item).
 */
public final class InventoryController implements ContextAware {

    @FXML private TextField searchField;
    @FXML private Button chipAll;
    @FXML private Button chipLow;
    @FXML private Button chipInStock;
    @FXML private ListView<StockItem> itemList;

    @FXML private Label headPill;
    @FXML private Label headTopline;
    @FXML private Label headTitle;
    @FXML private Label headFigure;
    @FXML private Label headFigureUnit;

    @FXML private VBox detailBody;
    @FXML private Label kvUnit;
    @FXML private Label kvOnHand;
    @FXML private Label kvReorder;
    @FXML private Label kvStatus;
    @FXML private VBox movementsBox;
    @FXML private VBox detailFooter;
    @FXML private VBox formFooter;
    @FXML private TextField adjustField;
    @FXML private Button adjustButton;
    @FXML private Button editButton;
    @FXML private Button deactivateButton;

    @FXML private VBox formBody;
    @FXML private TextField nameField;
    @FXML private TextField unitField;
    @FXML private TextField reorderField;
    @FXML private TextField openingField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    @FXML private Button newButton;
    @FXML private Button refreshButton;
    @FXML private Label messageLabel;

    private static final DateTimeFormatter MOVED_AT = DateTimeFormatter.ofPattern("d MMM HH:mm");
    private static final int MOVEMENTS_SHOWN = 4;

    private enum Filter { ALL, LOW, IN_STOCK }

    private AppContext context;
    private final ObservableList<StockItem> visible = FXCollections.observableArrayList();
    private List<StockItem> all = new ArrayList<>();
    private Filter filter = Filter.ALL;
    private StockItem editing;

    @FXML
    private void initialize() {
        itemList.setItems(visible);
        itemList.setCellFactory(v -> itemCell());
        itemList.getSelectionModel().selectedItemProperty()
            .addListener((obs, old, selected) -> { if (selected != null) showDetail(selected); });

        searchField.textProperty().addListener((obs, old, text) -> applyFilter());
        chipAll.setOnAction(e -> setFilter(Filter.ALL));
        chipLow.setOnAction(e -> setFilter(Filter.LOW));
        chipInStock.setOnAction(e -> setFilter(Filter.IN_STOCK));

        newButton.setOnAction(e -> showForm(null));
        editButton.setOnAction(e -> { if (selected() != null) showForm(selected()); });
        cancelButton.setOnAction(e -> cancelForm());
        saveButton.setOnAction(e -> save());
        deactivateButton.setOnAction(e -> toggleActive());
        adjustButton.setOnAction(e -> adjust());
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
        showForm(null);
    }

    private StockItem selected() {
        return itemList.getSelectionModel().getSelectedItem();
    }

    // ---------- list ----------

    private void reload() {
        run(() -> {
            StockItem keep = selected();
            all = context.inventoryService().listItems();
            applyFilter();
            if (keep != null) {
                for (StockItem item : visible) {
                    if (item.getStockItemId() == keep.getStockItemId()) {
                        itemList.getSelectionModel().select(item);
                        showDetail(item);
                        return;
                    }
                }
            }
        });
    }

    private void setFilter(Filter target) {
        filter = target;
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        List<StockItem> matching = new ArrayList<>();
        int low = 0;
        int inStock = 0;
        for (StockItem item : all) {
            if (isLow(item)) low++; else if (item.getStatus() == Status.ACTIVE) inStock++;
            boolean byFilter =
                filter == Filter.ALL
                || (filter == Filter.LOW && isLow(item))
                || (filter == Filter.IN_STOCK && !isLow(item) && item.getStatus() == Status.ACTIVE);
            boolean byQuery = query.isEmpty()
                || (item.getName() != null && item.getName().toLowerCase().contains(query));
            if (byFilter && byQuery) matching.add(item);
        }
        visible.setAll(matching);
        chip(chipAll, "All", all.size(), filter == Filter.ALL);
        chip(chipLow, "Low / Out", low, filter == Filter.LOW);
        chip(chipInStock, "In stock", inStock, filter == Filter.IN_STOCK);
    }

    private static void chip(Button chip, String text, int count, boolean active) {
        chip.setMinWidth(Button.USE_PREF_SIZE); // a crushed chip should wrap, not ellipsize
        chip.setText(text);
        Label badge = new Label(String.valueOf(count));
        badge.getStyleClass().add("chip-count");
        chip.setGraphic(badge);
        chip.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        chip.getStyleClass().remove("active");
        if (active) chip.getStyleClass().add("active");
    }

    /** One row card: initials tile, name + unit, then on-hand / reorder / status columns. */
    private ListCell<StockItem> itemCell() {
        ListCell<StockItem> cell = new ListCell<StockItem>() {
            @Override protected void updateItem(StockItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label initials = new Label(initialsOf(item.getName()));
                StackPane tile = new StackPane(initials);
                tile.getStyleClass().add("item-initials");

                Label name = new Label(item.getName());
                name.getStyleClass().add("title");
                Label sub = new Label("reorder at " + plain(item.getReorderLevel()) + " " + item.getUnitOfMeasure());
                sub.getStyleClass().add("sub");
                VBox text = new VBox(2, name, sub);
                text.setAlignment(Pos.CENTER_LEFT);

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);

                Label onHand = new Label(plain(item.getQuantityOnHand()) + " " + item.getUnitOfMeasure());
                onHand.getStyleClass().add("serif-num");
                onHand.setPrefWidth(90);
                onHand.setAlignment(Pos.CENTER_RIGHT);

                Label reorder = new Label(plain(item.getReorderLevel()) + " " + item.getUnitOfMeasure());
                reorder.getStyleClass().add("sub");
                reorder.setPrefWidth(80);
                reorder.setAlignment(Pos.CENTER_RIGHT);

                HBox pillBox = new HBox(StatusPill.make(statusText(item)));
                pillBox.setPrefWidth(92);
                pillBox.setAlignment(Pos.CENTER_RIGHT);

                HBox card = new HBox(14, tile, text, gap, onHand, reorder, pillBox);
                card.getStyleClass().add("row-card");
                card.setAlignment(Pos.CENTER_LEFT);
                setGraphic(card);
            }
        };
        // Track the viewport width so a narrow window truncates the card, not scrolls it.
        cell.prefWidthProperty().bind(itemList.widthProperty().subtract(24));
        cell.setMinWidth(0);
        return cell;
    }

    private static String initialsOf(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String trimmed = name.trim().toUpperCase();
        return trimmed.length() < 2 ? trimmed : trimmed.substring(0, 2);
    }

    private static String statusText(StockItem item) {
        if (item.getStatus() != Status.ACTIVE) return "Inactive";
        return isLow(item) ? "Low" : "In stock";
    }

    private static boolean isLow(StockItem item) {
        return item.getStatus() == Status.ACTIVE
            && item.getQuantityOnHand() != null && item.getReorderLevel() != null
            && item.getQuantityOnHand().compareTo(item.getReorderLevel()) <= 0;
    }

    // ---------- detail mode ----------

    private void showDetail(StockItem item) {
        editing = null;
        setMode(true);

        String status = statusText(item);
        headPill.setText(status);
        headPill.setVisible(true);
        headTopline.setText(item.getUnitOfMeasure() == null ? "" : item.getUnitOfMeasure().toUpperCase());
        headTitle.setText(item.getName());
        headFigure.setText(plain(item.getQuantityOnHand()));
        headFigureUnit.setText(item.getUnitOfMeasure() + " on hand");

        kvUnit.setText(item.getUnitOfMeasure());
        kvOnHand.setText(plain(item.getQuantityOnHand()) + " " + item.getUnitOfMeasure());
        kvReorder.setText(plain(item.getReorderLevel()) + " " + item.getUnitOfMeasure());
        kvStatus.setText(item.getStatus() == Status.ACTIVE ? "Active" : "Inactive");

        rebuildMovements(item);
        adjustField.clear();
        hideMessage();
    }

    private void rebuildMovements(StockItem item) {
        movementsBox.getChildren().clear();
        List<StockMovement> movements;
        try {
            movements = context.inventoryService().movementsFor(item.getStockItemId());
        } catch (RmsException e) {
            showMessage(e.getMessage());
            return;
        }
        if (movements.isEmpty()) {
            Label none = new Label("No movements recorded yet.");
            none.getStyleClass().add("detail-empty");
            movementsBox.getChildren().add(none);
            return;
        }
        for (StockMovement movement : movements.subList(0, Math.min(MOVEMENTS_SHOWN, movements.size()))) {
            movementsBox.getChildren().add(movementRow(movement, item));
        }
    }

    private HBox movementRow(StockMovement movement, StockItem item) {
        boolean in = movement.getQuantityChange() != null
            && movement.getQuantityChange().signum() >= 0;

        Label arrow = new Label(in ? "↑" : "↓");
        arrow.getStyleClass().addAll("mv-icon", in ? "in" : "out");

        Label type = new Label(movement.getMovementType() == MovementType.RECEIPT ? "Receipt" : "Adjustment");
        type.getStyleClass().add("mv-title");
        Label when = new Label(movement.getMovedAt() == null ? "" : MOVED_AT.format(movement.getMovedAt()));
        when.getStyleClass().add("mv-sub");
        VBox text = new VBox(1, type, when);
        text.setAlignment(Pos.CENTER_LEFT);

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        Label qty = new Label((in ? "+" : "") + plain(movement.getQuantityChange()) + " " + item.getUnitOfMeasure());
        qty.getStyleClass().addAll("mv-qty", in ? "in" : "out");

        HBox row = new HBox(10, arrow, text, gap, qty);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new javafx.geometry.Insets(7, 0, 7, 0));
        return row;
    }

    // ---------- form mode ----------

    /** Switches the panel to the create/edit form; {@code item == null} means a new item. */
    private void showForm(StockItem item) {
        editing = item;
        setMode(false);

        headPill.setVisible(false);
        headTopline.setText(item == null ? "CREATE" : "EDIT");
        headTitle.setText(item == null ? "New stock item" : item.getName());
        headFigure.setText("");
        headFigureUnit.setText("");

        nameField.setText(item == null ? "" : item.getName());
        unitField.setText(item == null ? "" : item.getUnitOfMeasure());
        reorderField.setText(item == null ? "" : plain(item.getReorderLevel()));
        openingField.setText(item == null ? "" : plain(item.getQuantityOnHand()));
        // On-hand is not editable after creation; it moves via adjustments only.
        openingField.setDisable(item != null);
        if (item == null) itemList.getSelectionModel().clearSelection();
        hideMessage();
    }

    /** Shows either the detail (true) or the form (false) body + footer pair. */
    private void setMode(boolean detail) {
        detailBody.setVisible(detail);
        detailBody.setManaged(detail);
        detailFooter.setVisible(detail);
        detailFooter.setManaged(detail);
        formBody.setVisible(!detail);
        formBody.setManaged(!detail);
        formFooter.setVisible(!detail);
        formFooter.setManaged(!detail);
    }

    private void cancelForm() {
        StockItem back = editing != null ? editing : selected();
        if (back != null) {
            showDetail(back);
        } else {
            showForm(null);
        }
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
            StockItem saved = context.inventoryService().save(context.session(), item);
            editing = null;
            reload();
            selectById(saved.getStockItemId());
        });
    }

    private void selectById(int stockItemId) {
        for (StockItem item : visible) {
            if (item.getStockItemId() == stockItemId) {
                itemList.getSelectionModel().select(item);
                showDetail(item);
                return;
            }
        }
        showForm(null);
    }

    private void toggleActive() {
        StockItem item = selected();
        if (item == null) {
            showMessage("Select a stock item first.");
            return;
        }
        run(() -> {
            if (item.getStatus() == Status.ACTIVE) {
                context.inventoryService().deactivate(context.session(), item.getStockItemId());
            } else {
                context.inventoryService().reactivate(context.session(), item.getStockItemId());
            }
            reload();
            selectById(item.getStockItemId());
        });
    }

    private void adjust() {
        StockItem item = selected();
        if (item == null) {
            showMessage("Select a stock item to adjust.");
            return;
        }
        run(() -> {
            BigDecimal delta = parseSignedDecimal(adjustField.getText());
            context.inventoryService().adjustStock(context.session(), item.getStockItemId(), delta);
            adjustField.clear();
            reload();
            selectById(item.getStockItemId());
        });
    }

    // ---------- helpers ----------

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
