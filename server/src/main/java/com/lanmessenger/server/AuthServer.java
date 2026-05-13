package com.lanmessenger.server;

import com.lanmessenger.db.DatabaseConfig;
import com.lanmessenger.handler.HeartbeatHandler;
import com.lanmessenger.db.UserRepository;
import com.lanmessenger.handler.AuthHandler;
import com.lanmessenger.handler.ContactHandler;
import com.lanmessenger.handler.PresenceHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * LAN Messenger — Auth Server
 *
 * Endpoints:
 *   POST /auth/register   — register with username + password 
 *   POST /auth/login      — get session token
 *   POST /auth/logout     — invalidate session token
 *   GET  /contacts        — list contacts
 *   POST /contacts        — add a contact by username
 *   DELETE /contacts/{id} — remove a contact
 *   GET  /health          — server health check
 *
 * Run:
 *   mvn package
 *   java -jar target/lan-messenger-1.0-SNAPSHOT.jar
 *
 * Config (config.properties next to the jar):
 *   server.port=8080
 *   db.host=localhost
 *   db.port=1433
 *   db.name=LanMessenger
 *   db.username=sa
 *   db.password=YourPassword
 */
public class AuthServer {

    public static void main(String[] args) throws IOException {
        Properties config = loadConfig(args.length > 0 ? args[0] : "config.properties");

        int    port       = Integer.parseInt(config.getProperty("server.port", "8080"));
        String dbHost     = config.getProperty("db.host",     "localhost");
        String dbPort     = config.getProperty("db.port",     "1433");
        String dbName     = config.getProperty("db.name",     "LanMessenger");
        String dbUser     = config.getProperty("db.username", "sa");
        String dbPassword = config.getProperty("db.password", "");

        DatabaseConfig  dbConfig  = new DatabaseConfig(dbHost, dbPort, dbName, dbUser, dbPassword);
        UserRepository  repo      = new UserRepository(dbConfig);
        AuthHandler     authH     = new AuthHandler(repo);
        ContactHandler  contactH  = new ContactHandler(repo);
        PresenceHandler presenceH = new PresenceHandler(repo);

        try {
            dbConfig.getConnection();
            System.out.println("[Server] Database connection OK");
        } catch (Exception e) {
            System.err.println("[Server] FATAL: Cannot connect to database — " + e.getMessage());
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/auth",    authH);
        server.createContext("/contacts", contactH);
        server.createContext("/users",   presenceH);   // GET /users/{username}/address
        HeartbeatHandler heartbeatH = new HeartbeatHandler(repo);
        server.createContext("/auth/heartbeat",  heartbeatH);
        server.createContext("/contacts/online", heartbeatH);
        server.createContext("/health",  exchange -> {
            byte[] resp = "{\"status\":\"ok\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.getResponseBody().close();
        });

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("═".repeat(50));
        System.out.println("  ChibiChat Auth Server");
        System.out.printf ("  Listening on http://0.0.0.0:%d%n", port);
        System.out.println("  Endpoints:");
        System.out.println("    POST /auth/register");
        System.out.println("    POST /auth/login");
        System.out.println("    POST /auth/logout");
        System.out.println("    GET  /contacts");
        System.out.println("    POST /contacts");
        System.out.println("    GET  /users/{username}/address");
        System.out.println("═".repeat(50));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            server.stop(2);
            dbConfig.close();
        }));
    }

    private static Properties loadConfig(String filename) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filename)) {
            props.load(fis);
            System.out.println("[Server] Loaded config from: " + filename);
            return props;
        } catch (IOException ignored) {}
        try (InputStream is = AuthServer.class.getClassLoader()
                                              .getResourceAsStream("config.properties")) {
            if (is != null) { props.load(is); return props; }
        } catch (IOException ignored) {}
        System.err.println("[Server] WARNING: No config.properties found. Using defaults.");
        return props;
    }
}
