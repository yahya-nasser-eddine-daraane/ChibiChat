package com.lanmessenger.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Runs in the background every 10 seconds:
 *   1. Sends POST /auth/heartbeat  → tells server we are online
 *   2. Sends GET  /contacts/online → gets list of online contact IDs
 *   3. Notifies the UI to update online dots
 */
public class PresenceService {

    private final String   serverUrl;
    private final String   token;
    private final Gson     gson = new Gson();

    /** Called with the set of currently-online contact userIds */
    private Consumer<Set<String>> onOnlineStatusUpdate;

    private ScheduledExecutorService scheduler;

    public PresenceService(String serverUrl, String token) {
        this.serverUrl = serverUrl;
        this.token     = token;
    }

    public void setOnOnlineStatusUpdate(Consumer<Set<String>> handler) {
        this.onOnlineStatusUpdate = handler;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presence-service");
            t.setDaemon(true);
            return t;
        });

        // Run immediately then every 10 seconds
        scheduler.scheduleAtFixedRate(this::tick, 0, 10, TimeUnit.SECONDS);
        System.out.println("[Presence] Service started.");
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    private void tick() {
        sendHeartbeat();
        Set<String> onlineIds = fetchOnlineContacts();
        if (onlineIds != null && onOnlineStatusUpdate != null) {
            Platform.runLater(() -> onOnlineStatusUpdate.accept(onlineIds));
        }
    }

    private void sendHeartbeat() {
        try {
            HttpURLConnection conn = open("POST", "/auth/heartbeat");
            conn.setDoOutput(true);
            conn.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            conn.getResponseCode(); // fire and forget
        } catch (IOException ignored) {}
    }

    private Set<String> fetchOnlineContacts() {
        try {
            HttpURLConnection conn = open("GET", "/contacts/online");
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> ids = gson.fromJson(r, new TypeToken<List<String>>(){}.getType());
                return new HashSet<>(ids);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private HttpURLConnection open(String method, String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
            new URL(serverUrl + path).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }
}
