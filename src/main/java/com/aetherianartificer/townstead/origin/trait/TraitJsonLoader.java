package com.aetherianartificer.townstead.origin.trait;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.origin.trait.effect.TraitEffectTypes;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@link DataTrait}s from {@code data/<ns>/trait/*.json} (MCA trait id = file
 * path, e.g. {@code townstead_origins:immortal}) and bridges each into MCA's
 * {@code Traits} registry so it behaves as a first-class MCA trait. Each file may
 * declare {@code chance} (default 0 — never random-rolls), {@code inherit} (default
 * 1.0), {@code usable_on_player} (default true so MCA's editor lists it),
 * {@code hidden}, and an {@code effects} array of single-entry objects
 * {@code [{ "<key>": <config> }]} keyed by a registered {@link TraitEffectTypes} key.
 *
 * <p>The same registration runs client-side from the catalog sync, so the editor
 * lists data-pack traits on remote clients too.</p>
 */
public final class TraitJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/TraitJsonLoader");
    private static final Gson GSON = new Gson();

    public TraitJsonLoader() {
        super(GSON, "trait");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, DataTrait> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(obj, "townstead:trait/v1");
                DataTrait trait = parse(file.toString(), obj);
                if (trait != null) parsed.put(trait.id(), trait);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse trait {}: {}", file, ex.getMessage());
            }
        }
        TraitRegistry.replaceAll(parsed);
        registerWithMca(parsed.values());
        LOGGER.info("Loaded {} traits", parsed.size());
    }

    /** Build a {@link DataTrait} from one file's JSON; id is the file id (e.g. {@code ns:path}). */
    public static DataTrait parse(String id, JsonObject obj) {
        float chance = GsonHelper.getAsFloat(obj, "chance", 0.0f);
        float inherit = GsonHelper.getAsFloat(obj, "inherit", 1.0f);
        boolean usableOnPlayer = GsonHelper.getAsBoolean(obj, "usable_on_player", true);
        boolean hidden = GsonHelper.getAsBoolean(obj, "hidden", false);
        Map<String, JsonElement> effects = new LinkedHashMap<>();
        if (obj.has("effects") && obj.get("effects").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("effects")) {
                if (!el.isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    var type = TraitEffectTypes.get(e.getKey()).orElse(null);
                    if (type == null) {
                        LOGGER.warn("{} — unknown trait effect '{}', skipping", id, e.getKey());
                        continue;
                    }
                    if (!type.validate(e.getValue())) {
                        LOGGER.warn("{} — invalid config for trait effect '{}', skipping", id, e.getKey());
                        continue;
                    }
                    effects.put(e.getKey(), e.getValue());
                }
            }
        }
        return new DataTrait(id, chance, inherit, usableOnPlayer, hidden, effects);
    }

    /**
     * Register a set of data-pack traits into MCA's registry, then ask MCA to enable all
     * registered traits via {@code Config.autocomplete()} (so the editor lists them). Wrapped
     * defensively: an MCA API change must never abort the data-pack reload.
     */
    public static void registerWithMca(Iterable<DataTrait> traits) {
        boolean any = false;
        for (DataTrait t : traits) {
            try {
                Traits.registerTrait(t.id(), t.chance(), t.inherit(), t.usableOnPlayer());
                any = true;
            } catch (Throwable e) {
                LOGGER.warn("Could not register trait {} with MCA: {}", t.id(), e.toString());
            }
        }
        if (any) enableRegisteredTraits();
    }

    /** MCA's own "default every registered trait to enabled" pass; isolated so drift can't crash reload. */
    public static void enableRegisteredTraits() {
        try {
            Config.getInstance().autocomplete();
        } catch (Throwable e) {
            LOGGER.warn("MCA Config.autocomplete() unavailable: {}", e.toString());
        }
    }
}
