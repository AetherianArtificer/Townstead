package com.aetherianartificer.townstead.root.appearance;

import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.root.RootGenes;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** Applies natural genetic ranges and arbitrary persisted fantasy palettes to MCA hair. */
public final class HairColors {

    private HairColors() {}

    public static void roll(VillagerEntityMCA villager, HairSettings settings, RandomSource random) {
        if (villager == null || settings == null) return;
        if (!settings.colorRanges().isEmpty()) {
            HairColorRange range = weightedRange(settings.colorRanges(), random);
            villager.getGenetics().setGene(Genetics.EUMELANIN, range.sampleDarkness(random));
            villager.getGenetics().setGene(Genetics.PHEOMELANIN, range.sampleRedness(random));
        }
        if (hasFantasy(settings)) {
            villager.setHairDye(rollFantasy(settings, random));
        } else if (!settings.colorRanges().isEmpty()) {
            villager.clearHairDye();
        }
    }

    public static void clamp(VillagerEntityMCA villager, HairSettings settings) {
        if (villager == null || settings == null) return;
        if (!settings.colorRanges().isEmpty()) {
            Genetics genetics = villager.getGenetics();
            float darkness = genetics.getGene(Genetics.EUMELANIN);
            float redness = genetics.getGene(Genetics.PHEOMELANIN);
            HairColorRange nearest = nearestRange(settings.colorRanges(), darkness, redness);
            genetics.setGene(Genetics.EUMELANIN, nearest.clampDarkness(darkness));
            genetics.setGene(Genetics.PHEOMELANIN, nearest.clampRedness(redness));
        }
        if (hasFantasy(settings)) {
            int current = villager.getHairDye();
            villager.setHairDye(current == 0xFF000000
                    ? primaryFantasy(settings) : nearestFantasy(settings, current));
        } else if (!settings.colorRanges().isEmpty()) {
            villager.clearHairDye();
        }
    }

    /** Inherit from available parent dyes; population-roll when neither parent has a fantasy color. */
    public static void inherit(VillagerEntityMCA villager, List<? extends Entity> parents) {
        if (villager == null) return;
        var life = TownsteadVillagers.get(villager).life();
        ResourceLocation rootId = ResourceLocation.tryParse(life.rootId());
        HairSettings settings = HairResolver.resolve(rootId, life.hasHeritage() ? life.heritage() : null);
        if (hasFantasy(settings)) {
            List<Integer> parentColors = parents == null ? List.of() : parents.stream()
                    .map(HairColors::parentHairColor)
                    .filter(color -> color != 0xFF000000)
                    .toList();
            int inherited;
            if (parentColors.isEmpty()) {
                inherited = rollFantasy(settings, villager.getRandom());
            } else if (settings.gradients().isEmpty() || parentColors.size() == 1) {
                inherited = parentColors.get(villager.getRandom().nextInt(parentColors.size()));
            } else {
                int first = parentColors.get(0);
                int second = parentColors.get(1);
                float blend = 0.25f + villager.getRandom().nextFloat() * 0.5f;
                inherited = 0xFF000000 | HairGradient.interpolate(
                        first, second, blend, HairGradient.Space.RGB);
            }
            villager.setHairDye(nearestFantasy(settings, inherited));
        }
        clamp(villager, settings);
    }

    public static void inherit(VillagerEntityMCA villager, Entity first, Entity second) {
        inherit(villager, List.of(first, second));
    }

    public static void clampForIdentity(VillagerEntityMCA villager) {
        var life = TownsteadVillagers.get(villager).life();
        ResourceLocation rootId = ResourceLocation.tryParse(life.rootId());
        Heritage heritage = life.hasHeritage() ? life.heritage() : null;
        clamp(villager, HairResolver.resolve(rootId, heritage));
    }

    /** Server-authoritative clamp for an editor snapshot before it is stored. */
    public static float[] clampSnapshot(float[] snapshot, HairSettings settings) {
        if (snapshot == null || settings == null || settings.colorRanges().isEmpty()) return snapshot;
        float darkness = RootGenes.readSnapshot(snapshot, Genetics.EUMELANIN);
        float redness = RootGenes.readSnapshot(snapshot, Genetics.PHEOMELANIN);
        HairColorRange nearest = nearestRange(settings.colorRanges(), darkness, redness);
        RootGenes.writeSnapshot(snapshot, Genetics.EUMELANIN, nearest.clampDarkness(darkness));
        RootGenes.writeSnapshot(snapshot, Genetics.PHEOMELANIN, nearest.clampRedness(redness));
        return snapshot;
    }

