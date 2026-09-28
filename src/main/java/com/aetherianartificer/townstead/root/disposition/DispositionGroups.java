package com.aetherianartificer.townstead.root.disposition;

import com.aetherianartificer.townstead.root.EntityGroups;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.gene.types.DispositionGroupGeneType;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType.Group;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Resolves the disposition group an entity belongs to, in priority order: the group of the mob it
 * replaces while it is still that mob (a wild vampire), its {@code disposition_group} gene (often an
 * aspect's grant), its {@code entity_group} gene (so a skeleton-bodied villager is {@code undead}),
 * then a group's authored {@code members}
 * list (so wild zombies are {@code undead} and plain villagers/players are {@code townsfolk} from
 * data, not code), else {@code default}. One group per entity, so relations never conflict. Nothing
 * here names a group: groups and their membership are entirely data-pack defined.
 */
public final class DispositionGroups {

    private DispositionGroups() {}

    public static String of(LivingEntity entity) {
        String wild = com.aetherianartificer.townstead.replace.MobReplacer.groupOf(entity);
        if (wild != null) return wild;
        java.util.List<DispositionGroupGeneType.Instance> declared =
                ExpressedGenes.instancesOf(entity, DispositionGroupGeneType.Instance.class);
        if (!declared.isEmpty()) return declared.get(0).group();
        Group gene = EntityGroups.of(entity);
        if (gene != Group.DEFAULT) return gene.name().toLowerCase(Locale.ROOT);
        String byMembers = DispositionRelations.groupOf(entity.getType());
        return byMembers != null ? byMembers : "default";
    }

    /**
     * The group an origin would resolve to before it is spawned: its {@code entity_group} gene first,
     * else the membership of the body entity type it spawns as ({@code bodyType}), else {@code default}.
     * Mirrors {@link #of(LivingEntity)} for a candidate origin during spawn selection.
     */
    public static String ofRoot(ResourceLocation rootId, EntityType<?> bodyType) {
        Group gene = RootRegistry.effectiveEntityGroup(rootId);
        if (gene != Group.DEFAULT) return gene.name().toLowerCase(Locale.ROOT);
        String byMembers = DispositionRelations.groupOf(bodyType);
        return byMembers != null ? byMembers : "default";
    }

    /**
     * Whether two groups clash: either regards the other as hostile. Symmetric so a one-sided
     * authored enmity still blocks a mixed settlement (the aggressor or the victim is enough).
     */
    public static boolean clash(String a, String b) {
        if (a == null || b == null || a.equals(b)) return false;
        DispositionRelations.GroupDef da = DispositionRelations.relations(a);
        DispositionRelations.GroupDef db = DispositionRelations.relations(b);
        if (da != null && da.hostile().contains(b)) return true;
        return db != null && db.hostile().contains(a);
    }
}
