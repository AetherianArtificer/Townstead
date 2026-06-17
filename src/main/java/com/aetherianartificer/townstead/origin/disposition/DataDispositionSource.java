package com.aetherianartificer.townstead.origin.disposition;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The data-pack-driven default answerer: resolves both entities' disposition groups
 * ({@link DispositionGroups}) and consults the viewer group's authored relations
 * ({@link DispositionRelations}). Kinship is checked before enmity, so a member of a group that is
 * friendly to its own kind is never accidentally hostile to them. Abstains (null) when the viewer's
 * group declares no opinion about the other's, leaving it neutral or to a higher-priority source
 * (the future faction system).
 */
public final class DataDispositionSource implements DispositionSource {

    @Override
    @Nullable
    public Disposition between(LivingEntity viewer, LivingEntity other) {
        DispositionRelations.GroupDef def = DispositionRelations.relations(DispositionGroups.of(viewer));
        if (def == null) return null;
        String otherGroup = DispositionGroups.of(other);
        if (def.friendly().contains(otherGroup)) return Disposition.FRIENDLY;
        if (def.hostile().contains(otherGroup)) return Disposition.HOSTILE;
        return null;
    }
}
