package com.lanmessenger.store;

import com.lanmessenger.messaging.ChatMessage;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads chat messages from the local SQL Server database.
 *
 * Used by ChatPane to:
 *   - Save every sent/received message immediately
 *   - Load conversation history when opening a chat
 *   - Mark messages as delivered when receipt arrives
 */
public class MessageStore {

    private final LocalDatabase db;

    public MessageStore(LocalDatabase db) {
        this.db = db;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Saves an outgoing message (direction = OUT).
     * Call this right after the user hits send.
     */
    public void saveOutgoing(ChatMessage msg, String contactId, String contactUsername) {
        save(msg, contactId, contactUsername, "OUT");
    }

    /**
     * Saves an incoming message (direction = IN).
     * Call this when a message arrives from a peer.
     */
    public void saveIncoming(ChatMessage msg) {
        save(msg, msg.getFromUserId(), msg.getFromUsername(), "IN");
    }

    private void save(ChatMessage msg, String contactId,
                      String contactUsername, String direction) {
        String sql =
            "IF NOT EXISTS (SELECT 1 FROM Messages WHERE message_id = ?) " +
            "INSERT INTO Messages " +
            "(message_id, contact_id, contact_name, direction, msg_type, " +
            " content, file_name, mime_type, file_size, sticker_id, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            String type = msg.getType().name();

            // For images/files, store content only if under 5MB to avoid DB bloat
            // Large files stored as null with a note in file_name
            String content = msg.getContent();
            if (content != null && content.length() > 5_000_000) {
                content = null; // too large to store — file_name still saved
            }

            ps.setString(1,  msg.getMessageId()); // EXISTS check
            ps.setString(2,  msg.getMessageId());
            ps.setString(3,  contactId);
            ps.setString(4,  contactUsername);
            ps.setString(5,  direction);
            ps.setString(6,  type);
            ps.setString(7,  content);
            ps.setString(8,  msg.getFileName());
            ps.setString(9,  msg.getMimeType());
            if (msg.getFileSize() > 0)
                ps.setLong(10, msg.getFileSize());
            else
                ps.setNull(10, Types.BIGINT);
            ps.setString(11, msg.getStickerId());
            ps.setTimestamp(12, Timestamp.from(Instant.ofEpochMilli(msg.getTimestamp())));

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[MessageStore] Save error: " + e.getMessage());
        }
    }

    // ── Mark delivered ────────────────────────────────────────────────────────

    /**
     * Marks a message as delivered when we receive a delivery receipt.
     */
    public void markDelivered(String messageId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE Messages SET delivered = 1 WHERE message_id = ?")) {
            ps.setString(1, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageStore] Deliver error: " + e.getMessage());
        }
    }

    // ── Load history ──────────────────────────────────────────────────────────

    /**
     * Loads the last N messages for a conversation, oldest first.
     * Call this when opening a chat pane to restore history.
     */
    public List<StoredMessage> loadHistory(String contactId, int limit) {
        String sql =
            "SELECT TOP (?) message_id, direction, msg_type, content, " +
            "file_name, mime_type, file_size, sticker_id, delivered, timestamp " +
            "FROM (" +
            "  SELECT TOP (?) message_id, direction, msg_type, content, " +
            "  file_name, mime_type, file_size, sticker_id, delivered, timestamp " +
            "  FROM Messages WHERE contact_id = ? ORDER BY timestamp DESC" +
            ") sub ORDER BY timestamp ASC";

        List<StoredMessage> history = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, limit);
            ps.setString(3, contactId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new StoredMessage(
                        rs.getString("message_id"),
                        rs.getString("direction"),
                        rs.getString("msg_type"),
                        rs.getString("content"),
                        rs.getString("file_name"),
                        rs.getString("mime_type"),
                        rs.getLong("file_size"),
                        rs.getString("sticker_id"),
                        rs.getBoolean("delivered"),
                        rs.getTimestamp("timestamp").toInstant().toEpochMilli()
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MessageStore] Load error: " + e.getMessage());
        }
        return history;
    }

    // ── StoredMessage record ──────────────────────────────────────────────────

    public record StoredMessage(
        String  messageId,
        String  direction,   // OUT or IN
        String  type,        // TEXT IMAGE FILE STICKER
        String  content,
        String  fileName,
        String  mimeType,
        long    fileSize,
        String  stickerId,
        boolean delivered,
        long    timestamp
    ) {
        public boolean isOutgoing() { return "OUT".equals(direction); }
    }
}
