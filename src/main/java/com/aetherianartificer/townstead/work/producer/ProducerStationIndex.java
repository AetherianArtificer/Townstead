package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.producer.ProducerRole;
import com.aetherianartificer.townstead.work.producer.ProducerWorkSupport;

import com.aetherianartificer.townstead.work.station.Stations;

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

    public static @Nullable Selection chooseBaristaSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil
    ) {
        return chooseBaristaSelection(level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, null);
    }

    public static @Nullable Selection chooseBaristaSelection(
            ServerLevel level,
            VillagerEntityMCA villager,
            WorkBuildingNav.Snapshot snapshot,
            Set<Long> worksiteBounds,
            Map<Long, Long> abandonedUntilByStation,
            long gameTime,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            @Nullable java.util.function.Predicate<StationSlot> stationFilter
    ) {
        return chooseForRole(ProducerRole.BARISTA, level, villager, snapshot, worksiteBounds,
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
        net.minecraft.resources.ResourceLocation[] taskTypes = role == ProducerRole.BARISTA
                ? new net.minecraft.resources.ResourceLocation[]{com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW}
                : new net.minecraft.resources.ResourceLocation[]{com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK,
                        com.aetherianartificer.townstead.profession.def.WorkTaskTypes.CHOP};
        return chooseForRole(role, level, villager, snapshot, worksiteBounds,
                abandonedUntilByStation, gameTime, recipeCooldownUntil, stationFilter, taskTypes);
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

            BlockPos stand = WorkBuildingNav.nearestStationStand(snapshot, villager, slot.pos());
            if (stand == null) stand = Stations.findStandingPosition(level, villager, slot.pos());
            if (stand == null) {
                logSkip(role, villager, slot, "no_stand");
                continue;
            }

            SessionSnapshot session = ProducerStationSessions.snapshot(level, slot.pos());
            ProducerStationState state = ProductionStations.classify(level, villager, slot.pos(), slot.type(), null, session);
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
                            ProducerWorkSupport.beveragesOnly(role),
                            taskTypes));
            List<ScoredRecipe> viable = new ArrayList<>();
            for (ScoredRecipe candidate : stationTypeCandidates) {
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
                logNoRecipe(role, level, villager, slot, worksiteBounds, recipeCooldownUntil,
                        stationTypeCandidates.size(), taskTypes);
                continue;
            }

            double bestScore = Double.NEGATIVE_INFINITY;
            for (ScoredRecipe viableRecipe : viable) {
                bestScore = Math.max(bestScore, viableRecipe.score());
            }
            List<ScoredRecipe> bestRecipes = new ArrayList<>();
            for (ScoredRecipe viableRecipe : viable) {
                if (viableRecipe.score() >= bestScore - 0.5d) {
                    bestRecipes.add(viableRecipe);
                }
            }
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
        List<Candidate> best = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (stateRank(candidate.state()) != stateRank(head.state())) continue;
            if (!(Double.compare(candidate.recipeScore(), head.recipeScore()) == 0
                    || Math.abs(candidate.recipeScore() - head.recipeScore()) <= 0.5d)) continue;
            if (candidate.usableCapacity() != head.usableCapacity()) continue;
            best.add(candidate);
        }
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
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            StationSlot slot,
            Set<Long> worksiteBounds,
            Map<net.minecraft.resources.ResourceLocation, Long> recipeCooldownUntil,
            int candidateCount,
            net.minecraft.resources.ResourceLocation... taskTypes
    ) {
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        // Name the first recipe that failed and why. "Nothing is makeable" is not an answer a
        // player can act on; "needs a bowl" is.
        String detail = "";
        for (ScoredRecipe candidate : RecipeSelector.candidateRecipes(
                level, villager, slot.type(), worksiteBounds, recipeCooldownUntil,
                ProducerWorkSupport.excludeBeverages(role, level, villager),
                ProducerWorkSupport.beveragesOnly(role), taskTypes)) {
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
                + " candidates=" + candidateCount + detail);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
