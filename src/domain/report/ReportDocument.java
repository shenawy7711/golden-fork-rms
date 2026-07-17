package domain.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rendered report, held in a neutral form that both the on-screen view and the PDF exporter
 * consume — so an export reproduces exactly what was displayed (FR-30, SC-007).
 *
 * <p>A document is a title, an ordered set of {@link Section}s (each a titled table with column
 * headers and string rows), and a list of summary lines. The generating user, timestamp, and applied
 * parameters live alongside so the exporter can stamp them into the header. This POJO carries no
 * {@code javafx.*} — it is produced by {@code ReportService} and reused by the future web tier
 * (Principle I).
 */
public final class ReportDocument {

    private final String title;
    private final List<Section> sections = new ArrayList<>();
    private final List<String> summary = new ArrayList<>();
    private final Map<String, String> parameters = new LinkedHashMap<>();

    public ReportDocument(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public ReportDocument addParameter(String key, String value) {
        parameters.put(key, value);
        return this;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Section addSection(String heading, List<String> columns) {
        Section section = new Section(heading, columns);
        sections.add(section);
        return section;
    }

    public List<Section> getSections() {
        return sections;
    }

    public ReportDocument addSummary(String line) {
        summary.add(line);
        return this;
    }

    public List<String> getSummary() {
        return summary;
    }

    /** A titled table: column headers plus string rows. */
    public static final class Section {
        private final String heading;
        private final List<String> columns;
        private final List<List<String>> rows = new ArrayList<>();

        Section(String heading, List<String> columns) {
            this.heading = heading;
            this.columns = columns;
        }

        public String getHeading() {
            return heading;
        }

        public List<String> getColumns() {
            return columns;
        }

        public Section addRow(List<String> row) {
            rows.add(row);
            return this;
        }

        public List<List<String>> getRows() {
            return rows;
        }
    }
}
