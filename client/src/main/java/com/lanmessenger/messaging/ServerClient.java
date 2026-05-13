package com.lanmessenger.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
            JsonObject obj = gson.fromJson(reader, JsonObject.class);
            return new PresenceInfo(
                obj.get("userId").getAsString(),
                obj.get("username").getAsString(),
                obj.get("displayName").getAsString(),
                obj.get("lanIp").getAsString(),
                obj.get("tcpPort").getAsInt()
            );
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
