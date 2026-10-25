package com.aetherianartificer.townstead.root.rig;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Enforces a rig's {@link RigDefinition#disabledSlots() disabled equipment slots}: a body that can't wear
 * or hold gear in a slot (an egg wears nothing). Server backstop on equipment change, alongside the scarf
 * gate, anything placed in a disabled slot is stripped back out, returned to a player (with a message) or
 * dropped. The slot then stays empty, so nothing renders there either. No-op for the common case where the
 * slot isn't disabled, so a normal humanoid rig is untouched.
 */
public final class RigEquipment {

    /** Shown when a rig refuses gear in a slot. */
    public static final String DENY_KEY = "message.townstead.equip_slot_unavailable";

    private RigEquipment() {}

    /**
     * Whether this entity's body takes gear in {@code slot} at all. Asked up front (an inventory slot
     * refusing the item, a villager not dressing a slot) so the backstop below is a last resort.
     * Either side: the client reads the synced rig.
     */
    public static boolean allows(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null) return true;
        RigDefinition def = entity.level().isClientSide ? ClientRig.defFor(entity) : ServerRig.defFor(entity);
        return def == null || !def.disabledSlots().contains(slot);
    }

    /**
     * Whether this entity's body wears but doesn't show gear in {@code slot} (a ghost's legs and feet).
     * Render-side: the client reads the synced rig.
     */
    public static boolean hides(LivingEntity entity, EquipmentSlot slot) {
        if (entity == null) return false;
        RigDefinition def = entity.level().isClientSide ? ClientRig.defFor(entity) : ServerRig.defFor(entity);
        return def != null && def.hiddenSlots().contains(slot);
    }

    /** The equipment slot a Curios slot type sits on, by its usual names, or null for one with no slot. */
    public static @org.jetbrains.annotations.Nullable EquipmentSlot curioBodySlot(String curioSlot) {
        if (curioSlot == null) return null;
        return switch (curioSlot.toLowerCase(java.util.Locale.ROOT)) {
            case "feet", "boots", "shoes", "anklet", "ankle" -> EquipmentSlot.FEET;
            case "legs", "pants", "leggings" -> EquipmentSlot.LEGS;
            default -> null;
        };
    }

    /** Whether a curio in {@code curioSlot} goes undrawn on this body (it sits on a part the body hides). */
    public static boolean hidesCurio(LivingEntity entity, String curioSlot) {
        EquipmentSlot slot = curioBodySlot(curioSlot);
        return slot != null && hides(entity, slot);
    }

    /** Client-only rig access, isolated so dedicated servers never load it. */
    private static final class ClientRig {
        static RigDefinition defFor(LivingEntity entity) {
            String base = com.aetherianartificer.townstead.client.species.RigModels.rigBaseFor(entity);
            return base == null || base.isEmpty() ? null
                    : com.aetherianartificer.townstead.client.species.RigModels.definition(base);
        }
    }

    /** Strips gear placed in a slot this entity's rig disables. Returns true if it removed something. */
    public static boolean enforce(LivingEntity entity, EquipmentSlot slot, ItemStack to) {
        if (entity.level().isClientSide || to.isEmpty()) return false;
        RigDefinition def = ServerRig.defFor(entity);
        if (def == null || !def.disabledSlots().contains(slot)) return false;

        // Setting the slot empty re-fires the change with an empty stack, which the to.isEmpty() guard skips.
        ItemStack returned = com.aetherianartificer.townstead.villager.WornItems.take(entity, slot);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(DENY_KEY), true);
            if (!returned.isEmpty() && !player.addItem(returned)) player.drop(returned, false);
        } else if (!returned.isEmpty()) {
            entity.spawnAtLocation(returned);
        }
        return true;
    }
}
