package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Modifies a vanilla entity attribute (max health, movement speed, …) by
 * {@code amount} (added to the base). Display-only for now; created so deviating
 * races (tough, frail, fast) have a home — Overworlder, being average, grants none.
 *
 * <p>JSON: {@code { "type":"townstead_origins:attribute",
 * "attribute":"minecraft:generic.max_health", "amount":4.0 }}</p>
 */
public final class AttributeGeneType implements GeneType {

    public static final String KEY = "townstead_origins:attribute";

    public record Instance(@Nullable ResourceLocation attribute, float amount) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        ResourceLocation attribute = json.has("attribute")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "attribute", ""))
                : null;
        if (attribute == null) return null;
        float amount = GsonHelper.getAsFloat(json, "amount", 0f);
        return new Instance(attribute, amount);
        // TODO(effects): apply an attribute modifier on the villager.
    }
}
