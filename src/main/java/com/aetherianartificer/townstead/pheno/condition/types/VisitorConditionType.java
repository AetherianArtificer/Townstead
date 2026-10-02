package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.visitor.Visitors;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when a visitor with {@code role} is loaded near the entity, or with {@code self}, when the
 * entity is that visitor.
 * <pre>
 *   { "type": "pheno:visitor", "role": "fledgling", "radius": 32 }
 *   { "type": "pheno:visitor", "role": "fledgling", "self": true }
 * </pre>
 */
public final class VisitorConditionType implements com.aetherianartificer.townstead.pheno.condition.ConditionType {
    public static final String KEY = "pheno:visitor";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "role", "");
        double radius = GsonHelper.getAsDouble(json, "radius", 32);
        boolean self = GsonHelper.getAsBoolean(json, "self", false);
        if (role.isBlank()) return null;
        if (self) return ctx -> ctx.entity() != null && role.equals(Visitors.role(ctx.entity()));
        return ctx -> ctx.entity() != null && Visitors.near(ctx.entity(), role, radius) != null;
    }
}
