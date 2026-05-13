package com.lanmessenger.ui;

import com.lanmessenger.messaging.ChatMessage;
import com.lanmessenger.model.ContactRecord;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.util.*;

public class MainScreen {

    private final Stage    stage;
    private final AppState state = AppState.get();

    private final Map<String, ChatPane> chatPanes    = new HashMap<>();
    private final Map<String, HBox>     contactCells = new HashMap<>();

    private BorderPane centerArea;
    private VBox       contactListBox;
    private VBox       groupListBox;

    public MainScreen(Stage stage) { this.stage = stage; }

    public Scene buildScene() {
        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar());

        centerArea = new BorderPane();
        centerArea.setCenter(buildEmptyState());
        root.setCenter(centerArea);

        wireMessageRouter();
        wirePresenceService();

        Scene scene = new Scene(root, 1000, 650);
        LoginScreen.applyStylesheet(scene);
        applyTheme(scene);
        state.darkModeProperty().addListener((obs, old, dark) -> applyTheme(scene));
        return scene;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);

        // App title
        Label title = new Label("ChibiChat");
        title.getStyleClass().add("sidebar-title");
        VBox.setMargin(title, new Insets(14, 14, 4, 14));

        // Search
        TextField search = new TextField();
        search.setPromptText("🔍  Search contacts");
        search.getStyleClass().add("search-field");
        VBox.setMargin(search, new Insets(4, 12, 8, 12));

        // Contact list
        contactListBox = new VBox(2);
        contactListBox.setPadding(new Insets(0, 8, 8, 8));
        rebuildContactList();

        state.getContacts().addListener(
            (javafx.collections.ListChangeListener<ContactRecord>) c -> rebuildContactList()
        );

        // Group list
        Label groupsHeader = new Label("GROUPS");
        groupsHeader.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -cc-text-tertiary;");
        VBox.setMargin(groupsHeader, new Insets(12, 14, 4, 14));

        groupListBox = new VBox(2);
        groupListBox.setPadding(new Insets(0, 8, 8, 8));
        rebuildGroupList();

        state.getGroups().addListener(
            (javafx.collections.ListChangeListener<com.lanmessenger.model.GroupRecord>) g -> rebuildGroupList()
        );

        VBox combinedList = new VBox(contactListBox, groupsHeader, groupListBox);
        ScrollPane scroll = new ScrollPane(combinedList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Buttons
        Button addBtn = new Button("＋  Add contact");
        addBtn.getStyleClass().add("add-contact-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(addBtn, new Insets(4, 12, 4, 12));
        addBtn.setOnAction(e -> showAddContactDialog());

        Button createGroupBtn = new Button("👥  Create group");
        createGroupBtn.getStyleClass().add("add-contact-btn");
        createGroupBtn.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(createGroupBtn, new Insets(4, 12, 4, 12));
        createGroupBtn.setOnAction(e -> showCreateGroupDialog());

        sidebar.getChildren().addAll(title, search, scroll, addBtn, createGroupBtn, buildUserBar());

        search.textProperty().addListener((obs, old, val) -> filterContacts(val));
        return sidebar;
    }

    // ── Contact list ──────────────────────────────────────────────────────────

    private void rebuildContactList() {
        Platform.runLater(() -> {
            contactCells.clear();
            contactListBox.getChildren().clear();

            // Sort: contacts with unread first, then by last message time
            List<ContactRecord> sorted = new ArrayList<>(state.getContacts());
            sorted.sort((a, b) -> {
                ContactState sa = state.getContactState(a.userId());
                ContactState sb = state.getContactState(b.userId());
                // Unread contacts go to top
                if (sa.getUnreadCount() > 0 && sb.getUnreadCount() == 0) return -1;
                if (sb.getUnreadCount() > 0 && sa.getUnreadCount() == 0) return  1;
                // Then sort by most recent message
                return Long.compare(sb.getLastMessageAt(), sa.getLastMessageAt());
            });

            for (ContactRecord c : sorted) {
                HBox cell = buildContactCell(c);
                contactCells.put(c.userId(), cell);
                contactListBox.getChildren().add(cell);
            }
        });
    }

    private HBox buildContactCell(ContactRecord contact) {
        HBox cell = new HBox(10);
        cell.getStyleClass().add("contact-cell");
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(10, 12, 10, 12));

        ContactState cs = state.getContactState(contact.userId());

        // Avatar with online indicator
        StackPane avatar = buildAvatar(contact.displayName(), cs.isOnline());

        // Update avatar when online status changes
        cs.onlineProperty().addListener((obs, old, online) -> {
            Platform.runLater(() -> {
                StackPane newAvatar = buildAvatar(contact.displayName(), online);
                cell.getChildren().set(0, newAvatar);
            });
        });

        // Name + last message
        Label nameLabel = new Label("@" + contact.username());
        nameLabel.getStyleClass().add("contact-name");

        Label previewLabel = new Label(cs.getLastMessage().isEmpty()
            ? (contact.nickname() != null ? contact.nickname() : contact.displayName())
            : cs.getLastMessage());
        previewLabel.getStyleClass().add("contact-preview");
        previewLabel.setMaxWidth(120);
        previewLabel.setEllipsisString("...");
        previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        // Update preview when last message changes
        cs.lastMessageProperty().addListener((obs, old, msg) ->
            Platform.runLater(() -> previewLabel.setText(msg)));

        VBox nameBox = new VBox(2, nameLabel, previewLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        // Right side: unread badge
        StackPane badge = buildUnreadBadge(cs);

        cell.getChildren().addAll(avatar, nameBox, badge);

        // Select on click
        cell.setOnMouseClicked(e -> selectContact(contact));

        if (contact.userId().equals(state.getSelectedContactId())) {
            cell.getStyleClass().add("selected");
        }

        return cell;
    }

    private StackPane buildAvatar(String name, boolean online) {
        Circle bg = new Circle(20);
        String[] colors = {"#2563EB","#0F6E56","#993C1D","#533AB7","#993556","#0E7490"};
        bg.setFill(Color.web(colors[Math.abs(name.charAt(0)) % colors.length]));

        String init = name.length() >= 2
            ? name.substring(0, 2).toUpperCase()
            : name.substring(0, 1).toUpperCase();
        Label initLabel = new Label(init);
        initLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");

        StackPane avatar = new StackPane(bg, initLabel);
        avatar.setPrefSize(40, 40);

        // Online dot — bottom right of avatar
        if (online) {
            Circle dot = new Circle(6);
            dot.setFill(Color.web("#10B981"));
            dot.setStroke(Color.WHITE);
            dot.setStrokeWidth(2);
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            avatar.getChildren().add(dot);
        }

        return avatar;
    }

    private StackPane buildUnreadBadge(ContactState cs) {
        Circle badgeBg = new Circle(10);
        badgeBg.setFill(Color.web("#10B981")); // green
        badgeBg.setVisible(cs.getUnreadCount() > 0);

        Label badgeLabel = new Label(cs.getUnreadCount() > 0
            ? String.valueOf(cs.getUnreadCount()) : "");
        badgeLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white;");
        badgeLabel.setVisible(cs.getUnreadCount() > 0);

        StackPane badge = new StackPane(badgeBg, badgeLabel);
        badge.setPrefSize(20, 20);

        // Update badge when unread count changes
        cs.unreadCountProperty().addListener((obs, old, count) ->
            Platform.runLater(() -> {
                boolean hasUnread = count.intValue() > 0;
                badgeBg.setVisible(hasUnread);
                badgeLabel.setVisible(hasUnread);
                badgeLabel.setText(hasUnread ? String.valueOf(count.intValue()) : "");
                // Re-sort the contact list so this contact bubbles to top
                if (hasUnread) rebuildContactList();
            })
        );

        return badge;
    }

    private void selectContact(ContactRecord contact) {
        // Clear selection style on all cells
        contactCells.values().forEach(c -> c.getStyleClass().remove("selected"));
        HBox cell = contactCells.get(contact.userId());
        if (cell != null) cell.getStyleClass().add("selected");

        state.setSelectedContactId(contact.userId());

        // Clear unread count
        state.getContactState(contact.userId()).clearUnread();

        // Get or create chat pane
        ChatPane pane = chatPanes.computeIfAbsent(
            contact.userId(), id -> new ChatPane(contact)
        );
        centerArea.setCenter(pane.getRoot());
    }

    private void rebuildGroupList() {
        Platform.runLater(() -> {
            groupListBox.getChildren().clear();
            for (com.lanmessenger.model.GroupRecord g : state.getGroups()) {
                HBox cell = buildGroupCell(g);
                groupListBox.getChildren().add(cell);
            }
        });
    }

    private HBox buildGroupCell(com.lanmessenger.model.GroupRecord group) {
        HBox cell = new HBox(10);
        cell.getStyleClass().add("contact-cell");
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(10, 12, 10, 12));

        GroupState gs = state.getGroupState(group.groupId());

        StackPane avatar = buildAvatar(group.name(), false); // Groups are always "offline" in this UI sense

        Label nameLabel = new Label(group.name());
        nameLabel.getStyleClass().add("contact-name");

        Label previewLabel = new Label(gs.getLastMessage().isEmpty() ? "Group Chat" : gs.getLastMessage());
        previewLabel.getStyleClass().add("contact-preview");
        
        VBox nameBox = new VBox(2, nameLabel, previewLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        StackPane badge = buildGroupUnreadBadge(gs);

        cell.getChildren().addAll(avatar, nameBox, badge);
        cell.setOnMouseClicked(e -> selectGroup(group));

        if (group.groupId().equals(state.getSelectedGroupId())) {
            cell.getStyleClass().add("selected");
        }

        return cell;
    }

    private StackPane buildGroupUnreadBadge(GroupState gs) {
        Circle badgeBg = new Circle(10);
        badgeBg.setFill(Color.web("#10B981"));
        badgeBg.setVisible(gs.getUnreadCount() > 0);

        Label badgeLabel = new Label(gs.getUnreadCount() > 0 ? String.valueOf(gs.getUnreadCount()) : "");
        badgeLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white;");
        badgeLabel.setVisible(gs.getUnreadCount() > 0);

        StackPane badge = new StackPane(badgeBg, badgeLabel);
        gs.unreadCountProperty().addListener((obs, old, count) -> Platform.runLater(() -> {
            boolean hasUnread = count.intValue() > 0;
            badgeBg.setVisible(hasUnread);
            badgeLabel.setVisible(hasUnread);
            badgeLabel.setText(hasUnread ? String.valueOf(count.intValue()) : "");
        }));
        return badge;
    }

    private void selectGroup(com.lanmessenger.model.GroupRecord group) {
        contactCells.values().forEach(c -> c.getStyleClass().remove("selected"));
        groupListBox.getChildren().forEach(n -> n.getStyleClass().remove("selected"));
        
        state.setSelectedGroupId(group.groupId());
        state.getGroupState(group.groupId()).clearUnread();

        ChatPane pane = chatPanes.computeIfAbsent(
            group.groupId(), id -> new ChatPane(group)
        );
        centerArea.setCenter(pane.getRoot());
    }

    private void filterContacts(String query) {
        contactListBox.getChildren().clear();
        String q = query.toLowerCase();
        for (ContactRecord c : state.getContacts()) {
            if (q.isEmpty()
                    || c.username().contains(q)
                    || c.displayName().toLowerCase().contains(q)) {
                HBox cell = contactCells.getOrDefault(c.userId(), buildContactCell(c));
                contactListBox.getChildren().add(cell);
            }
        }
    }

    // ── User bar ──────────────────────────────────────────────────────────────

    private HBox buildUserBar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("user-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 12, 10, 12));

        StackPane avatar = buildAvatar(state.getDisplayName(), true);
        avatar.setScaleX(0.8);
        avatar.setScaleY(0.8);

        VBox nameBox = new VBox(1);
        Label nameLabel = new Label("@" + state.getUsername());
        nameLabel.getStyleClass().add("user-bar-name");
        Label statusLabel = new Label("● online");
        statusLabel.getStyleClass().add("user-bar-status");
        nameBox.getChildren().addAll(nameLabel, statusLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Button themeBtn = new Button(state.isDarkMode() ? "☀" : "🌙");
        themeBtn.getStyleClass().add("theme-toggle-btn");
        themeBtn.setOnAction(e -> {
            state.setDarkMode(!state.isDarkMode());
            themeBtn.setText(state.isDarkMode() ? "☀" : "🌙");
        });

        bar.getChildren().addAll(avatar, nameBox, themeBtn);
        return bar;
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    private VBox buildEmptyState() {
        VBox empty = new VBox(14);
        empty.getStyleClass().add("empty-chat");
        empty.setAlignment(Pos.CENTER);

        Label icon = new Label("💬");
        icon.setStyle("-fx-font-size: 64px;");
        Label text = new Label("Select a contact to start chatting");
        text.getStyleClass().add("empty-chat-text");
        Label sub = new Label("Messages go directly over your local network");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: -cc-text-tertiary;");

        empty.getChildren().addAll(icon, text, sub);
        return empty;
    }

    // ── Message routing ───────────────────────────────────────────────────────

    private void wireMessageRouter() {
        if (state.getRouter() == null) return;

        state.getRouter().setOnMessageReceived((fromUsername, msg) -> {
            if (msg.getType() == ChatMessage.Type.DELIVERY ||
                msg.getType() == ChatMessage.Type.PING ||
                msg.getType() == ChatMessage.Type.PONG) return;

            Platform.runLater(() -> {
                String preview = switch (msg.getType()) {
                    case TEXT    -> msg.getContent();
                    case IMAGE   -> "📷 Image";
                    case FILE    -> "📎 " + (msg.getFileName() != null ? msg.getFileName() : "File");
                    case STICKER -> "🎭 Sticker";
                    default      -> "";
                };

                if (msg.isGroupMessage()) {
                    String groupId = msg.getGroupId();
                    GroupState gs = state.getGroupState(groupId);
                    gs.setLastMessage(fromUsername + ": " + preview, msg.getTimestamp());

                    if (!groupId.equals(state.getSelectedGroupId())) {
                        gs.incrementUnread();
                    }

                    com.lanmessenger.model.GroupRecord group = state.getGroups().stream()
                        .filter(g -> g.groupId().equals(groupId))
                        .findFirst().orElse(new com.lanmessenger.model.GroupRecord(groupId, msg.getGroupName(), ""));

                    ChatPane pane = chatPanes.computeIfAbsent(groupId, id -> new ChatPane(group));
                    pane.receiveMessage(msg);
                } else {
                    state.getContacts().stream()
                        .filter(c -> c.username().equals(fromUsername))
                        .findFirst()
                        .ifPresent(contact -> {
                            ContactState cs = state.getContactState(contact.userId());
                            cs.setLastMessage(preview, msg.getTimestamp());

                            if (!contact.userId().equals(state.getSelectedContactId())) {
                                cs.incrementUnread();
                            }

                            ChatPane pane = chatPanes.computeIfAbsent(contact.userId(), id -> new ChatPane(contact));
                            pane.receiveMessage(msg);
                        });
                }
            });
        });
    }

    // ── Presence ──────────────────────────────────────────────────────────────

    private void wirePresenceService() {
        PresenceService ps = state.getPresenceService();
        if (ps == null) return;

        ps.setOnOnlineStatusUpdate(onlineIds -> {
            for (ContactRecord contact : state.getContacts()) {
                boolean online = onlineIds.contains(contact.userId());
                state.getContactState(contact.userId()).setOnline(online);
            }
        });
    }

    // ── Add contact dialog ────────────────────────────────────────────────────

    private void showAddContactDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add contact");
        dialog.setHeaderText("Enter the @username of the person to add");

        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("@username");
        usernameField.getStyleClass().add("login-field");

        VBox content = new VBox(8, new Label("Username:"), usernameField);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(btn ->
            btn == addType ? usernameField.getText().trim() : null);

        dialog.showAndWait().ifPresent(username -> {
            if (!username.isEmpty())
                new Thread(() -> addContact(username)).start();
        });
    }

    private void addContact(String username) {
        try {
            String target = username.startsWith("@") ? username.substring(1) : username;
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.addProperty("username", target);

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(state.getServerUrl() + "/contacts").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + state.getToken());
            conn.setDoOutput(true);
            conn.getOutputStream().write(
                new com.google.gson.Gson().toJson(body)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            if (conn.getResponseCode() == 201) {
                // Refresh contacts
                java.net.HttpURLConnection get = (java.net.HttpURLConnection)
                    new java.net.URL(state.getServerUrl() + "/contacts").openConnection();
                get.setRequestProperty("Authorization", "Bearer " + state.getToken());
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(get.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                    java.util.List<ContactRecord> contacts =
                        new com.google.gson.Gson().fromJson(r,
                            new com.google.gson.reflect.TypeToken<
                                java.util.List<ContactRecord>>(){}.getType());
                    Platform.runLater(() -> state.setContacts(contacts));
                }
            } else {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Not found");
                    alert.setContentText("No user found with that username.");
                    alert.showAndWait();
                });
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void showCreateGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Group");
        dialog.setHeaderText("Create a new group chat");
        dialog.setContentText("Group Name:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                new Thread(() -> {
                    try {
                        com.lanmessenger.model.GroupRecord group = state.getServerClient().createGroup(name);
                        Platform.runLater(() -> {
                            state.getGroups().add(group);
                            selectGroup(group);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setContentText("Failed to create group: " + e.getMessage());
                            alert.showAndWait();
                        });
                    }
                }).start();
            }
        });
    }

    private void applyTheme(Scene scene) {
        if (state.isDarkMode()) scene.getRoot().getStyleClass().add("dark");
        else scene.getRoot().getStyleClass().remove("dark");
    }
}
