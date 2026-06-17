package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

/**
 * Listed mobs flee the bearer (a generalized {@code scare_creepers}). {@code mobs} is a
 * list of entity-type ids and/or {@code #}-prefixed entity-type tags; a mob matching any
 * of them, within {@code radius}, is steered away. Enforced server-side by the passive
 * ticker.
 *
 * <p>JSON: {@code { "type":"pheno:scare_mob",
 * "mobs":["minecraft:creeper","#minecraft:skeletons"], "radius":8 }}</p>
 */
public final class ScareMobGeneType implements GeneType {

    public static final String KEY = "pheno:scare_mob";

    public record Instance(Set<ResourceLocation> ids, Set<TagKey<EntityType<?>>> tags, double radius)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }

        public boolean matches(Entity entity) {
            if (ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return true;
            for (TagKey<EntityType<?>> tag : tags) {
                if (entity.getType().is(tag)) return true;
            }
            return false;
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Set<ResourceLocation> ids = new HashSet<>();
        Set<TagKey<EntityType<?>>> tags = new HashSet<>();
        if (json.has("mobs") && json.get("mobs").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("mobs");
            for (var el : arr) {
                String raw = el.getAsString();
                if (raw.startsWith("#")) {
                    ResourceLocation id = DataPackLang.parseId(raw.substring(1));
                    if (id != null) tags.add(TagKey.create(Registries.ENTITY_TYPE, id));
                } else {
                    ResourceLocation id = DataPackLang.parseId(raw);
                    if (id != null) ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && tags.isEmpty()) return null;
        double radius = GsonHelper.getAsDouble(json, "radius", 8.0);
        return new Instance(ids, tags, radius);
    }
}
