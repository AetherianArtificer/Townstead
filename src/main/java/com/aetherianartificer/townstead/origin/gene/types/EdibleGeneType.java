package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lets a race eat items others can't (raw flesh, grass, ...). The listed items
 * become right-click edible for a player of this origin, restoring {@code nutrition}
 * and {@code saturation} and optionally running an {@link Action} (a side effect like
 * a brief poison or speed). Player-facing; villager diet is handled by the
 * {@code diet} gene + the consumption system.
 *
 * <p>JSON: {@code { "type":"pheno:edible", "items":["minecraft:rotten_flesh"],
 * "nutrition":4, "saturation":0.6 }}</p>
 */
public final class EdibleGeneType implements GeneType {

    public static final String KEY = "pheno:edible";

    public record Instance(Set<ResourceLocation> items, int nutrition, float saturation,
                           @Nullable Action onEat) implements GeneInstance {
        public Instance { items = Set.copyOf(items); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Set<ResourceLocation> items = new LinkedHashSet<>();
        for (var element : GsonHelper.getAsJsonArray(json, "items", new JsonArray())) {
            ResourceLocation id = DataPackLang.parseId(element.getAsString());
            if (id != null) items.add(id);
        }
        if (items.isEmpty()) return null;
        int nutrition = Math.max(0, GsonHelper.getAsInt(json, "nutrition", 4));
        float saturation = GsonHelper.getAsFloat(json, "saturation", 0.3f);
        Action onEat = json.has("action") ? Actions.parse(json.get("action")) : null;
        return new Instance(items, nutrition, saturation, onEat);
    }
}
