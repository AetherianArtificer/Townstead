package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType;
import com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType.Group;
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

    /**
     * The group an entity plays as: its {@code entity_group} gene when it has one, otherwise the
     * group vanilla gives its type, so a plain zombie reads as undead. Only pheno conditions ask
     * this; the gene-only {@link #of} stays what the combat hooks and mixins layer onto vanilla.
     */
    public static Group expressed(LivingEntity entity) {
        Group gene = of(entity);
        if (gene != Group.DEFAULT) return gene;
        //? if >=1.21 {
        var type = entity.getType();
        if (type.is(net.minecraft.tags.EntityTypeTags.UNDEAD)) return Group.UNDEAD;
        if (type.is(net.minecraft.tags.EntityTypeTags.ARTHROPOD)) return Group.ARTHROPOD;
        if (type.is(net.minecraft.tags.EntityTypeTags.ILLAGER)) return Group.ILLAGER;
        if (type.is(net.minecraft.tags.EntityTypeTags.AQUATIC)) return Group.AQUATIC;
        return Group.DEFAULT;
        //?} else {
        /*net.minecraft.world.entity.MobType type = entity.getMobType();
        if (type == net.minecraft.world.entity.MobType.UNDEAD) return Group.UNDEAD;
        if (type == net.minecraft.world.entity.MobType.ARTHROPOD) return Group.ARTHROPOD;
        if (type == net.minecraft.world.entity.MobType.ILLAGER) return Group.ILLAGER;
        if (type == net.minecraft.world.entity.MobType.WATER) return Group.AQUATIC;
        return Group.DEFAULT;
        *///?}
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
