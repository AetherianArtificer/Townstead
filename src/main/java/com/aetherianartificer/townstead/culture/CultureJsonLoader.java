package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.LegacyIds;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.aetherianartificer.townstead.naming.FamilyNames;
import com.aetherianartificer.townstead.naming.GivenNames;
import com.aetherianartificer.townstead.naming.NameLists;
import com.aetherianartificer.townstead.naming.NamingTradition;
import com.aetherianartificer.townstead.naming.NamingTraditionJsonLoader;
import com.aetherianartificer.townstead.naming.NamingTraditions;
import com.aetherianartificer.townstead.root.Demonym;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads cultures from {@code data/<ns>/culture/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:culture/v1",
 *   "name": "townstead_mobs.culture.piglish_ridge",
 *   "naming_tradition": "townstead_mobs:piglish_ridge"
 * }
 * }</pre>
 *
 * <p>A culture that needs no tradition of its own writes one in place instead, so the simple case is
 * a single file:</p>
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:culture/v1",
 *   "name": "townstead_mobs.culture.piglish_ridge",
 *   "naming": {
 *     "given": { "male": ["Grunk"], "female": ["Ashka"] },
 *     "family": { "type": "patronymic", "affix": { "male": { "suffix": "sson" } } }
 *   }
 * }
 * }</pre>
 *
 * <p>An optional {@code demonym} names the culture's people, with the same fields as a Root's:
 * {@code singular}, {@code plural} (defaults to singular) and {@code adjective}. Dialogue prints it
 * through the {@code self_demonym} family of placeholders.</p>
 */
