package com.aetherianartificer.townstead.root.reproduction;

import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootSpawnHandler;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;

import java.util.List;

/**
 * Non-overworlder reproduction: a human baby item makes no sense for a spider, so any
 * species other than the overworlder bears its young directly into the world at the
 * mother (like the gestation path) instead of handing the player MCA's carry-able baby
 * item. Overworlders keep the vanilla baby item.
 *
 * <p>Each child is built through MCA's {@code Pregnancy.createChild}, so it gets the
 * usual genetics/traits/family wiring plus Townstead's diploid inheritance (via
 * {@code PregnancyInheritanceMixin}). When the co-parent is a player (the common
 * "try for a baby" case) {@code createChild} can only take a villager partner, so the
 * child is bred from the mother and then has its heritage/genotype/root and parentage
 * re-resolved from both true parents. The whole clutch is spawned here, so
 * {@code LitterGestationMixin} stands down while {@link #spawning()} is true (it would
 * otherwise double the litter).</p>
 */
public final class DirectBirth {

    private static final ThreadLocal<Boolean> SPAWNING = ThreadLocal.withInitial(() -> false);

    private DirectBirth() {}

    /** Whether {@code mother}'s species bears young directly rather than via a baby item. */
    public static boolean bypassesBabyItem(VillagerEntityMCA mother) {
        if (mother == null) return false;
        ResourceLocation species = RootRegistry.effectiveSpecies(
                ResourceLocation.tryParse(TownsteadVillagers.get(mother).life().rootId()));
        ResourceLocation overworlder = RootRegistry.effectiveSpecies(RootRegistry.DEFAULT_ID);
        return species != null && overworlder != null && !species.equals(overworlder);
    }

    /** True while {@link #spawnOffspring} is running, so the litter mixin doesn't also fire. */
    public static boolean spawning() {
        return SPAWNING.get();
    }

    /**
     * Scatter a newborn around the mother (a ring of 0.5-2.0 blocks) so a clutch doesn't pile up on one
     * tile. Shared with the gestation litter path.
     */
    public static void scatterAround(VillagerEntityMCA child, VillagerEntityMCA mother, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = 0.5 + random.nextDouble() * 1.5;
        child.setPos(mother.getX() + Math.cos(angle) * radius, mother.getY(), mother.getZ() + Math.sin(angle) * radius);
    }

    /** Spawn the mother's whole clutch at her position, inheriting from both parents. */
    public static List<VillagerEntityMCA> spawnOffspring(VillagerEntityMCA mother, Entity spouse) {
        if (!(mother.level() instanceof ServerLevel level)) return List.of();
        int litter = LitterSize.roll(mother, mother.getRandom());
        boolean playerCoParent = !(spouse instanceof VillagerEntityMCA);
        VillagerEntityMCA geneticPartner = spouse instanceof VillagerEntityMCA v ? v : mother;
        RandomSource random = mother.getRandom();
        List<VillagerEntityMCA> born = new java.util.ArrayList<>(litter);

        SPAWNING.set(true);
        try {
            for (int i = 0; i < litter; i++) {
                Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
                VillagerEntityMCA child = mother.getRelationships().getPregnancy().createChild(gender, geneticPartner);
                if (child == null) continue;

                // createChild + PregnancyInheritanceMixin bred the child from (mother, geneticPartner). With a
                // player co-parent that partner was the mother (self), so record the real parents instead.
                if (playerCoParent) {
                    var family = child.getRelationships().getFamilyEntry();
                    family.removeMother();
                    family.removeFather();
                    FamilyTree tree = FamilyTree.get(level);
                    family.assignParent(tree.getOrCreate(mother));
                    family.assignParent(tree.getOrCreate(spouse));
                    Heredity.inheritFromEntities(TownsteadVillagers.get(child).life(), List.of(mother, spouse), random);
                    RootSpawnHandler.backfillIfMissing(child);
                }

                scatterAround(child, mother, random);
                WorldUtils.spawnEntity(level, child, MobSpawnType.BREEDING);
                com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.birth(child);
                born.add(child);
            }
        } finally {
            SPAWNING.set(false);
        }
        return born;
    }
}
