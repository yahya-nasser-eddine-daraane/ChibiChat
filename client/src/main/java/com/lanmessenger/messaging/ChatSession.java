package com.lanmessenger.messaging;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Represents one open TCP connection to a peer.
 *
 * Messages are sent as newline-delimited JSON (one JSON object per line).
 * This makes parsing simple — read a line, parse it, done.
 *
 * One ChatSession = one contact connection.
 * The MessageRouter keeps a map of userId → ChatSession.
 */
public class ChatSession {

    private final String        peerId;       // the other user's userId
    private final String        peerUsername;
    private final Socket        socket;
    private final PrintWriter   writer;
    private final Gson          gson = new Gson();

    private Consumer<ChatMessage> onMessage;  // callback when a message arrives
    private Runnable              onClose;    // callback when connection drops

    private volatile boolean running = false;
    private Thread readerThread;

    public ChatSession(String peerId, String peerUsername,
                       Socket socket) throws IOException {
        this.peerId      = peerId;
        this.peerUsername = peerUsername;
        this.socket      = socket;

        // Auto-flush writer — each println() sends immediately
        this.writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true
        );
    }

    /** Starts the background reader thread. Call after setting listeners. */
    public void start() {
        running = true;
        readerThread = new Thread(this::readLoop, "session-" + peerUsername);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** Sends a message to this peer. Thread-safe. */
    public synchronized void send(ChatMessage message) {
        if (!isConnected()) return;
        writer.println(gson.toJson(message));
    }

    /** Closes this session cleanly. */
    public void close() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
        if (readerThread != null) readerThread.interrupt();
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed() && socket.isConnected();
    }

    // ── Internal reader loop ──────────────────────────────────────────────────

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    ChatMessage msg = gson.fromJson(line, ChatMessage.class);
                    if (msg == null) continue;

                    // Handle pings inline — don't bubble up to the app
                    if (msg.getType() == ChatMessage.Type.PING) {
                        send(ChatMessage.pong());
                        continue;
                    }
                    if (msg.getType() == ChatMessage.Type.PONG) continue;

                    if (onMessage != null) onMessage.accept(msg);

                } catch (Exception e) {
                    System.err.printf("[Session:%s] Parse error: %s%n", peerUsername, e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.printf("[Session:%s] Connection lost: %s%n", peerUsername, e.getMessage());
            }
        } finally {
            running = false;
            if (onClose != null) onClose.run();
        }
    }

    // ── Setters & Getters ─────────────────────────────────────────────────────

    public void setOnMessage(Consumer<ChatMessage> onMessage) { this.onMessage = onMessage; }
    public void setOnClose(Runnable onClose)                   { this.onClose   = onClose;   }
    public String getPeerId()       { return peerId;       }
    public String getPeerUsername() { return peerUsername; }
}
