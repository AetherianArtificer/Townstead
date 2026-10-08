package com.aetherianartificer.townstead.inventory;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Player-assigned armor stays in the real equipment slot; only ownership is persisted here. */
public final class AssignedArmor {
    private static final String KEY = "townstead:assigned_armor";
    private static final String REVISION = "townstead:armor_revision";

    private AssignedArmor() {}

    public static boolean contains(CompoundTag data, EquipmentSlot slot) {
        return (data.getInt(KEY) & bit(slot)) != 0;
    }

    public static void set(CompoundTag data, EquipmentSlot slot, boolean assigned) {
        int bit = bit(slot);
        if (bit == 0) return;
        int mask = data.getInt(KEY);
        data.putInt(KEY, assigned ? mask | bit : mask & ~bit);
        data.putInt(REVISION, data.getInt(REVISION) + 1);
    }

    private static int bit(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 1;
            case CHEST -> 2;
            case LEGS -> 4;
            case FEET -> 8;
            default -> 0;
        };
    }

    public static boolean protects(VillagerEntityMCA villager, EquipmentSlot slot) {
        return protects(villager.getPersistentData(), slot, !villager.getItemBySlot(slot).isEmpty());
    }

    /** Broken/removed equipment relinquishes the assignment before automatic gear fills the slot. */
    public static boolean protects(CompoundTag data, EquipmentSlot slot, boolean occupied) {
        if (!contains(data, slot)) return false;
        if (occupied) return true;
        set(data, slot, false);
        return false;
    }

    public static boolean protects(VillagerEntityMCA villager, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (EquipmentSlot slot : LivingEquipmentContainer.SLOTS)
            if (villager.getItemBySlot(slot) == stack && protects(villager, slot)) return true;
        return false;
    }

    public static int revision(VillagerEntityMCA villager) {
        return villager.getPersistentData().getInt(REVISION);
    }

    /** MCA's automatic equipment is an inventory alias or a generated uniform, not another item. */
    public static boolean automatic(VillagerEntityMCA villager, EquipmentSlot slot) {
        if (protects(villager, slot)) return false;
        if (villager.getVillagerBrain().getArmorWear()
                || com.aetherianartificer.townstead.compat.mca.McaRegistryCompat.isGuardOrArcher(villager.getVillagerData().getProfession())
                //? if neoforge {
                || villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.WEARS_ARMOR)) return true;
                //?} else {
                /*|| villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.WEARS_ARMOR.get())) return true;
                *///?}
        ItemStack worn = villager.getItemBySlot(slot);
        if (worn.isEmpty()) return false;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++)
            if (villager.getInventory().getItem(i) == worn) return true;
        return false;
    }
}
