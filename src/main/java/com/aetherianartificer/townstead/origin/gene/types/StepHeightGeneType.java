package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Raises how high the entity steps up (Apugli's {@code step_height}, the {@code upper_height} half).
 * "Step height" became a vanilla attribute, so this just adds {@code amount} to it, resolving the
 * per-version attribute ({@code minecraft:step_height} on 1.21, {@code forge:step_height_addition} on
 * 1.20.1) at load so the pack stays version-agnostic. It parses straight into an
 * {@link AttributeGeneType.Instance}, so {@code GeneAttributeApplier} applies it like any attribute.
 *
 * <p>Apugli's step-<i>down</i> correction ({@code lower_height}) and {@code allow_jump_after} are not
 * modeled: they need a movement mixin into vanilla step logic and have no vanilla equivalent.</p>
 *
 * <p>JSON: {@code { "type":"pheno:step_height", "amount":1.0 }}</p>
 */
public final class StepHeightGeneType implements GeneType {

    public static final String KEY = "pheno:step_height";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        // "amount" is the native field; "upper_height" is Apugli's, accepted so a raw port also loads.
        float amount = GsonHelper.getAsFloat(json, "amount", GsonHelper.getAsFloat(json, "upper_height", 0f));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new AttributeGeneType.Instance(stepHeightAttribute(), amount, AttributeGeneType.Op.ADD, condition);
    }

    private static ResourceLocation stepHeightAttribute() {
        //? if neoforge {
        return DataPackLang.parseId("minecraft:step_height");
        //?} else {
        /*return DataPackLang.parseId("forge:step_height_addition");
        *///?}
    }
}
