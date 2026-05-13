package com.lanmessenger.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages the JDBC connection to SQL Server.
 *
 * For a LAN app, a simple single-connection approach is fine.
 * We use synchronized methods to prevent concurrent access issues.
 *
 * Connection string format:
 *   jdbc:sqlserver://HOST:1433;databaseName=LanMessenger;encrypt=true;trustServerCertificate=true
 *
 * Set these in config.properties (see AuthServer for loading).
 */
public class DatabaseConfig {

    private final String url;
    private final String username;
    private final String password;

    private Connection connection;

    public DatabaseConfig(String host, String port, String dbName,
                          String username, String password) {
        this.url = String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;" +
            "encrypt=true;trustServerCertificate=true;loginTimeout=10;",
            host, port, dbName
        );
        this.username = username;
        this.password = password;
    }

    /**
     * Returns an open connection, reconnecting if the previous one dropped.
     * Synchronized so multiple handler threads don't race on the connection.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            System.out.println("[DB] Connecting to SQL Server...");
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            connection = DriverManager.getConnection(url, props);
            connection.setAutoCommit(true);
            System.out.println("[DB] Connected.");
        }
        return connection;
    }

    /** Call this on shutdown to cleanly close the connection. */
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DB] Connection closed.");
            } catch (SQLException ignored) {}
        }
    }
}
