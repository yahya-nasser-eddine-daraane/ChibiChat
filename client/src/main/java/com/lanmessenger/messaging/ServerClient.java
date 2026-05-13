package com.lanmessenger.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.lanmessenger.model.GroupRecord;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for talking to the auth server from the messaging layer.
 * Used to look up a contact's LAN IP before connecting to them.
 */
public class ServerClient {

    private final String serverBaseUrl; // e.g. "http://192.168.1.10:8080"
    private final String token;         // session token
    private final Gson   gson = new Gson();

    public ServerClient(String serverBaseUrl, String token) {
        this.serverBaseUrl = serverBaseUrl;
        this.token         = token;
    }

    /**
     * Asks the server for a contact's current LAN address.
     * Returns null if the contact is offline or not in our contact list.
     */
    public PresenceInfo getContactAddress(String username) throws IOException {
        URL url = new URL(serverBaseUrl + "/users/" + username + "/address");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status != 200) return null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, PresenceInfo.class);
        }
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    public List<GroupRecord> getGroups() throws IOException {
        URL url = new URL(serverBaseUrl + "/groups");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, new TypeToken<List<GroupRecord>>(){}.getType());
        }
    }

    public GroupRecord createGroup(String name) throws IOException {
        URL url = new URL(serverBaseUrl + "/groups");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(Map.of("name", name)).getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, GroupRecord.class);
        }
    }

    public List<PresenceInfo> getGroupMembers(String groupId) throws IOException {
        URL url = new URL(serverBaseUrl + "/groups/" + groupId + "/members");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(reader, new TypeToken<List<PresenceInfo>>(){}.getType());
        }
    }

    public void inviteToGroup(String groupId, String username) throws IOException {
        URL url = new URL(serverBaseUrl + "/groups/" + groupId + "/members");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(Map.of("username", username)).getBytes(StandardCharsets.UTF_8));
        }
        
        if (conn.getResponseCode() >= 400) {
             throw new IOException("Failed to invite member: " + conn.getResponseCode());
        }
    }

    public record PresenceInfo(
        String userId,
        String username,
        String displayName,
        String lanIp,
        int    tcpPort
    ) {}
}
