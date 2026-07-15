package dao;

import config.DbSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides JDBC {@link Connection}s to the {@code rms} database.
 *
 * <p>Connector/J 8 auto-registers its driver via the JDBC 4 service loader, so
 * no {@code Class.forName(...)} is required. No framework dependencies
 * (Constitution I/II); DAOs and services use this to obtain connections and run
 * their prepared statements.
 *
 * <p>A transaction helper (begin/commit/rollback for atomic financial and stock
 * mutations, Constitution VII) will be added here alongside the first
 * transactional service.
 */
public final class ConnectionFactory {

    private final DbSettings settings;

    /** Uses settings from {@code config/db.properties}. */
    public ConnectionFactory() {
        this(DbSettings.load());
    }

    public ConnectionFactory(DbSettings settings) {
        this.settings = settings;
    }

    /**
     * Opens a new auto-commit connection. The caller owns it and must close it
     * (use try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(settings.url(), settings.user(), settings.password());
    }
}
