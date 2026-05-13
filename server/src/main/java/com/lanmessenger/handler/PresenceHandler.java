package com.lanmessenger.handler;

import com.lanmessenger.db.UserRepository;
import com.lanmessenger.model.PresenceRecord;
import com.lanmessenger.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * GET /users/{username}/address
 *
 * Returns the LAN IP + TCP port of a contact so the client
 * can open a direct TCP connection to them.
 *
 * Security: only works if the requester has that user in their contacts.
 * You can't look up strangers' IPs.
 */
public class PresenceHandler implements HttpHandler {

    private final UserRepository repo;

    public PresenceHandler(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        // Authenticate
        String token = JsonUtil.extractBearerToken(exchange);
        if (token == null) { JsonUtil.sendError(exchange, 401, "Missing token"); return; }

        try {
            Optional<String> requesterIdOpt = repo.validateSession(token);
            if (requesterIdOpt.isEmpty()) {
                JsonUtil.sendError(exchange, 401, "Invalid or expired session");
                return;
            }
            String requesterId = requesterIdOpt.get();

            // Extract username from path: /users/alice/address
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 4) {
                JsonUtil.sendError(exchange, 400, "Invalid path");
                return;
            }
            String targetUsername = parts[2];

            Optional<PresenceRecord> presence = repo.getPresence(requesterId, targetUsername);
            if (presence.isEmpty()) {
                JsonUtil.sendError(exchange, 404,
                    "@" + targetUsername + " is offline or not in your contacts");
                return;
            }

            JsonUtil.sendJson(exchange, 200, presence.get());

        } catch (SQLException e) {
            System.err.println("[PresenceHandler] DB error: " + e.getMessage());
            JsonUtil.sendError(exchange, 500, "Server error");
        }
    }
}
