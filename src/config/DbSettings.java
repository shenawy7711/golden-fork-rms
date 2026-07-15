package config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Database connection settings, loaded from {@code config/db.properties}.
 *
 * <p>The properties file holds real credentials and is git-ignored; copy
 * {@code config/db.properties.example} to {@code config/db.properties} and fill
 * in your MySQL user/password. No framework dependencies (Constitution I/II).
 */
public final class DbSettings {

    private final String url;
    private final String user;
    private final String password;

    private DbSettings(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Loads settings from the default location {@code config/db.properties}. */
    public static DbSettings load() {
        return load(Paths.get("config", "db.properties"));
    }

    /** Loads settings from a specific properties file. */
    public static DbSettings load(Path path) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Cannot read " + path.toAbsolutePath()
                + " - copy config/db.properties.example to config/db.properties "
                + "and fill in your credentials.", e);
        }
        return new DbSettings(
            require(p, "db.url"),
            require(p, "db.user"),
            p.getProperty("db.password", ""));
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Missing required property '" + key + "' in db.properties");
        }
        return v.trim();
    }

    public String url()      { return url; }
    public String user()     { return user; }
    public String password() { return password; }
}
