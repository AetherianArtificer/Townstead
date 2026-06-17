package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.Nullable;

/**
 * Scales incoming damage of a given type or type tag by {@code modifier}
 * (0 = immune, &lt;1 = resistant, &gt;1 = vulnerable), optionally gated by a
 * {@link Condition}. Applied by {@code GeneDamageHandler}.
 *
 * <p>JSON: {@code { "type":"pheno:damage_modifier",
 * "damage_tag":"minecraft:is_fire", "modifier":0.0 }}</p>
 */
public final class DamageModifierGeneType implements GeneType {

    public static final String KEY = "pheno:damage_modifier";

    public record Instance(@Nullable TagKey<DamageType> damageTag,
                           @Nullable ResourceLocation damageType,
                           float modifier,
                           @Nullable Condition condition,
                           @Nullable com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        TagKey<DamageType> damageTag = null;
        if (json.has("damage_tag")) {
            ResourceLocation tagId = DataPackLang.parseId(GsonHelper.getAsString(json, "damage_tag", ""));
            if (tagId != null) damageTag = TagKey.create(Registries.DAMAGE_TYPE, tagId);
        }
        ResourceLocation damageType = json.has("damage_type")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "damage_type", ""))
                : null;
        com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition damageCondition =
                json.has("damage_condition")
                        ? com.aetherianartificer.townstead.pheno.condition.damage.DamageConditions.parse(
                                json.get("damage_condition"))
                        : null;
        if (damageTag == null && damageType == null && damageCondition == null) return null;
        float modifier = GsonHelper.getAsFloat(json, "modifier", 1f);
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(damageTag, damageType, modifier, condition, damageCondition);
    }
}
