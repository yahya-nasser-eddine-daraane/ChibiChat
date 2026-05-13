 package com.lanmessenger.db;

import com.lanmessenger.model.ContactRecord;
import com.lanmessenger.model.UserRecord;
import com.lanmessenger.model.PresenceRecord;
import com.lanmessenger.util.TokenUtil;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    private final DatabaseConfig db;

    public UserRepository(DatabaseConfig db) {
        this.db = db;
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    /** Returns true if username is already taken (case-insensitive). */
    public boolean usernameExists(String username) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM Users WHERE LOWER(username) = LOWER(?)")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Creates a new user. Returns the generated UUID. */
    public String createUser(String username, String displayName,
                             String passwordHash, String passwordSalt) throws SQLException {
        String sql = "INSERT INTO Users (username, display_name, password_hash, password_salt) " +
                     "OUTPUT INSERTED.user_id VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, displayName);
            ps.setString(3, passwordHash);
            ps.setString(4, passwordSalt);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                throw new SQLException("Insert returned no ID");
            }
        }
    }

    /** Finds a user by username (case-insensitive). */
    public Optional<UserRecord> findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, display_name, password_hash, " +
                     "password_salt, public_key FROM Users WHERE LOWER(username) = LOWER(?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    /** Finds a user by UUID. */
    public Optional<UserRecord> findById(String userId) throws SQLException {
        String sql = "SELECT user_id, username, display_name, password_hash, " +
                     "password_salt, public_key FROM Users WHERE user_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public void updateLastSeen(String userId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Users SET last_seen = GETUTCDATE() WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }
    
        /** Stores the user's current LAN IP and TCP listening port. Called on login. */
    public void updatePresence(String userId, String lanIp, int tcpPort) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Users SET lan_ip = ?, tcp_port = ?, last_seen = GETUTCDATE() " +
                     "WHERE user_id = ?")) {
            ps.setString(1, lanIp);
            ps.setInt(2, tcpPort);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    /** Clears presence on logout so contacts see the user as offline. */
    public void clearPresence(String userId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Users SET lan_ip = NULL, tcp_port = NULL WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the LAN address of a user by username.
     * Only returns a result if the requester has that user in their contacts.
     * This prevents strangers from looking up each other's IPs.
     */
    public Optional<PresenceRecord> getPresence(String requesterUserId,
                                                String targetUsername) throws SQLException {
        String sql =
            "SELECT u.user_id, u.username, u.display_name, u.lan_ip, u.tcp_port, u.last_seen " +
            "FROM Users u " +
            "JOIN Contacts c ON c.contact_user_id = u.user_id " +
            "WHERE c.owner_id = ? AND LOWER(u.username) = LOWER(?) " +
            "AND u.lan_ip IS NOT NULL AND u.tcp_port IS NOT NULL";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, requesterUserId);
            ps.setString(2, targetUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PresenceRecord(
                    rs.getString("user_id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("lan_ip"),
                    rs.getInt("tcp_port"),
                    rs.getTimestamp("last_seen") != null
                        ? rs.getTimestamp("last_seen").toInstant().toString() : null
                ));
            }
        }
    }


    private UserRecord mapUser(ResultSet rs) throws SQLException {
        return new UserRecord(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            rs.getString("password_salt"),
            rs.getString("public_key")
        );
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    public String createSession(String userId) throws SQLException {
        String raw   = TokenUtil.generateToken();
        String hash  = TokenUtil.hash(raw);
        Timestamp exp = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO Sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, hash);
            ps.setTimestamp(3, exp);
            ps.executeUpdate();
        }
        return raw;
    }

    public Optional<String> validateSession(String rawToken) throws SQLException {
        String hash = TokenUtil.hash(rawToken);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id FROM Sessions WHERE token_hash = ? AND expires_at > GETUTCDATE()")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String userId = rs.getString("user_id");
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE Sessions SET last_used = GETUTCDATE() WHERE token_hash = ?")) {
                    upd.setString(1, hash);
                    upd.executeUpdate();
                }
                return Optional.of(userId);
            }
        }
    }

    public void deleteSession(String rawToken) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM Sessions WHERE token_hash = ?")) {
            ps.setString(1, TokenUtil.hash(rawToken));
            ps.executeUpdate();
        }
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    /**
     * Adds a contact by @username.
     * Returns false if that username doesn't exist.
     */
    public boolean addContact(String ownerUserId, String contactUsername,
                              String nickname) throws SQLException {
        Optional<UserRecord> contact = findByUsername(contactUsername);
        if (contact.isEmpty()) return false;
        String contactId = contact.get().userId();

        String sql = "IF NOT EXISTS " +
                     "(SELECT 1 FROM Contacts WHERE owner_id = ? AND contact_user_id = ?) " +
                     "INSERT INTO Contacts (owner_id, contact_user_id, nickname) VALUES (?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            ps.setString(2, contactId);
            ps.setString(3, ownerUserId);
            ps.setString(4, contactId);
            ps.setString(5, nickname);
            ps.executeUpdate();
        }
        return true;
    }

    public List<ContactRecord> getContacts(String ownerUserId) throws SQLException {
        String sql = "SELECT u.user_id, u.username, u.display_name, u.public_key, " +
             "u.last_seen, c.nickname " +
             "FROM Contacts c JOIN Users u ON c.contact_user_id = u.user_id " +
             "WHERE c.owner_id = ? ORDER BY COALESCE(c.nickname, u.display_name)";
        List<ContactRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
        list.add(new ContactRecord(
    rs.getString("user_id"),
    rs.getString("username"),
    rs.getString("display_name"),
    rs.getString("nickname"),
    rs.getString("public_key"),
    null,
    0,
    rs.getTimestamp("last_seen") != null
        ? rs.getTimestamp("last_seen").toInstant().toString() : null
));
                }
            }
        }
        return list;
    }
    
    

    public void removeContact(String ownerUserId, String contactUserId) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM Contacts WHERE owner_id = ? AND contact_user_id = ?")) {
            ps.setString(1, ownerUserId);
            ps.setString(2, contactUserId);
            ps.executeUpdate();
        }
    }
    
    public List<String> getOnlineContactIds(String ownerUserId,
                                         int withinSeconds) throws SQLException {
    String sql =
        "SELECT u.user_id FROM Contacts c " +
        "JOIN Users u ON c.contact_user_id = u.user_id " +
        "WHERE c.owner_id = ? " +
        "AND u.last_seen >= DATEADD(SECOND, ?, GETUTCDATE())";
    List<String> ids = new ArrayList<>();
    try (Connection c = db.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, ownerUserId);
        ps.setInt(2, -withinSeconds); // negative = subtract seconds
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("user_id"));
        }
    }
    return ids;
}
}
