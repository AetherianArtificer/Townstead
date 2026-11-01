package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Evaluates a document's {@code "mods"} or {@code "requires"} gate: whether the mods and data
 * packs a piece of data depends on are installed. The two keys share one grammar, which composes
 * arbitrarily:
 *
 * <pre>
 * "mods": "farmersdelight"                       one mod required
 * "mods": ["farmersdelight", "rusticdelight"]    all required
 * "mods": {"any": ["chefsdelight", "vca"]}       at least one
 * "mods": {"not": "someconflict"}                must be absent
 * "mods": {"all": ["farmersdelight", {"any": ["a", "b"]}], "not": "c"}
 * "requires": {"namespace": "townstead_classic"}  a data pack namespace must be present
 * "requires": {"mod": "ribbits", "namespace": ["a", "b"]}
 * "mods": {"mod": "examplemod", "version": ">=1.2.0 <2"}   a mod in this version range
 * "mods": {"minecraft": ">=1.21"}                          this Minecraft version
 * "mods": {"block": "examplemod:machine"}                  this block is registered
 * "mods": {"block": "examplemod:machine", "property": "stirred"}   and has this block state property
 * </pre>
 *
 * Bare strings are mod ids. Object clauses ({@code all}/{@code any}/{@code not}/{@code mod}/
 * {@code namespace}/{@code minecraft}/{@code block}) may appear together and are ANDed. A namespace
 * is present when any loaded data pack or mod ships data under it (see {@link DataNamespaces}).
 * {@code version} and {@code block} tell apart two builds of one mod that share an id. {@link #evaluate}
 * returns null for a malformed expression so callers can refuse to load the document: a broken gate
 * must never read as satisfied. A {@code block} clause evaluated before blocks are registered, as
 * the boot-time profession scan does, also returns null: absence would mean nothing yet.
 */
public final class ModGate {

    private ModGate() {}

    /** What the build clauses read from the running game. */
    public interface Installed {
        /** The installed version, or null when absent. */
        @Nullable String version(String mod);

        /** Whether the block, and its property when named, exists; null when unknowable or malformed. */
        @Nullable Boolean block(String id, @Nullable String property);
    }

    private static final class Live implements Installed {
        /** Townstead's own block: once it is registered, every mod's blocks are. */
        private static final ResourceLocation REGISTERED_MARKER = ResourceLocation.tryParse("townstead:field_post");

        @Override
        public @Nullable String version(String mod) {
            return ModCompat.version(mod);
        }

        @Override
        public @Nullable Boolean block(String id, @Nullable String property) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(REGISTERED_MARKER)) return null;
            if (!BuiltInRegistries.BLOCK.containsKey(key)) return false;
            if (property == null) return true;
            Block block = BuiltInRegistries.BLOCK.get(key);
            return block.getStateDefinition().getProperty(property) != null;
        }
    }

    /** The document keys that carry a gate. Both use the same grammar and must both be met. */
    public static final String MODS = "mods";
    public static final String REQUIRES = "requires";

    /** Whether a document may load: it declares no gate, or every gate it declares is met. */
    public static boolean allows(@Nullable JsonObject document) {
        return document == null || Boolean.TRUE.equals(evaluateDocument(document));
    }

    /**
     * The combined {@code "mods"} and {@code "requires"} gate of a document: true when absent or
     * met, false when unmet, null when either is malformed.
     */
    public static @Nullable Boolean evaluateDocument(JsonObject document) {
        boolean result = true;
        for (String key : new String[]{MODS, REQUIRES}) {
            if (!document.has(key)) continue;
            Boolean met = evaluate(document.get(key));
            if (met == null) return null;
            result &= met;
        }
        return result;
    }

    public static @Nullable Boolean evaluate(JsonElement expr) {
        return evaluate(expr, ModCompat::isLoaded, DataNamespaces::isPresent);
    }

    public static @Nullable Boolean evaluate(JsonElement expr, Predicate<String> loaded) {
        return evaluate(expr, loaded, DataNamespaces::isPresent);
    }

    public static @Nullable Boolean evaluate(JsonElement expr, Predicate<String> loaded, Predicate<String> namespaces) {
        return evaluate(expr, loaded, namespaces, new Live());
    }

    public static @Nullable Boolean evaluate(JsonElement expr, Predicate<String> loaded, Predicate<String> namespaces,
                                             Installed installed) {
        if (expr == null) return null;
        if (expr.isJsonPrimitive() && expr.getAsJsonPrimitive().isString()) {
            return loaded.test(expr.getAsString());
        }
        if (expr.isJsonArray()) {
            for (JsonElement e : expr.getAsJsonArray()) {
                Boolean met = evaluate(e, loaded, namespaces, installed);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
        if (expr.isJsonObject()) {
            JsonObject obj = expr.getAsJsonObject();
            boolean recognized = false;
            if (obj.has("all")) {
                recognized = true;
                Boolean met = evaluate(obj.get("all"), loaded, namespaces, installed);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("any")) {
                recognized = true;
                if (!obj.get("any").isJsonArray()) return null;
                boolean anyMet = false;
                for (JsonElement e : obj.getAsJsonArray("any")) {
                    Boolean met = evaluate(e, loaded, namespaces, installed);
                    if (met == null) return null;
                    if (met) anyMet = true;
                }
                if (!anyMet) return Boolean.FALSE;
            }
            if (obj.has("not")) {
                recognized = true;
                Boolean met = evaluate(obj.get("not"), loaded, namespaces, installed);
                if (met == null) return null;
                if (met) return Boolean.FALSE;
            }
            if (obj.has("mod")) {
                recognized = true;
                Boolean met = obj.has("version")
                        ? modInRange(obj.get("mod"), obj.get("version"), loaded, installed)
                        : allNames(obj.get("mod"), loaded);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            } else if (obj.has("version")) {
                return null;
            }
            if (obj.has("namespace")) {
                recognized = true;
                Boolean met = allNames(obj.get("namespace"), namespaces);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("minecraft")) {
                recognized = true;
                Boolean met = inRange("minecraft", obj.get("minecraft"), installed);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("block")) {
                recognized = true;
                Boolean met = blockExists(obj, installed);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            } else if (obj.has("property")) {
                return null;
            }
            return recognized ? Boolean.TRUE : null;
        }
        return null;
    }

    /** Whether the one named mod is loaded and in range; null when malformed. */
    private static @Nullable Boolean modInRange(JsonElement mod, JsonElement range, Predicate<String> loaded,
                                                Installed installed) {
        if (!isString(mod)) return null;
        Boolean met = inRange(mod.getAsString(), range, installed);
        if (met == null) return null;
        return met && loaded.test(mod.getAsString());
    }

    private static @Nullable Boolean inRange(String mod, JsonElement range, Installed installed) {
        if (!isString(range)) return null;
        VersionRange parsed = VersionRange.parse(range.getAsString());
        if (parsed == null) return null;
        String version = installed.version(mod);
        return version != null && parsed.contains(version);
    }

    private static @Nullable Boolean blockExists(JsonObject clause, Installed installed) {
        JsonElement block = clause.get("block");
        JsonElement property = clause.get("property");
        if (!isString(block) || (property != null && !isString(property))) return null;
        return installed.block(block.getAsString(), property == null ? null : property.getAsString());
    }

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    /** A name or array of names, all of which must pass {@code present}; null when malformed. */
    private static @Nullable Boolean allNames(JsonElement value, Predicate<String> present) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return present.test(value.getAsString());
        }
        if (!value.isJsonArray()) return null;
        boolean all = true;
        for (JsonElement e : value.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
            if (!present.test(e.getAsString())) all = false;
        }
        return all;
    }
}
