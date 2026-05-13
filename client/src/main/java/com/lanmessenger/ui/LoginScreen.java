package com.lanmessenger.ui;

import com.lanmessenger.store.LocalDatabase;
import com.lanmessenger.store.MessageStore;
import java.util.Properties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lanmessenger.messaging.MessageRouter;
import com.lanmessenger.messaging.MessageServer;
import com.lanmessenger.messaging.ServerClient;
import com.lanmessenger.model.ContactRecord;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LoginScreen {

    private final Stage stage;
    private final Gson  gson = new Gson();

    private TextField     serverField;
    private TextField     usernameField;
    private PasswordField passwordField;
    private Label         errorLabel;
    private Button        loginBtn;
    private boolean       isRegistering = false;
    private TextField     displayNameField;

    public LoginScreen(Stage stage) { this.stage = stage; }

    public Scene buildScene() {
        VBox root = new VBox(14);
        root.getStyleClass().add("login-container");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        // Logo / title
        Label title = new Label("💬 ChibiChat");
        title.getStyleClass().add("login-title");

        Label subtitle = new Label("Secure LAN Messenger");
        subtitle.getStyleClass().add("login-subtitle");

        // Server URL field
        serverField = new TextField("http://localhost:8080");
        serverField.setPromptText("Server URL");
        serverField.getStyleClass().add("login-field");

        // Display name (only shown during registration)
        displayNameField = new TextField();
        displayNameField.setPromptText("Display name");
        displayNameField.getStyleClass().add("login-field");
        displayNameField.setVisible(false);
        displayNameField.setManaged(false);

        // Username
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.getStyleClass().add("login-field");

        // Password
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("login-field");

        // Error label
        errorLabel = new Label("");
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        // Login / Register button
        loginBtn = new Button("Sign in");
        loginBtn.getStyleClass().add("login-btn");
        loginBtn.setOnAction(e -> handleSubmit());

        // Press Enter to submit
        passwordField.setOnAction(e -> handleSubmit());

        // Toggle register/login
        Button toggleBtn = new Button("Don't have an account? Register");
        toggleBtn.getStyleClass().add("login-link");
        toggleBtn.setOnAction(e -> toggleMode());

        root.getChildren().addAll(
            title, subtitle,
            serverField, displayNameField, usernameField, passwordField,
            errorLabel, loginBtn, toggleBtn
        );

        Scene scene = new Scene(root, 1000, 650);
        applyStylesheet(scene);
        return scene;
    }

    private void toggleMode() {
        isRegistering = !isRegistering;
        displayNameField.setVisible(isRegistering);
        displayNameField.setManaged(isRegistering);
        loginBtn.setText(isRegistering ? "Create account" : "Sign in");
        errorLabel.setText("");
    }

    private void handleSubmit() {
        String server   = serverField.getText().trim();
        String username = usernameField.getText().trim().toLowerCase();
        String password = passwordField.getText();

        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        loginBtn.setDisable(true);
        loginBtn.setText(isRegistering ? "Creating account..." : "Signing in...");

        new Thread(() -> {
            try {
                if (isRegistering) {
                    String displayName = displayNameField.getText().trim();
                    if (displayName.isEmpty()) displayName = username;
                    doRegister(server, username, displayName, password);
                } else {
                    doLogin(server, username, password);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Connection error: " + e.getMessage());
                    loginBtn.setDisable(false);
                    loginBtn.setText(isRegistering ? "Create account" : "Sign in");
                });
            }
        }).start();
    }

    private void doRegister(String server, String username,
                             String displayName, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username",    username);
        body.addProperty("displayName", displayName);
        body.addProperty("password",    password);

        JsonObject resp = post(server + "/auth/register", body, null);
        Platform.runLater(() -> {
            if (resp != null && resp.has("message")) {
                isRegistering = false;
                displayNameField.setVisible(false);
                displayNameField.setManaged(false);
                loginBtn.setText("Sign in");
                showError("✓ Registered! Now sign in.");
            } else {
                showError(resp != null && resp.has("error")
                    ? resp.get("error").getAsString() : "Registration failed.");
            }
            loginBtn.setDisable(false);
        });
    }

    private void doLogin(String server, String username, String password) throws IOException {
        // Step 1: Start the message server so the OS assigns a free port.
        // We use a temporary placeholder router — replaced after login succeeds.
        MessageServer msgServer = new MessageServer(session -> {}); // placeholder
        msgServer.start();
        int tcpPort = msgServer.getPort(); // OS-assigned, guaranteed free

        // Step 2: Login with that port so the server stores our address.
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("tcpPort",  tcpPort);

        JsonObject resp = post(server + "/auth/login", body, null);

        if (resp == null || !resp.has("token")) {
            String err = resp != null && resp.has("error")
                ? resp.get("error").getAsString() : "Login failed.";
            msgServer.stop();
            Platform.runLater(() -> {
                showError(err);
                loginBtn.setDisable(false);
                loginBtn.setText("Sign in");
            });
            return;
        }

        String token       = resp.get("token").getAsString();
        String userId      = resp.get("userId").getAsString();
        String displayName = resp.get("displayName").getAsString();

        // Step 3: Build the real router and rewire the server to use it.
        AppState      state        = AppState.get();
        ServerClient  serverClient = new ServerClient(server, token);
        MessageRouter router       = new MessageRouter(userId, username, serverClient);
        msgServer.rewireHandler(router::handleIncomingSession); // swap in real handler

        state.setAuth(userId, username, displayName, token, server);
        state.setServerClient(serverClient);
        state.setRouter(router);
        state.setMsgServer(msgServer);
        PresenceService presenceService = new PresenceService(server, token);
        state.setPresenceService(presenceService);
        presenceService.start();

        // Init local SQL Server database for message history
        try {
            Properties clientConfig = loadClientConfig();
            String dbHost = clientConfig.getProperty("local.db.host", "localhost");
            int    dbPort = Integer.parseInt(clientConfig.getProperty("local.db.port", "1433"));
            String dbUser = clientConfig.getProperty("local.db.username", "sa");
            String dbPass = clientConfig.getProperty("local.db.password", "");

            LocalDatabase localDb = new LocalDatabase(dbHost, dbPort, dbUser, dbPass, username);
            localDb.init();
            MessageStore store = new MessageStore(localDb);
            state.setLocalDb(localDb);
            state.setMessageStore(store);
            System.out.println("[Login] Local message store ready.");
        } catch (Exception e) {
            // Non-fatal — app works without history if DB is unavailable
            System.err.println("[Login] Local DB unavailable: " + e.getMessage());
            System.err.println("[Login] Messages will not be saved this session.");
        }

        // Fetch contacts
        List<ContactRecord> contacts = fetchContacts(server, token);
        state.setContacts(contacts);

        Platform.runLater(ChibiChatApp::showMain);
    }

    private Properties loadClientConfig() {
        Properties props = new Properties();
        // Try loading from filesystem next to the jar first
        try (FileInputStream fis = new FileInputStream("client.properties")) {
            props.load(fis);
            return props;
        } catch (IOException ignored) {}
        // Fallback to classpath
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("client.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {}
        return props;
    }

    @SuppressWarnings("unchecked")
    private List<ContactRecord> fetchContacts(String server, String token) throws IOException {
        URL url = new URL(server + "/contacts");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() != 200) return List.of();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return gson.fromJson(r,
                new com.google.gson.reflect.TypeToken<List<ContactRecord>>(){}.getType());
        }
    }

    private JsonObject post(String url, JsonObject body, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
        }
        InputStream is = conn.getResponseCode() >= 400
            ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return gson.fromJson(r, JsonObject.class);
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
    }

    static void applyStylesheet(Scene scene) {
        String css = ChibiChatApp.class.getResource("/styles.css") != null
            ? ChibiChatApp.class.getResource("/styles.css").toExternalForm() : null;
        if (css != null) scene.getStylesheets().add(css);
    }
  
}