public final class CultureJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/CultureJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:culture/v1";

    public CultureJsonLoader() {
        super(GSON, "culture");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, NamingTradition> inlineTraditions = new LinkedHashMap<>();
        Map<ResourceLocation, GivenNames> inlineGiven = new LinkedHashMap<>();
        Map<ResourceLocation, FamilyNames> inlineFamily = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> legacyIds = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                Component displayName = root.has("name")
                        ? DataPackLang.parseComponent(root.get("name"), file.toString(), lang)
                        : Component.literal(file.getPath());

                // A tradition written in place belongs to this culture alone and is keyed by its
                // id, so a self-contained culture is one file and shared traditions stay referable.
                ResourceLocation traditionId;
                JsonObject written = GsonHelper.getAsJsonObject(root, "naming", null);
                if (written != null) {
                    NamingTradition inline = NamingTraditionJsonLoader.parse(file, written);
                    if (inline != null) {
                        inlineTraditions.put(file, inline);
                        GivenNames names = NamingTraditionJsonLoader.inlineGivenOf(file, written);
                        if (names != null) inlineGiven.put(file, names);
                        FamilyNames surnames = NamingTraditionJsonLoader.inlineFamilyOf(
                                file, GsonHelper.getAsJsonObject(written, "family", null));
                        if (surnames != null) inlineFamily.put(file, surnames);
                        traditionId = file;
                    } else {
                        LOGGER.warn("Culture {} writes a naming tradition in place but it is unusable", file);
                        traditionId = null;
                    }
                } else {
                    String tradition = GsonHelper.getAsString(root, "naming_tradition", "").trim();
                    traditionId = tradition.isEmpty() ? null : ResourceLocation.tryParse(tradition);
                    if (!tradition.isEmpty() && traditionId == null) {
                        LOGGER.warn("Culture {} names an unreadable naming tradition '{}'", file, tradition);
                    }
                }

                String settlementNames = GsonHelper.getAsString(root, "settlement_names", "").trim();
                ResourceLocation settlementNamesId = settlementNames.isEmpty()
                        ? null : ResourceLocation.tryParse(settlementNames);
                if (!settlementNames.isEmpty() && settlementNamesId == null) {
                    LOGGER.warn("Culture {} names an unreadable settlement-name list '{}'", file, settlementNames);
                }

                var aliases = LegacyIds.parse(root, file);
                for (ResourceLocation legacyId : aliases) {
                    ResourceLocation previous = legacyIds.get(legacyId);
                    if (previous != null && !previous.equals(file)) {
                        throw new IllegalArgumentException("legacy id " + legacyId
                                + " is already claimed by " + previous);
                    }
                }
                loaded.put(file, new Culture(file, displayName, traditionId, settlementNamesId,
                        CultureClothing.parse(root), root.has("faction_names")
                                ? ResourceLocation.tryParse(GsonHelper.getAsString(root, "faction_names")) : null,
                        Demonym.parse(root, file.toString(), lang), parent(root, file), spirit(root, file),
                        forms(root, file)));
                for (ResourceLocation legacyId : aliases) legacyIds.put(legacyId, file);
            } catch (Exception exception) {
                LOGGER.warn("Could not load culture {}", file, exception);
            }
        }

        NameLists.addInlineGiven(inlineGiven);
        NameLists.addInlineFamily(inlineFamily);
        NamingTraditions.addInline(inlineTraditions);
        for (ResourceLocation canonical : loaded.keySet()) {
            ResourceLocation claimant = legacyIds.remove(canonical);
            if (claimant != null) {
                LOGGER.warn("Culture {} cannot be a legacy id for {}; the canonical definition wins",
                        canonical, claimant);
            }
        }
        Map<ResourceLocation, Culture> resolved = inherit(loaded);
        Cultures.replace(resolved, legacyIds);
        LOGGER.info("Loaded {} culture(s)", resolved.size());
    }

    private static @Nullable ResourceLocation parent(JsonObject root, ResourceLocation file) {
        if (!root.has("parent")) return null;
        ResourceLocation parent = DataPackLang.parseId(GsonHelper.getAsString(root, "parent"));
        if (parent == null) throw new IllegalArgumentException("'parent' must be a culture id");
        if (parent.equals(file)) throw new IllegalArgumentException("A culture cannot be its own parent");
        return parent;
    }

    /** {@code "spirit": { "commercial": 1.0, "nautical": 0.6 }}: Community Spirit axes and their pull. */
    private static Map<String, Float> spirit(JsonObject root, ResourceLocation file) {
        JsonObject json = GsonHelper.getAsJsonObject(root, "spirit", null);
        if (json == null) return Map.of();
        Map<String, Float> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            float weight = GsonHelper.convertToFloat(e.getValue(), "spirit." + e.getKey());
            if (!Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Spirit weight for " + e.getKey() + " must be non-negative");
            if (SpiritRegistry.get(e.getKey()).isEmpty()) {
                LOGGER.warn("Culture {} names unknown spirit '{}'", file, e.getKey());
                continue;
            }
            if (weight > 0) out.put(e.getKey(), weight);
        }
        return out;
    }

    /** {@code "forms": [ { "profile": "ns:id", "weight": 3 } ]}: founding profiles this culture's towns use. */
    private static List<Culture.Form> forms(JsonObject root, ResourceLocation file) {
        if (!root.has("forms")) return List.of();
        List<Culture.Form> out = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(root, "forms")) {
            JsonObject json = GsonHelper.convertToJsonObject(element, "forms[]");
            ResourceLocation profile = DataPackLang.parseId(GsonHelper.getAsString(json, "profile"));
            if (profile == null) throw new IllegalArgumentException("Every form needs a founding profile id");
            float weight = GsonHelper.getAsFloat(json, "weight", 1.0F);
            if (!Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Form weight must be non-negative");
            if (weight > 0) out.add(new Culture.Form(profile, weight));
        }
        return out;
    }

    /**
     * Fills each subculture's unset fields from its parent, root first. Players see only the root, so
     * a subculture always takes the root's display name. A parent that is missing or loops back is
     * dropped with a warning, and the culture stands alone.
     */
    static Map<ResourceLocation, Culture> inherit(Map<ResourceLocation, Culture> loaded) {
        Map<ResourceLocation, Culture> resolved = new LinkedHashMap<>();
        for (ResourceLocation id : loaded.keySet()) resolve(id, loaded, resolved, new LinkedHashSet<>());
        return resolved;
    }

    private static Culture resolve(ResourceLocation id, Map<ResourceLocation, Culture> loaded,
                                   Map<ResourceLocation, Culture> resolved, Set<ResourceLocation> visiting) {
        Culture done = resolved.get(id);
        if (done != null) return done;
        Culture own = loaded.get(id);
        if (own.parent() == null) {
            resolved.put(id, own);
            return own;
        }
        visiting.add(id);
        Culture parentDef = loaded.get(own.parent());
        if (parentDef == null || visiting.contains(own.parent())) {
            LOGGER.warn("Culture {} has parent {} which is {}; it stands alone", id, own.parent(),
                    parentDef == null ? "not loaded" : "part of a loop");
            Culture alone = new Culture(id, own.displayName(), own.namingTradition(), own.settlementNames(),
                    own.clothing(), own.factionNames(), own.demonym(), null, own.spirit(), own.forms());
            resolved.put(id, alone);
            return alone;
        }
        Culture parent = resolve(own.parent(), loaded, resolved, visiting);
        Culture merged = new Culture(id, parent.displayName(),
                own.namingTradition() != null ? own.namingTradition() : parent.namingTradition(),
                own.settlementNames() != null ? own.settlementNames() : parent.settlementNames(),
                own.clothing().inheriting(parent.clothing()),
                own.factionNames() != null ? own.factionNames() : parent.factionNames(),
                own.demonym() != null ? own.demonym() : parent.demonym(),
                own.parent(), own.spirit(), own.forms());
        resolved.put(id, merged);
        return merged;
    }
}
