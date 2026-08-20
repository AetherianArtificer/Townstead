package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Evaluates a document's {@code "mods"} gate: whether the mods a piece of data depends on are
 * installed. The expression grammar composes arbitrarily:
 *
 * <pre>
 * "mods": "farmersdelight"                       one mod required
 * "mods": ["farmersdelight", "rusticdelight"]    all required
 * "mods": {"any": ["chefsdelight", "vca"]}       at least one
 * "mods": {"not": "someconflict"}                must be absent
 * "mods": {"all": ["farmersdelight", {"any": ["a", "b"]}], "not": "c"}
 * </pre>
 *
 * Object clauses ({@code all}/{@code any}/{@code not}) may appear together and are ANDed.
 * {@link #evaluate} returns null for a malformed expression so callers can refuse to load the
 * document: a broken gate must never read as satisfied.
 */
public final class ModGate {

    private ModGate() {}

    public static @Nullable Boolean evaluate(JsonElement expr) {
        return evaluate(expr, ModCompat::isLoaded);
    }

    public static @Nullable Boolean evaluate(JsonElement expr, Predicate<String> loaded) {
        if (expr == null) return null;
        if (expr.isJsonPrimitive() && expr.getAsJsonPrimitive().isString()) {
            return loaded.test(expr.getAsString());
        }
        if (expr.isJsonArray()) {
            for (JsonElement e : expr.getAsJsonArray()) {
                Boolean met = evaluate(e, loaded);
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
                Boolean met = evaluate(obj.get("all"), loaded);
                if (met == null) return null;
                if (!met) return Boolean.FALSE;
            }
            if (obj.has("any")) {
                recognized = true;
                if (!obj.get("any").isJsonArray()) return null;
                boolean anyMet = false;
                for (JsonElement e : obj.getAsJsonArray("any")) {
                    Boolean met = evaluate(e, loaded);
                    if (met == null) return null;
                    if (met) anyMet = true;
                }
                if (!anyMet) return Boolean.FALSE;
            }
            if (obj.has("not")) {
                recognized = true;
                Boolean met = evaluate(obj.get("not"), loaded);
                if (met == null) return null;
                if (met) return Boolean.FALSE;
            }
            return recognized ? Boolean.TRUE : null;
        }
        return null;
    }
}
