package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes a race move freely through blocks that would otherwise slow it ("stick" it), like a spider in a
 * cobweb. The listed blocks no longer apply their {@code makeStuckInBlock} drag to the entity, enforced by
 * {@code EntityStuckImmunityMixin} at that chokepoint. Generic so any "stuck" block can be listed (cobweb,
 * sweet berry bush, ...); server-side (villager movement runs there).
 *
 * <p>JSON: {@code { "type":"pheno:stuck_immunity", "blocks":["minecraft:cobweb"] }}</p>
 */
public final class StuckImmunityGeneType implements GeneType {

    public static final String KEY = "pheno:stuck_immunity";

    public record Instance(Set<ResourceLocation> blocks) implements GeneInstance {
        public Instance { blocks = Set.copyOf(blocks); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.stuckImmunity(blocks); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        for (var element : GsonHelper.getAsJsonArray(json, "blocks", new JsonArray())) {
            ResourceLocation id = DataPackLang.parseId(element.getAsString());
            if (id != null) blocks.add(id);
        }
        if (blocks.isEmpty()) return null;
        return new Instance(blocks);
    }
}
