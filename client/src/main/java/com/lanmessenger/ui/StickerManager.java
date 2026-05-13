package com.lanmessenger.ui;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages sticker packs stored in ~/.chibichat/stickers/
 *
 * Folder structure:
 *   ~/.chibichat/stickers/
 *       pack1/
 *           sticker1.png
 *           sticker2.gif
 *       pack2/
 *           ...
 *
 * Users add sticker packs by dropping folders of PNG/GIF files
 * into ~/.chibichat/stickers/. No app restart needed — packs
 * are reloaded each time the sticker picker is opened.
 *
 * Stickers are sent as base64-encoded image data over TCP,
 * exactly like regular images.
 */
public class StickerManager {

    public static final Path STICKERS_DIR =
        Path.of(System.getProperty("user.home"), ".chibichat", "stickers");

    private static final Set<String> SUPPORTED = Set.of(
        ".png", ".gif", ".webp", ".jpg", ".jpeg"
    );

    // ── Data classes ──────────────────────────────────────────────────────────

    public record StickerPack(String name, List<Sticker> stickers) {}

    public record Sticker(
        String  name,       // filename without extension
        Path    path,       // full path on disk
        String  mimeType,   // image/png, image/gif, etc.
        boolean isAnimated  // true for GIFs
    ) {}

    // ── Loading ───────────────────────────────────────────────────────────────

    /**
     * Scans the stickers directory and returns all packs.
     * Creates the directory if it doesn't exist.
     * Also creates a sample pack with placeholder info if empty.
     */
    public static List<StickerPack> loadPacks() {
        try {
            Files.createDirectories(STICKERS_DIR);
        } catch (IOException ignored) {}

        List<StickerPack> packs = new ArrayList<>();

        try (DirectoryStream<Path> packDirs =
                 Files.newDirectoryStream(STICKERS_DIR)) {

            for (Path packDir : packDirs) {
                if (!Files.isDirectory(packDir)) continue;

                List<Sticker> stickers = new ArrayList<>();

                try (DirectoryStream<Path> files =
                         Files.newDirectoryStream(packDir)) {
                    for (Path file : files) {
                        if (!Files.isRegularFile(file)) continue;
                        String fname = file.getFileName().toString().toLowerCase();
                        String ext   = getExtension(fname);
                        if (!SUPPORTED.contains(ext)) continue;

                        String mime = switch (ext) {
                            case ".gif"  -> "image/gif";
                            case ".webp" -> "image/webp";
                            case ".jpg", ".jpeg" -> "image/jpeg";
                            default      -> "image/png";
                        };

                        stickers.add(new Sticker(
                            stripExtension(file.getFileName().toString()),
                            file,
                            mime,
                            ext.equals(".gif")
                        ));
                    }
                }

                // Sort stickers alphabetically within pack
                stickers.sort(Comparator.comparing(Sticker::name));

                if (!stickers.isEmpty()) {
                    packs.add(new StickerPack(
                        packDir.getFileName().toString(),
                        stickers
                    ));
                }
            }

        } catch (IOException e) {
            System.err.println("[StickerManager] Error loading packs: " + e.getMessage());
        }

        // Sort packs alphabetically
        packs.sort(Comparator.comparing(StickerPack::name));
        return packs;
    }

    /**
     * Reads a sticker file and returns its base64-encoded content.
     * Used when sending a sticker — the full image data is sent over TCP.
     */
    public static String toBase64(Sticker sticker) throws IOException {
        byte[] bytes = Files.readAllBytes(sticker.path());
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Returns the path to the stickers directory so the user can
     * open it in a file manager. Creates it if needed.
     */
    public static Path ensureStickersDir() {
        try { Files.createDirectories(STICKERS_DIR); }
        catch (IOException ignored) {}
        return STICKERS_DIR;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
