package com.aetherianartificer.townstead.compat.curios;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gate for the optional Curios integration. Curios is compile-time only: this class names no Curios
 * type, and every call is a no-op without the mod, so nothing here can trip class loading on an
 * install that lacks it. The typed calls live in {@link CuriosBridge}, which is only touched once
 * {@link #present()} has answered yes.
 */
public final class CuriosCompat {

    private static final boolean PRESENT = ModCompat.isLoaded("curios");

    private CuriosCompat() {}

    /** Whether Curios is installed (so callers can gate work that's pointless without it). */
    public static boolean present() {
        return PRESENT;
    }

    /** Feeds every non-empty Curios-slot stack the entity wears to {@code out}. No-op without Curios. */
    public static void forEachWorn(LivingEntity entity, Consumer<ItemStack> out) {
        if (PRESENT && entity != null) CuriosBridge.forEachWorn(entity, out);
    }

    /**
     * Feeds every Curios-slot stack the entity shows, with its slot type id, the way Curios' own render
     * layer walks them: a cosmetic stack first, else the worn stack when its render toggle is on.
     */
    public static void forEachWornVisible(LivingEntity entity, BiConsumer<String, ItemStack> out) {
        if (PRESENT && entity != null) CuriosBridge.forEachWornVisible(entity, out);
    }

    /**
     * Server-side: strips every worn stack matching {@code test} out of its Curios slot, handing each
     * removed copy to {@code onRemoved} (to return it to the player, message, etc.). No-op without Curios.
     */
    public static void removeWhere(LivingEntity entity, Predicate<ItemStack> test, Consumer<ItemStack> onRemoved) {
        if (!PRESENT || entity == null || entity.level().isClientSide) return;
        CuriosBridge.removeWhere(entity, test, onRemoved);
    }

    /**
     * Every Curios slot the entity has, in Curios' display order, as menu-ready handles. Empty without
     * Curios or for an entity Curios assigns no slots to.
     */
    public static List<CurioSlotSpec> slotSpecs(LivingEntity entity) {
        return PRESENT && entity != null ? CuriosBridge.slotSpecs(entity) : List.of();
    }

    /** Whether Curios would accept {@code stack} in the entity's given slot (tags, curio rules). */
    public static boolean canEquip(LivingEntity entity, String slotId, int index, ItemStack stack) {
        return PRESENT && CuriosBridge.canEquip(entity, slotId, index, stack);
    }

    /** Whether the curio worn in the entity's given slot may be taken out again. */
    public static boolean canUnequip(LivingEntity entity, String slotId, int index, ItemStack stack) {
        return !PRESENT || CuriosBridge.canUnequip(entity, slotId, index, stack);
    }

    /** Lets a curio react to being put on through a slot (equip sound and the like), as Curios' own slot does. */
    public static void onEquipFromUse(LivingEntity entity, String slotId, int index, ItemStack stack) {
        if (PRESENT && entity != null) CuriosBridge.onEquipFromUse(entity, slotId, index, stack);
    }

    /** Whether the curio in the entity's given slot renders on them (Curios' per-slot toggle). */
    public static boolean isRendered(LivingEntity entity, String slotId, int index) {
        return PRESENT && entity != null && CuriosBridge.isRendered(entity, slotId, index);
    }

    public static void setRendered(LivingEntity entity, String slotId, int index, boolean render) {
        if (PRESENT && entity != null) CuriosBridge.setRendered(entity, slotId, index, render);
    }

    /**
     * Puts one copy of {@code stack} into the first empty Curios slot that accepts it. Returns true when
     * placed; false without Curios, or when no slot is free or fitting. Does not shrink {@code stack}.
     */
    public static boolean equipFirstFree(LivingEntity entity, ItemStack stack) {
        return PRESENT && entity != null && CuriosBridge.equipFirstFree(entity, stack);
    }
}
