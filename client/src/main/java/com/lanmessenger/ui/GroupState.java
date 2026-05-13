package com.lanmessenger.ui;

import javafx.beans.property.*;

public class GroupState {

    private final String groupId;

    private final IntegerProperty unreadCount   = new SimpleIntegerProperty(0);
    private final StringProperty  lastMessage   = new SimpleStringProperty("");
    private final LongProperty    lastMessageAt = new SimpleLongProperty(0);

    public GroupState(String groupId) {
        this.groupId = groupId;
    }

    public void incrementUnread() {
        unreadCount.set(unreadCount.get() + 1);
    }

    public void clearUnread() {
        unreadCount.set(0);
    }

    public IntegerProperty unreadCountProperty() { return unreadCount; }
    public int getUnreadCount()                  { return unreadCount.get(); }

    public void setLastMessage(String preview, long timestamp) {
        lastMessage.set(preview);
        lastMessageAt.set(timestamp);
    }

    public StringProperty lastMessageProperty()  { return lastMessage; }
    public LongProperty   lastMessageAtProperty(){ return lastMessageAt; }
    public String         getLastMessage()       { return lastMessage.get(); }
    public long           getLastMessageAt()     { return lastMessageAt.get(); }

    public String getGroupId() { return groupId; }
}
