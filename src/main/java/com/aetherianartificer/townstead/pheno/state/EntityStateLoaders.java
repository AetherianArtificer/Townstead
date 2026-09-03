package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reload listeners for the independently extensible state identity, backing, and effect families. */
public final class EntityStateLoaders {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/EntityStates");

    private EntityStateLoaders() {}

    public static final class Definitions extends SimpleJsonResourceReloadListener {
        public Definitions() { super(GSON, "entity_state"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, EntityStateDefinition> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!enabled(json)) continue;
                    EntityStateDefinition definition = EntityStateDefinition.parse(entry.getKey(), json);
                    if (loaded.containsKey(definition.id())) {
                        throw new IllegalArgumentException("duplicate canonical state id '" + definition.id() + "'");
                    }
                    loaded.put(definition.id(), definition);
                } catch (Exception exception) {
                    LOGGER.warn("Entity state {} rejected: {}", entry.getKey(), message(exception));
                }
            }
            EntityStates.replaceDefinitions(loaded);
            LOGGER.info("Loaded {} open entity-state identities", loaded.size());
        }
    }

    public static final class Backings extends SimpleJsonResourceReloadListener {
        public Backings() { super(GSON, "state_backing"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<StateBacking> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!enabled(json)) continue;
                    StateBacking backing = StateBacking.parse(entry.getKey(), json);
                    EntityStateDefinition definition = EntityStates.definition(backing.state());
                    if (definition == null) throw new IllegalArgumentException("unknown canonical state '" + backing.state() + "'");
                    for (StateBacking.Level level : backing.amplifierLevels().values()) {
                        if (level.tier() != null && definition.tier(level.tier()) == null) {
                            throw new IllegalArgumentException("unknown tier '" + level.tier() + "' for " + backing.state());
                        }
                    }
                    loaded.add(backing);
                } catch (Exception exception) {
                    LOGGER.warn("State backing {} rejected: {}", entry.getKey(), message(exception));
                }
            }
            EntityStates.replaceBackings(loaded);
            LOGGER.info("Loaded {} open entity-state backings", loaded.size());
        }
    }

    public static final class Effects extends SimpleJsonResourceReloadListener {
        public Effects() { super(GSON, "state_effect"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<StateEffect> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!enabled(json)) continue;
                    StateEffect effect = StateEffect.parse(entry.getKey(), json);
                    EntityStateDefinition definition = EntityStates.definition(effect.state());
                    if (definition == null) throw new IllegalArgumentException("unknown canonical state '" + effect.state() + "'");
                    if (effect.tier() != null && definition.tier(effect.tier()) == null) {
                        throw new IllegalArgumentException("unknown tier '" + effect.tier() + "' for " + effect.state());
                    }
                    loaded.add(effect);
                } catch (Exception exception) {
                    LOGGER.warn("State effect {} rejected: {}", entry.getKey(), message(exception));
                }
            }
            EntityStates.replaceEffects(loaded);
            LOGGER.info("Loaded {} open entity-state effect contributions", loaded.size());
        }
    }

    private static boolean enabled(JsonObject json) {
        if (!json.has("mods")) return true;
        Boolean enabled = ModGate.evaluate(json.get("mods"));
        if (enabled == null) throw new IllegalArgumentException("'mods' is malformed");
        return enabled;
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
