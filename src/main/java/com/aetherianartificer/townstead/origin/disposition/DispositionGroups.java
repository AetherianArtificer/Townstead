package com.aetherianartificer.townstead.origin.disposition;

import com.aetherianartificer.townstead.origin.EntityGroups;
import com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType.Group;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Resolves the disposition group an entity belongs to, in priority order: its {@code entity_group}
 * gene (so a skeleton-bodied villager is {@code undead}), then a group's authored {@code members}
 * list (so wild zombies are {@code undead} and plain villagers/players are {@code townsfolk} from
 * data, not code), else {@code default}. One group per entity, so relations never conflict. Nothing
 * here names a group: groups and their membership are entirely data-pack defined.
 */
public final class DispositionGroups {

    private DispositionGroups() {}

    public static String of(LivingEntity entity) {
        Group gene = EntityGroups.of(entity);
        if (gene != Group.DEFAULT) return gene.name().toLowerCase(Locale.ROOT);
        String byMembers = DispositionRelations.groupOf(entity.getType());
        return byMembers != null ? byMembers : "default";
    }
}
