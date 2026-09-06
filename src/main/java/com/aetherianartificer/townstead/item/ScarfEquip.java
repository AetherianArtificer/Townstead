package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.root.EntityGroups;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Puts a held scarf onto a villager: into a free Curios slot when Curios is present, else the head slot
 * (vanilla's head-item render draws it), swapping out whatever it wore there. Only spider-folk (the
 * arthropod {@link EntityGroups entity group}) may wear a scarf; equipping a non-arthropod is refused
 * with an action-bar message. Server-side; the interaction event reports success on both sides.
 */
public final class ScarfEquip {

    /** Shown when something that isn't spider-folk is offered a scarf. */
    public static final String DENY_KEY = "message.townstead.scarf_arthropod_only";

    private ScarfEquip() {}

    /** True if the stack is a scarf (so the caller cancels the interaction); equips it server-side. */
    public static boolean tryEquip(Player player, LivingEntity villager, ItemStack stack) {
        if (!(stack.getItem() instanceof ScarfItem)) return false;
        if (villager.level().isClientSide) return true;

        if (!EntityGroups.isArthropod(villager)) {
            player.displayClientMessage(Component.translatable(DENY_KEY), true);
            return true; // consume the interaction, but refuse the equip
        }

        // With Curios the scarf takes the villager's necklace slot, as it does on a player; the head
        // slot is the fallback for installs without it (or when every fitting slot is taken).
        ItemStack previous = ItemStack.EMPTY;
        if (!CuriosCompat.equipFirstFree(villager, stack)) {
            previous = villager.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack worn = stack.copy();
            worn.setCount(1);
            villager.setItemSlot(EquipmentSlot.HEAD, worn);
        }

        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!previous.isEmpty() && !player.addItem(previous)) player.drop(previous, false);
        return true;
    }

    /** The scarf rule as a slot predicate: anything but a scarf passes, a scarf needs spider-folk. */
    public static boolean mayWear(LivingEntity entity, ItemStack stack) {
        return !(stack.getItem() instanceof ScarfItem) || EntityGroups.isArthropod(entity);
    }

    /**
     * Server backstop on equipment change: if a non-arthropod ends up with a scarf in its head slot (the
     * inventory armor-slot drag, a dispenser, a command, mob spawn gear), strip it back out. The active
     * right-click gates refuse cleanly; this catches every other path so the rule holds everywhere. Returns
     * the removed scarf to a player's inventory (with the deny message), or drops it for a non-player.
     */
    public static boolean enforce(LivingEntity entity, EquipmentSlot slot, ItemStack to) {
        if (slot != EquipmentSlot.HEAD || entity.level().isClientSide) return false;
        if (!(to.getItem() instanceof ScarfItem)) return false;
        if (EntityGroups.isArthropod(entity)) return false;

        // Setting the slot empty re-fires the change with an empty stack, which this method ignores.
        entity.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack returned = to.copy();
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(DENY_KEY), true);
            if (!player.addItem(returned)) player.drop(returned, false);
        } else {
            entity.spawnAtLocation(returned);
        }
        return true;
    }

    /**
     * Curios counterpart of {@link #enforce}: a Curios slot has no per-wearer validity, so a non-arthropod
     * could drop a scarf into one. Polled on the player tick (Curios only), this strips any such scarf back
     * to the player with the deny message. No-op without Curios, for spider-folk, or on the client.
     */
    public static void enforceCurios(Player player) {
        if (player.level().isClientSide || !CuriosCompat.present()) return;
        CuriosCompat.removeWhere(player,
                stack -> stack.getItem() instanceof ScarfItem && !EntityGroups.isArthropod(player),
                removed -> {
                    player.displayClientMessage(Component.translatable(DENY_KEY), true);
                    if (!player.addItem(removed)) player.drop(removed, false);
                });
    }
}
