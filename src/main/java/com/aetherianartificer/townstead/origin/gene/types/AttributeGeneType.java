package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Modifies a vanilla entity attribute (max health, movement speed, …). The
 * {@code operation} mirrors Apoli's {@code modify_attribute}; an optional
 * {@code condition} makes the modifier apply only while it holds (Apoli's
 * {@code conditioned_attribute}, e.g. stronger at night). Applied by
 * {@code GeneAttributeApplier}.
 *
 * <p>JSON: {@code { "type":"pheno:attribute",
 * "attribute":"minecraft:generic.max_health", "amount":4.0, "operation":"add",
 * "condition":{ "type":"pheno:time_of_day", "min":13000, "max":23000 } }}</p>
 */
public final class AttributeGeneType implements GeneType {

    public static final String KEY = "pheno:attribute";

    /** Operation, version-mapped to vanilla in the applier. */
    public enum Op { ADD, MULTIPLY_BASE, MULTIPLY_TOTAL }

    public record Instance(@Nullable ResourceLocation attribute, float amount, Op operation,
                           @Nullable Condition condition) implements GeneInstance {
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
        Op operation = parseOp(GsonHelper.getAsString(json, "operation", "add"));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(attribute, amount, operation, condition);
    }

    private static Op parseOp(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "multiply_base", "multiplier_base", "multiply_additive" -> Op.MULTIPLY_BASE;
            case "multiply_total", "multiplier_total" -> Op.MULTIPLY_TOTAL;
            default -> Op.ADD;
        };
    }
}
