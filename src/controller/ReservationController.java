package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.DiningTable;
import domain.Reservation;
import domain.enums.ReservationStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reservations (T071, FR-24…FR-26) over {@code ReservationService}, matching design mockup #3:
 * booking cards filtered by status chips, and a navy detail side-panel whose footer offers only
 * the lifecycle moves that make sense from the booking's current status. The service enforces the
 * rules regardless (BR-27, BR-29): booking prevents double-booking, and each move releases or
 * holds the table as required.
 */
public final class ReservationController implements ContextAware {

    @FXML private FlowPane cardsPane;
    @FXML private Button chipAll;
    @FXML private Button chipBooked;
    @FXML private Button chipSeated;
    @FXML private Button chipCompleted;
    @FXML private Button chipCancelled;
    @FXML private Button chipNoShow;

    @FXML private Label headPill;
    @FXML private Label headTopline;
    @FXML private Label headTitle;
    @FXML private Label headSub;

    @FXML private VBox detailBody;
    @FXML private Label kvTable;
    @FXML private Label kvWhen;
    @FXML private Label kvDuration;
    @FXML private Label kvParty;
    @FXML private Label kvPhone;
    @FXML private Label kvEmail;
    @FXML private VBox detailFooter;
    @FXML private VBox actionBox;

    @FXML private VBox formBody;
    @FXML private ComboBox<DiningTable> tableCombo;
    @FXML private TextField customerField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField whenField;
    @FXML private TextField partyField;
    @FXML private CheckBox overrideCapacityBox;
    @FXML private VBox formFooter;
    @FXML private Button createButton;
    @FXML private Button cancelFormButton;

    @FXML private Button newButton;
    @FXML private Button refreshButton;
    @FXML private Label messageLabel;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CARD_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DETAIL_WHEN = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm");

    private AppContext context;
    private final ObservableList<DiningTable> tables = FXCollections.observableArrayList();
    private final Map<Integer, String> tableLabels = new HashMap<>();
    private List<Reservation> reservations = new ArrayList<>();
    private ReservationStatus filter; // null = all
    private Reservation selected;

    @FXML
    private void initialize() {
        tableCombo.setItems(tables);
        tableCombo.setButtonCell(tableCell());
        tableCombo.setCellFactory(v -> tableCell());

        chipAll.setOnAction(e -> setFilter(null));
        chipBooked.setOnAction(e -> setFilter(ReservationStatus.BOOKED));
        chipSeated.setOnAction(e -> setFilter(ReservationStatus.SEATED));
        chipCompleted.setOnAction(e -> setFilter(ReservationStatus.COMPLETED));
        chipCancelled.setOnAction(e -> setFilter(ReservationStatus.CANCELLED));
        chipNoShow.setOnAction(e -> setFilter(ReservationStatus.NO_SHOW));

        newButton.setOnAction(e -> showForm());
        cancelFormButton.setOnAction(e -> cancelForm());
        createButton.setOnAction(e -> create());
        refreshButton.setOnAction(e -> reload());
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
        reload();
        showForm();
    }

    private void reload() {
        run(() -> {
            tables.setAll(context.tableService().listTables());
            tableLabels.clear();
            for (DiningTable t : tables) {
                tableLabels.put(t.getTableId(), t.getLabel());
            }
            reservations = context.reservationService().listReservations();
            if (selected != null) {
                selected = findById(selected.getReservationId());
            }
            rebuildCards();
            if (selected != null) {
                showDetail(selected);
            }
        });
    }

    private Reservation findById(int reservationId) {
        for (Reservation r : reservations) {
            if (r.getReservationId() == reservationId) return r;
        }
        return null;
    }

    private String tableLabel(int tableId) {
        return tableLabels.getOrDefault(tableId, "#" + tableId);
    }

    // ---------- cards ----------

    private void setFilter(ReservationStatus target) {
        filter = target;
        rebuildCards();
    }

    private void rebuildCards() {
        Map<ReservationStatus, Integer> counts = new HashMap<>();
        List<VBox> cards = new ArrayList<>();
        for (Reservation r : reservations) {
            Integer c = counts.get(r.getStatus());
            counts.put(r.getStatus(), c == null ? 1 : c + 1);
            if (filter == null || r.getStatus() == filter) {
                cards.add(bookingCard(r));
            }
        }
        cardsPane.getChildren().setAll(cards);
        chip(chipAll, "All", reservations.size(), filter == null);
        chip(chipBooked, "Booked", count(counts, ReservationStatus.BOOKED), filter == ReservationStatus.BOOKED);
        chip(chipSeated, "Seated", count(counts, ReservationStatus.SEATED), filter == ReservationStatus.SEATED);
        chip(chipCompleted, "Completed", count(counts, ReservationStatus.COMPLETED), filter == ReservationStatus.COMPLETED);
        chip(chipCancelled, "Cancelled", count(counts, ReservationStatus.CANCELLED), filter == ReservationStatus.CANCELLED);
        chip(chipNoShow, "No-Show", count(counts, ReservationStatus.NO_SHOW), filter == ReservationStatus.NO_SHOW);
    }

    private static int count(Map<ReservationStatus, Integer> counts, ReservationStatus status) {
        Integer c = counts.get(status);
        return c == null ? 0 : c;
    }

