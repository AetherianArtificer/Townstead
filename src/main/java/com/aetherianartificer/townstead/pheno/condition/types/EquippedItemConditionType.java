package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Locale;

/**
 * True when the entity's item in {@code slot} satisfies an {@code item_condition}
 * (Apoli's {@code equipped_item}). {@code slot} is {@code mainhand} (default),
 * {@code offhand}, {@code head}, {@code chest}, {@code legs} or {@code feet}.
 *
 * <p>JSON: {@code { "type":"pheno:equipped_item", "slot":"head",
 * "item_condition":{ "type":"pheno:enchantment", "enchantment":"minecraft:aqua_affinity" } }}</p>
 */
public final class EquippedItemConditionType implements ConditionType {

    public static final String KEY = "pheno:equipped_item";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        EquipmentSlot slot = parseSlot(GsonHelper.getAsString(json, "slot", "mainhand"));
        ItemCondition item = ItemConditions.parse(json.get("item_condition"));
        if (item == null) return null;
        return ctx -> item.test(ctx.level(), ctx.entity().getItemBySlot(slot));
    }

    private static EquipmentSlot parseSlot(String raw) {
        try {
            return EquipmentSlot.byName(raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EquipmentSlot.MAINHAND;
        }
    }
}
