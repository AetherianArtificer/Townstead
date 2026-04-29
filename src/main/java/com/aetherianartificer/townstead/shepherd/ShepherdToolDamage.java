package com.aetherianartificer.townstead.shepherd;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Durability bridge for shepherd shears. Mirrors {@code ButcherToolDamage}:
 * the displayed main-hand stack is a copy maintained by {@link
 * com.aetherianartificer.townstead.tick.WorkToolTicker}, so damage applied
 * there does not persist. This helper damages the first matching shears in
 * the villager's inventory and breaks them when their durability hits zero.
 */
public final class ShepherdToolDamage {
    private ShepherdToolDamage() {}

    public static boolean hasShears(VillagerEntityMCA villager) {
        return ShepherdShearToolCompatRegistry.findShearsSlot(villager.getInventory()) >= 0;
    }

    public static void consumeShearsUse(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int slot = ShepherdShearToolCompatRegistry.findShearsSlot(inv);
        if (slot < 0) return;
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
