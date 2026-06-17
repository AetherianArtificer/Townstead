package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.origin.EntityGroups;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * True when the entity belongs to the given creature {@code group} (Apoli's
 * {@code entity_group}): {@code undead}, {@code arthropod}, etc., as resolved by
 * {@link EntityGroups}.
 *
 * <p>JSON: {@code { "type":"pheno:entity_group", "group":"undead" }}</p>
 */
public final class EntityGroupConditionType implements ConditionType {

    public static final String KEY = "pheno:entity_group";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "").toLowerCase(Locale.ROOT);
        if (group.isEmpty()) return null;
        return ctx -> EntityGroups.of(ctx.entity()).name().toLowerCase(Locale.ROOT).equals(group);
    }
}
