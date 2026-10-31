package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Scales damage the bearer deals to a specific victim by {@code modifier}, gated by an
 * optional {@link Condition} on the bearer and/or a {@code bientity_condition} over
 * (attacker, victim). The attacker-side twin of {@code damage_modifier}: unlike the
 * {@code damage_dealt} modifier target (which resolves through the capability layer with
 * only the bearer in scope), this applies inside {@code GeneDamageHandler} where the
 * victim is known - so positional gates like relative_rotation work. Multiplying the
 * event amount also avoids the i-frame swallow a second {@code hurt()} would hit.
 *
 * <p>An optional {@code damage_condition} limits it to matching damage, such as
 * {@code { "type":"pheno:magic" }} for a bonus to spells and potions only.</p>
 *
 * <p>JSON: {@code { "type":"pheno:attack_modifier", "modifier":1.5,
 * "bientity_condition":{ "type":"pheno:relative_rotation",
 *                        "reference":"target", "inverted":true } }}</p>
 */
public final class AttackModifierGeneType implements GeneType {

    public static final String KEY = "pheno:attack_modifier";

    public record Instance(float modifier,
                           @Nullable Condition condition,
                           @Nullable BiEntityCondition bientityCondition,
                           com.aetherianartificer.townstead.pheno.condition.damage.@Nullable DamageCondition damageCondition)
            implements GeneInstance {
        public Instance(float modifier, @Nullable Condition condition, @Nullable BiEntityCondition bientityCondition) {
            this(modifier, condition, bientityCondition, null);
        }

        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        if (!json.has("modifier")) return null;
        float modifier = GsonHelper.getAsFloat(json, "modifier", 1f);
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        BiEntityCondition bientityCondition = json.has("bientity_condition")
                ? BiEntityConditions.parse(json.get("bientity_condition"))
                : null;
        if (json.has("bientity_condition") && bientityCondition == null) return null;
        var damageCondition = json.has("damage_condition")
                ? com.aetherianartificer.townstead.pheno.condition.damage.DamageConditions.parse(json.get("damage_condition"))
                : null;
        if (json.has("damage_condition") && damageCondition == null) return null;
        return new Instance(modifier, condition, bientityCondition, damageCondition);
    }
}
