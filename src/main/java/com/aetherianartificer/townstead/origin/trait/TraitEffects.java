package com.aetherianartificer.townstead.origin.trait;

import com.aetherianartificer.townstead.origin.trait.effect.types.SetImmortalEffectType;
import net.conczin.mca.entity.VillagerEntityMCA;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Engine-side queries over a villager's trait capabilities. Trait membership lives
 * in MCA (synced + persisted); this answers "do any of this villager's traits grant
 * capability X?". The immortal-conferring id set is cached on registry reload so the
 * per-villager check is allocation-free.
 */
public final class TraitEffects {

    private static volatile Set<String> IMMORTAL_TRAIT_IDS = Set.of();

    private TraitEffects() {}

    /** Recompute cached capability id sets from the loaded traits. Called on reload. */
    static void rebuild(Collection<DataTrait> traits) {
        Set<String> immortal = new LinkedHashSet<>();
        for (DataTrait t : traits) {
            if (t.flag(SetImmortalEffectType.KEY, false)) immortal.add(t.id());
        }
        IMMORTAL_TRAIT_IDS = Set.copyOf(immortal);
    }

    /** True if the villager carries any trait conferring {@code life.immortal}. */
    public static boolean isImmortal(VillagerEntityMCA villager) {
        if (villager == null || IMMORTAL_TRAIT_IDS.isEmpty()) return false;
        for (String id : IMMORTAL_TRAIT_IDS) {
            if (villager.getTraits().hasTrait(id)) return true;
        }
        return false;
    }
}
