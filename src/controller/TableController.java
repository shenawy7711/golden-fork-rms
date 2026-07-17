package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.DiningTable;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import service.TableService;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table layout and the live floor board (T042, FR-08, FR-09) over {@code TableService}.
 *
 * <p>The status dropdown offers only the transitions the state machine permits from where the
 * selected table currently is. That is a convenience, not the rule: {@code TableService} validates
 * the move again and refuses an illegal one regardless of what the UI offered (BR-12, BR-03).
 */
public final class TableController implements ContextAware {

    @FXML private FlowPane floorPane;

    @FXML private TableView<DiningTable> tableView;
    @FXML private TableColumn<DiningTable, String> labelColumn;
    @FXML private TableColumn<DiningTable, String> capacityColumn;
    @FXML private TableColumn<DiningTable, String> statusColumn;

    @FXML private Label formTitle;
    @FXML private TextField labelField;
    @FXML private TextField capacityField;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deleteButton;

    @FXML private ComboBox<TableStatus> statusCombo;
    @FXML private Label statusHint;
    @FXML private Button changeStatusButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private AppContext context;

    private final ObservableList<DiningTable> tables = FXCollections.observableArrayList();
    private final ObservableList<TableStatus> nextStatuses = FXCollections.observableArrayList();

    private DiningTable editing;

    @FXML
    private void initialize() {
        labelColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLabel()));
        capacityColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            String.valueOf(cell.getValue().getCapacity())));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getStatus().dbValue()));
        StatusPill.apply(statusColumn);
        tableView.setItems(tables);
        statusCombo.setItems(nextStatuses);

        tableView.getSelectionModel().selectedItemProperty()
            .addListener((observable, old, selected) -> showInForm(selected));

        saveButton.setOnAction(event -> save());
        newButton.setOnAction(event -> startNew());
        deleteButton.setOnAction(event -> deleteSelected());
        changeStatusButton.setOnAction(event -> applyStatus());
        refreshButton.setOnAction(event -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        startNew();
        reload();
    }

    private void reload() {
        run(() -> {
            DiningTable selected = tableView.getSelectionModel().getSelectedItem();
            tables.setAll(context.tableService().listTables());
            rebuildFloor();
            if (selected != null) {
                reselectById(selected.getTableId());
            }
        });
    }

    private void reselectById(int tableId) {
        for (DiningTable table : tables) {
            if (table.getTableId() == tableId) {
                tableView.getSelectionModel().select(table);
                return;
            }
        }
    }

    /** The colour-coded floor board (FR-09), one tile per table. */
    private void rebuildFloor() {
        List<VBox> tiles = new ArrayList<>();
        for (DiningTable table : tables) {
            Label dot = new Label("●");
            dot.getStyleClass().add(statusStyle(table.getStatus()));

            Label label = new Label(table.getLabel());
            label.getStyleClass().add("id");

            HBox top = new HBox(6, dot, label);

            Label detail = new Label(table.getStatus().dbValue() + " · seats " + table.getCapacity());
            detail.getStyleClass().add("detail");

            VBox tile = new VBox(4, top, detail);
            tile.getStyleClass().add("table-tile");
            // Clicking a tile selects the table, so the floor drives the panels beside it.
            tile.setOnMouseClicked(event -> tableView.getSelectionModel().select(table));
            tiles.add(tile);
        }
        floorPane.getChildren().setAll(tiles);
    }

    /** Maps a status to its colour helper in app.css. */
    private static String statusStyle(TableStatus status) {
        switch (status) {
            case FREE: return "st-free";
            case OCCUPIED: return "st-occupied";
            case RESERVED: return "st-reserved";
            case NEEDS_CLEANING: return "st-cleaning";
            default: return "st-free";
        }
    }

    private void startNew() {
        editing = null;
        formTitle.setText("New table");
        labelField.clear();
        capacityField.clear();
        tableView.getSelectionModel().clearSelection();
        nextStatuses.clear();
        statusHint.setText("Select a table to see where it can go next.");
        hideMessage();
    }

    private void showInForm(DiningTable table) {
        if (table == null) return;
        editing = table;
        formTitle.setText("Edit " + table.getLabel());
        labelField.setText(table.getLabel());
        capacityField.setText(String.valueOf(table.getCapacity()));
        refreshStatusOptions(table);
        hideMessage();
    }

    /** Offers only what the state machine allows from this table's current status. */
    private void refreshStatusOptions(DiningTable table) {
        List<TableStatus> allowed = new ArrayList<>();
        for (TableStatus candidate : TableStatus.values()) {
            if (candidate != table.getStatus() && TableService.isLegalTransition(table.getStatus(), candidate)) {
                allowed.add(candidate);
            }
        }
        nextStatuses.setAll(allowed);
        statusCombo.setValue(allowed.isEmpty() ? null : allowed.get(0));
        statusHint.setText(allowed.isEmpty()
            ? table.getLabel() + " is " + table.getStatus().dbValue() + " and cannot move from here."
            : "From " + table.getStatus().dbValue() + ", " + table.getLabel() + " can become: " + join(allowed));
    }

    private static String join(List<TableStatus> statuses) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) text.append(i == statuses.size() - 1 ? " or " : ", ");
            text.append(statuses.get(i).dbValue());
        }
        return text.toString();
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

            context.tableService().defineTable(context.session(), table);
            startNew();
            reload();
        });
    }

    private void deleteSelected() {
        DiningTable selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a table first.");
            return;
        }
        if (!confirm("Delete table " + selected.getLabel() + "?", "This cannot be undone.")) return;
        run(() -> {
            context.tableService().deleteTable(context.session(), selected.getTableId());
            startNew();
            reload();
        });
    }

    private void applyStatus() {
        DiningTable selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a table first.");
            return;
        }
        TableStatus target = statusCombo.getValue();
        if (target == null) {
            showMessage("Select a status to move to.");
            return;
        }
        run(() -> {
            context.tableService().changeStatus(context.session(), selected.getTableId(), target);
            reload();
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
