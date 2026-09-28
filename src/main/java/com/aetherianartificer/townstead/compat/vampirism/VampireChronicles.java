package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.replace.MobReplacer;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/** The vampire moments worth remembering: a bite nobody asked for, a full turning, a hunter's kill. */
public final class VampireChronicles {
    private static final String RANK_SEEN = "townstead:vampire_rank_seen";
    private static final int FULL_VAMPIRE = 4;

    private VampireChronicles() {}

    /** A villager was drunk from without consent. Willing bites between spouses or a thrall stay private. */
    public static void bitten(VillagerEntityMCA biter, LivingEntity prey, boolean willing) {
        if (willing || !(prey instanceof VillagerEntityMCA)) return;
        ChronicleTaps.survival(prey, ChronicleTapKeys.VAMPIRE_BITE, Map.of("biter", biter.getName().getString()));
    }

    /**
     * Notes the moment a resident vampire's blood carries them to a full vampire. The first look only
     * records where they stand, so vampires who arrived already turned are not announced.
     */
    public static void observeRank(VillagerEntityMCA villager) {
        if (villager.tickCount % 100 != 0 || MobReplacer.isWild(villager)) return;
        EntityStates.Resolved state = EntityStates.resolve(villager, VampireVillagers.STATE);
        int level = state.active() ? (int) Math.floor(state.amount()) : 0;
        var data = villager.getPersistentData();
        boolean seen = data.contains(RANK_SEEN);
        int before = data.getInt(RANK_SEEN);
        if (seen && before == level) return;
        data.putInt(RANK_SEEN, level);
        if (seen && before < FULL_VAMPIRE && level >= FULL_VAMPIRE) {
            ChronicleTaps.survival(villager, ChronicleTapKeys.BECAME_FULL_VAMPIRE, Map.of());
        }
    }

    /** A vampire villager, resident or wild, killed by a hunter: a hunter mob or a player in the hunters. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof VillagerEntityMCA villager) || !VampireVillagers.isVampire(villager)) return;
        if (!(source.getEntity() instanceof LivingEntity killer)) return;
        boolean hunter = "hunter".equals(DispositionGroups.of(killer)) || VampirismStateProviders.isHunter(killer);
        if (!hunter) return;
        ChronicleTaps.survival(villager, ChronicleTapKeys.HUNTER_SLEW_VAMPIRE, Map.of("hunter", killer.getName().getString()));
    }
}
