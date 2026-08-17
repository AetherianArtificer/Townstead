package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A kind of tie between people, defined in {@code data/<ns>/bond_kind/}. Nothing
 * about marriage or friendship is written in Java: a pack declares what kinds
 * exist and how they behave, and {@code pheno:bonds} and chronicle templates
 * refer to them by id.
 *
 * <ul>
 *   <li>{@code max_active} — how many may hold at once, 0 for no limit
 *       (marriage 1, friendship unlimited).</li>
 *   <li>{@code unique_per_pair} — whether the same two people may form it more
 *       than once. Two people become fast friends once.</li>
 *   <li>{@code symmetric} — whether it means the same in both directions.</li>
 *   <li>{@code source} — an engine-provided feed for kinds Townstead does not
 *       store itself yet, e.g. {@code mca:marriage}. Unknown sources are inert,
 *       so a pack naming one is not an error.</li>
 * </ul>
 */
public record BondKind(ResourceLocation id, String displayLangKey, String displayLiteral,
                       int maxActive, boolean uniquePerPair, boolean symmetric,
                       @Nullable String source) {

    public static final String SCHEMA = "townstead:bond_kind/v1";

    public BondKind {
        displayLangKey = displayLangKey == null ? "" : displayLangKey;
        displayLiteral = displayLiteral == null ? "" : displayLiteral;
        maxActive = Math.max(0, maxActive);
    }

    /** What an unregistered id behaves like: unlimited, repeatable, symmetric. */
    public static BondKind fallback(ResourceLocation id) {
        return new BondKind(id, "", id.getPath(), 0, false, true, null);
    }

    public boolean unlimited() {
        return maxActive == 0;
    }

    public static BondKind parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        TownsteadSchema.validate(json, SCHEMA);
        String defaultKey = "bond." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String langKey = defaultKey;
        String literal = id.getPath();
        if (json.has("display")) {
            JsonObject display = GsonHelper.getAsJsonObject(json, "display");
            if (display.has("translate")) {
                langKey = GsonHelper.getAsString(display, "translate");
            } else if (display.has("text")) {
                literal = GsonHelper.getAsString(display, "text");
            }
        }
        String indexed = lang.get(langKey);
        literal = indexed != null ? indexed : DataPackLang.resolveFallback(langKey, "en_us", literal);
        return new BondKind(id, langKey, literal,
                GsonHelper.getAsInt(json, "max_active", 0),
                GsonHelper.getAsBoolean(json, "unique_per_pair", false),
                GsonHelper.getAsBoolean(json, "symmetric", true),
                json.has("source") ? GsonHelper.getAsString(json, "source") : null);
    }
}
