package com.aetherianartificer.townstead.villager;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Items Townstead puts in a villager's hand only to show them: a tool while working, a serving
 * being eaten. The real item stays in the inventory or in Townstead's own records, so a prop must
 * never become an item of its own. MCA files a replaced hand item into the inventory unless it is
 * {@code mca:temporary}, so props carry that component where MCA has it, plus Townstead's own tag
 * for MCA builds without it and for 1.20.1. Props are also kept out of death drops.
 */
public final class HandProps {
    private static final String TAG = "townstead:prop";

    //? if >=1.21 {
    private static final net.minecraft.resources.ResourceLocation MCA_TEMPORARY =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mca", "temporary");
    private static volatile boolean temporaryResolved;
    private static volatile net.minecraft.core.component.DataComponentType<Boolean> mcaTemporary;
    //?}

    private HandProps() {}

    /** A one-off copy of {@code source} marked as a prop. */
    public static ItemStack prop(ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = source.copy();
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                copy, tag -> tag.putBoolean(TAG, true));
        var temporary = mcaTemporary();
        if (temporary != null) copy.set(temporary, true);
        //?} else {
        /*copy.getOrCreateTag().putBoolean(TAG, true);
        *///?}
        return copy;
    }

    public static boolean isProp(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        //? if >=1.21 {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TAG);
        //?} else {
        /*return stack.hasTag() && stack.getTag().getBoolean(TAG);
        *///?}
    }

    /**
     * Shows {@code source} in the hand as a prop. A real item the hand held goes back into the
     * inventory first, so nothing is lost and MCA has nothing left to file a second time.
     */
    public static void show(VillagerEntityMCA villager, InteractionHand hand, ItemStack source) {
        if (source == null || source.isEmpty()) {
            clear(villager, hand);
            return;
        }
        ItemStack held = villager.getItemInHand(hand);
        if (isProp(held) && shows(held, source)) return;
        displace(villager, hand);
        villager.setItemInHand(hand, prop(source));
    }

    /** Empties the hand if it holds one of Townstead's props; anything else stays. */
    public static void clear(VillagerEntityMCA villager, InteractionHand hand) {
        if (isProp(villager.getItemInHand(hand))) villager.setItemInHand(hand, ItemStack.EMPTY);
    }

    /** Whether the hand shows {@code expected}, ignoring the prop marks. */
    public static boolean shows(ItemStack held, ItemStack expected) {
        return ItemStack.matches(strip(held), strip(expected));
    }

    private static ItemStack strip(ItemStack stack) {
        if (!isProp(stack)) return stack;
        ItemStack copy = stack.copy();
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                copy, tag -> tag.remove(TAG));
        var temporary = mcaTemporary();
        if (temporary != null) copy.remove(temporary);
        //?} else {
        /*copy.removeTagKey(TAG);
        *///?}
        return copy;
    }

    /** Puts a real hand item back into the inventory. Props and items MCA lent from the inventory stay put. */
    private static void displace(VillagerEntityMCA villager, InteractionHand hand) {
        ItemStack held = villager.getItemInHand(hand);
        if (held.isEmpty() || isProp(held) || isMcaTemporary(held) || inInventory(villager, held)) return;
        // MCA builds without temporary equipment save a lent item twice, so after a reload the
        // hand holds a separate copy of an inventory item. That copy is not a second item.
        if (!mcaTracksLentItems() && inventoryHasSame(villager, held)) return;
        ItemStack real = held.copyAndClear();
        ItemStack left = villager.getInventory().addItem(real);
        if (!left.isEmpty()) villager.spawnAtLocation(left);
    }

    private static boolean inInventory(VillagerEntityMCA villager, ItemStack stack) {
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == stack) return true;
        }
        return false;
    }

    private static boolean inventoryHasSame(VillagerEntityMCA villager, ItemStack stack) {
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            //? if >=1.21 {
            if (ItemStack.isSameItemSameComponents(inventory.getItem(i), stack)) return true;
            //?} else {
            /*if (ItemStack.isSameItemSameTags(inventory.getItem(i), stack)) return true;
            *///?}
        }
        return false;
    }

    /** Whether this MCA keeps lent and temporary equipment apart from real items (it registers {@code mca:temporary}). */
    private static boolean mcaTracksLentItems() {
        //? if >=1.21 {
        return mcaTemporary() != null;
        //?} else {
        /*return false;
        *///?}
    }

    private static boolean isMcaTemporary(ItemStack stack) {
        //? if >=1.21 {
        var temporary = mcaTemporary();
        return temporary != null && Boolean.TRUE.equals(stack.get(temporary));
        //?} else {
        /*return false;
        *///?}
    }

    //? if >=1.21 {
    @SuppressWarnings("unchecked")
    private static net.minecraft.core.component.DataComponentType<Boolean> mcaTemporary() {
        if (!temporaryResolved) {
            mcaTemporary = (net.minecraft.core.component.DataComponentType<Boolean>)
                    net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.get(MCA_TEMPORARY);
            temporaryResolved = true;
        }
        return mcaTemporary;
    }
    //?}
}
