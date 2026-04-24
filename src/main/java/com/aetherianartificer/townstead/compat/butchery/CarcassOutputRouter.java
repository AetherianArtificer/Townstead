package com.aetherianartificer.townstead.compat.butchery;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Routes carcass drops into the appropriate Butchery station so higher-tier
 * shops actually earn their keep: hides go to the Skin Rack, organs to the
 * Pestle and Mortar. Stations are looked up in any building in the villager's
 * village (a butcher can drop hides at the Tannery across town).
 *
 * <p>If no matching station exists or all candidate inventories are full,
 * the leftover stack is returned to the caller so they can fall back to
 * villager inventory and the usual generic-storage offload.
 */
public final class CarcassOutputRouter {
    //? if >=1.21 {
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:skins"));
    private static final TagKey<Item> ORGANS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:organs"));
    private static final ResourceLocation SKIN_RACK_ID =
            ResourceLocation.parse("butchery:skin_rack");
    private static final ResourceLocation PESTLE_ID =
            ResourceLocation.parse("butchery:pestle_and_mortar");
    //?} else {
    /*private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "skins"));
    private static final TagKey<Item> ORGANS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "organs"));
    private static final ResourceLocation SKIN_RACK_ID =
            new ResourceLocation("butchery", "skin_rack");
    private static final ResourceLocation PESTLE_ID =
            new ResourceLocation("butchery", "pestle_and_mortar");
    *///?}

    private CarcassOutputRouter() {}

    /**
     * Try to insert a drop into the appropriate station somewhere in the
     * village. Returns whatever portion of the stack could not be placed
     * (empty if fully consumed, or the original stack if no routing applies).
     */
    public static ItemStack route(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation stationId = stationFor(stack);
        if (stationId == null) return stack;
        Optional<Village> village = resolveVillage(villager);
        if (village.isEmpty()) return stack;
        return insertIntoStation(level, village.get(), stationId, stack);
    }

    @Nullable
    private static ResourceLocation stationFor(ItemStack stack) {
        if (stack.is(SKINS_TAG)) return SKIN_RACK_ID;
        if (stack.is(ORGANS_TAG)) return PESTLE_ID;
        return null;
    }

    private static ItemStack insertIntoStation(ServerLevel level, Village village,
            ResourceLocation stationId, ItemStack stack) {
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            List<BlockPos> positions = building.getBlocks().get(stationId);
            if (positions == null || positions.isEmpty()) continue;
            for (BlockPos pos : positions) {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof Container container)) continue;
                stack = insertIntoContainer(container, stack);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * Vanilla-compatible container insertion without the hopper's side /
     * face filtering. Fills existing matching stacks first, then lands in
     * empty slots.
     */
    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int size = container.getContainerSize();

        // First pass: merge into existing matching stacks.
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) continue;
            //? if >=1.21 {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            //?} else {
            /*if (!ItemStack.isSameItemSameTags(existing, stack)) continue;
            *///?}
            int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
            if (room <= 0) continue;
            int move = Math.min(room, stack.getCount());
            existing.grow(move);
            stack.shrink(move);
            container.setChanged();
        }

        // Second pass: empty slots.
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            ItemStack placed = stack.copy();
            int cap = Math.min(placed.getMaxStackSize(), container.getMaxStackSize());
            placed.setCount(Math.min(cap, stack.getCount()));
            stack.shrink(placed.getCount());
            container.setItem(i, placed);
            container.setChanged();
        }

        return stack;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }
}
