package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType;
import com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType.Group;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Resolves an entity's expressed creature {@link Group} from its
 * {@code entity_group} gene. The 1.20.1 {@code getMobType} mixin and the 1.21.1
 * combat hooks both read this. Server-side (genotype lives server-side).
 */
public final class EntityGroups {

    private EntityGroups() {}

    public static Group of(LivingEntity entity) {
        List<EntityGroupGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, EntityGroupGeneType.Instance.class);
        return genes.isEmpty() ? Group.DEFAULT : genes.get(0).group();
    }

    public static boolean isUndead(LivingEntity entity) {
        return of(entity) == Group.UNDEAD;
    }

    public static boolean isArthropod(LivingEntity entity) {
        return of(entity) == Group.ARTHROPOD;
    }

    //? if <1.21 {
    /*public static net.minecraft.world.entity.MobType mobType(LivingEntity entity) {
        return switch (of(entity)) {
            case UNDEAD -> net.minecraft.world.entity.MobType.UNDEAD;
            case ARTHROPOD -> net.minecraft.world.entity.MobType.ARTHROPOD;
            case ILLAGER -> net.minecraft.world.entity.MobType.ILLAGER;
            case AQUATIC -> net.minecraft.world.entity.MobType.WATER;
            case DEFAULT -> null;
        };
    }
    *///?}
}
