package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Knocks the acting mob's main-hand item to the ground as a pickable item entity
 * (players are never force-disarmed). Empty hand is a no-op. Pair with a combat
 * trigger targeting {@code other} and a {@code chance} wrapper: goblin Nimble Fingers
 * makes a struck foe sometimes drop its weapon.
 *
 * <p>JSON: {@code { "type":"pheno:disarm" }}</p>
 */
public final class DisarmActionType implements ActionType {

    public static final String KEY = "pheno:disarm";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.entity() instanceof Mob mob) || !mob.isAlive()) return;
            if (mob.getMainHandItem().isEmpty()) return;
            ItemStack held = com.aetherianartificer.townstead.villager.WornItems.knockOut(mob, EquipmentSlot.MAINHAND);
            if (!held.isEmpty()) mob.spawnAtLocation(held);
        };
    }
}
