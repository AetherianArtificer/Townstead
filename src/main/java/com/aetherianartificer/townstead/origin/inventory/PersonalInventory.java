package com.aetherianartificer.townstead.origin.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * Opens and persists an {@code inventory} gene's personal container. Contents live under
 * the player's persisted-data subtag (so they survive death by default, like an ender
 * chest), keyed by the gene id, and save on every change. Player-only.
 */
public final class PersonalInventory {

    private PersonalInventory() {}

    public static void open(ServerPlayer player, ResourceLocation geneId, int size) {
        int rows = Math.max(1, Math.min(6, size / 9));
        SimpleContainer container = new SimpleContainer(rows * 9);
        load(player, geneId, container);
        container.addListener(c -> save(player, geneId, container));
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ChestMenu(menuType(rows), id, inventory, container, rows),
                Component.translatable("container.townstead.personal_inventory")));
    }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static CompoundTag store(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String key(ResourceLocation geneId) {
        return "townstead_inventory/" + geneId;
    }

    private static void load(ServerPlayer player, ResourceLocation geneId, SimpleContainer container) {
        CompoundTag store = store(player);
        if (!store.contains(key(geneId))) return;
        CompoundTag tag = store.getCompound(key(geneId));
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        //? if >=1.21 {
        ContainerHelper.loadAllItems(tag, items, player.level().registryAccess());
        //?} else {
        /*ContainerHelper.loadAllItems(tag, items);
        *///?}
        for (int i = 0; i < items.size(); i++) container.setItem(i, items.get(i));
    }

    private static void save(ServerPlayer player, ResourceLocation geneId, SimpleContainer container) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) items.set(i, container.getItem(i));
        CompoundTag tag = new CompoundTag();
        //? if >=1.21 {
        ContainerHelper.saveAllItems(tag, items, player.level().registryAccess());
        //?} else {
        /*ContainerHelper.saveAllItems(tag, items);
        *///?}
        CompoundTag store = store(player);
        store.put(key(geneId), tag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, store);
    }
}
