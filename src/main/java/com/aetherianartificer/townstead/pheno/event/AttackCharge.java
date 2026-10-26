package com.aetherianartificer.townstead.pheno.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How charged a player's swing was. Vanilla resets the attack timer before the victim is hurt, so
 * a hurt or attack trigger reading the live timer would always see an empty bar; the attack
 * event records the charge first, and readers in the same tick get that value.
 */
public final class AttackCharge {

    private record Swing(int tick, float charge) {}

    private static final Map<Player, Swing> SWINGS = new WeakHashMap<>();

    private AttackCharge() {}

    /** Called from the attack event, before vanilla resets the timer. */
    public static void record(Player player) {
        SWINGS.put(player, new Swing(player.tickCount, player.getAttackStrengthScale(0.5f)));
    }

    /** The charge of the swing landing this tick, else the live bar. Non-players always swing full. */
    public static float of(LivingEntity entity) {
        if (!(entity instanceof Player player)) return 1f;
        Swing swing = SWINGS.get(player);
        if (swing != null && swing.tick() == player.tickCount) return swing.charge();
        return player.getAttackStrengthScale(0.5f);
    }
}
