package com.lanmessenger.messaging;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The central hub of the messaging layer.
 *
 * Responsibilities:
 *  - Keeps a map of open ChatSessions (one per contact)
 *  - Opens new TCP connections when we message a contact we aren't connected to
 *  - Routes incoming messages to the right handler
 *  - Notifies the UI when messages arrive
 *
 * Usage:
 *   MessageRouter router = new MessageRouter(self, serverClient);
 *   router.setOnMessageReceived((msg) -> updateUI(msg));
 *   router.sendMessage("bob", "Hello Bob!");
 */
public class MessageRouter {

    private final String       myUserId;
    private final String       myUsername;
    private final ServerClient serverClient;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    private BiConsumer<String, ChatMessage> onMessageReceived;
    private BiConsumer<String, Boolean>     onPresenceChanged;

    public MessageRouter(String myUserId, String myUsername, ServerClient serverClient) {
        this.myUserId     = myUserId;
        this.myUsername   = myUsername;
        this.serverClient = serverClient;
    }

    /** Sends any ChatMessage (text, image, file, sticker) to a contact. */
    public void send(String toUsername, String toUserId, ChatMessage msg) throws IOException {
        ChatSession session = getOrOpenSession(toUsername, toUserId);
        if (session == null) throw new IOException("@" + toUsername + " is offline");
        session.send(msg);
    }

    /** Legacy: send a text message by content string. */
    public String sendMessage(String toUsername, String toUserId, String content) throws IOException {
        ChatMessage msg = ChatMessage.text(myUserId, myUsername, toUserId, content);
        send(toUsername, toUserId, msg);
        return msg.getMessageId();
    }

    public void sendToGroup(String groupId, String groupName, String content) throws IOException {
        var members = serverClient.getGroupMembers(groupId);
        ChatMessage msg = ChatMessage.groupText(myUserId, myUsername, groupId, groupName, content);

        for (var member : members) {
            if (member.userId().equals(myUserId)) continue;
            try {
                send(member.username(), member.userId(), msg);
            } catch (IOException e) {
                System.err.println("[Router] Failed to send group message to " + member.username() + ": " + e.getMessage());
            }
        }
    }

    public ChatSession getOrOpenSession(String username, String userId) throws IOException {
        ChatSession existing = sessions.get(userId);
        if (existing != null && existing.isConnected()) return existing;

        ServerClient.PresenceInfo presence = serverClient.getContactAddress(username);
        if (presence == null) return null;

        System.out.printf("[Router] Connecting to @%s at %s:%d%n",
                username, presence.lanIp(), presence.tcpPort());

        Socket socket   = new Socket(presence.lanIp(), presence.tcpPort());
        ChatSession session = new ChatSession(presence.userId(), username, socket);
        registerSession(session);
        session.start();
        return session;
    }

    public void handleIncomingSession(ChatSession session) {
        session.setOnMessage(msg -> {
            sessions.putIfAbsent(msg.getFromUserId(), session);
            dispatchIncoming(msg);
        });
        session.setOnClose(() -> {
            sessions.values().remove(session);
            if (onPresenceChanged != null)
                onPresenceChanged.accept(session.getPeerUsername(), false);
        });
        session.start();
    }

    private void registerSession(ChatSession session) {
        sessions.put(session.getPeerId(), session);
        session.setOnMessage(this::dispatchIncoming);
        session.setOnClose(() -> {
            sessions.remove(session.getPeerId());
            if (onPresenceChanged != null)
                onPresenceChanged.accept(session.getPeerUsername(), false);
        });
        if (onPresenceChanged != null)
            onPresenceChanged.accept(session.getPeerUsername(), true);
    }

    private void dispatchIncoming(ChatMessage msg) {
        if (msg.getType() == ChatMessage.Type.TEXT && onMessageReceived != null) {
            // Send delivery receipt
            ChatSession s = sessions.get(msg.getFromUserId());
            if (s != null) {
                s.send(ChatMessage.delivery(
                    msg.getMessageId(), myUserId, myUsername, msg.getFromUserId()));
            }
        }
        if (onMessageReceived != null) onMessageReceived.accept(msg.getFromUsername(), msg);
    }

    public void closeAll() {
        sessions.values().forEach(ChatSession::close);
        sessions.clear();
    }

    public void setOnMessageReceived(BiConsumer<String, ChatMessage> h) { onMessageReceived = h; }
    public void setOnPresenceChanged(BiConsumer<String, Boolean> h)     { onPresenceChanged = h; }
    public Map<String, ChatSession> getSessions() { return sessions; }
}