    /** One booking card: serif time + duration, guest, party · table, phone, status pill. */
    private VBox bookingCard(Reservation r) {
        Label time = new Label(r.getReservationDatetime() == null
            ? "—" : CARD_TIME.format(r.getReservationDatetime()));
        time.getStyleClass().add("serif-num");
        Label duration = new Label(r.getDurationMinutes() + " min");
        duration.getStyleClass().add("sub");
        VBox timeBox = new VBox(1, time, duration);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        timeBox.setMinWidth(64);

        Label name = new Label(r.getCustomerName());
        name.getStyleClass().add("title");
        Label line = new Label("Party of " + r.getPartySize() + " · " + tableLabel(r.getTableId()));
        line.getStyleClass().add("sub");
        VBox text = new VBox(2, name, line);
        text.setAlignment(Pos.CENTER_LEFT);

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Label pill = StatusPill.make(r.getStatus().dbValue());
        pill.setMinWidth(Label.USE_PREF_SIZE);
        HBox top = new HBox(14, timeBox, text, gap, pill);
        top.setAlignment(Pos.CENTER_LEFT);

        Label phone = new Label(r.getContactPhone() == null ? " " : r.getContactPhone());
        phone.getStyleClass().add("sub");
        VBox.setMargin(phone, new javafx.geometry.Insets(8, 0, 0, 0));

        VBox card = new VBox(2, top, phone);
        card.getStyleClass().add("row-card");
        card.setPrefWidth(396);
        if (selected != null && selected.getReservationId() == r.getReservationId()) {
            card.getStyleClass().add("selected");
        }
        card.setOnMouseClicked(e -> select(r));
        return card;
    }

    private void select(Reservation r) {
        selected = r;
        rebuildCards();
        showDetail(r);
    }

    private static void chip(Button chip, String text, int chipCount, boolean active) {
        chip.setMinWidth(Button.USE_PREF_SIZE); // a crushed chip should wrap, not ellipsize
        chip.setText(text);
        Label badge = new Label(String.valueOf(chipCount));
        badge.getStyleClass().add("chip-count");
        chip.setGraphic(badge);
        chip.setContentDisplay(ContentDisplay.RIGHT);
        chip.getStyleClass().remove("active");
        if (active) chip.getStyleClass().add("active");
    }

    // ---------- detail mode ----------

    private void showDetail(Reservation r) {
        setMode(true);

        headPill.setText(r.getStatus().dbValue());
        headPill.setVisible(true);
        headTopline.setText(tableLabel(r.getTableId()).toUpperCase());
        headTitle.setText(r.getCustomerName());
        headSub.setText((r.getReservationDatetime() == null
            ? "" : DETAIL_WHEN.format(r.getReservationDatetime()) + " · ")
            + "party of " + r.getPartySize());

        kvTable.setText(tableLabel(r.getTableId()));
        kvWhen.setText(r.getReservationDatetime() == null
            ? "—" : DETAIL_WHEN.format(r.getReservationDatetime()));
        kvDuration.setText(r.getDurationMinutes() + " minutes");
        kvParty.setText(String.valueOf(r.getPartySize()));
        kvPhone.setText(r.getContactPhone() == null ? "—" : r.getContactPhone());
        kvEmail.setText(r.getContactEmail() == null ? "—" : r.getContactEmail());

        rebuildActions(r);
        hideMessage();
    }

    /** Only the lifecycle moves that make sense from this status; the gold one leads (FR-26). */
    private void rebuildActions(Reservation r) {
        actionBox.getChildren().clear();
        switch (r.getStatus()) {
            case BOOKED:
                actionBox.getChildren().add(action("Seat guests", "btn-gold", () ->
                    context.reservationService().seat(context.session(), r.getReservationId())));
                HBox row = new HBox(8,
                    action("Mark no-show", "qa", () ->
                        context.reservationService().markNoShow(context.session(), r.getReservationId())),
                    action("Cancel booking", "qa", () ->
                        context.reservationService().cancel(context.session(), r.getReservationId())));
                for (javafx.scene.Node b : row.getChildren()) {
                    HBox.setHgrow(b, Priority.ALWAYS);
                }
                actionBox.getChildren().add(row);
                break;
            case SEATED:
                actionBox.getChildren().add(action("Complete visit", "btn-gold", () ->
                    context.reservationService().complete(context.session(), r.getReservationId())));
                break;
            default:
                Label done = new Label("This booking is " + r.getStatus().dbValue() + " — no further moves.");
                done.getStyleClass().add("detail-empty");
                done.setWrapText(true);
                actionBox.getChildren().add(done);
                break;
        }
    }

    private Button action(String text, String style, Runnable serviceCall) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> run(() -> {
            serviceCall.run();
            reload();
        }));
        return button;
    }

    // ---------- form mode ----------

    private void showForm() {
        setMode(false);
        headPill.setVisible(false);
        headTopline.setText("CREATE");
        headTitle.setText("New reservation");
        headSub.setText("Hold a table for a guest.");
        selected = null;
        rebuildCards();
        hideMessage();
    }

    private void cancelForm() {
        clearForm();
        if (selected != null) {
            showDetail(selected);
        } else {
            showForm();
        }
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
