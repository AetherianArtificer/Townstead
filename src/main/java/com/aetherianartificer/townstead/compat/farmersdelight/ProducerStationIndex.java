package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.ai.work.WorkBuildingNav;
import com.aetherianartificer.townstead.compat.farmersdelight.ProducerStationSessions.SessionSnapshot;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.RecipeSelector;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.RecipeSelector.ScoredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ProducerStationIndex {
    public record Selection(
            StationSlot station,
            BlockPos standPos,
            ProducerStationState state,
            int usableCapacity,
            @Nullable DiscoveredRecipe recipe
    ) {}

    private record Candidate(
            StationSlot station,
            BlockPos standPos,
            ProducerStationState state,
            int usableCapacity,
            double distanceSq,
            @Nullable DiscoveredRecipe recipe,
            double recipeScore
    ) {}

    private ProducerStationIndex() {}

    public static @Nullable Selection chooseCookSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil
    ) {
        return chooseSelection(
                ProducerRole.COOK,
                level,
                villager,
                snapshot,
                worksiteBounds,
                abandonedUntilByStation,
                gameTime,
                recipeCooldownUntil);
    }

    public static @Nullable Selection chooseBaristaSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil
    ) {
        return chooseSelection(ProducerRole.BARISTA, level, villager, snapshot, worksiteBounds, abandonedUntilByStation, gameTime, recipeCooldownUntil);
    }

    private static @Nullable Selection chooseSelection(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil
    ) {
        if (level == null || villager == null || snapshot == null || snapshot.stations().isEmpty()) return null;

        Map<StationType, List<ScoredRecipe>> candidateRecipesByType = new java.util.EnumMap<>(StationType.class);
        List<Candidate> candidates = new ArrayList<>();
        for (StationSlot slot : snapshot.stations()) {
            if (abandonedUntilByStation != null && abandonedUntilByStation.getOrDefault(slot.pos().asLong(), 0L) > gameTime) {
                logSkip(role, villager, slot, "recently_abandoned");
                continue;
            }
            if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), slot.pos())) {
                logSkip(role, villager, slot, "claimed");
                continue;
            }

            BlockPos stand = WorkBuildingNav.nearestStationStand(snapshot, villager, slot.pos());
            if (stand == null) stand = StationHandler.findStandingPosition(level, villager, slot.pos());
            if (stand == null) {
                logSkip(role, villager, slot, "no_stand");
                continue;
            }

            SessionSnapshot session = ProducerStationSessions.snapshot(level, slot.pos());
            ProducerStationState state = StationHandler.classifyProducerStation(level, villager, slot.pos(), slot.type(), null, session);
            int usableCapacity = stationUsableCapacity(level, slot);
            double distanceSq = villager.distanceToSqr(slot.pos().getX() + 0.5, slot.pos().getY() + 0.5, slot.pos().getZ() + 0.5);

            if (state == ProducerStationState.BLOCKED) {
                logSkip(role, villager, slot, "blocked");
                continue;
            }

            if (state == ProducerStationState.FINISHED_OUTPUT || state == ProducerStationState.OWNED_STAGED) {
                candidates.add(new Candidate(slot, stand, state, usableCapacity, distanceSq, null, Double.POSITIVE_INFINITY));
                continue;
            }

            List<ScoredRecipe> stationTypeCandidates = candidateRecipesByType.computeIfAbsent(slot.type(), type ->
                    RecipeSelector.candidateRecipes(
                            level,
                            villager,
                            type,
                            worksiteBounds,
                            recipeCooldownUntil,
                            ProducerWorkSupport.excludeBeverages(role, level, villager),
                            ProducerWorkSupport.beveragesOnly(role)));
            List<ScoredRecipe> viable = stationTypeCandidates.stream()
                    .filter(candidate -> StationHandler.stationSupportsRecipe(level, slot.pos(), candidate.recipe()))
                    .filter(candidate -> IngredientResolver.canFulfill(level, villager, candidate.recipe(), slot.pos(), worksiteBounds))
                    .toList();
            if (viable.isEmpty()) {
                logNoRecipe(role, level, villager, slot, worksiteBounds, recipeCooldownUntil, stationTypeCandidates.size());
                continue;
            }

            double bestScore = viable.stream().mapToDouble(ScoredRecipe::score).max().orElse(Double.NEGATIVE_INFINITY);
            List<ScoredRecipe> bestRecipes = viable.stream()
                    .filter(r -> r.score() >= bestScore - 0.5d)
                    .toList();
            ScoredRecipe chosenRecipe = bestRecipes.get(ThreadLocalRandom.current().nextInt(bestRecipes.size()));
            candidates.add(new Candidate(slot, stand, state, usableCapacity, distanceSq, chosenRecipe.recipe(), chosenRecipe.score()));
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator
                .comparingInt((Candidate c) -> stateRank(c.state()))
                .thenComparing(Comparator.comparingDouble((Candidate c) -> c.recipeScore()).reversed())
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.usableCapacity()).reversed())
                .thenComparingDouble(Candidate::distanceSq));

        Candidate head = candidates.get(0);
        List<Candidate> best = candidates.stream()
                .filter(c -> stateRank(c.state()) == stateRank(head.state()))
                .filter(c -> Double.compare(c.recipeScore(), head.recipeScore()) == 0 || Math.abs(c.recipeScore() - head.recipeScore()) <= 0.5d)
                .filter(c -> c.usableCapacity() == head.usableCapacity())
                .toList();
        Candidate choice = best.get(ThreadLocalRandom.current().nextInt(best.size()));
        return new Selection(choice.station(), choice.standPos(), choice.state(), choice.usableCapacity(), choice.recipe());
    }

    private static int stationUsableCapacity(ServerLevel level, StationSlot slot) {
        return Math.max(0, slot.capacity());
    }

    private static int stateRank(ProducerStationState state) {
        return switch (state) {
            case FINISHED_OUTPUT -> 0;
            case OWNED_STAGED -> 1;
            case EMPTY_READY -> 2;
            case COMPATIBLE_PARTIAL -> 3;
            case FOREIGN_CONTENTS -> 4;
            case BLOCKED -> 5;
        };
    }

    private static void logSkip(ProducerRole role, VillagerEntityMCA villager, StationSlot slot, String reason) {
    }

    private static void logNoRecipe(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            StationSlot slot,
            Set<Long> worksiteBounds,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            int candidateCount
    ) {
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
