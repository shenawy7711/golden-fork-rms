package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.DiningTable;
import domain.enums.TableStatus;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.TableService;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The live floor as a card grid plus the navy detail side-panel (T042, FR-08, FR-09) over
 * {@code TableService}, matching design mockup #2: occupied tables go navy, selecting a card
 * drives the panel, and the panel offers only the transitions the state machine permits from
 * where the table currently is. That is a convenience, not the rule: {@code TableService}
 * validates the move again and refuses an illegal one regardless of what the UI offered (BR-12).
 */
public final class TableController implements ContextAware {

    @FXML private FlowPane floorPane;
    @FXML private Button chipAll;
    @FXML private Button chipFree;
    @FXML private Button chipOccupied;
    @FXML private Button chipReserved;
    @FXML private Button chipCleaning;

    @FXML private Label headPill;
    @FXML private Label headTopline;
    @FXML private Label headTitle;
    @FXML private Label headFigure;
    @FXML private Label headFigureUnit;

    @FXML private VBox detailBody;
    @FXML private Label kvStatus;
    @FXML private Label kvCapacity;
    @FXML private Label statusHint;
    @FXML private VBox detailFooter;
    @FXML private VBox transitionBox;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    @FXML private VBox formBody;
    @FXML private TextField labelField;
    @FXML private TextField capacityField;
    @FXML private VBox formFooter;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    @FXML private Button newButton;
    @FXML private Button refreshButton;
    @FXML private Label messageLabel;

    private AppContext context;

    private List<DiningTable> tables = new ArrayList<>();
    private TableStatus filter; // null = all
    private DiningTable selected;
    private DiningTable editing;

    @FXML
    private void initialize() {
        chipAll.setOnAction(e -> setFilter(null));
        chipFree.setOnAction(e -> setFilter(TableStatus.FREE));
        chipOccupied.setOnAction(e -> setFilter(TableStatus.OCCUPIED));
        chipReserved.setOnAction(e -> setFilter(TableStatus.RESERVED));
        chipCleaning.setOnAction(e -> setFilter(TableStatus.NEEDS_CLEANING));

        newButton.setOnAction(e -> showForm(null));
        editButton.setOnAction(e -> { if (selected != null) showForm(selected); });
        cancelButton.setOnAction(e -> cancelForm());
        saveButton.setOnAction(e -> save());
        deleteButton.setOnAction(e -> deleteSelected());
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
        showForm(null);
    }

    private void reload() {
        run(() -> {
            tables = context.tableService().listTables();
            if (selected != null) {
                selected = findById(selected.getTableId());
            }
            rebuildFloor();
            if (selected != null) {
                showDetail(selected);
            }
        });
    }

    private DiningTable findById(int tableId) {
        for (DiningTable table : tables) {
            if (table.getTableId() == tableId) return table;
        }
        return null;
    }

    // ---------- floor ----------

    private void setFilter(TableStatus target) {
        filter = target;
        rebuildFloor();
    }

    /** The colour-coded floor (FR-09): one card per table, navy when occupied (mockup #2). */
    private void rebuildFloor() {
        int free = 0;
        int occupied = 0;
        int reserved = 0;
        int cleaning = 0;
        List<VBox> cards = new ArrayList<>();
        for (DiningTable table : tables) {
            switch (table.getStatus()) {
                case FREE: free++; break;
                case OCCUPIED: occupied++; break;
                case RESERVED: reserved++; break;
                case NEEDS_CLEANING: cleaning++; break;
                default: break;
            }
            if (filter == null || table.getStatus() == filter) {
                cards.add(floorCard(table));
            }
        }
        floorPane.getChildren().setAll(cards);
        chip(chipAll, "All", tables.size(), filter == null);
        chip(chipFree, "Free", free, filter == TableStatus.FREE);
        chip(chipOccupied, "Occupied", occupied, filter == TableStatus.OCCUPIED);
        chip(chipReserved, "Reserved", reserved, filter == TableStatus.RESERVED);
        chip(chipCleaning, "Cleaning", cleaning, filter == TableStatus.NEEDS_CLEANING);
    }

    private VBox floorCard(DiningTable table) {
        Label id = new Label(table.getLabel());
        id.getStyleClass().add("id");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Label pill = StatusPill.make(statusWord(table.getStatus()));
        pill.setMinWidth(Label.USE_PREF_SIZE); // truncate the id, never the status pill
        HBox top = new HBox(8, id, gap, pill);
        top.setAlignment(Pos.CENTER_LEFT);

        Label seats = new Label("Seats " + table.getCapacity());
        seats.getStyleClass().add("line");
        Label state = new Label(table.getStatus().dbValue());
        state.getStyleClass().add("line-sub");

        VBox card = new VBox(6, top, seats, state);
        card.getStyleClass().add("floor-card");
        if (table.getStatus() == TableStatus.OCCUPIED) card.getStyleClass().add("navy");
        if (selected != null && selected.getTableId() == table.getTableId()) {
            card.getStyleClass().add("selected");
        }
        card.setOnMouseClicked(e -> select(table));
        return card;
    }

