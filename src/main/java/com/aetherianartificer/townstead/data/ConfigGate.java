package com.aetherianartificer.townstead.data;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Read-only predicate over another mod's ordinary TOML configuration.
 *
 * <p>The external fact stays in data ({@code file}, {@code path}, and {@code equals}); Java knows
 * nothing about the owning mod or its classes. Paths are resolved below the loader's config
 * directory and cannot escape it. A malformed or unreadable gate fails closed.</p>
 */
public final class ConfigGate {

    private static final long CACHE_NANOS = 1_000_000_000L;
    private static final ConcurrentHashMap<Path, CachedConfig> CACHE = new ConcurrentHashMap<>();

    private record CachedConfig(long checkedAt, long modified, long size,
                                @Nullable CommentedConfig config) {}

    private ConfigGate() {}

    public static @Nullable Boolean evaluate(@Nullable JsonElement expression) {
        return evaluate(expression, (Level) null);
    }

    /** Evaluates a global config, or a world's {@code serverconfig} when scope is {@code server}. */
    public static @Nullable Boolean evaluate(@Nullable JsonElement expression, @Nullable Level level) {
        if (!valid(expression)) return null;
        JsonObject object = expression.getAsJsonObject();
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        if (file == null || file.isBlank() || path == null || path.isEmpty()
                || expected == null || expected.isJsonNull()) return null;

        String scope = string(object.get("scope"));
        Path root = "server".equalsIgnoreCase(scope == null ? "" : scope) && level != null
                && level.getServer() != null
                ? level.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig")
                : configDirectory();
        root = root.toAbsolutePath().normalize();
        Path target = root.resolve(file).normalize();
        if (!target.startsWith(root) || !target.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".toml")) return null;
        CommentedConfig config = cached(target);
        if (config == null) return fallback(object, expected);
        Object actual = config.get(path);
        return actual == null ? fallback(object, expected) : compare(expected, actual);
    }

    /** World-free seam used by schema tests and by future config backends. */
    static @Nullable Boolean evaluate(@Nullable JsonElement expression,
                                      Function<List<String>, Object> lookup) {
        if (!valid(expression) || lookup == null) return null;
        JsonObject object = expression.getAsJsonObject();
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        if (path == null || path.isEmpty() || expected == null || expected.isJsonNull()) return null;
        Object actual = lookup.apply(path);
        return actual == null ? fallback(object, expected) : compare(expected, actual);
    }

    public static boolean valid(@Nullable JsonElement expression) {
        if (expression == null || !expression.isJsonObject()) return false;
        JsonObject object = expression.getAsJsonObject();
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        JsonElement fallback = object.get("default");
        return file != null && !file.isBlank() && path != null && !path.isEmpty()
                && expected != null && expected.isJsonPrimitive()
                && (fallback == null || fallback.isJsonPrimitive());
    }

    /**
     * Reads a number from an ordinary TOML file. The expression uses the same
     * {@code file}, {@code scope}, and {@code path} fields as a config condition,
     * with {@code default} supplying the value when the file or entry is absent.
     * This keeps data-authored timings and limits independent of Java config fields.
     */
    public static double number(@Nullable JsonElement expression, @Nullable Level level,
                                double fallback) {
        if (expression == null || !expression.isJsonObject()) return fallback;
        JsonObject object = expression.getAsJsonObject();
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        if (file == null || file.isBlank() || path == null || path.isEmpty()) return fallback;

        String scope = string(object.get("scope"));
        Path root = "server".equalsIgnoreCase(scope == null ? "" : scope) && level != null
                && level.getServer() != null
                ? level.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig")
                : configDirectory();
        root = root.toAbsolutePath().normalize();
        Path target = root.resolve(file).normalize();
        if (!target.startsWith(root) || !target.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".toml")) return fallback;

        CommentedConfig config = cached(target);
        Object actual = config == null ? null : config.get(path);
        if (actual instanceof Number number) return number.doubleValue();
        JsonElement defaultValue = object.get("default");
        return defaultValue != null && defaultValue.isJsonPrimitive()
                && defaultValue.getAsJsonPrimitive().isNumber()
                ? defaultValue.getAsDouble() : fallback;
    }

    /** Whether an object is a well-formed numeric config reference. */
    public static boolean validNumber(@Nullable JsonElement expression) {
        if (expression == null || !expression.isJsonObject()) return false;
        JsonObject object = expression.getAsJsonObject();
        JsonElement defaultValue = object.get("default");
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        return file != null && !file.isBlank()
                && path != null && !path.isEmpty()
                && (defaultValue == null || defaultValue.isJsonPrimitive()
                && defaultValue.getAsJsonPrimitive().isNumber());
    }

    private static Boolean fallback(JsonObject object, JsonElement expected) {
        JsonElement fallback = object.get("default");
        return fallback == null ? Boolean.FALSE : compare(expected, primitive(fallback));
    }

    private static @Nullable Object primitive(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        var value = element.getAsJsonPrimitive();
        if (value.isBoolean()) return value.getAsBoolean();
        if (value.isNumber()) return value.getAsDouble();
        return value.getAsString();
    }

    private static @Nullable CommentedConfig cached(Path target) {
        long now = System.nanoTime();
        CachedConfig cached = CACHE.get(target);
        if (cached != null && now - cached.checkedAt() < CACHE_NANOS) return cached.config();
        try {
            if (!Files.isRegularFile(target)) {
                CACHE.put(target, new CachedConfig(now, -1, -1, null));
                return null;
            }
            long modified = Files.getLastModifiedTime(target).toMillis();
            long size = Files.size(target);
            if (cached != null && cached.modified() == modified && cached.size() == size) {
                CACHE.put(target, new CachedConfig(now, modified, size, cached.config()));
                return cached.config();
            }
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                CommentedConfig config = CommentedConfig.inMemory();
                new TomlParser().parse(reader, config, ParsingMode.REPLACE);
                CACHE.put(target, new CachedConfig(now, modified, size, config));
                return config;
            }
        } catch (IOException | RuntimeException ignored) {
            CACHE.put(target, new CachedConfig(now, -1, -1, null));
            return null;
        }
    }

    private static @Nullable Boolean compare(JsonElement expected, @Nullable Object actual) {
        if (actual == null || expected == null || !expected.isJsonPrimitive()) return Boolean.FALSE;
        var primitive = expected.getAsJsonPrimitive();
        if (primitive.isBoolean()) return Objects.equals(primitive.getAsBoolean(), actual);
        if (primitive.isNumber() && actual instanceof Number number) {
            return Double.compare(primitive.getAsDouble(), number.doubleValue()) == 0;
        }
        if (primitive.isString()) return Objects.equals(primitive.getAsString(), String.valueOf(actual));
        return Boolean.FALSE;
    }

    private static @Nullable String string(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    private static @Nullable List<String> path(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return value.isBlank() ? null : List.of(value);
        }
        if (!element.isJsonArray()) return null;
        List<String> out = new ArrayList<>();
        for (JsonElement part : element.getAsJsonArray()) {
            String value = string(part);
            if (value == null || value.isBlank()) return null;
            out.add(value);
        }
        return List.copyOf(out);
    }

    private static Path configDirectory() {
        //? if >=1.21 {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        //?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?}
    }
}
