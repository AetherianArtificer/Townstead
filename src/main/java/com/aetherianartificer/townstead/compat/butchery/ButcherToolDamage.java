package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.tick.WorkToolTicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Durability bridge for butcher tools. Villager main-hand tools are copies
 * maintained by {@link WorkToolTicker}, so damage to the displayed stack does
 * not persist. This helper instead damages the first matching tool in the
 * villager's inventory, breaking it when the damage threshold is reached.
 *
 * <p>Work tasks invoke {@code consume*Use} after a successful action
 * (slaughter swing, carcass stage advance). {@link #hasCleaver} is used by
 * {@link CarcassWorkTask} to gate the task start and by
 * {@link ButcheryComplaintsTicker} to surface the shortage.
 */
public final class ButcherToolDamage {
    private ButcherToolDamage() {}

    public static boolean hasCleaver(VillagerEntityMCA villager) {
        return findFirst(villager, WorkToolTicker::isCleaver) >= 0;
    }

    public static boolean hasKnife(VillagerEntityMCA villager) {
        return findFirst(villager, WorkToolTicker::isKnife) >= 0;
    }

    public static boolean hasHacksaw(VillagerEntityMCA villager) {
        return findFirst(villager, WorkToolTicker::isHacksaw) >= 0;
    }

    /** Damage the first cleaver in inventory by one durability point. */
    public static void consumeCleaverUse(VillagerEntityMCA villager) {
        damageFirst(villager, WorkToolTicker::isCleaver);
    }

    /** Damage the first skinning knife in inventory by one durability point. */
    public static void consumeKnifeUse(VillagerEntityMCA villager) {
        damageFirst(villager, WorkToolTicker::isKnife);
    }

    /** Damage the first hacksaw in inventory by one durability point. */
    public static void consumeHacksawUse(VillagerEntityMCA villager) {
        damageFirst(villager, WorkToolTicker::isHacksaw);
    }

    private static int findFirst(VillagerEntityMCA villager, java.util.function.Predicate<ItemStack> matcher) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (matcher.test(stack)) return i;
        }
        return -1;
    }

    private static void damageFirst(VillagerEntityMCA villager, java.util.function.Predicate<ItemStack> matcher) {
        int slot = findFirst(villager, matcher);
        if (slot < 0) return;
        SimpleContainer inv = villager.getInventory();
        ItemStack stack = inv.getItem(slot);
        if (!stack.isDamageableItem()) return;
        int newDamage = stack.getDamageValue() + 1;
        if (newDamage >= stack.getMaxDamage()) {
            stack.shrink(stack.getCount());
            villager.level().playSound(
                    null, villager.blockPosition(),
                    SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL,
                    0.8f, 0.8f + villager.level().random.nextFloat() * 0.4f);
        } else {
            stack.setDamageValue(newDamage);
        }
        inv.setChanged();
    }
}