    private void select(DiningTable table) {
        selected = table;
        rebuildFloor();
        showDetail(table);
    }

    private static String statusWord(TableStatus status) {
        switch (status) {
            case FREE: return "Free";
            case OCCUPIED: return "Occupied";
            case RESERVED: return "Reserved";
            case NEEDS_CLEANING: return "Cleaning";
            default: return status.dbValue();
        }
    }

    private static void chip(Button chip, String text, int count, boolean active) {
        chip.setMinWidth(Button.USE_PREF_SIZE); // a crushed chip should wrap, not ellipsize
        chip.setText(text);
        Label badge = new Label(String.valueOf(count));
        badge.getStyleClass().add("chip-count");
        chip.setGraphic(badge);
        chip.setContentDisplay(ContentDisplay.RIGHT);
        chip.getStyleClass().remove("active");
        if (active) chip.getStyleClass().add("active");
    }

    // ---------- detail mode ----------

    private void showDetail(DiningTable table) {
        editing = null;
        setMode(true);

        headPill.setText(statusWord(table.getStatus()));
        headPill.setVisible(true);
        headTopline.setText("TABLE");
        headTitle.setText(table.getLabel());
        headFigure.setText(String.valueOf(table.getCapacity()));
        headFigureUnit.setText("seats");

        kvStatus.setText(table.getStatus().dbValue());
        kvCapacity.setText(table.getCapacity() + " seats");

        rebuildTransitions(table);
        hideMessage();
    }

    /** One button per legal move from here (BR-12): the first is the gold call-to-action. */
    private void rebuildTransitions(DiningTable table) {
        List<TableStatus> allowed = new ArrayList<>();
        for (TableStatus candidate : TableStatus.values()) {
            if (candidate != table.getStatus()
                && TableService.isLegalTransition(table.getStatus(), candidate)) {
                allowed.add(candidate);
            }
        }
        statusHint.setText(allowed.isEmpty()
            ? table.getLabel() + " is " + table.getStatus().dbValue() + " and cannot move from here."
            : "Where can " + table.getLabel() + " go from " + table.getStatus().dbValue() + "?");

        transitionBox.getChildren().clear();
        for (int i = 0; i < allowed.size(); i++) {
            TableStatus target = allowed.get(i);
            Button move = new Button("Mark " + statusWord(target));
            move.getStyleClass().add(i == 0 ? "btn-gold" : "qa");
            move.setMaxWidth(Double.MAX_VALUE);
            move.setOnAction(e -> applyStatus(target));
            transitionBox.getChildren().add(move);
        }
    }

    private void applyStatus(TableStatus target) {
        if (selected == null) return;
        run(() -> {
            context.tableService().changeStatus(context.session(), selected.getTableId(), target);
            reload();
        });
    }

    // ---------- form mode ----------

    /** Switches the panel to the create/edit form; {@code table == null} means a new table. */
    private void showForm(DiningTable table) {
        editing = table;
        setMode(false);

        headPill.setVisible(false);
        headTopline.setText(table == null ? "CREATE" : "EDIT");
        headTitle.setText(table == null ? "New table" : table.getLabel());
        headFigure.setText("");
        headFigureUnit.setText("");

        labelField.setText(table == null ? "" : table.getLabel());
        capacityField.setText(table == null ? "" : String.valueOf(table.getCapacity()));
        if (table == null) {
            selected = null;
            rebuildFloor();
        }
        hideMessage();
    }

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
        if (editing != null) {
            showDetail(editing);
        } else if (selected != null) {
            showDetail(selected);
        } else {
            showForm(null);
        }
    }

    private void save() {
        run(() -> {
            DiningTable table = new DiningTable();
            if (editing != null) {
                table.setTableId(editing.getTableId());
                table.setStatus(editing.getStatus());
            }
            table.setLabel(labelField.getText());
            table.setCapacity(parseCapacity(capacityField.getText()));

            DiningTable saved = context.tableService().defineTable(context.session(), table);
            editing = null;
            selected = saved;
            reload();
        });
    }

    private void deleteSelected() {
        if (selected == null) {
            showMessage("Select a table first.");
            return;
        }
        if (!confirm("Delete table " + selected.getLabel() + "?", "This cannot be undone.")) return;
        run(() -> {
            context.tableService().deleteTable(context.session(), selected.getTableId());
            selected = null;
            reload();
            showForm(null);
        });
    }

    private static int parseCapacity(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter a whole number ≥ 1 for the capacity.");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a whole number ≥ 1 for the capacity.");
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

    private static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Golden Fork RMS");
        alert.setHeaderText(header);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }
}
