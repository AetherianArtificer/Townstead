package com.aetherianartificer.townstead.chronicle.store;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Bootstraps the bundled SQLite JDBC driver and owns connection creation for the
 * chronicle archive. The driver class is loaded directly rather than through
 * {@link java.sql.DriverManager} service discovery, which does not see nested-jar
 * providers under the mod-loader module layers.
 */
public final class ChronicleSqlite {

    private static volatile Driver driver;

    private ChronicleSqlite() {
    }

    public static Connection open(String url) throws Exception {
        Driver d = driver;
        if (d == null) {
            synchronized (ChronicleSqlite.class) {
                d = driver;
                if (d == null) {
                    d = (Driver) Class.forName("org.sqlite.JDBC")
                            .getDeclaredConstructor().newInstance();
                    driver = d;
                }
            }
        }
        Connection c = d.connect(url, new Properties());
        if (c == null) {
            throw new IllegalStateException("SQLite driver rejected url: " + url);
        }
        return c;
    }

    /**
     * Debug-gated startup check that the nested driver and its native library
     * actually load in this environment (loader, launcher, OS).
     */
    public static void smokeTest(Logger log) {
        try (Connection c = open("jdbc:sqlite::memory:");
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE smoke(id INTEGER PRIMARY KEY, v TEXT)");
            s.executeUpdate("INSERT INTO smoke(v) VALUES ('ok')");
            try (ResultSet r = s.executeQuery("SELECT v FROM smoke")) {
                if (r.next() && "ok".equals(r.getString(1))) {
                    log.info("[Chronicles] SQLite smoke test passed (sqlite {})",
                            c.getMetaData().getDatabaseProductVersion());
                    return;
                }
            }
            log.warn("[Chronicles] SQLite smoke test returned an unexpected result");
        } catch (Throwable t) {
            log.error("[Chronicles] SQLite smoke test failed; chronicle archive will be unavailable", t);
        }
    }
}
