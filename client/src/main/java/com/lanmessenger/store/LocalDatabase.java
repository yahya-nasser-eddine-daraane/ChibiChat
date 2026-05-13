package com.lanmessenger.store;

import java.sql.*;

/**
 * Manages the client's personal SQL Server database.
 *
 * Database name: ChibiChat_<username>  e.g. ChibiChat_alice
 *
 * On first login the app:
 *   1. Connects to SQL Server (master db)
 *   2. Creates ChibiChat_<username> if it doesn't exist
 *   3. Creates the Messages table if it doesn't exist
 *   4. Keeps the connection open for the session
 */
public class LocalDatabase {

    private final String host;
    private final int    port;
    private final String username; // SQL Server username
    private final String password;
    private final String dbName;   // ChibiChat_<chatUsername>

    private Connection connection;

    public LocalDatabase(String host, int port,
                         String sqlUsername, String sqlPassword,
                         String chatUsername) {
        this.host     = host;
        this.port     = port;
        this.username = sqlUsername;
        this.password = sqlPassword;
        this.dbName   = "ChibiChat_" + chatUsername.toLowerCase()
                        .replaceAll("[^a-z0-9_]", "_"); // safe DB name
    }

    /**
     * Connects to SQL Server, creates the database and schema if needed.
     * Call this once after login succeeds.
     */
    public void init() throws SQLException {
        createDatabaseIfNeeded();
        openConnection();
        createSchemaIfNeeded();
        System.out.println("[LocalDB] Ready: " + dbName);
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void createDatabaseIfNeeded() throws SQLException {
        // Connect to master to run CREATE DATABASE
        String masterUrl = buildUrl("master");
        try (Connection c = DriverManager.getConnection(masterUrl, username, password);
             Statement s = c.createStatement()) {
            s.execute(
                "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = '" + dbName + "') " +
                "CREATE DATABASE [" + dbName + "]"
            );
        }
    }

    private void openConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return;
        connection = DriverManager.getConnection(buildUrl(dbName), username, password);
        connection.setAutoCommit(true);
    }

    private void createSchemaIfNeeded() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(
                "IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Messages' AND xtype='U') " +
                "CREATE TABLE Messages (" +
                "  id           UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY," +
                "  message_id   VARCHAR(64)      NOT NULL UNIQUE," +
                "  contact_id   VARCHAR(64)      NOT NULL," +
                "  contact_name NVARCHAR(100)    NOT NULL," +
                "  sender_id    VARCHAR(64)      NULL," +   // NEW: for group chats
                "  sender_name  NVARCHAR(100)    NULL," +   // NEW
                "  direction    VARCHAR(4)       NOT NULL," +  // OUT or IN
                "  msg_type     VARCHAR(10)      NOT NULL," +  // TEXT IMAGE FILE STICKER
                "  content      NVARCHAR(MAX)    NULL," +
                "  file_name    NVARCHAR(260)    NULL," +
                "  mime_type    VARCHAR(100)     NULL," +
                "  file_size    BIGINT           NULL," +
                "  sticker_id   NVARCHAR(10)     NULL," +
                "  delivered    BIT              DEFAULT 0," +
                "  timestamp    DATETIME2        NOT NULL," +
                "  created_at   DATETIME2        DEFAULT GETUTCDATE()" +
                ")"
            );

            // Migration: Add sender_id and sender_name if they don't exist
            s.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Messages') AND name = 'sender_id') " +
                      "ALTER TABLE Messages ADD sender_id VARCHAR(64) NULL");
            s.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('Messages') AND name = 'sender_name') " +
                      "ALTER TABLE Messages ADD sender_name NVARCHAR(100) NULL");
            s.execute(
                "IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_Messages_Contact') " +
                "CREATE INDEX IX_Messages_Contact ON Messages(contact_id, timestamp)"
            );
        }
    }

    private String buildUrl(String db) {
        return String.format(
            "jdbc:sqlserver://%s:%d;databaseName=%s;" +
            "encrypt=true;trustServerCertificate=true;loginTimeout=10;",
            host, port, db
        );
    }

    // ── Connection access ─────────────────────────────────────────────────────

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            openConnection();
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }
}
