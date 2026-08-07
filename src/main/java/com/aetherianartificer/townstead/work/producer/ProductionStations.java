package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.StationContents;
import com.aetherianartificer.townstead.work.station.StationDropOutputs;
import com.aetherianartificer.townstead.work.station.StationCapacities;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/** Classifies station ownership/content state and coordinates foreign-content cleanup. */
public final class ProductionStations {
    private ProductionStations() {}

    public static ProducerStationState classify(
            ServerLevel level, VillagerEntityMCA villager, BlockPos pos,
            @Nullable StationType stationType, @Nullable DiscoveredRecipe expectedRecipe,
            @Nullable ProducerStationSessions.SessionSnapshot session
    ) {
        if (level == null || villager == null || pos == null || stationType == null) return ProducerStationState.BLOCKED;
        if (ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return ProducerStationState.BLOCKED;
        if (StationProtocols.handles(level, pos)) {
            return StationProtocols.classify(level, villager, pos, stationType, expectedRecipe, session);
        }

        boolean hasContents = StationProtocols.handles(level, pos)
                ? StationProtocols.hasAnyContents(level, pos) : StationContents.hasAny(level, pos);
        boolean ownsSession = session != null && session.isOwner(villager.getUUID());
        if (stationType == StationType.HOT_STATION && expectedRecipe != null) {
            Item output = BuiltInRegistries.ITEM.get(expectedRecipe.output());
            if (output != Items.AIR
                    && StationContents.count(level, pos, output) >= expectedRecipe.outputCount()
                    && StationContents.canExtract(level, pos, output, expectedRecipe.outputCount())) {
                return ProducerStationState.FINISHED_OUTPUT;
            }
        }
        Set<ResourceLocation> allOutputs = WorkRecipeRegistry.allOutputIds(level);
        if (stationType == StationType.HOT_STATION
                && StationContents.hasOutput(level, pos, allOutputs)) {
            return ProducerStationState.FINISHED_OUTPUT;
        }
        if (stationType == StationType.FIRE_STATION && expectedRecipe != null
                && StationDropOutputs.has(level, pos, Set.of(expectedRecipe.output()))) {
            return ProducerStationState.FINISHED_OUTPUT;
        }
        if (stationType == StationType.FIRE_STATION
                && StationDropOutputs.has(level, pos, allOutputs)) {
            return ProducerStationState.FINISHED_OUTPUT;
        }
        if (stationType == StationType.FIRE_STATION && StationCapacities.capacity(level, pos, stationType) > 0) {
            return ownsSession ? ProducerStationState.OWNED_STAGED : ProducerStationState.EMPTY_READY;
        }
        if (!hasContents) return ProducerStationState.EMPTY_READY;
        if (ownsSession) return ProducerStationState.OWNED_STAGED;
        if (expectedRecipe != null && stationType == StationType.HOT_STATION
                && StationContents.matchesRecipe(level, pos, expectedRecipe)) {
            return ProducerStationState.COMPATIBLE_PARTIAL;
        }
        return ProducerStationState.FOREIGN_CONTENTS;
    }

    public static boolean cleanup(
            ServerLevel level, VillagerEntityMCA villager, BlockPos pos,
            @Nullable StationType stationType, Set<Long> storageBounds
    ) {
        if (level == null || villager == null || pos == null || stationType == null) return false;
        boolean movedAny = false;
        List<ItemStack> drops = StationDropOutputs.collectWithinWorksite(
                level, pos, WorkRecipeRegistry.allOutputIds(level), storageBounds);
        for (ItemStack drop : drops) {
            WorkIngredients.storeOutputInWorksiteStorage(level, villager, drop, pos, storageBounds);
            if (!drop.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(drop);
                if (!remainder.isEmpty()) villager.spawnAtLocation(remainder);
            }
            movedAny = true;
        }
        if (stationType == StationType.HOT_STATION) {
            movedAny |= StationCleanup.clear(level, villager, pos, storageBounds);
        }
        boolean hasContents = StationProtocols.handles(level, pos)
                ? StationProtocols.hasAnyContents(level, pos) : StationContents.hasAny(level, pos);
        return movedAny || !hasContents;
    }
}
