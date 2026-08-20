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

/**
 * Read-only predicate over another mod's ordinary TOML configuration.
 *
 * <p>The external fact stays in data ({@code file}, {@code path}, and {@code equals}); Java knows
 * nothing about the owning mod or its classes. Paths are resolved below the loader's config
 * directory and cannot escape it. A malformed or unreadable gate fails closed.</p>
 */
public final class ConfigGate {

    private ConfigGate() {}

    public static @Nullable Boolean evaluate(@Nullable JsonElement expression) {
        if (!valid(expression)) return null;
        JsonObject object = expression.getAsJsonObject();
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        if (file == null || file.isBlank() || path == null || path.isEmpty()
                || expected == null || expected.isJsonNull()) return null;

        Path root = configDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(file).normalize();
        if (!target.startsWith(root) || !target.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".toml")) return null;
        if (!Files.isRegularFile(target)) return Boolean.FALSE;

        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            CommentedConfig config = CommentedConfig.inMemory();
            new TomlParser().parse(reader, config, ParsingMode.REPLACE);
            return compare(expected, config.get(path));
        } catch (IOException | RuntimeException ignored) {
            return Boolean.FALSE;
        }
    }

    /** World-free seam used by schema tests and by future config backends. */
    static @Nullable Boolean evaluate(@Nullable JsonElement expression,
                                      Function<List<String>, Object> lookup) {
        if (!valid(expression) || lookup == null) return null;
        JsonObject object = expression.getAsJsonObject();
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        if (path == null || path.isEmpty() || expected == null || expected.isJsonNull()) return null;
        return compare(expected, lookup.apply(path));
    }

    public static boolean valid(@Nullable JsonElement expression) {
        if (expression == null || !expression.isJsonObject()) return false;
        JsonObject object = expression.getAsJsonObject();
        String file = string(object.get("file"));
        List<String> path = path(object.get("path"));
        JsonElement expected = object.get("equals");
        return file != null && !file.isBlank() && path != null && !path.isEmpty()
                && expected != null && expected.isJsonPrimitive();
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
