package com.aetherianartificer.townstead.villager;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces what an entity wears when the caller takes charge of the item it displaces. MCA builds
 * that lend inventory items as equipment file a displaced item into the villager's inventory
 * themselves; here that filing is held off, so the displaced item reaches the caller exactly once.
 */
public final class WornItems {
    private static final ThreadLocal<Boolean> HOLD_FILING = ThreadLocal.withInitial(() -> false);
    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation MCA_TEMPORARY =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mca", "temporary");
    //?}

    private WornItems() {}

    /**
     * Puts {@code stack} in {@code slot} and returns the displaced item the caller now owns. Empty when
     * the slot held nothing of its own: a prop, or a villager's inventory item that stays in the inventory.
     */
    public static ItemStack replace(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
        ItemStack previous = entity.getItemBySlot(slot);
        if (!(entity instanceof VillagerEntityMCA villager)) {
            entity.setItemSlot(slot, stack);
            return previous;
        }
        boolean own = !previous.isEmpty() && !HandProps.isProp(previous) && !mcaTemporary(previous)
                && !lent(villager, previous);
        HOLD_FILING.set(true);
        try {
            villager.setItemSlot(slot, stack);
        } finally {
            HOLD_FILING.set(false);
        }
        return own ? previous : ItemStack.EMPTY;
    }

    /** Empties {@code slot}; see {@link #replace}. */
    public static ItemStack take(LivingEntity entity, EquipmentSlot slot) {
        return replace(entity, slot, ItemStack.EMPTY);
    }

    /**
     * Empties {@code slot} and returns what it held, taking a villager's inventory item out of the
     * inventory too: the item leaves the villager entirely. Props come back empty.
     */
    public static ItemStack knockOut(LivingEntity entity, EquipmentSlot slot) {
        ItemStack held = entity.getItemBySlot(slot);
        if (entity instanceof VillagerEntityMCA villager && !held.isEmpty()) {
            var inventory = villager.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventory.getItem(i) != held) continue;
                inventory.setItem(i, ItemStack.EMPTY);
                take(entity, slot);
                return held;
            }
        }
        return take(entity, slot);
    }

    /** Read by the equipment mixin: whether MCA's setter should leave the displaced item alone. */
    public static boolean holdingFiling() {
        return HOLD_FILING.get();
    }

    /** MCA's mark for generated equipment (uniforms, mourning flowers), on builds that have it. */
    public static boolean mcaTemporary(ItemStack stack) {
        //? if >=1.21 {
        var type = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(MCA_TEMPORARY);
        return type != null && Boolean.TRUE.equals(stack.get(type));
        //?} else {
        /*return false;
        *///?}
    }

    /** Whether a villager wears this exact stack straight out of its inventory. */
    public static boolean lent(VillagerEntityMCA villager, ItemStack stack) {
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == stack) return true;
        }
        return false;
    }
}
