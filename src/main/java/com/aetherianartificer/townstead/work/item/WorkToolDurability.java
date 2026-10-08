package com.aetherianartificer.townstead.work.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Durability operations for real tools retained in a worker's inventory. */
public final class WorkToolDurability {
    private WorkToolDurability() {}

    public static void damageFirstMatching(VillagerEntityMCA villager,
                                           Predicate<ItemStack> matcher) {
        if (villager == null || matcher == null) return;
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack) || !stack.isDamageableItem()) continue;
            int damage = stack.getDamageValue() + 1;
            if (damage >= stack.getMaxDamage()) {
                stack.shrink(stack.getCount());
                villager.level().playSound(null, villager.blockPosition(),
                        SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL,
                        0.8f, 0.8f + villager.level().random.nextFloat() * 0.4f);
            } else {
                stack.setDamageValue(damage);
            }
            inventory.setChanged();
            return;
        }
    }
}
