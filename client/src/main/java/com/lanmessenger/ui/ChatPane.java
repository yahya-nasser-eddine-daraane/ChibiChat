package com.lanmessenger.ui;

import com.lanmessenger.messaging.ChatMessage;
import com.lanmessenger.messaging.ChatSession;
import com.lanmessenger.model.ContactRecord;
import com.lanmessenger.store.MessageStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Popup;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatPane {

    private final ContactRecord contact;
    private final AppState      state = AppState.get();

    private VBox       messageList;
    private ScrollPane scroll;
    private TextField  inputField;
    private BorderPane root;

    public ChatPane(ContactRecord contact) {
        this.contact = contact;
        buildPane();
    }

    private void buildPane() {
        root = new BorderPane();
        root.setTop(buildHeader());

        messageList = new VBox(6);
        messageList.setPadding(new Insets(16, 20, 16, 20));
        messageList.setFillWidth(true);

        scroll = new ScrollPane(messageList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        root.setCenter(scroll);
        root.setBottom(buildInputBar());

        loadHistory();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);

        ContactState cs = state.getContactState(contact.userId());

        // Avatar
        Circle bg = new Circle(20);
        String[] colors = {"#2563EB","#0F6E56","#993C1D","#533AB7","#993556","#0E7490"};
        bg.setFill(Color.web(colors[Math.abs(contact.displayName().charAt(0)) % colors.length]));
        String init = contact.displayName().length() >= 2
            ? contact.displayName().substring(0, 2).toUpperCase()
            : contact.displayName().substring(0, 1).toUpperCase();
        Label initLabel = new Label(init);
        initLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");
        StackPane avatar = new StackPane(bg, initLabel);
        avatar.setPrefSize(40, 40);

        VBox info = new VBox(2);
        Label name = new Label("@" + contact.username());
        name.getStyleClass().add("chat-peer-name");

        Label status = new Label(cs.isOnline() ? "● Online" : "● Offline");
        status.getStyleClass().add("chat-peer-status");
        if (!cs.isOnline()) status.setStyle("-fx-text-fill: -cc-offline;");

        // Update status label when online state changes
        cs.onlineProperty().addListener((obs, old, online) ->
            Platform.runLater(() -> {
                status.setText(online ? "● Online" : "● Offline");
                status.setStyle(online ? "" : "-fx-text-fill: -cc-offline;");
            })
        );

        info.getChildren().addAll(name, status);
        header.getChildren().addAll(avatar, info);
        return header;
    }

    // ── Input bar ─────────────────────────────────────────────────────────────

    private HBox buildInputBar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("input-bar");
        bar.setAlignment(Pos.CENTER);

        Button fileBtn    = new Button("📎");
        fileBtn.getStyleClass().add("icon-btn");
        fileBtn.setOnAction(e -> pickFile());

        Button stickerBtn = new Button("🎭");
        stickerBtn.getStyleClass().add("icon-btn");
        stickerBtn.setOnAction(e -> showStickerPicker(stickerBtn));

        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.getStyleClass().add("message-input");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendText());

        Button sendBtn = new Button("↑");
        sendBtn.getStyleClass().add("send-btn");
        sendBtn.setOnAction(e -> sendText());

        bar.getChildren().addAll(fileBtn, stickerBtn, inputField, sendBtn);
        return bar;
    }

    // ── Sticker picker ────────────────────────────────────────────────────────

    private void showStickerPicker(Button anchor) {
        List<StickerManager.StickerPack> packs = StickerManager.loadPacks();

        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox container = new VBox(8);
        container.getStyleClass().add("sticker-picker");
        container.setPrefWidth(300);
        container.setMaxHeight(320);

        if (packs.isEmpty()) {
            // No sticker packs installed — show instructions
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(16));

            Label icon = new Label("🎭");
            icon.setStyle("-fx-font-size: 36px;");
            Label msg = new Label("No sticker packs found");
            msg.setStyle("-fx-font-weight: bold; -fx-text-fill: -cc-text-primary;");
            Label instructions = new Label(
                "Add sticker packs by placing folders of\n" +
                "PNG or GIF images in:\n" +
                StickerManager.STICKERS_DIR.toString()
            );
            instructions.setStyle("-fx-font-size: 11px; -fx-text-fill: -cc-text-secondary;");
            instructions.setWrapText(true);
            instructions.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Button openFolder = new Button("📂  Open stickers folder");
            openFolder.getStyleClass().add("add-contact-btn");
            openFolder.setOnAction(e -> {
                popup.hide();
                openStickersFolder();
            });

            empty.getChildren().addAll(icon, msg, instructions, openFolder);
            container.getChildren().add(empty);

        } else {
            // Show pack tabs
            TabPane tabPane = new TabPane();
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabPane.setStyle("-fx-background-color: transparent;");

            for (StickerManager.StickerPack pack : packs) {
                Tab tab = new Tab(pack.name());
                tab.setContent(buildStickerGrid(pack, popup));
                tabPane.getTabs().add(tab);
            }

            ScrollPane sp = new ScrollPane(tabPane);
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            sp.setPrefHeight(300);
            sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            container.getChildren().add(sp);
        }

        popup.getContent().add(container);
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMinX(), bounds.getMinY() - 340);
    }

    private VBox buildStickerGrid(StickerManager.StickerPack pack, Popup popup) {
        VBox grid = new VBox(4);
        grid.setPadding(new Insets(8));

        HBox row = new HBox(6);
        int count = 0;

        for (StickerManager.Sticker sticker : pack.stickers()) {
            Button btn = new Button();
            btn.getStyleClass().add("sticker-btn");
            btn.setTooltip(new Tooltip(sticker.name()));

            // Load the sticker image
            try {
                Image img = new Image(sticker.path().toUri().toString(),
                    64, 64, true, true, true);
                ImageView iv = new ImageView(img);
                iv.setFitWidth(64);
                iv.setFitHeight(64);
                iv.setPreserveRatio(true);
                btn.setGraphic(iv);
            } catch (Exception e) {
                btn.setText("?");
            }

            btn.setOnAction(e -> {
                popup.hide();
                sendSticker(sticker);
            });

            row.getChildren().add(btn);
            count++;

            if (count % 4 == 0) {
                grid.getChildren().add(row);
                row = new HBox(6);
            }
        }
        if (!row.getChildren().isEmpty()) grid.getChildren().add(row);

        return grid;
    }

    private void sendSticker(StickerManager.Sticker sticker) {
        new Thread(() -> {
            try {
                String base64 = StickerManager.toBase64(sticker);
                ChatMessage msg = ChatMessage.image(
                    state.getUserId(), state.getUsername(),
                    contact.userId(), base64,
                    sticker.name() + getExtension(sticker.path().toString()),
                    sticker.mimeType()
                );
                // Mark as sticker for display
                Platform.runLater(() -> addOutgoingSticker(msg));
                saveAndSend(msg);
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Error",
                    "Could not load sticker: " + e.getMessage()));
            }
        }).start();
    }

    private void openStickersFolder() {
        try {
            Path dir = StickerManager.ensureStickersDir();
            new ProcessBuilder("xdg-open", dir.toString()).start();
        } catch (IOException e) {
            showAlert("Stickers folder", StickerManager.STICKERS_DIR.toString());
        }
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot) : ".png";
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    private void sendText() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        ChatMessage msg = ChatMessage.text(
            state.getUserId(), state.getUsername(), contact.userId(), text);

        addOutgoingBubble(msg);
        if (state.getMessageStore() != null)
            state.getMessageStore().saveOutgoing(msg, contact.userId(), contact.username());
        saveAndSend(msg);

        // Update contact state
        state.getContactState(contact.userId())
             .setLastMessage(text, msg.getTimestamp());
    }

    private void pickFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Send a file");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images",
                "*.png","*.jpg","*.jpeg","*.gif","*.webp"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (bytes.length > 20 * 1024 * 1024) {
                    Platform.runLater(() ->
                        showAlert("File too large", "Maximum file size is 20 MB."));
                    return;
                }
                String base64   = Base64.getEncoder().encodeToString(bytes);
                String mime     = Files.probeContentType(file.toPath());
                if (mime == null) mime = "application/octet-stream";
                boolean isImage = mime.startsWith("image/");

                ChatMessage msg = isImage
                    ? ChatMessage.image(state.getUserId(), state.getUsername(),
                                        contact.userId(), base64, file.getName(), mime)
                    : ChatMessage.file(state.getUserId(), state.getUsername(),
                                       contact.userId(), base64,
                                       file.getName(), mime, bytes.length);

                Platform.runLater(() -> addOutgoingBubble(msg));
                if (state.getMessageStore() != null)
                    state.getMessageStore().saveOutgoing(
                        msg, contact.userId(), contact.username());
                saveAndSend(msg);

            } catch (IOException e) {
                Platform.runLater(() ->
                    showAlert("Error", "Could not read file: " + e.getMessage()));
            }
        }).start();
    }

    private void saveAndSend(ChatMessage msg) {
        new Thread(() -> {
            try {
                ChatSession session = state.getRouter()
                    .getOrOpenSession(contact.username(), contact.userId());
                if (session == null) {
                    Platform.runLater(() -> showAlert("Offline",
                        "@" + contact.username() + " appears to be offline."));
                    return;
                }
                session.send(msg);
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Offline",
                    "@" + contact.username() + " appears to be offline."));
            }
        }).start();
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    public void receiveMessage(ChatMessage msg) {
        if (msg.getType() == ChatMessage.Type.DELIVERY) {
            if (state.getMessageStore() != null)
                state.getMessageStore().markDelivered(msg.getMessageId());
            return;
        }
        if (state.getMessageStore() != null)
            state.getMessageStore().saveIncoming(msg);
        addIncomingBubble(msg);
    }

    // ── Bubble helpers ────────────────────────────────────────────────────────

    private void addOutgoingBubble(ChatMessage msg) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(1, 0, 1, 80));
        row.getChildren().add(buildBubble(msg, true));
        messageList.getChildren().add(row);
        scrollToBottom();
    }

    private void addOutgoingSticker(ChatMessage msg) {
        // Stickers sent as images but displayed without bubble background
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(1, 0, 1, 80));
        row.getChildren().add(buildImageNode(msg, true));
        messageList.getChildren().add(row);
        scrollToBottom();
    }

    private void addIncomingBubble(ChatMessage msg) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 80, 1, 0));
        row.getChildren().add(buildBubble(msg, false));
        messageList.getChildren().add(row);
        scrollToBottom();
    }

    private VBox buildBubble(ChatMessage msg, boolean outgoing) {
        if (msg.getType() == ChatMessage.Type.IMAGE) {
            return buildImageNode(msg, outgoing);
        }

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add(outgoing ? "bubble-out" : "bubble-in");

        switch (msg.getType()) {
            case TEXT -> {
                Label text = new Label(msg.getContent());
                text.getStyleClass().add(outgoing ? "bubble-text-out" : "bubble-text-in");
                text.setWrapText(true);
                text.setMaxWidth(340);
                bubble.getChildren().add(text);
            }
            case FILE -> {
                VBox card = new VBox(4);
                card.getStyleClass().add("file-card");
                Label icon = new Label("📄  " + msg.getFileName());
                icon.getStyleClass().add("file-name-label");
                Label size = new Label(formatSize(msg.getFileSize()) + " — click to save");
                size.getStyleClass().add("file-size-label");
                card.getChildren().addAll(icon, size);
                card.setOnMouseClicked(e -> saveFile(msg));
                bubble.getStyleClass().clear();
                bubble.getChildren().add(card);
                return bubble;
            }
            case STICKER -> {
                Label s = new Label(msg.getStickerId());
                s.setStyle("-fx-font-size: 52px;");
                bubble.getStyleClass().clear();
                bubble.getChildren().add(s);
                return bubble;
            }
        }

        // Timestamp
        String time = new SimpleDateFormat("HH:mm").format(new Date(msg.getTimestamp()));
        Label timeLabel = new Label(outgoing ? time + " ✓" : time);
        timeLabel.getStyleClass().add(outgoing ? "bubble-time-out" : "bubble-time");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().add(timeLabel);
        return bubble;
    }

    private VBox buildImageNode(ChatMessage msg, boolean outgoing) {
        VBox wrapper = new VBox(4);

        try {
            byte[] bytes  = Base64.getDecoder().decode(msg.getContent());
            Image  image  = new Image(new ByteArrayInputStream(bytes));
            ImageView iv  = new ImageView(image);
            iv.setFitWidth(200);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.15),8,0,0,2);");
            iv.setOnMouseClicked(e -> saveFile(msg));

            String time = new SimpleDateFormat("HH:mm").format(new Date(msg.getTimestamp()));
            Label timeLabel = new Label(outgoing ? time + " ✓" : time);
            timeLabel.getStyleClass().add(outgoing ? "bubble-time-out" : "bubble-time");

            wrapper.getChildren().addAll(iv, timeLabel);
        } catch (Exception ex) {
            wrapper.getChildren().add(new Label("⚠ Could not load image"));
        }
        return wrapper;
    }

    // ── History loading ───────────────────────────────────────────────────────

    private void loadHistory() {
        MessageStore store = state.getMessageStore();
        if (store == null) return;

        new Thread(() -> {
            List<MessageStore.StoredMessage> history =
                store.loadHistory(contact.userId(), 100);
            if (history.isEmpty()) return;

            Platform.runLater(() -> {
                Label divider = new Label("─── Previous messages ───");
                divider.setStyle(
                    "-fx-font-size: 11px; -fx-text-fill: -cc-text-tertiary; " +
                    "-fx-padding: 8 0 8 0;");
                divider.setMaxWidth(Double.MAX_VALUE);
                divider.setAlignment(Pos.CENTER);
                messageList.getChildren().add(0, divider);

                int idx = 1;
                for (MessageStore.StoredMessage m : history) {
                    ChatMessage msg = rebuildFromStore(m);
                    if (msg == null) continue;
                    HBox row = new HBox();
                    if (m.isOutgoing()) {
                        row.setAlignment(Pos.CENTER_RIGHT);
                        row.setPadding(new Insets(1, 0, 1, 80));
                    } else {
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(1, 80, 1, 0));
                    }
                    row.getChildren().add(buildBubble(msg, m.isOutgoing()));
                    messageList.getChildren().add(idx++, row);
                }
                scrollToBottom();
            });
        }).start();
    }

    private ChatMessage rebuildFromStore(MessageStore.StoredMessage m) {
        try {
            String fromId   = m.isOutgoing() ? state.getUserId()   : contact.userId();
            String fromName = m.isOutgoing() ? state.getUsername() : contact.username();
            String toId     = m.isOutgoing() ? contact.userId()    : state.getUserId();
            return switch (m.type()) {
                case "TEXT"    -> ChatMessage.text(fromId, fromName, toId, m.content());
                case "IMAGE"   -> m.content() == null ? null :
                                  ChatMessage.image(fromId, fromName, toId,
                                    m.content(), m.fileName(), m.mimeType());
                case "FILE"    -> m.content() == null ? null :
                                  ChatMessage.file(fromId, fromName, toId,
                                    m.content(), m.fileName(), m.mimeType(), m.fileSize());
                case "STICKER" -> ChatMessage.sticker(fromId, fromName, toId, m.stickerId());
                default        -> null;
            };
        } catch (Exception e) { return null; }
    }

    // ── File save ─────────────────────────────────────────────────────────────

    private void saveFile(ChatMessage msg) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save file");
        chooser.setInitialFileName(
            msg.getFileName() != null ? msg.getFileName() : "download");
        File dest = chooser.showSaveDialog(root.getScene().getWindow());
        if (dest == null) return;
        try {
            Files.write(dest.toPath(), Base64.getDecoder().decode(msg.getContent()));
        } catch (IOException e) {
            showAlert("Error", "Could not save: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void scrollToBottom() {
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public BorderPane getRoot() { return root; }
}
