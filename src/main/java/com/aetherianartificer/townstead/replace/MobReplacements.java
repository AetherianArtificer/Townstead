package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loaded mob replacements, looked up by the spawning mob's type. */
public final class MobReplacements {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/MobReplacements");
    private static volatile List<MobReplacement> loaded = List.of();

    private MobReplacements() {}

    public static boolean any() {
        return !loaded.isEmpty();
    }

    /** The first replacement that names this mob type, directly or by tag. */
    public static @Nullable MobReplacement forType(EntityType<?> type) {
        List<MobReplacement> all = loaded;
        if (all.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        for (MobReplacement replacement : all) {
            if (replacement.types().contains(id)) return replacement;
            for (ResourceLocation tag : replacement.tags()) {
                if (type.is(TagKey.create(Registries.ENTITY_TYPE, tag))) return replacement;
            }
        }
        return null;
    }

    public static @Nullable MobReplacement byId(ResourceLocation id) {
        for (MobReplacement replacement : loaded) if (replacement.id().equals(id)) return replacement;
        return null;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "mob_replacement"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            List<MobReplacement> parsed = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (json.has("mods") && ModGate.evaluate(json.get("mods")) == null) {
                        throw new IllegalArgumentException("'mods' is malformed");
                    }
                    if (!ModGate.allows(json)) continue;
                    parsed.add(MobReplacement.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    LOGGER.warn("Mob replacement {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            loaded = List.copyOf(parsed);
            LOGGER.info("Loaded {} mob replacements", parsed.size());
        }
    }
}
