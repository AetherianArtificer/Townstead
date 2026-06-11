package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A data-driven profession: its progression track, how skills unlock, the points awarded per
 * tier, and its retraining policy. The skill tree is formed by the {@link SkillDef}s that name
 * this profession; {@code skills} is an optional explicit membership list for documentation and
 * ordering. Pheno owns this definition and the capabilities it grants; Townstead still owns what
 * counts as successful work and emits the events that drive XP.
 */
public record ProfessionDef(
        ResourceLocation id,
        Component displayName,
        @Nullable Component description,
        ProgressionTrack progression,
        UnlockModel unlockModel,
        int pointsPerTier,
        RetrainingPolicy retraining,
        List<ResourceLocation> skills) {
}
