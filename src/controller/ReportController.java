package controller;

import app.AppContext;
import app.ContextAware;
import app.Navigator;
import domain.enums.OrderType;
import domain.report.ReportDocument;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import service.exception.RmsException;
import service.exception.ValidationException;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Reports (T074, FR-27…FR-30) over {@code ReportService} and {@code ReportExporter}. A Cashier never
 * reaches this screen (no {@code VIEW_REPORTS}), and the service refuses one regardless (BR-03). The
 * export reproduces the displayed document exactly because both consume the same {@link ReportDocument}.
 */
public final class ReportController implements ContextAware {

    /** The three report kinds the screen offers. */
    private enum Kind { SALES, INVENTORY, STAFF_ACTIVITY;
        @Override public String toString() {
            switch (this) {
                case SALES: return "Sales";
                case INVENTORY: return "Inventory";
                default: return "Staff activity";
            }
        }
    }

    @FXML private ComboBox<Kind> kindCombo;
    @FXML private TextField startField;
    @FXML private TextField endField;
    @FXML private ComboBox<OrderType> orderTypeCombo;
    @FXML private CheckBox lowOnlyBox;
    @FXML private Button generateButton;
    @FXML private Button exportButton;

    @FXML private Label reportTitle;
    @FXML private VBox reportArea;
    @FXML private Label messageLabel;

    private AppContext context;
    private ReportDocument current;

    @FXML
    private void initialize() {
        kindCombo.setItems(FXCollections.observableArrayList(Kind.values()));
        kindCombo.setValue(Kind.SALES);
        orderTypeCombo.setItems(FXCollections.observableArrayList(OrderType.values()));

        LocalDate today = LocalDate.now();
        startField.setText(today.withDayOfMonth(1).toString());
        endField.setText(today.toString());

        generateButton.setOnAction(e -> generate());
        exportButton.setOnAction(e -> export());
        exportButton.setDisable(true);
    }

    @Override
    public void init(AppContext context, Navigator navigator) {
        this.context = context;
    }

    private void generate() {
        run(() -> {
            Kind kind = kindCombo.getValue();
            ReportDocument doc;
            switch (kind) {
                case INVENTORY:
                    doc = context.reportService().inventoryReport(context.session(), lowOnlyBox.isSelected());
                    break;
                case STAFF_ACTIVITY:
                    doc = context.reportService().staffActivityReport(context.session(),
                        parseDate(startField.getText(), "start"), parseDate(endField.getText(), "end"));
                    break;
                case SALES:
                default:
                    doc = context.reportService().salesReport(context.session(),
                        parseDate(startField.getText(), "start"), parseDate(endField.getText(), "end"),
                        orderTypeCombo.getValue());
                    break;
            }
            current = doc;
            render(doc);
            exportButton.setDisable(false);
        });
    }

    private void render(ReportDocument doc) {
        reportTitle.setText(doc.getTitle());
        reportArea.getChildren().clear();

        for (java.util.Map.Entry<String, String> param : doc.getParameters().entrySet()) {
            Label label = new Label(param.getKey() + ": " + param.getValue());
            label.getStyleClass().add("hint");
            reportArea.getChildren().add(label);
        }

        for (String line : doc.getSummary()) {
            reportArea.getChildren().add(new Label(line));
        }

        for (ReportDocument.Section section : doc.getSections()) {
            Label heading = new Label(section.getHeading());
            heading.getStyleClass().add("h2");
            reportArea.getChildren().add(heading);
            reportArea.getChildren().add(buildTable(section));
        }
    }

    private TableView<List<String>> buildTable(ReportDocument.Section section) {
        TableView<List<String>> table = new TableView<>();
        List<String> columns = section.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final int index = i;
            TableColumn<List<String>, String> column = new TableColumn<>(columns.get(i));
            column.setCellValueFactory(cell -> {
                List<String> row = cell.getValue();
                return new SimpleStringProperty(index < row.size() ? row.get(index) : "");
            });
            column.setPrefWidth(140);
            table.getColumns().add(column);
        }
        table.setItems(FXCollections.observableArrayList(section.getRows()));
        table.setPrefHeight(220);
        return table;
    }

    private void export() {
        if (current == null) {
            showMessage("Generate a report first.");
            return;
        }
        hideMessage();
        byte[] pdf;
        try {
            pdf = context.reportExporter().export(context.session(), current);
        } catch (RmsException e) {
            showMessage(e.getMessage());
            return;
        }
        try {
            String name = current.getTitle().replaceAll("\\s+", "-").toLowerCase();
            File file = new File(System.getProperty("java.io.tmpdir"), name + ".pdf");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(pdf);
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                showMessage("Report saved to " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            showMessage("The report was generated but could not be opened automatically.");
        }
    }

    private static LocalDate parseDate(String text, String label) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("Enter the " + label + " date as YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            throw new ValidationException("Enter the " + label + " date as YYYY-MM-DD.");
        }
    }

    private void run(Runnable action) {
        hideMessage();
        try {
            action.run();
        } catch (RmsException e) {
            showMessage(e.getMessage());
        } catch (Exception e) {
            showMessage("The report could not be produced.");
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
