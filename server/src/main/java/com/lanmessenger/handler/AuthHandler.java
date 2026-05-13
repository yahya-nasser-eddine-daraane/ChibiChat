package com.lanmessenger.handler;

import com.google.gson.JsonObject;
import com.lanmessenger.db.UserRepository;
import com.lanmessenger.model.UserRecord;
import com.lanmessenger.util.JsonUtil;
import com.lanmessenger.util.PasswordUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * POST /auth/register  { "username", "displayName", "password" }
 * POST /auth/login     { "username", "password", "tcpPort" }
 * POST /auth/logout    Authorization: Bearer <token>
 */
public class AuthHandler implements HttpHandler {

    private final UserRepository repo;

    public AuthHandler(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("POST".equals(method)) {
                switch (path) {
                    case "/auth/register" -> handleRegister(exchange);
                    case "/auth/login"    -> handleLogin(exchange);
                    case "/auth/logout"   -> handleLogout(exchange);
                    default -> JsonUtil.sendError(exchange, 404, "Unknown endpoint");
                }
            } else {
                JsonUtil.sendError(exchange, 405, "Method not allowed");
            }
        } catch (SQLException e) {
            System.err.println("[AuthHandler] DB error: " + e.getMessage());
            JsonUtil.sendError(exchange, 500, "Server error");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException, SQLException {
        JsonObject body = JsonUtil.readBody(exchange, JsonObject.class);
        if (body == null || !body.has("username") || !body.has("displayName") || !body.has("password")) {
            JsonUtil.sendError(exchange, 400, "username, displayName and password are required");
            return;
        }

        String username    = body.get("username").getAsString().trim().toLowerCase();
        String displayName = body.get("displayName").getAsString().trim();
        String password    = body.get("password").getAsString();

        if (!username.matches("[a-z0-9_]{3,32}")) {
            JsonUtil.sendError(exchange, 400,
                "Username must be 3-32 characters: letters, numbers, underscores only");
            return;
        }
        if (password.length() < 8) {
            JsonUtil.sendError(exchange, 400, "Password must be at least 8 characters");
            return;
        }
        if (repo.usernameExists(username)) {
            JsonUtil.sendError(exchange, 409, "Username @" + username + " is already taken");
            return;
        }

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(password, salt);
        repo.createUser(username, displayName, hash, salt);

        System.out.printf("[Auth] Registered: @%s%n", username);
        JsonUtil.sendMessage(exchange, 201, "Registered successfully. You can now log in.");
    }

    private void handleLogin(HttpExchange exchange) throws IOException, SQLException {
        JsonObject body = JsonUtil.readBody(exchange, JsonObject.class);
        if (body == null || !body.has("username") || !body.has("password")) {
            JsonUtil.sendError(exchange, 400, "username and password are required");
            return;
        }

        String username = body.get("username").getAsString().trim().toLowerCase();
        String password = body.get("password").getAsString();

        // tcpPort: the port this client will listen on for incoming messages
        int tcpPort = body.has("tcpPort") ? body.get("tcpPort").getAsInt() : 54322;

        Optional<UserRecord> userOpt = repo.findByUsername(username);
        if (userOpt.isEmpty() || !PasswordUtil.verify(password,
                userOpt.get().passwordHash(), userOpt.get().passwordSalt())) {
            JsonUtil.sendError(exchange, 401, "Invalid username or password");
            return;
        }

        UserRecord user   = userOpt.get();
        String token      = repo.createSession(user.userId());

        // Store the client's LAN IP (from the TCP connection) and their listening port
        String clientIp = getRealIp(exchange);
        repo.updatePresence(user.userId(), clientIp, tcpPort);

        System.out.printf("[Auth] Login: @%s from %s:%d%n", username, clientIp, tcpPort);

        JsonObject response = new JsonObject();
        response.addProperty("token",       token);
        response.addProperty("userId",      user.userId());
        response.addProperty("username",    user.username());
        response.addProperty("displayName", user.displayName());
        JsonUtil.sendJson(exchange, 200, response);
    }

    private void handleLogout(HttpExchange exchange) throws IOException, SQLException {
        String token = JsonUtil.extractBearerToken(exchange);
        if (token != null) {
            // Find user and clear their presence before deleting session
            Optional<String> userId = repo.validateSession(token);
            userId.ifPresent(id -> {
                try { repo.clearPresence(id); } catch (SQLException ignored) {}
            });
            repo.deleteSession(token);
        }
        JsonUtil.sendMessage(exchange, 200, "Logged out");
    }
    
    /**
 * Gets the real LAN IP of the connecting client.
 * Falls back to the socket's remote address if no forwarded header exists.
 * Filters out loopback (127.x) so we always store a routable LAN address.
 */
private String getRealIp(com.sun.net.httpserver.HttpExchange exchange) {
    // Check X-Forwarded-For header (set by some proxies)
    String forwarded = exchange.getRequestHeaders().getFirst("X-Real-IP");
    if (forwarded != null && !forwarded.isBlank()) return forwarded;

    String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();

    // If it's loopback, the client is on the same machine as the server.
    // In that case return the server's own LAN IP — they share the same address.
    if (remoteIp.startsWith("127.") || remoteIp.equals("0:0:0:0:0:0:0:1")) {
        return getServerLanIp();
    }
    return remoteIp;
}

private String getServerLanIp() {
    try {
        java.util.Enumeration<java.net.NetworkInterface> interfaces =
            java.net.NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            java.net.NetworkInterface ni = interfaces.nextElement();
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
            java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                java.net.InetAddress addr = addresses.nextElement();
                if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }
    } catch (java.net.SocketException ignored) {}
    return "127.0.0.1";
}
}

