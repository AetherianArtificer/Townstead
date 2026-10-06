package com.aetherianartificer.townstead.replace.behavior;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.replace.MobReplacement;
import com.aetherianartificer.townstead.replace.MobReplacer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** Loaded behavior profiles, and the one an entity acts on now. */
public final class BehaviorProfiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/BehaviorProfiles");
    private static volatile Map<ResourceLocation, BehaviorProfile> loaded = Map.of();

    private BehaviorProfiles() {}

    /** The profile of a wild replacement whose states still hold, else one whose {@code while_state} is active, else null. */
    public static @Nullable BehaviorProfile of(LivingEntity entity) {
        if (loaded.isEmpty()) return null;
        MobReplacement replacement = MobReplacer.activeReplacement(entity);
        if (replacement != null && replacement.behavior() != null) return loaded.get(replacement.behavior());
        for (BehaviorProfile profile : loaded.values()) {
            if (profile.whileState() != null
                    && com.aetherianartificer.townstead.pheno.state.EntityStates.resolve(entity, profile.whileState()).active()) {
                return profile;
            }
        }
        return null;
    }

    public static <T extends BehaviorProfile.Behavior> @Nullable T behavior(LivingEntity entity, ResourceLocation type, Class<T> kind) {
        BehaviorProfile profile = of(entity);
        return profile == null ? null : profile.get(type, kind);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "behavior_profile"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, BehaviorProfile> parsed = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!ModGate.allows(json)) continue;
                    parsed.put(entry.getKey(), BehaviorProfile.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    LOGGER.warn("Behavior profile {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            loaded = Map.copyOf(parsed);
            LOGGER.info("Loaded {} behavior profiles", parsed.size());
        }
    }
}
