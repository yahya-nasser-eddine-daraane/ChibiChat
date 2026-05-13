package com.lanmessenger.handler;

import com.google.gson.JsonObject;
import com.lanmessenger.db.UserRepository;
import com.lanmessenger.model.ContactRecord;
import com.lanmessenger.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Contact endpoints (all require Authorization: Bearer <token>):
 *
 *   GET    /contacts           — list my contacts
 *   POST   /contacts           — add contact by @username
 *   DELETE /contacts/{userId}  — remove a contact
 */
public class ContactHandler implements HttpHandler {

    private final UserRepository repo;

    public ContactHandler(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String userId = authenticate(exchange);
        if (userId == null) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/contacts")) {
                switch (method) {
                    case "GET"  -> handleList(exchange, userId);
                    case "POST" -> handleAdd(exchange, userId);
                    default     -> JsonUtil.sendError(exchange, 405, "Method not allowed");
                }
            } else if (path.startsWith("/contacts/") && "DELETE".equals(method)) {
                handleRemove(exchange, userId, path.substring("/contacts/".length()));
            } else {
                JsonUtil.sendError(exchange, 404, "Not found");
            }
        } catch (SQLException e) {
            System.err.println("[ContactHandler] DB error: " + e.getMessage());
            JsonUtil.sendError(exchange, 500, "Server error");
        }
    }

    // ── GET /contacts ─────────────────────────────────────────────────────────

    private void handleList(HttpExchange exchange, String userId) throws IOException, SQLException {
        List<ContactRecord> contacts = repo.getContacts(userId);
        JsonUtil.sendJson(exchange, 200, contacts);
    }

    // ── POST /contacts ────────────────────────────────────────────────────────
    // Body: { "username": "bob", "nickname": "Bob from work" }   (nickname optional)

    private void handleAdd(HttpExchange exchange, String userId) throws IOException, SQLException {
        JsonObject body = JsonUtil.readBody(exchange, JsonObject.class);

        if (body == null || !body.has("username")) {
            JsonUtil.sendError(exchange, 400, "username is required");
            return;
        }

        String username = body.get("username").getAsString().trim().toLowerCase();
        // Strip leading @ if user types @bob
        if (username.startsWith("@")) username = username.substring(1);

        String nickname = body.has("nickname") ? body.get("nickname").getAsString().trim() : null;

        boolean added = repo.addContact(userId, username, nickname);
        if (!added) {
            JsonUtil.sendError(exchange, 404, "No user found with username @" + username);
            return;
        }

        JsonUtil.sendMessage(exchange, 201, "Contact @" + username + " added");
    }

    // ── DELETE /contacts/{contactUserId} ──────────────────────────────────────

    private void handleRemove(HttpExchange exchange, String userId,
                              String contactId) throws IOException, SQLException {
        repo.removeContact(userId, contactId);
        JsonUtil.sendMessage(exchange, 200, "Contact removed");
    }

    // ── Auth helper ───────────────────────────────────────────────────────────

    private String authenticate(HttpExchange exchange) throws IOException {
        String token = JsonUtil.extractBearerToken(exchange);
        if (token == null) {
            JsonUtil.sendError(exchange, 401, "Missing Authorization header");
            return null;
        }
        try {
            Optional<String> userId = repo.validateSession(token);
            if (userId.isEmpty()) {
                JsonUtil.sendError(exchange, 401, "Invalid or expired session");
                return null;
            }
            repo.updateLastSeen(userId.get());
            return userId.get();
        } catch (SQLException e) {
            JsonUtil.sendError(exchange, 500, "Server error");
            return null;
        }
    }
}
