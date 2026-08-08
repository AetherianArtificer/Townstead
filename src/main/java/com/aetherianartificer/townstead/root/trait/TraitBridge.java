package com.aetherianartificer.townstead.root.trait;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Bridges a data trait into MCA's registry as ONE entry under its one data-pack id.
 * MCA's {@code Trait.valueOf} is case-exact on 7.7.x but uppercases lookups on both
 * 1.20.1 lines (legacy enum-era NBT compat), where a lowercase-keyed entry is
 * unreachable. There the entry is re-keyed in the registry to the uppercase form
 * while its {@code id()} stays the data-pack id, so {@code valueOf} resolves it and
 * the translation key, {@code enabledTraits} entry, and stored NBT keys all remain
 * the single lowercase id. {@link #migrate} case-normalizes keys stored by older
 * builds, which registered a separate uppercase alias trait.
 */
public final class TraitBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/TraitBridge");
    private static final CDataParameter<CompoundTag> TRAITS_PARAM = lookupTraitsParam();

    private TraitBridge() {}

    /**
     * Register {@code id} with MCA, re-keyed so this MCA's {@code valueOf} resolves
     * it. Throws on MCA API drift; callers isolate per trait so one failure can't
     * abort a reload.
     */
    public static void register(String id, float chance, float inherit, boolean usableOnPlayer) {
        Traits.Trait trait = Traits.registerTrait(id, chance, inherit, usableOnPlayer);
        String upper = id.toUpperCase(Locale.ROOT);
        // Identity probe, not an id guard (a stale entry from a previous /reload can
        // answer for the id): unless valueOf round-trips to the entry just registered,
        // this MCA uppercases lookups — move the entry to the key valueOf will ask for.
        if (!upper.equals(id) && com.aetherianartificer.townstead.compat.mca.McaTraitCompat.resolve(id).orElse(null) != trait) {
            Traits.TRAIT_REGISTRY.remove(id);
            Traits.TRAIT_REGISTRY.put(upper, trait);
        }
    }

    /**
     * Case-normalize stored data-trait keys to the data-pack id. Older builds
     * registered an uppercase alias trait, so grants and editor toggles could store
     * either casing; hasTrait/removeTrait compare the raw stored key, so a
     * mismatched casing reads as "doesn't have the trait" and can't be toggled off
     * in the editor. Server-side, on villager load.
     */
    public static void migrate(VillagerEntityMCA villager) {
        if (villager == null || TRAITS_PARAM == null) return;
        try {
            CompoundTag traits = villager.getTrackedValue(TRAITS_PARAM);
            CompoundTag fixed = null;
            for (String key : traits.getAllKeys()) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (lower.equals(key) || TraitRegistry.byId(lower) == null) continue;
                if (fixed == null) fixed = traits.copy();
                fixed.remove(key);
                fixed.putBoolean(lower, true);
            }
            if (fixed != null) villager.setTrackedValue(TRAITS_PARAM, fixed);
        } catch (Throwable e) {
            LOGGER.warn("Could not normalize trait keys: {}", e.toString());
        }
    }

    /** MCA's private tracked-data parameter for the traits compound; null on drift. */
    @SuppressWarnings("unchecked")
    private static CDataParameter<CompoundTag> lookupTraitsParam() {
        try {
            Field field = Traits.class.getDeclaredField("TRAITS");
            field.setAccessible(true);
            return (CDataParameter<CompoundTag>) field.get(null);
        } catch (Throwable e) {
            LOGGER.warn("MCA Traits.TRAITS unavailable, trait key migration disabled: {}", e.toString());
            return null;
        }
    }
}
