package com.aetherianartificer.townstead.root.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.EntityGroups;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * True when the entity belongs to the given creature {@code group} (Apoli's
 * {@code entity_group}): {@code undead}, {@code arthropod}, etc. An entity-group gene wins;
 * without one, the vanilla group of the entity's type applies, so a zombie is undead.
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
        return ctx -> EntityGroups.expressed(ctx.entity()).name().toLowerCase(Locale.ROOT).equals(group);
    }
}
