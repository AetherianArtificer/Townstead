package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * A dhampir hunter plays with vampire quarry, as a cat does: the blow that would finish a vampire
 * is held back and they step off for a moment, once at a steady pull of the blood, twice when
 * hungry. Near their oath they finish it at once. Each held blow counts {@link #TOYED} on them.
 */
public final class Toying {
    /** Chronicle counter on the hunter for every blow they held back. */
    public static final String TOYED = "townstead:toyed";
    private static final ResourceLocation PULL = ResourceLocation.tryParse("townstead_state:blood_pull");
    private static final ResourceLocation DHAMPIR = ResourceLocation.tryParse("townstead_state:dhampir");
    private static final String HELD = "townstead:toyed_with";
    private static final double STEADY = 25;
    private static final double HUNGRY = 50;
    private static final int STEP_BACK_TICKS = 40;

    private Toying() {}

    /** Damage hook: returns the damage to deal, 0 when the hunter holds the finishing blow. */
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        if (amount < victim.getHealth() || !(source.getEntity() instanceof VillagerEntityMCA hunter)
                || !(victim.level() instanceof ServerLevel level)) return amount;
        if (!EntityStates.resolve(hunter, DHAMPIR).active() || !quarry(victim)) return amount;
        double pull = EntityStates.resolve(hunter, PULL).amount();
        int allowed = pull >= HUNGRY ? 2 : pull >= STEADY ? 1 : 0;
        int held = victim.getPersistentData().getInt(HELD);
        if (held >= allowed) return amount;
        victim.getPersistentData().putInt(HELD, held + 1);
        hunter.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, STEP_BACK_TICKS);
        Chronicles.addCounter(level.getServer(), hunter.getUUID(), TOYED, 1);
        return 0f;
    }

    private static boolean quarry(LivingEntity victim) {
        String group = com.aetherianartificer.townstead.root.disposition.DispositionGroups.of(victim);
        return VampireVillagers.isVampire(victim) || "vampire".equals(group) || "wild_vampire".equals(group);
    }
}
