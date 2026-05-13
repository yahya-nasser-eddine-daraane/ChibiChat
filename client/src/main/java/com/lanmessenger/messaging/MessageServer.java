package com.lanmessenger.messaging;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Listens for incoming TCP connections from other clients.
 *
 * Port selection strategy:
 *   Pass port 0 → OS picks any available port automatically.
 *   Call getPort() after start() to find out which port was assigned.
 *   That port is then sent to the auth server on login so contacts
 *   can reach us — no hardcoded ports, no conflicts.
 */
public class MessageServer {

    // Preferred port — used as a hint, falls back to random if taken
    public static final int PREFERRED_PORT = 54322;

    private Consumer<ChatSession> onNewSession;
    private ServerSocket                serverSocket;
    private volatile boolean            running = false;
    private Thread                      acceptThread;
    private int                         assignedPort = -1;

    public MessageServer(Consumer<ChatSession> onNewSession) {
        this.onNewSession = onNewSession;
    }

    public void start() throws IOException {
        // Try the preferred port first; if it's taken fall back to OS-assigned
        try {
            serverSocket = new ServerSocket(PREFERRED_PORT);
        } catch (IOException e) {
            serverSocket = new ServerSocket(0); // 0 = let OS pick a free port
        }

        assignedPort = serverSocket.getLocalPort();
        running      = true;

        acceptThread = new Thread(this::acceptLoop, "msg-server");
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.printf("[MessageServer] Listening on port %d%n", assignedPort);
    }

    /**
     * Swaps the session handler after the server has already started.
     * Used to replace the placeholder handler with the real router
     * once login succeeds and we have a valid token.
     */
    public void rewireHandler(Consumer<ChatSession> newHandler) {
        this.onNewSession = newHandler;
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (acceptThread != null) acceptThread.interrupt();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                System.out.printf("[MessageServer] Incoming connection from %s%n",
                        socket.getInetAddress().getHostAddress());
                try {
                    ChatSession session = new ChatSession("unknown", "unknown", socket);
                    onNewSession.accept(session);
                } catch (IOException e) {
                    System.err.println("[MessageServer] Session error: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) System.err.println("[MessageServer] Accept error: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the actual port the server is listening on.
     * Always call this AFTER start() — before start() it returns -1.
     */
    public int getPort() {
        return assignedPort;
    }
}
