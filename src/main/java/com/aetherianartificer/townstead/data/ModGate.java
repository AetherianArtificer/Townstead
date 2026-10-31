package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * </pre>
 *
 * Bare strings are mod ids. Object clauses ({@code all}/{@code any}/{@code not}/{@code mod}/
 * {@code namespace}) may appear together and are ANDed. A namespace is present when any loaded
 * data pack or mod ships data under it (see {@link DataNamespaces}). {@link #evaluate} returns
 * null for a malformed expression so callers can refuse to load the document: a broken gate must
 * never read as satisfied.
 */
public final class ModGate {

    private ModGate() {}

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
        if (expr == null) return null;
        if (expr.isJsonPrimitive() && expr.getAsJsonPrimitive().isString()) {
            return loaded.test(expr.getAsString());
        }
        if (expr.isJsonArray()) {
            for (JsonElement e : expr.getAsJsonArray()) {
                Boolean met = evaluate(e, loaded, namespaces);
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
                Boolean met = evaluate(obj.get("all"), loaded, namespaces);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("any")) {
                recognized = true;
                if (!obj.get("any").isJsonArray()) return null;
                boolean anyMet = false;
                for (JsonElement e : obj.getAsJsonArray("any")) {
                    Boolean met = evaluate(e, loaded, namespaces);
                    if (met == null) return null;
                    if (met) anyMet = true;
                }
                if (!anyMet) return Boolean.FALSE;
            }
            if (obj.has("not")) {
                recognized = true;
                Boolean met = evaluate(obj.get("not"), loaded, namespaces);
                if (met == null) return null;
                if (met) return Boolean.FALSE;
            }
            if (obj.has("mod")) {
                recognized = true;
                Boolean met = allNames(obj.get("mod"), loaded);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("namespace")) {
                recognized = true;
                Boolean met = allNames(obj.get("namespace"), namespaces);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            return recognized ? Boolean.TRUE : null;
        }
        return null;
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
