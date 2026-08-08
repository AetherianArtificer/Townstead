package com.aetherianartificer.townstead.root.reproduction;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.gene.types.FertilityGeneType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Server-side enforcement of the {@code townstead_roots:fertility} gene. An entity is sterile when
 * any expressed fertility gene reports {@code fertility <= 0}; the breeding mixins block MCA's
 * procreation and gestation when either prospective parent is sterile. Entities with no fertility
 * gene (most villagers, players) are fertile by default.
 */
public final class Fertility {

    private Fertility() {}

    /** Whether this entity can reproduce at all (no sterile fertility gene expressed). */
    public static boolean isFertile(LivingEntity entity) {
        if (entity == null) return true;
        List<FertilityGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, FertilityGeneType.Instance.class);
        for (FertilityGeneType.Instance gene : genes) {
            if (!gene.fertile()) return false;
        }
        return true;
    }

    /** Whether two prospective parents can produce offspring (both fertile). */
    public static boolean canBreed(LivingEntity a, LivingEntity b) {
        return isFertile(a) && isFertile(b);
    }

    /**
     * Mirror the genetic fertility state onto MCA's own {@code infertile} trait, so MCA's gestation
     * gate (and other mods reading traits) agree with the {@code townstead_roots:fertility} gene.
     * The trait is version-specific: MCA registered it recently, and older builds lack it entirely.
     * We resolve it by id rather than the {@code Traits.INFERTILE} field so this compiles and runs on
     * both; when the trait is absent this is a no-op and only the breeding mixins enforce sterility.
     */
    public static void syncMcaInfertileTrait(VillagerEntityMCA villager) {
        if (villager == null) return;
        Traits.Trait infertile =
                com.aetherianartificer.townstead.compat.mca.McaTraitCompat.resolve("INFERTILE").orElse(null);
        if (infertile == null) return; // absent on this MCA version
        if (isFertile(villager)) {
            villager.getTraits().removeTrait(infertile);
        } else {
            villager.getTraits().addTrait(infertile);
        }
    }
}
