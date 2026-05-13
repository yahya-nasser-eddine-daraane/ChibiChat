package com.lanmessenger.messaging;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {

    public enum Type {
        TEXT,       // plain text
        IMAGE,      // inline image (base64 payload)
        FILE,       // any file (base64 payload)
        STICKER,    // sticker id — receiver looks it up locally
        DELIVERY,   // delivery receipt
        PING,
        PONG
    }

    private String messageId;
    private Type   type;
    private String fromUserId;
    private String fromUsername;
    private String toUserId;
    private String groupId;       // NEW: ID of the group if this is a group message
    private String groupName;     // NEW: Name of the group
    private String content;       // text content or base64 data
    private String fileName;      // original file name (for FILE type)
    private String mimeType;      // e.g. image/png, application/pdf
    private long   fileSize;      // bytes
    private String stickerId;     // sticker identifier (for STICKER type)
    private long   timestamp;

    public ChatMessage() {}

    public static ChatMessage text(String fromUserId, String fromUsername,
                                   String toUserId, String content) {
        ChatMessage m = new ChatMessage();
        m.messageId   = UUID.randomUUID().toString();
        m.type        = Type.TEXT;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.toUserId    = toUserId;
        m.content     = content;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage groupText(String fromUserId, String fromUsername,
                                        String groupId, String groupName, String content) {
        ChatMessage m = new ChatMessage();
        m.messageId   = UUID.randomUUID().toString();
        m.type        = Type.TEXT;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.groupId     = groupId;
        m.groupName   = groupName;
        m.content     = content;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage image(String fromUserId, String fromUsername,
                                    String toUserId, String base64Data,
                                    String fileName, String mimeType) {
        ChatMessage m = new ChatMessage();
        m.messageId   = UUID.randomUUID().toString();
        m.type        = Type.IMAGE;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.toUserId    = toUserId;
        m.content     = base64Data;
        m.fileName    = fileName;
        m.mimeType    = mimeType;
        m.fileSize    = base64Data.length() * 3 / 4;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage file(String fromUserId, String fromUsername,
                                   String toUserId, String base64Data,
                                   String fileName, String mimeType, long fileSize) {
        ChatMessage m = new ChatMessage();
        m.messageId   = UUID.randomUUID().toString();
        m.type        = Type.FILE;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.toUserId    = toUserId;
        m.content     = base64Data;
        m.fileName    = fileName;
        m.mimeType    = mimeType;
        m.fileSize    = fileSize;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage sticker(String fromUserId, String fromUsername,
                                      String toUserId, String stickerId) {
        ChatMessage m = new ChatMessage();
        m.messageId   = UUID.randomUUID().toString();
        m.type        = Type.STICKER;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.toUserId    = toUserId;
        m.stickerId   = stickerId;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage delivery(String originalId, String fromUserId,
                                       String fromUsername, String toUserId) {
        ChatMessage m = new ChatMessage();
        m.messageId   = originalId;
        m.type        = Type.DELIVERY;
        m.fromUserId  = fromUserId;
        m.fromUsername = fromUsername;
        m.toUserId    = toUserId;
        m.timestamp   = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage ping() {
        ChatMessage m = new ChatMessage();
        m.type = Type.PING;
        m.timestamp = Instant.now().toEpochMilli();
        return m;
    }

    public static ChatMessage pong() {
        ChatMessage m = new ChatMessage();
        m.type = Type.PONG;
        m.timestamp = Instant.now().toEpochMilli();
        return m;
    }

    // Getters
    public String getMessageId()    { return messageId; }
    public Type   getType()         { return type; }
    public String getFromUserId()   { return fromUserId; }
    public String getFromUsername() { return fromUsername; }
    public String getToUserId()     { return toUserId; }
    public String getGroupId()      { return groupId; }
    public String getGroupName()    { return groupName; }
    public boolean isGroupMessage() { return groupId != null; }
    public String getContent()      { return content; }
    public String getFileName()     { return fileName; }
    public String getMimeType()     { return mimeType; }
    public long   getFileSize()     { return fileSize; }
    public String getStickerId()    { return stickerId; }
    public long   getTimestamp()    { return timestamp; }
}
