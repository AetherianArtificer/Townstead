package com.aetherianartificer.townstead.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A living entity's armor and offhand slots viewed as a five-slot container, for menu slots to sit on. */
public final class LivingEquipmentContainer implements Container {

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    private final LivingEntity entity;

    public LivingEquipmentContainer(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public int getContainerSize() {
        return SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot slot : SLOTS) {
            if (!entity.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        // Armor slots show real personal items, not MCA's generated uniforms or inventory aliases.
        if (index < 4 && entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager
                && AssignedArmor.automatic(villager, SLOTS[index])) return ItemStack.EMPTY;
        return entity.getItemBySlot(SLOTS[index]);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack worn = getItem(index);
        if (worn.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = worn.split(count);
        setItem(index, worn);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack worn = getItem(index);
        setItem(index, ItemStack.EMPTY);
        return worn;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 4 && entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
            AssignedArmor.set(villager.getPersistentData(), SLOTS[index], !stack.isEmpty());
            com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.invalidateEquipment(villager);
        }
        entity.setItemSlot(SLOTS[index], stack);
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return entity.isAlive();
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SLOTS.length; i++) setItem(i, ItemStack.EMPTY);
    }
}
