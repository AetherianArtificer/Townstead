package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?} else if forge {
/*import net.minecraftforge.common.ForgeConfigSpec;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every server setting the Switchboard can override, keyed by its dotted TOML path
 * ({@code needs.hunger.enableVillagerHunger}), read straight from the server spec so a new
 * config value is overridable the moment it is defined.
 */
public final class SettingIndex {
    private SettingIndex() {}

    public record Entry(String key, Supplier<?> value, Object defaultValue, Object spec) {}

    private static volatile Map<String, Entry> byKey;
    private static volatile Map<String, Entry> clientByKey;

    public static Collection<Entry> all() {
        return index().values();
    }

    public static @Nullable Entry get(String key) {
        return key == null ? null : index().get(key);
    }

    /** The key of a server config value, or null when it is absent (e.g. a compat value never defined). */
    public static @Nullable String keyOf(@Nullable Object configValue) {
        if (configValue == null) return null;
        for (Entry entry : all()) if (entry.value() == configValue) return entry.key();
        return null;
    }

    /** This computer's own settings, which never belong to a world. */
    public static Collection<Entry> client() {
        Map<String, Entry> map = clientByKey;
        if (map == null) {
            synchronized (SettingIndex.class) {
                if (clientByKey == null) clientByKey = build(TownsteadConfig.CLIENT_SPEC);
                map = clientByKey;
            }
        }
        return map.values();
    }

    private static Map<String, Entry> index() {
        Map<String, Entry> map = byKey;
        if (map == null) {
            synchronized (SettingIndex.class) {
                if (byKey == null) byKey = build(TownsteadConfig.SERVER_SPEC);
                map = byKey;
            }
        }
        return map;
    }

    /** Indexes a different server spec. Tests only: the real spec needs a running mod loader. */
    static synchronized void useSpec(Object spec) {
        byKey = build(spec);
    }

    private static Map<String, Entry> build(Object spec) {
        //? if neoforge {
        ModConfigSpec server = (ModConfigSpec) spec;
        //?} else if forge {
        /*ForgeConfigSpec server = (ForgeConfigSpec) spec;
        *///?}
        Map<String, Entry> out = new LinkedHashMap<>();
        walk(server.getValues(), server.getSpec(), new ArrayList<>(), out);
        return Collections.unmodifiableMap(out);
    }

    private static void walk(UnmodifiableConfig config, UnmodifiableConfig specs, List<String> path,
                             Map<String, Entry> out) {
        for (Map.Entry<String, Object> e : config.valueMap().entrySet()) {
            path.add(e.getKey());
            if (e.getValue() instanceof UnmodifiableConfig child) {
                walk(child, specs, path, out);
            } else if (e.getValue() instanceof Supplier<?> value) {
                Object spec = specs.get(path);
                Object def = defaultOf(spec);
                if (def != null) {
                    String key = String.join(".", path);
                    out.put(key, new Entry(key, value, def, spec));
                }
            }
            path.remove(path.size() - 1);
        }
    }

    private static @Nullable Object defaultOf(Object spec) {
        //? if neoforge {
        return spec instanceof ModConfigSpec.ValueSpec vs ? vs.getDefault() : null;
        //?} else if forge {
        /*return spec instanceof ForgeConfigSpec.ValueSpec vs ? vs.getDefault() : null;
        *///?}
    }

    public static @Nullable String translationKey(Entry entry) {
        //? if neoforge {
        return entry.spec() instanceof ModConfigSpec.ValueSpec vs ? vs.getTranslationKey() : null;
        //?} else if forge {
        /*return entry.spec() instanceof ForgeConfigSpec.ValueSpec vs ? vs.getTranslationKey() : null;
        *///?}
    }

    public static @Nullable String comment(Entry entry) {
        //? if neoforge {
        return entry.spec() instanceof ModConfigSpec.ValueSpec vs ? vs.getComment() : null;
        //?} else if forge {
        /*return entry.spec() instanceof ForgeConfigSpec.ValueSpec vs ? vs.getComment() : null;
        *///?}
    }

    /** The {@code [min, max]} of a ranged number, or null when the value is not ranged. */
    public static @Nullable Number[] range(Entry entry) {
        //? if neoforge {
        if (!(entry.spec() instanceof ModConfigSpec.ValueSpec vs) || vs.getRange() == null) return null;
        //?} else if forge {
        /*if (!(entry.spec() instanceof ForgeConfigSpec.ValueSpec vs) || vs.getRange() == null) return null;
        *///?}
        return vs.getRange().getMin() instanceof Number min && vs.getRange().getMax() instanceof Number max
                ? new Number[]{min, max} : null;
    }

    /** Sets a client value and saves the client config file. */
    @SuppressWarnings("unchecked")
    public static void writeClient(Entry entry, Object value) {
        //? if neoforge {
        ((ModConfigSpec.ConfigValue<Object>) entry.value()).set(value);
        //?} else if forge {
        /*((ForgeConfigSpec.ConfigValue<Object>) entry.value()).set(value);
        *///?}
    }

    public static void saveClient() {
        TownsteadConfig.CLIENT_SPEC.save();
    }

    private static boolean accepts(Entry entry, Object value) {
        //? if neoforge {
        return entry.spec() instanceof ModConfigSpec.ValueSpec vs && vs.test(value);
        //?} else if forge {
        /*return entry.spec() instanceof ForgeConfigSpec.ValueSpec vs && vs.test(value);
        *///?}
    }

    /** Converts JSON to the setting's Java type, or null when it is the wrong shape or out of range. */
    public static @Nullable Object parse(Entry entry, @Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) return null;
        Object def = entry.defaultValue();
        Object value;
        try {
            if (def instanceof Boolean) {
                value = json.getAsJsonPrimitive().isBoolean() ? json.getAsBoolean()
                        : Boolean.parseBoolean(json.getAsString());
            } else if (def instanceof Integer) {
                value = json.getAsInt();
            } else if (def instanceof Long) {
                value = json.getAsLong();
            } else if (def instanceof Double) {
                value = json.getAsDouble();
            } else if (def instanceof Enum<?> e) {
                value = enumConstant(e.getDeclaringClass(), json.getAsString());
            } else if (def instanceof List<?>) {
                if (!json.isJsonArray()) return null;
                List<String> list = new ArrayList<>();
                for (JsonElement part : json.getAsJsonArray()) list.add(part.getAsString());
                value = List.copyOf(list);
            } else if (def instanceof String) {
                value = json.getAsString();
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return value != null && accepts(entry, value) ? value : null;
    }

    public static JsonElement toJson(Object value) {
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (value instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object part : list) array.add(String.valueOf(part));
            return array;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    private static @Nullable Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name.trim())) return constant;
        }
        return null;
    }
}
