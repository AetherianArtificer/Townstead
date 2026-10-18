package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.culture.CulturalSpawnBias;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.root.RootRegistry;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.function.ToDoubleFunction;

/** Root-weight contribution from the persisted founding profile of the local MCA village. */
public final class FoundingPopulationWeights {
    private FoundingPopulationWeights() {}

    /** Neutral outside a profiled village or for a population strategy this Townstead does not own. */
    public static double multiplier(ServerLevel level, BlockPos pos, ResourceLocation root) {
        return at(level, pos).applyAsDouble(root);
    }

    /** Resolves the local profile once, then cheaply weighs every candidate Root in the roll. */
    public static ToDoubleFunction<ResourceLocation> at(ServerLevel level, BlockPos pos) {
        FoundingProfileDefinition profile = profileAt(level, pos);
        if (profile == null || !profile.population().strategy()
                .equals(FoundingProfileDefinition.CULTURAL_AFFINITY)) return ignored -> 1.0D;
        return shares(profile, RootRegistry.all().stream().map(com.aetherianartificer.townstead.root.Root::id).toList(),
                RootRegistry::effectiveCulturalSpawnBias);
    }

    /**
     * Residents as shares of the town: roots with a rate for this culture split everything but the
     * outsider baseline by that rate, and every other root splits the baseline by one plus its rate
     * for any culture. Installing more roots then leaves the natives' share as it was. With no native
     * root installed, every root gets its {@link #culturalAffinity} weight instead.
     */
    static ToDoubleFunction<ResourceLocation> shares(FoundingProfileDefinition profile,
                                                     java.util.Collection<ResourceLocation> roots,
                                                     java.util.function.Function<ResourceLocation, CulturalSpawnBias> biasOf) {
        float outsiderShare = Math.max(0.0F, Math.min(1.0F, profile.population().outsiderBaseline()));
        double nativeTotal = 0;
        double outsiderTotal = 0;
        for (ResourceLocation root : roots) {
            CulturalSpawnBias bias = biasOf.apply(root);
            double adjustment = profile.population().adjustments().getOrDefault(root, 1.0F);
            double nativeRate = nativeRate(profile, bias);
            if (nativeRate > 0) nativeTotal += nativeRate * adjustment;
            else outsiderTotal += outsiderRate(bias) * adjustment;
        }
        if (nativeTotal <= 0) return root -> culturalAffinity(profile, root, biasOf.apply(root));
        double natives = nativeTotal;
        double outsiders = outsiderTotal;
        return root -> {
            CulturalSpawnBias bias = biasOf.apply(root);
            double adjustment = profile.population().adjustments().getOrDefault(root, 1.0F);
            double nativeRate = nativeRate(profile, bias);
            if (nativeRate > 0) return (1.0 - outsiderShare) * nativeRate * adjustment / natives;
            return outsiders <= 0 ? 0.0 : outsiderShare * outsiderRate(bias) * adjustment / outsiders;
        };
    }

    private static double nativeRate(FoundingProfileDefinition profile, CulturalSpawnBias bias) {
        if (profile.culture() == null) return 0;
        return Math.max(0.0F, bias.weights().getOrDefault(profile.culture().toString(), 0.0F));
    }

    private static double outsiderRate(CulturalSpawnBias bias) {
        return 1.0 + Math.max(0.0F, bias.weights().getOrDefault(Cultures.ANY, 0.0F));
    }

    static float culturalAffinity(FoundingProfileDefinition profile, ResourceLocation root,
                                  CulturalSpawnBias bias) {
        float affinity = bias.weights().getOrDefault(Cultures.ANY, 0.0F);
        if (profile.culture() != null) {
            affinity += bias.weights().getOrDefault(profile.culture().toString(), 0.0F);
        }
        float adjustment = profile.population().adjustments().getOrDefault(root, 1.0F);
        return Math.max(0.0F, (profile.population().outsiderBaseline() + affinity) * adjustment);
    }

    private static FoundingProfileDefinition profileAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        try {
            Village village = VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
            if (village == null) return null;
            SettlementFoundingRecord record = PoliticalSavedData.get(level.getServer()).founding(
                    new SettlementRef(level.dimension().location(), village.getId()));
            return record == null ? null : FoundingProfiles.get(record.profile());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
