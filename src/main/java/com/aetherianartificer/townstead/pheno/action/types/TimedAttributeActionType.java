package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionScheduler;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
//?} else {
/*import net.minecraft.world.entity.ai.attributes.Attribute;
import java.util.UUID;
*///?}

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Changes an attribute for a while: {@code { "type":"pheno:attribute_for", "attribute":"minecraft:generic.jump_strength",
 * "amount":-0.5, "operation":"multiply_total", "duration":20 }}. Running it again before it wears off keeps it
 * going rather than stacking, so a field that reapplies it every few ticks holds it while someone stands inside.
 * An attribute this game version doesn't have does nothing.
 */
public final class TimedAttributeActionType implements ActionType {

    public static final String KEY = "pheno:attribute_for";

    // When each body's copy wears off, by modifier.
    private static final Map<LivingEntity, Map<String, Long>> UNTIL = new WeakHashMap<>();

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation attributeId = DataPackLang.parseId(GsonHelper.getAsString(json, "attribute", ""));
        if (attributeId == null) return null;
        double amount = GsonHelper.getAsDouble(json, "amount", 0);
        String operation = GsonHelper.getAsString(json, "operation", "add").toLowerCase(Locale.ROOT);
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 20));
        // One modifier per authored line, so two different ones on the same attribute both count.
        String key = attributeId + "|" + amount + "|" + operation;
        return ctx -> {
            LivingEntity entity = ctx.entity();
            if (entity.level().isClientSide()) return;
            AttributeInstance attr = instance(entity, attributeId);
            if (attr == null) return;
            long now = entity.level().getGameTime();
            UNTIL.computeIfAbsent(entity, e -> new java.util.HashMap<>()).put(key, now + duration);
            if (!has(attr, key)) add(attr, key, amount, operation);
            ActionScheduler.schedule(ctx, duration, later -> {
                Map<String, Long> until = UNTIL.get(entity);
                Long end = until == null ? null : until.get(key);
                if (end != null && end > entity.level().getGameTime()) return;
                if (until != null) until.remove(key);
                AttributeInstance current = instance(entity, attributeId);
                if (current != null) remove(current, key);
            });
        };
    }

    private static AttributeInstance instance(LivingEntity entity, ResourceLocation id) {
        //? if neoforge {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, id)).orElse(null);
        return holder == null ? null : entity.getAttribute(holder);
        //?} else {
        /*Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        return attribute == null ? null : entity.getAttribute(attribute);
        *///?}
    }

    //? if neoforge {
    private static ResourceLocation id(String key) {
        return ResourceLocation.fromNamespaceAndPath("townstead",
                "timed/" + Integer.toHexString(key.hashCode()));
    }

    private static boolean has(AttributeInstance attr, String key) {
        return attr.getModifier(id(key)) != null;
    }

    private static void add(AttributeInstance attr, String key, double amount, String operation) {
        AttributeModifier.Operation op = switch (operation) {
            case "multiply_base", "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "multiply_total", "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
        attr.addTransientModifier(new AttributeModifier(id(key), amount, op));
    }

    private static void remove(AttributeInstance attr, String key) {
        if (has(attr, key)) attr.removeModifier(id(key));
    }
    //?} else {
    /*private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("townstead:timed/" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean has(AttributeInstance attr, String key) {
        return attr.getModifier(id(key)) != null;
    }

    private static void add(AttributeInstance attr, String key, double amount, String operation) {
        AttributeModifier.Operation op = switch (operation) {
            case "multiply_base", "add_multiplied_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total", "add_multiplied_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
        attr.addTransientModifier(new AttributeModifier(id(key), "townstead:timed", amount, op));
    }

    private static void remove(AttributeInstance attr, String key) {
        if (has(attr, key)) attr.removeModifier(id(key));
    }
    *///?}
}
