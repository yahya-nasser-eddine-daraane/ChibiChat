package com.lanmessenger.handler;

import com.lanmessenger.db.GroupRepository;
import com.lanmessenger.db.UserRepository;
import com.lanmessenger.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public class GroupHandler implements HttpHandler {

    private final GroupRepository groupRepo;
    private final UserRepository userRepo;

    public GroupHandler(GroupRepository groupRepo, UserRepository userRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath();

        try {
            Optional<String> userIdOpt = AuthHandler.authenticate(exchange, userRepo);
            if (userIdOpt.isEmpty()) {
                JsonUtil.sendMessage(exchange, 401, "Unauthorized");
                return;
            }
            String requesterId = userIdOpt.get();

            if (method.equals("GET") && path.equals("/groups")) {
                handleListGroups(exchange, requesterId);
            } else if (method.equals("POST") && path.equals("/groups")) {
                handleCreateGroup(exchange, requesterId);
            } else if (method.equals("GET") && path.matches("/groups/[^/]+/members")) {
                String groupId = path.split("/")[2];
                handleListMembers(exchange, groupId);
            } else if (method.equals("POST") && path.matches("/groups/[^/]+/members")) {
                String groupId = path.split("/")[2];
                handleInviteMember(exchange, groupId);
            } else {
                JsonUtil.sendMessage(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendMessage(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleListGroups(HttpExchange exchange, String userId) throws SQLException, IOException {
        var groups = groupRepo.getGroupsForUser(userId);
        JsonUtil.sendJson(exchange, 200, groups);
    }

    private void handleCreateGroup(HttpExchange exchange, String userId) throws IOException, SQLException {
        Map<String, String> body = JsonUtil.readJson(exchange);
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            JsonUtil.sendMessage(exchange, 400, "Group name required");
            return;
        }
        String groupId = groupRepo.createGroup(name, userId);
        JsonUtil.sendJson(exchange, 201, Map.of("group_id", groupId, "name", name));
    }

    private void handleListMembers(HttpExchange exchange, String groupId) throws SQLException, IOException {
        var members = groupRepo.getGroupMembers(groupId);
        JsonUtil.sendJson(exchange, 200, members);
    }

    private void handleInviteMember(HttpExchange exchange, String groupId) throws IOException, SQLException {
        Map<String, String> body = JsonUtil.readJson(exchange);
        String username = body.get("username");
        if (username == null) {
            JsonUtil.sendMessage(exchange, 400, "Username required");
            return;
        }
        var user = userRepo.findByUsername(username);
        if (user.isEmpty()) {
            JsonUtil.sendMessage(exchange, 404, "User not found");
            return;
        }
        groupRepo.addMember(groupId, user.get().userId());
        JsonUtil.sendMessage(exchange, 200, "Member added");
    }
}