    /** Validate a persisted MCA HairColor against the resolved exact palette. */
    public static int clampDye(int hairColor, HairSettings settings) {
        if (settings == null) return hairColor;
        if (hasFantasy(settings)) {
            return hairColor == 0xFF000000
                    ? primaryFantasy(settings) : nearestFantasy(settings, hairColor);
        }
        // A genetic-only constraint must render the genetics rather than an old custom dye.
        if (!settings.colorRanges().isEmpty()) return 0xFF000000;
        return hairColor;
    }

    private static HairColorRange nearestRange(List<HairColorRange> ranges, float darkness, float redness) {
        HairColorRange best = ranges.get(0);
        float distance = best.distanceSquared(darkness, redness);
        for (int i = 1; i < ranges.size(); i++) {
            float candidate = ranges.get(i).distanceSquared(darkness, redness);
            if (candidate < distance) { best = ranges.get(i); distance = candidate; }
        }
        return best;
    }

    private static HairColorRange weightedRange(List<HairColorRange> ranges, RandomSource random) {
        int total = 0;
        for (HairColorRange range : ranges) total += range.weight();
        if (total <= 0) return ranges.get(random.nextInt(ranges.size()));
        int roll = random.nextInt(total);
        for (HairColorRange range : ranges) {
            roll -= range.weight();
            if (roll < 0) return range;
        }
        return ranges.get(ranges.size() - 1);
    }

    private static boolean hasFantasy(HairSettings settings) {
        return !settings.colors().isEmpty() || !settings.gradients().isEmpty();
    }

    private static int rollFantasy(HairSettings settings, RandomSource random) {
        int total = 0;
        for (HairColorChoice color : settings.colors()) total += color.weight();
        for (HairGradient gradient : settings.gradients()) total += gradient.weight();
        if (total <= 0) {
            int count = settings.colors().size() + settings.gradients().size();
            int selected = random.nextInt(count);
            return selected < settings.colors().size()
                    ? settings.colors().get(selected).argb()
                    : settings.gradients().get(selected - settings.colors().size()).sample(random);
        }
        int roll = random.nextInt(total);
        for (HairColorChoice color : settings.colors()) {
            roll -= color.weight();
            if (roll < 0) return color.argb();
        }
        for (HairGradient gradient : settings.gradients()) {
            roll -= gradient.weight();
            if (roll < 0) return gradient.sample(random);
        }
        return settings.gradients().isEmpty()
                ? settings.colors().get(settings.colors().size() - 1).argb()
                : settings.gradients().get(settings.gradients().size() - 1).sample(random);
    }

    private static int primaryFantasy(HairSettings settings) {
        int bestColor = settings.colors().isEmpty() ? 0 : settings.colors().get(0).argb();
        int bestWeight = settings.colors().isEmpty() ? -1 : settings.colors().get(0).weight();
        for (HairColorChoice color : settings.colors()) {
            if (color.weight() > bestWeight) { bestColor = color.argb(); bestWeight = color.weight(); }
        }
        for (HairGradient gradient : settings.gradients()) {
            if (gradient.weight() > bestWeight) {
                bestColor = gradient.colorAt(0.5f);
                bestWeight = gradient.weight();
            }
        }
        return bestColor;
    }

    private static int nearestFantasy(HairSettings settings, int argb) {
        int best = 0;
        long distance = Long.MAX_VALUE;
        for (HairColorChoice color : settings.colors()) {
            long candidate = rgbDistance(color.rgb(), argb);
            if (candidate < distance) { best = color.argb(); distance = candidate; }
        }
        for (HairGradient gradient : settings.gradients()) {
            int candidateColor = gradient.nearest(argb);
            long candidate = rgbDistance(candidateColor, argb);
            if (candidate < distance) { best = candidateColor; distance = candidate; }
        }
        return best;
    }

    private static int parentHairColor(Entity entity) {
        if (entity instanceof VillagerEntityMCA villager) return villager.getHairDye();
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            var tag = net.conczin.mca.server.world.data.PlayerSaveData.get(player).getEntityData();
            return tag.contains("HairColor") ? tag.getInt("HairColor") : 0xFF000000;
        }
        return 0xFF000000;
    }

    private static long rgbDistance(int rgb, int argb) {
        int dr = ((rgb >> 16) & 255) - ((argb >> 16) & 255);
        int dg = ((rgb >> 8) & 255) - ((argb >> 8) & 255);
        int db = (rgb & 255) - (argb & 255);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }
}
