package com.aetherianartificer.townstead.pheno.particle;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every data-pack particle ({@link DataParticleDef}), loaded from {@code data/<ns>/particle/} on the
 * server and mirrored to each client on join and reload. Also the one place Pheno turns a particle
 * id into something it can spawn: a registered particle type, or a data particle.
 */
public final class DataParticles {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/DataParticles");

    /** Raw definitions by id, kept as JSON so the server can sync exactly what it loaded. */
    private static volatile Map<String, String> sources = Map.of();
    private static volatile Map<ResourceLocation, DataParticleDef> defs = Map.of();

    private DataParticles() {}

    public static @Nullable DataParticleDef get(ResourceLocation id) {
        return defs.get(id);
    }

    public static boolean has(ResourceLocation id) {
        return defs.containsKey(id);
    }

    public static Map<String, String> sources() {
        return sources;
    }

    /**
     * What to spawn for {@code id}: a registered parameterless particle type, else a data particle,
     * else null. Registered types win so a pack cannot shadow a vanilla particle by accident.
     */
    public static @Nullable ParticleOptions options(@Nullable ResourceLocation id) {
        if (id == null) return null;
        if (BuiltInRegistries.PARTICLE_TYPE.containsKey(id)
                && BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof ParticleOptions options) {
            return options;
        }
        return has(id) ? new DataParticleOptions(id) : null;
    }

    /** Client side: replace the mirror with what the server sent. */
    public static void accept(Map<String, String> synced) {
        install(synced);
    }

    private static void install(Map<String, String> raw) {
        Map<ResourceLocation, DataParticleDef> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            try {
                DataParticleDef def = DataParticleDef.parse(JsonParser.parseString(entry.getValue()).getAsJsonObject());
                if (def != null) parsed.put(id, def);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse particle {}: {}", id, ex.getMessage());
            }
        }
        sources = Map.copyOf(raw);
        defs = Map.copyOf(parsed);
    }

    /** Server reload listener for {@code data/<ns>/particle/*.json}. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new Gson();

        public Loader() {
            super(GSON, "particle");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            entries = com.aetherianartificer.townstead.data.DataDocuments.resolve("particle", entries, manager);
            Map<String, String> raw = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                if (!(entry.getValue() instanceof JsonObject json) || DataParticleDef.parse(json) == null) {
                    LOGGER.warn("Skipping particle {}: it needs a \"texture\"", entry.getKey());
                    continue;
                }
                raw.put(entry.getKey().toString(), json.toString());
            }
            install(raw);
            if (!raw.isEmpty()) LOGGER.info("Loaded {} data particles", raw.size());
        }
    }
}
