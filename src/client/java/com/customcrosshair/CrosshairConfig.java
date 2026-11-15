package com.customcrosshair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class CrosshairConfig {
    public enum Mode { SIMPLE, PRESET, CUSTOM }
    public enum ColorMode { VANILLA, STATIC, DYNAMIC }
    public enum ColorSlot { BASE, PLAYER, HOSTILE, PASSIVE }
    public enum Scale { SMALL, NORMAL, LARGE, AUTO }

    public static final int DEFAULT_BASE = 0xC66BFF;
    public static final int DEFAULT_PLAYER = 0xFF5555;
    public static final int DEFAULT_HOSTILE = 0x5599FF;
    public static final int DEFAULT_PASSIVE = 0x55FF55;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = LoggerFactory.getLogger("Custom Crosshair");
    public static final CrosshairConfig INSTANCE = new CrosshairConfig();

    public boolean enabled;
    public Mode mode;
    public ColorMode colorMode;
    public Scale scale;
    public boolean customScale, dot, chroma;
    public int length, gap, thickness, preset, color, player, hostile, passive, alpha;
    public boolean[][] custom;

    private CrosshairConfig() {
        custom = CrosshairPatterns.blank();
        reset();
    }

    /** Resets options without deleting the saved drawing. */
    public void reset() {
        enabled = true;
        mode = Mode.SIMPLE;
        colorMode = ColorMode.DYNAMIC;
        scale = Scale.AUTO;
        customScale = false;
        dot = false;
        chroma = false;
        length = 4;
        gap = 2;
        thickness = 1;
        preset = 0;
        resetPalette();
        alpha = 100;
    }

    public void resetPalette() {
        color = DEFAULT_BASE;
        player = DEFAULT_PLAYER;
        hostile = DEFAULT_HOSTILE;
        passive = DEFAULT_PASSIVE;
    }

    public int getColor(ColorSlot slot) {
        return switch (slot) {
            case BASE -> color;
            case PLAYER -> player;
            case HOSTILE -> hostile;
            case PASSIVE -> passive;
        } & 0xFFFFFF;
    }

    public void setColor(ColorSlot slot, int value) {
        int rgb = value & 0xFFFFFF;
        switch (slot) {
            case BASE -> color = rgb;
            case PLAYER -> player = rgb;
            case HOSTILE -> hostile = rgb;
            case PASSIVE -> passive = rgb;
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("customcrosshair.json");
    }

    public void load() {
        Path path = configFile();
        if (!Files.isRegularFile(path)) return;
        final JsonObject json;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            LOG.warn("Could not read customcrosshair.json; keeping current settings", error);
            return;
        }

        enabled = bool(json, "enabled", enabled);
        mode = option(json, "mode", mode, Mode.class);
        colorMode = option(json, "colorMode", colorMode, ColorMode.class);
        scale = option(json, "scale", scale, Scale.class);
        customScale = bool(json, "customScale", customScale);
        dot = bool(json, "dot", dot);
        chroma = bool(json, "chroma", chroma);
        length = number(json, "length", length, CrosshairPatterns.MIN_LENGTH, CrosshairPatterns.MAX_LENGTH);
        gap = number(json, "gap", gap, CrosshairPatterns.MIN_GAP, CrosshairPatterns.MAX_GAP);
        thickness = number(json, "thickness", thickness, CrosshairPatterns.MIN_THICKNESS, CrosshairPatterns.MAX_THICKNESS);
        preset = number(json, "preset", preset, 0, CrosshairPatterns.PRESET_NAMES.size() - 1);
        alpha = number(json, "alpha", alpha, 0, 100);
        color = rgb(json, "color", color);
        player = rgb(json, "player", player);
        hostile = rgb(json, "hostile", rgb(json, "enemy", hostile));
        passive = rgb(json, "passive", rgb(json, "friendly", passive));
        boolean[][] drawing = CrosshairPatterns.importCode(string(json, "custom"));
        if (drawing != null) custom = drawing;
    }

    public void save() {
        Path path = configFile();
        String drawing = CrosshairPatterns.exportCode(custom);
        if (drawing == null) {
            LOG.warn("Refusing to save an invalid custom crosshair shape");
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("mode", mode.name());
        json.addProperty("colorMode", colorMode.name());
        json.addProperty("scale", scale.name());
        json.addProperty("customScale", customScale);
        json.addProperty("dot", dot);
        json.addProperty("chroma", chroma);
        json.addProperty("length", length);
        json.addProperty("gap", gap);
        json.addProperty("thickness", thickness);
        json.addProperty("preset", preset);
        json.addProperty("alpha", alpha);
        json.addProperty("color", hex(color));
        json.addProperty("player", hex(player));
        json.addProperty("hostile", hex(hostile));
        json.addProperty("passive", hex(passive));
        json.addProperty("custom", drawing);

        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), ".customcrosshair-", ".tmp");
            try {
                Files.writeString(temp, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException noAtomicMove) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException error) {
            LOG.warn("Could not save customcrosshair.json", error);
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static JsonElement get(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value : null;
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = get(json, key);
        return value != null && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static int rgb(JsonObject json, String key, int fallback) {
        String value = string(json, key);
        if (value == null || !value.matches("#?[0-9a-fA-F]{6}")) return fallback;
        return Integer.parseInt(value.charAt(0) == '#' ? value.substring(1) : value, 16);
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement value = get(json, key);
        return value != null && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static int number(JsonObject json, String key, int fallback, int min, int max) {
        JsonElement value = get(json, key);
        if (value == null || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return Math.clamp(value.getAsBigDecimal().intValueExact(), min, max);
        } catch (ArithmeticException | NumberFormatException invalid) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T option(JsonObject json, String key, T fallback, Class<T> type) {
        String value = string(json, key);
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return fallback;
        }
    }
}
