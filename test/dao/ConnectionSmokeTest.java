package dao;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connectivity smoke test: proves the toolchain is wired end-to-end
 * (Maven -> Connector/J -> the seeded {@code rms} database).
 *
 * <p>Requires MySQL running and {@code config/db.properties} filled in.
 * Run with: {@code mvn test}
 */
class ConnectionSmokeTest {

    @Test
    void connectsAndReadsSeededReferenceData() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        try (Connection c = factory.getConnection();
             Statement st = c.createStatement()) {

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM role")) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "expected the 3 seeded roles");
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM user_account WHERE status = 'Active'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "expected the seeded admin account");
            }
        }
    }
}
