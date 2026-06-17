package com.aetherianartificer.townstead.pheno.condition.damage;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a damage-condition JSON into a {@link DamageCondition}. Non-deprecated Apoli
 * subset: {@code amount} ({@code min}/{@code max}), {@code in_tag} (damage-type tag),
 * {@code type} (damage-type id), {@code name} (source message id), {@code projectile},
 * {@code magic} (Apugli; direct or indirect magic damage),
 * and {@code attacker} (an entity condition on the source's attacker); plus
 * {@code and}/{@code or}/{@code constant}. {@code "inverted":true} negates. (The six
 * deprecated flag conditions — fire, explosive, falling, etc. — are just
 * {@code in_tag} on vanilla damage-type tags and are not included.)
 */
public final class DamageConditions {

    private DamageConditions() {}

    @Nullable
    public static DamageCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        DamageCondition condition = build(stripNamespace(GsonHelper.getAsString(json, "type", "")), json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }

    @Nullable
    private static DamageCondition build(String type, JsonObject json) {
        switch (type) {
            case "amount": {
                float min = GsonHelper.getAsFloat(json, "min", -Float.MAX_VALUE);
                float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
                return (source, amount) -> amount >= min && amount <= max;
            }
            case "in_tag": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                if (id == null) return null;
                TagKey<DamageType> tag = TagKey.create(Registries.DAMAGE_TYPE, id);
                return (source, amount) -> source.is(tag);
            }
            case "type": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "damage_type", ""));
                if (id == null) return null;
                ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, id);
                return (source, amount) -> source.is(key);
            }
            case "name": {
                String name = GsonHelper.getAsString(json, "name", "");
                if (name.isEmpty()) return null;
                return (source, amount) -> source.getMsgId().equals(name);
            }
            case "projectile":
                return (source, amount) -> source.is(DamageTypeTags.IS_PROJECTILE);
            case "magic":
                return (source, amount) -> source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                        || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
            case "attacker": {
                Condition inner = Conditions.parse(json.get("condition"));
                if (inner == null) return null;
                return (source, amount) -> source.getEntity() instanceof LivingEntity attacker
                        && inner.test(new ConditionContext(attacker));
            }
            case "and": {
                List<DamageCondition> all = parseList(json);
                if (all == null) return null;
                return (source, amount) -> all.stream().allMatch(c -> c.test(source, amount));
            }
            case "or": {
                List<DamageCondition> any = parseList(json);
                if (any == null) return null;
                return (source, amount) -> any.stream().anyMatch(c -> c.test(source, amount));
            }
            case "constant": {
                boolean value = GsonHelper.getAsBoolean(json, "value", true);
                return (source, amount) -> value;
            }
            default:
                return null;
        }
    }

    @Nullable
    private static List<DamageCondition> parseList(JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return null;
        List<DamageCondition> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("conditions")) {
            DamageCondition condition = parse(element);
            if (condition == null) return null;
            out.add(condition);
        }
        return out.isEmpty() ? null : out;
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
