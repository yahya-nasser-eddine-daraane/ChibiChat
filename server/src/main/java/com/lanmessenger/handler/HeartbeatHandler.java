package com.lanmessenger.handler;

import com.lanmessenger.db.UserRepository;
import com.lanmessenger.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * POST /auth/heartbeat
 * Called every 10 seconds by each client to signal they are online.
 * Updates last_seen timestamp in the DB.
 *
 * GET /contacts/online
 * Returns which of the caller's contacts are currently online
 * (last_seen within 15 seconds).
 */
public class HeartbeatHandler implements HttpHandler {

    private final UserRepository repo;

    public HeartbeatHandler(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        String token = JsonUtil.extractBearerToken(exchange);
        if (token == null) {
            JsonUtil.sendError(exchange, 401, "Missing token");
            return;
        }

        try {
            Optional<String> userIdOpt = repo.validateSession(token);
            if (userIdOpt.isEmpty()) {
                JsonUtil.sendError(exchange, 401, "Invalid session");
                return;
            }
            String userId = userIdOpt.get();

            if ("POST".equals(method) && "/auth/heartbeat".equals(path)) {
                repo.updateLastSeen(userId);
                JsonUtil.sendMessage(exchange, 200, "ok");

            } else if ("GET".equals(method) && "/contacts/online".equals(path)) {
                // Returns list of contact userIds that are online right now
                java.util.List<String> onlineIds = repo.getOnlineContactIds(userId, 15);
                JsonUtil.sendJson(exchange, 200, onlineIds);

            } else {
                JsonUtil.sendError(exchange, 404, "Not found");
            }

        } catch (SQLException e) {
            System.err.println("[HeartbeatHandler] DB error: " + e.getMessage());
            JsonUtil.sendError(exchange, 500, "Server error");
        }
    }
}
