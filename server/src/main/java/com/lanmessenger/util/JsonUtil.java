package com.lanmessenger.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for reading JSON request bodies and writing JSON responses.
 * All HTTP handlers use these instead of touching raw streams directly.
 */
public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private JsonUtil() {}

    /** Parses the request body as JSON into the given class. */
    public static <T> T readBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, clazz);
        }
    }

    /** Sends a JSON response with the given HTTP status code. */
    public static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        String json = GSON.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Sends a simple {"message": "..."} JSON response. */
    public static void sendMessage(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("message", message);
        sendJson(exchange, statusCode, obj);
    }

    /** Sends a simple {"error": "..."} JSON response. */
    public static void sendError(HttpExchange exchange, int statusCode, String error) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", error);
        sendJson(exchange, statusCode, obj);
    }

    /** Extracts the Bearer token from the Authorization header. Returns null if missing. */
    public static String extractBearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).trim();
    }

    public static Gson gson() { return GSON; }
}
