package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Picks the founding profile of a newly recognized village: each loaded profile weighs its authored
 * {@code weight} times its {@code spawn_bias} for the biome at the village center, and a profile's
 * {@code when} must hold for a resident. The roll is seeded by the world and the village, so the
 * same village picks the same profile however often it is asked.
 */
public final class FoundingSelection {
    private FoundingSelection() {}

    public static @Nullable FoundingProfileDefinition choose(ServerLevel level, Village village) {
        BlockPos center = new BlockPos(village.getCenter());
        LivingEntity resident = resident(level, village);
        List<FoundingProfileDefinition> candidates = new ArrayList<>(FoundingProfiles.all());
        candidates.sort(Comparator.comparing(profile -> profile.id().toString()));
        List<FoundingProfileDefinition> eligible = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        float total = 0;
        for (FoundingProfileDefinition profile : candidates) {
            if (profile.when() != Conditions.ALWAYS
                    && (resident == null || !profile.when().test(new ConditionContext(resident)))) continue;
            float weight = FoundingProfileApplier.environment(level, center, profile).naturalWeight();
            if (weight <= 0) continue;
            eligible.add(profile);
            weights.add(weight);
            total += weight;
        }
        if (total <= 0) return null;
        RandomSource random = RandomSource.create(level.getSeed() ^ (village.getId() * 0x9E3779B97F4A7C15L));
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
