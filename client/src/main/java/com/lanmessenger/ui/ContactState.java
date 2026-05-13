package com.lanmessenger.ui;

import javafx.beans.property.*;

/**
 * Observable state for one contact in the sidebar.
 * The contact cell binds to these properties so the UI
 * updates automatically when messages arrive or online
 * status changes.
 */
public class ContactState {

    private final String contactId;

    private final IntegerProperty unreadCount   = new SimpleIntegerProperty(0);
    private final StringProperty  lastMessage   = new SimpleStringProperty("");
    private final LongProperty    lastMessageAt = new SimpleLongProperty(0);
    private final BooleanProperty online        = new SimpleBooleanProperty(false);

    public ContactState(String contactId) {
        this.contactId = contactId;
    }

    // ── Unread ────────────────────────────────────────────────────────────────

    public void incrementUnread() {
        unreadCount.set(unreadCount.get() + 1);
    }

    public void clearUnread() {
        unreadCount.set(0);
    }

    public IntegerProperty unreadCountProperty() { return unreadCount; }
    public int getUnreadCount()                  { return unreadCount.get(); }

    // ── Last message ──────────────────────────────────────────────────────────

    public void setLastMessage(String preview, long timestamp) {
        lastMessage.set(preview);
        lastMessageAt.set(timestamp);
    }

    public StringProperty lastMessageProperty()  { return lastMessage; }
    public LongProperty   lastMessageAtProperty(){ return lastMessageAt; }
    public String         getLastMessage()       { return lastMessage.get(); }
    public long           getLastMessageAt()     { return lastMessageAt.get(); }

    // ── Online ────────────────────────────────────────────────────────────────

    public BooleanProperty onlineProperty() { return online; }
    public boolean         isOnline()       { return online.get(); }
    public void            setOnline(boolean v) { online.set(v); }

    public String getContactId() { return contactId; }
}
