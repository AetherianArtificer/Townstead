package com.aetherianartificer.townstead.root.trait;

import com.aetherianartificer.townstead.compat.mca.McaTraitCompat;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.gene.types.TraitOccurrenceGeneType;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * MCA traits a root rules out ({@code "block": true} trait genes, such as dwarfism for a people
 * already small). MCA rolls traits at spawn and passes them on at birth with no say from roots, so
 * a blocked trait is taken off wherever one can arrive: founding, birth, a root change, and each
 * time a villager enters the world.
 */
public final class TraitBlocks {

    private TraitBlocks() {}

    /** Removes the traits the villager's root blocks. */
    public static void apply(VillagerEntityMCA villager) {
        if (villager == null) return;
        ResourceLocation rootId = ResourceLocation.tryParse(TownsteadVillagers.get(villager).life().rootId());
        for (TraitOccurrenceGeneType.Instance gene : RootRegistry.traitGenes(rootId)) {
            if (!gene.blocks()) continue;
            Traits.Trait trait = McaTraitCompat.resolve(gene.trait()).orElse(null);
            if (trait != null && villager.getTraits().hasTrait(trait)) villager.getTraits().removeTrait(trait);
        }
    }

    /**
     * Removes the traits {@code rootId} blocks from a player's saved MCA data, the record MCA draws a
     * villager-model player (and sizes them) from. Returns whether anything was removed.
     */
    public static boolean applyTo(CompoundTag entityData, @Nullable ResourceLocation rootId) {
        if (!entityData.contains("Traits", 10)) return false;
        CompoundTag traits = entityData.getCompound("Traits");
        boolean changed = false;
        for (TraitOccurrenceGeneType.Instance gene : RootRegistry.traitGenes(rootId)) {
            if (!gene.blocks()) continue;
            Traits.Trait trait = McaTraitCompat.resolve(gene.trait()).orElse(null);
            if (trait == null) continue;
            // Stored keys differ in case and namespace across MCA lines; match by the trait they name.
            for (String key : java.util.List.copyOf(traits.getAllKeys())) {
                if (McaTraitCompat.resolve(key).orElse(null) == trait) {
                    traits.remove(key);
                    changed = true;
                }
            }
        }
        if (changed) entityData.put("Traits", traits);
        return changed;
    }
}
