package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.root.rig.RigEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * True when the entity's item in {@code slot} satisfies an {@code item_condition}
 * (Apoli's {@code equipped_item}). {@code slot} is {@code mainhand} (default),
 * {@code offhand}, {@code head}, {@code chest}, {@code legs} or {@code feet}.
 *
 * <p>JSON: {@code { "type":"pheno:equipped_item", "slot":"head",
 * "item_condition":{ "type":"pheno:enchantment", "enchantment":"minecraft:aqua_affinity" } }}.
 * {@code item_condition} is optional: omit it to test only that the slot holds any item (e.g. "is
 * the head covered"), which with {@code "inverted":true} reads as "that slot is empty".
 * With Curios, a curio worn on the same body part also counts (a hat in the head slot).</p>
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
        // No item_condition: test only that the slot is occupied (any item present).
        if (!json.has("item_condition")) {
            return ctx -> anyWorn(ctx.entity(), slot, stack -> true);
        }
        ItemCondition item = ItemConditions.parse(json.get("item_condition"));
        if (item == null) return null;
        return ctx -> anyWorn(ctx.entity(), slot, stack -> item.test(ctx.level(), stack));
    }

    /** The vanilla slot, then any Curios slot worn on the same body part (a hat in the Curios head slot). */
    private static boolean anyWorn(LivingEntity entity, EquipmentSlot slot, Predicate<ItemStack> test) {
        ItemStack vanilla = entity.getItemBySlot(slot);
        if (!vanilla.isEmpty() && test.test(vanilla)) return true;
        if (!CuriosCompat.present()) return false;
        boolean[] found = {false};
        CuriosCompat.forEachEquipped(entity, (id, stack) -> {
            if (!found[0] && RigEquipment.curioBodySlot(id) == slot && test.test(stack)) found[0] = true;
        });
        return found[0];
    }

    private static EquipmentSlot parseSlot(String raw) {
        try {
            return EquipmentSlot.byName(raw.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EquipmentSlot.MAINHAND;
        }
    }
}
