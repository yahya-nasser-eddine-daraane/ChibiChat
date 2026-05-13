package com.lanmessenger.db;

import com.lanmessenger.model.PresenceRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GroupRepository {

    private final DatabaseConfig db;

    public GroupRepository(DatabaseConfig db) {
        this.db = db;
    }

    public String createGroup(String name, String creatorId) throws SQLException {
        String sql = "INSERT INTO Groups (name, created_by) OUTPUT INSERTED.group_id VALUES (?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String groupId = rs.getString(1);
                    addMember(groupId, creatorId);
                    return groupId;
                }
                throw new SQLException("Group creation failed");
            }
        }
    }

    public void addMember(String groupId, String userId) throws SQLException {
        String sql = "IF NOT EXISTS (SELECT 1 FROM GroupMembers WHERE group_id = ? AND user_id = ?) " +
                     "INSERT INTO GroupMembers (group_id, user_id) VALUES (?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, userId);
            ps.setString(3, groupId);
            ps.setString(4, userId);
            ps.executeUpdate();
        }
    }

    public List<GroupInfo> getGroupsForUser(String userId) throws SQLException {
        String sql = "SELECT g.group_id, g.name, g.created_by, g.created_at " +
                     "FROM Groups g JOIN GroupMembers gm ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ? ORDER BY g.created_at DESC";
        List<GroupInfo> groups = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(new GroupInfo(
                        rs.getString("group_id"),
                        rs.getString("name"),
                        rs.getString("created_by")
                    ));
                }
            }
        }
        return groups;
    }

    public List<PresenceRecord> getGroupMembers(String groupId) throws SQLException {
        String sql = "SELECT u.user_id, u.username, u.display_name, u.lan_ip, u.tcp_port, u.last_seen " +
                     "FROM Users u JOIN GroupMembers gm ON u.user_id = gm.user_id " +
                     "WHERE gm.group_id = ?";
        List<PresenceRecord> members = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(new PresenceRecord(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("lan_ip"),
                        rs.getInt("tcp_port"),
                        rs.getTimestamp("last_seen") != null ? rs.getTimestamp("last_seen").toInstant().toString() : null
                    ));
                }
            }
        }
        return members;
    }

    public static record GroupInfo(String groupId, String name, String createdBy) {}
}
