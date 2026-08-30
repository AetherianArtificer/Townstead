package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.producer.ProducerRole;
import com.aetherianartificer.townstead.work.producer.ProducerWorkSupport;

import com.aetherianartificer.townstead.work.station.Stations;
import com.aetherianartificer.townstead.work.station.StationProtocols;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.WorkBuildingNav;
import com.aetherianartificer.townstead.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.work.producer.ProducerStationSessions.SessionSnapshot;
import com.aetherianartificer.townstead.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.storage.WorksiteStorageIndex;
import com.aetherianartificer.townstead.work.recipe.RecipeSelector;
import com.aetherianartificer.townstead.work.recipe.RecipeSelector.ScoredRecipe;
import com.aetherianartificer.townstead.work.station.Stations.StationSlot;
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
            int stationPreference,
            int orderRank,
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
        return chooseForRole(
                ProducerRole.COOK,
                level,
                villager,
                snapshot,
                worksiteBounds,
                abandonedUntilByStation,
                gameTime,
                recipeCooldownUntil,
                null);
    }

    public static @Nullable Selection chooseCookSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter
    ) {
        return chooseForRole(ProducerRole.COOK, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter);
    }

    public static @Nullable Selection chooseBeverageArtisanSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil
    ) {
        return chooseBeverageArtisanSelection(level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, null);
    }

    public static @Nullable Selection chooseBeverageArtisanSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter
    ) {
        return chooseForRole(ProducerRole.BEVERAGE_ARTISAN, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter);
    }

    public static @Nullable Selection chooseForRole(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter
    ) {
        net.minecraft.resources.ResourceLocation[] taskTypes = role == ProducerRole.BEVERAGE_ARTISAN
                ? new net.minecraft.resources.ResourceLocation[]{com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW}
                : new net.minecraft.resources.ResourceLocation[]{com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK,
                        com.aetherianartificer.townstead.profession.def.WorkTaskTypes.CHOP};
        return chooseForRole(role, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter,
                slot -> 0, output -> 0, taskTypes);
    }

    public static @Nullable Selection chooseForRole(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter,
            net.minecraft.resources.ResourceLocation... taskTypes
    ) {
        return chooseForRole(role, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter,
                slot -> 0, output -> 0, taskTypes);
    }

    public static @Nullable Selection chooseForRole(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter,
            java.util.function.ToIntFunction<DiscoveredRecipe> recipePriority,
            net.minecraft.resources.ResourceLocation... taskTypes
    ) {
        return chooseForRole(role, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter,
                slot -> 0, recipePriority, taskTypes);
    }

    public static @Nullable Selection chooseForRole(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter,
            java.util.function.ToIntFunction<StationSlot> stationPreference,
            java.util.function.ToIntFunction<DiscoveredRecipe> recipePriority,
            net.minecraft.resources.ResourceLocation... taskTypes
    ) {
        if (level == null || villager == null || snapshot == null || snapshot.stations().isEmpty()) return null;

        Map<StationType, List<ScoredRecipe>> candidateRecipesByType = new java.util.EnumMap<>(StationType.class);
        Map<net.minecraft.resources.ResourceLocation, Boolean> toolAvailableByRecipe = new java.util.HashMap<>();
        WorksiteStorageIndex.Snapshot kitchenSnapshot = WorksiteStorageIndex.snapshot(level, villager, worksiteBounds);
        List<Candidate> candidates = new ArrayList<>();
        for (StationSlot slot : snapshot.stations()) {
            if (stationFilter != null && !stationFilter.test(slot)) continue;
            if (abandonedUntilByStation != null && abandonedUntilByStation.getOrDefault(slot.pos().asLong(), 0L) > gameTime) {
                logSkip(role, villager, slot, "recently_abandoned");
                continue;
            }
            if (ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), slot.pos())) {
                logSkip(role, villager, slot, "claimed");
                continue;
            }

            // Ranking is deliberately geometry-only. Pathfinding every station before knowing
            // whether its recipes, claim, state, order, or capacity can win made acquisition scale
            // as stations x path probes. Reachability is resolved lazily for the ranked winners.
            BlockPos stand = WorkBuildingNav.nearestStationStand(snapshot, villager, slot.pos());
            if (stand == null) {
                logSkip(role, villager, slot, "no_stand");
                continue;
            }

            SessionSnapshot session = ProducerStationSessions.snapshot(level, slot.pos());
            ProducerStationState state = ProductionStations.classify(level, villager, slot.pos(), slot.type(), null, session);
            int usableCapacity = stationUsableCapacity(level, slot);
            double distanceSq = villager.distanceToSqr(slot.pos().getX() + 0.5, slot.pos().getY() + 0.5, slot.pos().getZ() + 0.5);

            // Output collection and an already-owned staged cycle do not need a speculative
            // recipe at all. Building the complete scored recipe graph here used to make even
            // "take the finished meal out" pay the most expensive part of station acquisition.
            if (state == ProducerStationState.FINISHED_OUTPUT || state == ProducerStationState.OWNED_STAGED) {
                candidates.add(new Candidate(slot, stand, state, usableCapacity, distanceSq,
                        null, stationPreference.applyAsInt(slot), 0, Double.POSITIVE_INFINITY));
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
                            ProducerWorkSupport.restrictToBeverages(role),
                            taskTypes));

            if (state == ProducerStationState.BLOCKED) {
                ScoredRecipe resumable = bestResumableRecipe(
                        level, slot, stationTypeCandidates, recipePriority);
                if (resumable != null) {
                    int orderRank = recipePriority.applyAsInt(resumable.recipe());
                    candidates.add(new Candidate(slot, stand, ProducerStationState.COMPATIBLE_PARTIAL,
                            usableCapacity, distanceSq, resumable.recipe(),
                            stationPreference.applyAsInt(slot), orderRank, resumable.score()));
                    continue;
                }
                logSkip(role, villager, slot, "blocked");
                continue;
            }

            List<ScoredRecipe> viable = new ArrayList<>();
            for (ScoredRecipe candidate : stationTypeCandidates) {
                if (recipePriority.applyAsInt(candidate.recipe()) == Integer.MAX_VALUE) continue;
                if (!ProductionStations.supportsRecipe(level, slot.pos(), candidate.recipe())) continue;
                if (!WorkIngredients.canFulfill(
                        level,
                        villager,
                        candidate.recipe(),
                        slot.pos(),
                        worksiteBounds,
                        kitchenSnapshot,
                        toolAvailableByRecipe)) continue;
                viable.add(candidate);
            }
            if (viable.isEmpty()) {
                logNoRecipe(level, villager, slot, worksiteBounds, stationTypeCandidates,
                        recipePriority);
                continue;
            }

            int bestOrderRank = Integer.MAX_VALUE;
            for (ScoredRecipe viableRecipe : viable) {
                bestOrderRank = Math.min(bestOrderRank,
                        recipePriority.applyAsInt(viableRecipe.recipe()));
            }
            double bestScore = Double.NEGATIVE_INFINITY;
            for (ScoredRecipe viableRecipe : viable) {
                if (recipePriority.applyAsInt(viableRecipe.recipe()) != bestOrderRank) continue;
                bestScore = Math.max(bestScore, viableRecipe.score());
            }
            List<ScoredRecipe> bestRecipes = new ArrayList<>();
            for (ScoredRecipe viableRecipe : viable) {
                if (recipePriority.applyAsInt(viableRecipe.recipe()) != bestOrderRank) continue;
                if (viableRecipe.score() >= bestScore - 0.5d) {
                    bestRecipes.add(viableRecipe);
                }
            }
            ScoredRecipe chosenRecipe = bestRecipes.get(ThreadLocalRandom.current().nextInt(bestRecipes.size()));
            candidates.add(new Candidate(slot, stand, state, usableCapacity, distanceSq,
                    chosenRecipe.recipe(), stationPreference.applyAsInt(slot),
                    bestOrderRank, chosenRecipe.score()));
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator
                .comparingInt(Candidate::stationPreference)
                .thenComparingInt(c -> stateRank(c.state()))
                .thenComparingInt(Candidate::orderRank)
                .thenComparing(Comparator.comparingDouble((Candidate c) -> c.recipeScore()).reversed())
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.usableCapacity()).reversed())
                .thenComparingDouble(Candidate::distanceSq));

        List<Candidate> remaining = new ArrayList<>(candidates);
        while (!remaining.isEmpty()) {
            Candidate head = remaining.get(0);
            List<Candidate> best = new ArrayList<>();
            for (Candidate candidate : remaining) {
                if (stateRank(candidate.state()) != stateRank(head.state())) continue;
                if (candidate.stationPreference() != head.stationPreference()) continue;
                if (candidate.orderRank() != head.orderRank()) continue;
                if (!(Double.compare(candidate.recipeScore(), head.recipeScore()) == 0
                        || Math.abs(candidate.recipeScore() - head.recipeScore()) <= 0.5d)) continue;
                if (candidate.usableCapacity() != head.usableCapacity()) continue;
                best.add(candidate);
            }

            // Keep the old random choice among equivalent winners, but fall through when that
            // whole preference group is unreachable instead of pathfinding every station up front.
            int first = ThreadLocalRandom.current().nextInt(best.size());
            for (int offset = 0; offset < best.size(); offset++) {
                Candidate choice = best.get((first + offset) % best.size());
                BlockPos reachableStand = WorkBuildingNav.nearestReachableStationStand(
                        level, snapshot, villager, choice.station().pos());
                if (reachableStand != null) {
                    return new Selection(choice.station(), reachableStand, choice.state(),
                            choice.usableCapacity(), choice.recipe());
                }
            }
            remaining.removeAll(best);
        }
        return null;
    }

    /**
     * Reconstruct a lost cycle from public station inventory plus the recipe types owned by the
     * block. Order priority disambiguates compatible recipes before the normal recipe score.
     */
    private static @Nullable ScoredRecipe bestResumableRecipe(
            ServerLevel level, StationSlot slot, List<ScoredRecipe> candidates,
            java.util.function.ToIntFunction<DiscoveredRecipe> recipePriority) {
        ScoredRecipe best = null;
        int bestOrder = Integer.MAX_VALUE;
        for (ScoredRecipe candidate : candidates) {
            int order = recipePriority.applyAsInt(candidate.recipe());
            if (!ProductionStations.supportsRecipe(level, slot.pos(), candidate.recipe())) continue;
            if (!StationProtocols.matchesPendingInputs(level, slot.pos(), candidate.recipe())) continue;
            if (best == null || order < bestOrder
                    || (order == bestOrder && candidate.score() > best.score())) {
                best = candidate;
                bestOrder = order;
            }
        }
        return best;
    }

    private static int stationUsableCapacity(ServerLevel level, StationSlot slot) {
        return Math.max(0, slot.capacity());
    }

    private static int stateRank(ProducerStationState state) {
        return switch (state) {
            case FINISHED_OUTPUT -> 0;
            case OWNED_STAGED -> 1;
            case COMPATIBLE_PARTIAL -> 2;
            case EMPTY_READY -> 3;
            case FOREIGN_CONTENTS -> 4;
            case BLOCKED -> 5;
        };
    }

    /**
     * Why one station was passed over. These were empty stubs, so the loop that decides whether a
     * worker has anything to do threw away every reason it had. That is the difference between a
     * cook who cannot explain itself and one who can.
     */
    private static void logSkip(ProducerRole role, VillagerEntityMCA villager, StationSlot slot, String reason) {
        say(villager, "SKIP:" + slot.blockId().getPath() + " @ "
                + slot.pos().getX() + "," + slot.pos().getY() + "," + slot.pos().getZ()
                + " (" + reason + ")");
    }

    /** Narrates to the nearest player when villager-AI debugging is on. */
    private static void say(VillagerEntityMCA villager, String message) {
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!(level.getNearestPlayer(villager, 24)
                instanceof net.minecraft.server.level.ServerPlayer player)) return;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[Station:" + villager.getName().getString() + "] " + message));
    }

    private static void logNoRecipe(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationSlot slot,
            Set<Long> worksiteBounds,
            List<ScoredRecipe> candidates,
            java.util.function.ToIntFunction<DiscoveredRecipe> recipePriority
    ) {
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        // Explain the highest-priority ordered recipe, not an arbitrary high-scoring recipe from
        // the station's entire book. "Sticky rice needs..." does not help someone who ordered
        // candied potatoes.
        String detail = "";
        int bestOrder = Integer.MAX_VALUE;
        for (ScoredRecipe candidate : candidates) {
            bestOrder = Math.min(bestOrder, recipePriority.applyAsInt(candidate.recipe()));
        }
        for (ScoredRecipe candidate : candidates) {
            if (recipePriority.applyAsInt(candidate.recipe()) != bestOrder) continue;
            if (!ProductionStations.supportsRecipe(level, slot.pos(), candidate.recipe())) {
                detail = " first-rejected=" + candidate.recipe().output() + " -> station does not support recipe";
                break;
            }
            detail = " first-blocked=" + candidate.recipe().output() + " -> "
                    + WorkIngredients.describeMissingRequirements(
                            level, villager, candidate.recipe(), slot.pos(), worksiteBounds);
            break;
        }
        say(villager, "NO-RECIPE:" + slot.blockId().getPath() + " @ "
                + slot.pos().getX() + "," + slot.pos().getY() + "," + slot.pos().getZ()
                + " candidates=" + candidates.size() + detail);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
