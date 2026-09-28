package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.LifeCycle;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

/**
 * A dhampir lives long: once their aspect takes hold, the adult and senior stages of their own
 * life cycle last twice as long. Childhood keeps its pace, so a dhampir still grows up with the
 * other children. Done once, on their stored schedule, so an age never slides backwards.
 */
public final class DhampirAging {
    public static final ResourceLocation STATE = ResourceLocation.tryParse("townstead_state:dhampir");
    private static final String PACED = "townstead:dhampir_paced";
    private static final int ADULT_PACE = 2;

    private DhampirAging() {}

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % 100 != 0 || villager.getPersistentData().getBoolean(PACED)) return;
        if (EntityStates.definition(STATE) == null || !EntityStates.resolve(villager, STATE).active()) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        LifeCycle cycle = RootRegistry.effectiveLifeCycle(ResourceLocation.tryParse(life.rootId()));
        int[] days = life.stageDays();
        // Not rolled yet (a newborn a tick old): try again shortly.
        if (cycle == null || days.length == 0 || days.length != cycle.size()) return;
        int[] paced = days.clone();
        for (int i = 0; i < paced.length; i++) {
            CanonicalStage stage = cycle.stageAt(i).presentsAs();
            if (stage == CanonicalStage.ADULT || stage == CanonicalStage.SENIOR) paced[i] *= ADULT_PACE;
        }
        life.setStageDays(paced);
        villager.getPersistentData().putBoolean(PACED, true);
    }
}
