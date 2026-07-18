package controller;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * Renders a status-text column as a coloured pill (T-design), matching the reference mockups: green
 * for healthy states, amber for holds/low, red for problems, slate for neutral. Purely presentational
 * — the underlying value is still the plain status string the value factory supplies.
 */
final class StatusPill {

    private StatusPill() {}

    /** A standalone pill label for card lists and detail headers (same colour rules as columns). */
    static Label make(String value, String... extraStyles) {
        Label pill = new Label(value);
        pill.getStyleClass().addAll("pill", variantFor(value));
        pill.getStyleClass().addAll(extraStyles);
        return pill;
    }

    /** Turns a {@code TableColumn<S,String>} of status text into a pill column. */
    static <S> void apply(TableColumn<S, String> column) {
        column.setCellFactory(col -> new TableCell<S, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.trim().isEmpty()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label pill = new Label(value);
                pill.getStyleClass().addAll("pill", variantFor(value));
                setGraphic(pill);
                setText(null);
            }
        });
    }

    /** Maps a status word to one of the four pill variants defined in app.css. */
    private static String variantFor(String value) {
        String s = value.toLowerCase();
        if (s.contains("free") || s.contains("in stock") || s.equals("active")
            || s.contains("seated") || s.contains("completed") || s.contains("received")
            || s.contains("paid")) {
            return "good";
        }
        if (s.contains("reserved") || s.contains("booked") || s.contains("low")
            || s.contains("partially") || s.contains("pending")) {
            return "warn";
        }
        if (s.contains("occupied") || s.contains("no-show") || s.contains("cancelled")
            || s.contains("out")) {
            return "danger";
        }
        return "neutral"; // needs cleaning, inactive, ordered, and anything else
    }
}
