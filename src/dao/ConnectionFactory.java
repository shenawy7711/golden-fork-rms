package dao;

import config.DbSettings;
import service.exception.PersistenceException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides JDBC {@link Connection}s to the {@code rms} database, plus the
 * transaction helper that makes multi-DAO service operations atomic.
 *
 * <p>Connector/J 8 auto-registers its driver via the JDBC 4 service loader, so
 * no {@code Class.forName(...)} is required. No framework dependencies
 * (Constitution I/II); DAOs and services use this to obtain connections and run
 * their prepared statements.
 *
 * <p>{@link #inTransaction} is the single entry point for atomic financial and
 * stock mutations (Constitution VII): order finalisation (BR-19) and delivery
 * receipt (BR-24) commit every row together or leave the database untouched.
 * DAOs take the supplied {@link Connection} so all their statements enlist in
 * the same transaction.
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

    /**
     * Runs {@code work} inside a single transaction and returns its result: opens a connection with
     * {@code autoCommit=false}, commits when {@code work} returns, rolls back if it throws anything.
     *
     * <p>A business failure thrown by {@code work} ({@link service.exception.RmsException} and its subtypes — a
     * validation error, a rejected state change) rolls back and propagates unchanged, so callers
     * still map it to the FRD message. A {@link SQLException} rolls back and surfaces as
     * {@link PersistenceException} (NFR-03: no partial data is kept either way).
     */
    public <T> T inTransaction(TransactionalWork<T> work) {
        Connection connection = null;
        try {
            connection = getConnection();
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Throwable failure) {
                rollbackQuietly(connection, failure);
                throw failure;
            }
        } catch (SQLException e) {
            throw new PersistenceException("The operation could not be completed. No changes were saved.", e);
        } catch (RuntimeException | Error e) {
            // Covers RmsException (unchecked base): business failures keep their FRD message.
            throw e;
        } finally {
            closeQuietly(connection);
        }
    }

    /** {@link #inTransaction} for work that produces no value. */
    public void inTransaction(VoidTransactionalWork work) {
        inTransaction(connection -> {
            work.execute(connection);
            return null;
        });
    }

    // A rollback failure must not mask the original cause — attach it and let the real one surface.
    private static void rollbackQuietly(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
            // The transaction already committed or rolled back; a close failure changes nothing.
        }
    }

    /** Transactional work returning a value; see {@link #inTransaction(TransactionalWork)}. */
    @FunctionalInterface
    public interface TransactionalWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    /** Transactional work returning nothing; see {@link #inTransaction(VoidTransactionalWork)}. */
    @FunctionalInterface
    public interface VoidTransactionalWork {
        void execute(Connection connection) throws SQLException;
    }
}
