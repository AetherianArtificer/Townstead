package com.aetherianartificer.townstead.origin.disposition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads disposition group definitions from {@code data/<ns>/disposition/<group>.json}: each file is
 * one group named by its path, with optional {@code members} (entity-type ids that belong to it) and
 * {@code friendly} / {@code hostile} group-name lists. Feeds {@link DispositionRelations}.
 */
public final class DispositionRelationsLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Disposition");
    private static final Gson GSON = new Gson();

    public DispositionRelationsLoader() {
        super(GSON, "disposition");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, DispositionRelations.GroupDef> groups = new HashMap<>();
        Map<EntityType<?>, String> members = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            String group = entry.getKey().getPath();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                TownsteadSchema.validate(obj, "townstead:disposition/v1");
                groups.put(group, new DispositionRelations.GroupDef(
                        names(obj, "friendly"), names(obj, "hostile")));
                if (obj.has("members") && obj.get("members").isJsonArray()) {
                    for (JsonElement m : obj.getAsJsonArray("members")) {
                        ResourceLocation id = DataPackLang.parseId(m.getAsString());
                        if (id == null) continue;
                        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> members.put(type, group));
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse disposition group {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        DispositionRelations.replaceAll(groups, members);
        LOGGER.info("Loaded {} disposition groups", groups.size());
    }

    private static Set<String> names(JsonObject obj, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) out.add(e.getAsString());
            }
        }
        return out;
    }
}
