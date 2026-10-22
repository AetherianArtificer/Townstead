package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.CultureRules;
import com.aetherianartificer.townstead.culture.CultureVillages;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Picks the founding profile of a newly recognized village: each loaded profile weighs its authored
 * {@code weight} times its {@code spawn_bias} for the biome at the village center, and a profile's
 * {@code when} must hold for a resident. The roll is seeded by the world and the village, so the
 * same village picks the same profile however often it is asked.
 *
 * <p>A village standing in a culture's own village structure rolls only among the profiles that
 * structure belongs to (see {@link CultureVillages}). Cultures switched off in the Switchboard never
 * found, and each culture's Switchboard rate scales its profiles' weights.</p>
 */
public final class FoundingSelection {
    private FoundingSelection() {}

    public static @Nullable FoundingProfileDefinition choose(ServerLevel level, Village village) {
        BlockPos center = new BlockPos(village.getCenter());
        LivingEntity resident = resident(level, village);
        List<FoundingProfileDefinition> candidates = new ArrayList<>(FoundingProfiles.all());
        candidates.sort(Comparator.comparing(profile -> profile.id().toString()));
        RandomSource random = RandomSource.create(level.getSeed() ^ (village.getId() * 0x9E3779B97F4A7C15L));
        // A village generated as a culture's own structure founds as that culture.
        ResourceLocation structure = CultureVillages.structureAt(level, center);
        if (structure != null) {
            FoundingProfileDefinition chosen = roll(random, candidates, profile -> claims(structure, profile)
                    && allowed(profile, resident) ? profile.weight() * cultureRate(profile) : 0);
            if (chosen != null) return chosen;
        }
        // What was already built leans the roll toward forms whose subcultures suit it.
        var spirit = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.totalsFor(village);
        return roll(random, candidates, profile -> allowed(profile, resident)
                ? FoundingProfileApplier.environment(level, center, profile).naturalWeight()
                        * com.aetherianartificer.townstead.culture.Subcultures.foundingFactor(profile.culture(), profile.id(), spirit)
                        * cultureRate(profile)
                : 0);
    }

    /** A structure claimed by profiles founds as one of them; one claimed by a culture, as any of its profiles. */
    private static boolean claims(ResourceLocation structure, FoundingProfileDefinition profile) {
        List<ResourceLocation> profiles = CultureVillages.profiles(structure);
        if (!profiles.isEmpty()) return profiles.contains(profile.id());
        return Cultures.within(profile.culture(), CultureVillages.culture(structure));
    }

    private static boolean allowed(FoundingProfileDefinition profile, @Nullable LivingEntity resident) {
        if (profile.culture() != null && !CultureRules.enabled(profile.culture().toString())) return false;
        return profile.when() == Conditions.ALWAYS
                || resident != null && profile.when().test(new ConditionContext(resident));
    }

    private static float cultureRate(FoundingProfileDefinition profile) {
        return profile.culture() == null ? 1.0F : (float) CultureRules.rate(profile.culture().toString());
    }

    private static @Nullable FoundingProfileDefinition roll(RandomSource random, List<FoundingProfileDefinition> candidates,
                                                            ToDoubleFunction<FoundingProfileDefinition> weightOf) {
        List<FoundingProfileDefinition> eligible = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0;
        for (FoundingProfileDefinition profile : candidates) {
            float weight = (float) weightOf.applyAsDouble(profile);
            if (!(weight > 0)) continue;
            eligible.add(profile);
            weights.add(weight);
            total += weight;
        }
        if (total <= 0) return null;
        float roll = random.nextFloat() * total;
        for (int i = 0; i < eligible.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return eligible.get(i);
        }
        return eligible.get(eligible.size() - 1);
    }

    private static @Nullable LivingEntity resident(ServerLevel level, Village village) {
        for (UUID id : village.getResidentsUUIDs().toList()) {
            Entity entity = id == null ? null : level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }
}
