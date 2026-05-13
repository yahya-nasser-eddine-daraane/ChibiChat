package com.lanmessenger.ui;

import com.lanmessenger.messaging.MessageRouter;
import com.lanmessenger.messaging.MessageServer;
import com.lanmessenger.messaging.ServerClient;
import com.lanmessenger.model.ContactRecord;
import com.lanmessenger.model.GroupRecord;
import com.lanmessenger.store.LocalDatabase;
import com.lanmessenger.store.MessageStore;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppState {

    // ── Auth ──────────────────────────────────────────────────────────────────
    private String userId;
    private String username;
    private String displayName;
    private String token;
    private String serverUrl;

    // ── Messaging ─────────────────────────────────────────────────────────────
    private MessageRouter   router;
    private MessageServer   msgServer;
    private ServerClient    serverClient;

    // ── Local storage ─────────────────────────────────────────────────────────
    private LocalDatabase   localDb;
    private MessageStore    messageStore;

    // ── Presence ──────────────────────────────────────────────────────────────
    private PresenceService presenceService;

    // ── Per-conversation UI state ─────────────────────────────────────────────
    private final Map<String, ContactState> contactStates = new ConcurrentHashMap<>();
    private final Map<String, GroupState>   groupStates   = new ConcurrentHashMap<>();

    // ── Observable lists ──────────────────────────────────────────────────────
    private final ObservableList<ContactRecord> contacts =
            FXCollections.observableArrayList();
    private final ObservableList<GroupRecord>   groups   =
            FXCollections.observableArrayList();

    private final StringProperty selectedContactId =
            new SimpleStringProperty(null);
    private final StringProperty selectedGroupId =
            new SimpleStringProperty(null);

    private final BooleanProperty darkMode =
            new SimpleBooleanProperty(false);

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static final AppState INSTANCE = new AppState();
    private AppState() {}
    public static AppState get() { return INSTANCE; }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public void setAuth(String userId, String username, String displayName,
                        String token, String serverUrl) {
        this.userId      = userId;
        this.username    = username;
        this.displayName = displayName;
        this.token       = token;
        this.serverUrl   = serverUrl;
    }

    public String getUserId()      { return userId; }
    public String getUsername()    { return username; }
    public String getDisplayName() { return displayName; }
    public String getToken()       { return token; }
    public String getServerUrl()   { return serverUrl; }

    // ── Messaging ─────────────────────────────────────────────────────────────

    public void setRouter(MessageRouter r)      { this.router       = r; }
    public void setMsgServer(MessageServer s)   { this.msgServer    = s; }
    public void setServerClient(ServerClient c) { this.serverClient = c; }

    public MessageRouter  getRouter()       { return router; }
    public MessageServer  getMsgServer()    { return msgServer; }
    public ServerClient   getServerClient() { return serverClient; }

    // ── Local storage ─────────────────────────────────────────────────────────

    public void setLocalDb(LocalDatabase db)        { this.localDb      = db; }
    public void setMessageStore(MessageStore store) { this.messageStore = store; }

    public LocalDatabase getLocalDb()       { return localDb; }
    public MessageStore  getMessageStore()  { return messageStore; }

    // ── Presence ──────────────────────────────────────────────────────────────

    public void setPresenceService(PresenceService s) { this.presenceService = s; }
    public PresenceService getPresenceService()       { return presenceService; }

    // ── States ────────────────────────────────────────────────────────────────

    public ContactState getContactState(String contactId) {
        return contactStates.computeIfAbsent(contactId, ContactState::new);
    }

    public GroupState getGroupState(String groupId) {
        return groupStates.computeIfAbsent(groupId, GroupState::new);
    }

    // ── Collections ───────────────────────────────────────────────────────────

    public ObservableList<ContactRecord> getContacts() { return contacts; }

    public void setContacts(List<ContactRecord> list) {
        contacts.setAll(list);
        list.forEach(c -> contactStates.computeIfAbsent(c.userId(), ContactState::new));
    }

    public ObservableList<GroupRecord> getGroups() { return groups; }

    public void setGroups(List<GroupRecord> list) {
        groups.setAll(list);
        list.forEach(g -> groupStates.computeIfAbsent(g.groupId(), GroupState::new));
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    public StringProperty  selectedContactIdProperty()     { return selectedContactId; }
    public String          getSelectedContactId()          { return selectedContactId.get(); }
    public void            setSelectedContactId(String id) { 
        selectedContactId.set(id); 
        if (id != null) selectedGroupId.set(null);
    }

    public StringProperty  selectedGroupIdProperty()       { return selectedGroupId; }
    public String          getSelectedGroupId()            { return selectedGroupId.get(); }
    public void            setSelectedGroupId(String id)   { 
        selectedGroupId.set(id); 
        if (id != null) selectedContactId.set(null);
    }

    public BooleanProperty darkModeProperty()      { return darkMode; }
    public boolean         isDarkMode()            { return darkMode.get(); }
    public void            setDarkMode(boolean v)  { darkMode.set(v); }
}
