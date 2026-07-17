package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.DiningTable;
import domain.Reservation;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Reservations (T071, FR-24…FR-26) over {@code ReservationService}. Booking prevents double-booking
 * a table (BR-27); the lifecycle buttons move a booking through Seated/Completed/Cancelled/No-Show,
 * releasing the table hold as the service dictates (BR-29).
 */
public final class ReservationController implements ContextAware {

    @FXML private ComboBox<DiningTable> tableCombo;
    @FXML private TextField customerField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField whenField;
    @FXML private TextField partyField;
    @FXML private CheckBox overrideCapacityBox;
    @FXML private Button createButton;

    @FXML private TableView<Reservation> reservationTable;
    @FXML private TableColumn<Reservation, String> customerColumn;
    @FXML private TableColumn<Reservation, String> tableColumn;
    @FXML private TableColumn<Reservation, String> whenColumn;
    @FXML private TableColumn<Reservation, String> partyColumn;
    @FXML private TableColumn<Reservation, String> statusColumn;

    @FXML private Button seatButton;
    @FXML private Button completeButton;
    @FXML private Button cancelButton;
    @FXML private Button noShowButton;

    @FXML private Label messageLabel;
    @FXML private Button refreshButton;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AppContext context;
    private final ObservableList<DiningTable> tables = FXCollections.observableArrayList();
    private final ObservableList<Reservation> reservations = FXCollections.observableArrayList();
    private final Map<Integer, String> tableLabels = new HashMap<>();

    @FXML
    private void initialize() {
        tableCombo.setItems(tables);
        tableCombo.setButtonCell(tableCell());
        tableCombo.setCellFactory(v -> tableCell());

        customerColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        tableColumn.setCellValueFactory(c -> new SimpleStringProperty(
            tableLabels.getOrDefault(c.getValue().getTableId(), "#" + c.getValue().getTableId())));
        whenColumn.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getReservationDatetime() == null ? "" : WHEN.format(c.getValue().getReservationDatetime())));
        partyColumn.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getPartySize())));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().dbValue()));
        reservationTable.setItems(reservations);

        createButton.setOnAction(e -> create());
        seatButton.setOnAction(e -> act("seat"));
        completeButton.setOnAction(e -> act("complete"));
        cancelButton.setOnAction(e -> act("cancel"));
        noShowButton.setOnAction(e -> act("noshow"));
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
    }

    private void reload() {
        run(() -> {
            tables.setAll(context.tableService().listTables());
            tableLabels.clear();
            for (DiningTable t : tables) {
                tableLabels.put(t.getTableId(), t.getLabel());
            }
            reservations.setAll(context.reservationService().listReservations());
        });
    }

    private void create() {
        DiningTable table = tableCombo.getValue();
        if (table == null) {
            showMessage("Select a table for the reservation.");
            return;
        }
        run(() -> {
            Reservation r = new Reservation();
            r.setTableId(table.getTableId());
            r.setCustomerName(customerField.getText());
            r.setContactPhone(blankToNull(phoneField.getText()));
            r.setContactEmail(blankToNull(emailField.getText()));
            r.setReservationDatetime(parseWhen(whenField.getText()));
            r.setPartySize(parseParty(partyField.getText()));
            context.reservationService().create(context.session(), r, overrideCapacityBox.isSelected());
            clearForm();
            reload();
            showMessage("Reservation booked.");
        });
    }

    private void act(String action) {
        Reservation selected = reservationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage("Select a reservation first.");
            return;
        }
        run(() -> {
            switch (action) {
                case "seat":     context.reservationService().seat(context.session(), selected.getReservationId()); break;
                case "complete": context.reservationService().complete(context.session(), selected.getReservationId()); break;
                case "cancel":   context.reservationService().cancel(context.session(), selected.getReservationId()); break;
                case "noshow":   context.reservationService().markNoShow(context.session(), selected.getReservationId()); break;
                default: break;
            }
            reload();
        });
    }

    private void clearForm() {
        customerField.clear();
        phoneField.clear();
        emailField.clear();
        whenField.clear();
        partyField.clear();
        overrideCapacityBox.setSelected(false);
    }

    private ListCell<DiningTable> tableCell() {
        return new ListCell<DiningTable>() {
            @Override protected void updateItem(DiningTable t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.getLabel() + " (seats " + t.getCapacity() + ")");
            }
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static LocalDateTime parseWhen(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter the date and time as YYYY-MM-DD HH:MM.");
        }
        try {
            return LocalDateTime.parse(text.trim(), WHEN);
        } catch (Exception e) {
            throw new ValidationException("Enter the date and time as YYYY-MM-DD HH:MM.");
        }
    }

    private static int parseParty(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter the party size.");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Enter a whole number for the party size.");
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
