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
import com.aetherianartificer.townstead.work.station.Stations;
import com.aetherianartificer.townstead.work.station.Workstations;
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

    /**
     * Whether this physical station can perform {@code recipe}, across both station engines.
     *
     * <p>{@link StationProtocols#supports} deliberately answers only for adapter-backed stations.
     * Farmer's Delight's cooking pot is not one: it is the built-in {@link StationType#HOT_STATION}
     * lifecycle driven through {@link StationContents}. Routing every station through the protocol
     * method therefore rejects every cooking-pot recipe before ingredients are considered.</p>
     */
    public static boolean supportsRecipe(
            ServerLevel level, BlockPos pos, @Nullable DiscoveredRecipe recipe
    ) {
        if (recipe == null) return true;
        if (level == null || pos == null) return false;

        StationType stationType = Stations.stationType(level, pos);
        if (stationType == null) return false;

        // Purification is a special cycle, not an ordinary campfire recipe. Its adapter exposes
        // that capability separately because the synthetic impure-water input has no vanilla
        // campfire recipe for the normal supports() check to match.
        if (recipe.purification()) {
            return recipe.stationType() == StationType.FIRE_STATION
                    && StationProtocols.handles(level, pos)
                    && StationProtocols.supportsPurification(level, pos);
        }

        if (StationProtocols.handles(level, pos)) {
            return StationProtocols.supports(level, pos, recipe);
        }

        return supportsUnadaptedRecipe(
                stationType,
                Workstations.declaredRecipeTypeAt(level, pos),
                WorkRecipeRegistry.foreignRecipeTypeId(recipe),
                level.getBlockEntity(pos) != null,
                recipe);
    }

    /** The world-free half of {@link #supportsRecipe}, kept explicit for regression coverage. */
    static boolean supportsUnadaptedRecipe(
            @Nullable StationType stationType,
            @Nullable ResourceLocation declaredRecipeType,
            @Nullable ResourceLocation recipeType,
            boolean hasBlockEntity,
            DiscoveredRecipe recipe
    ) {
        if (stationType == null || recipe == null) return false;
        // Protocol lifecycles are never allowed to fall through to the built-in station engine.
        if (isProtocolType(stationType) || isProtocolType(recipe.stationType())) return false;

        // A station declaring a custom recipe type owns only that family; a built-in station has
        // no declaration and accepts only the built-in family for its role.
        if (recipeType != null
                ? !recipeType.equals(declaredRecipeType)
                : declaredRecipeType != null) return false;
        if (stationType != recipe.stationType()) return false;

        // The built-in cutting-board interaction needs a real block entity. Hot stations use the
        // normal StationContents lifecycle and need no protocol adapter.
        return stationType != StationType.CUTTING_BOARD || hasBlockEntity;
    }

    /** Kept world-free so recipe-pairing tests do not have to load a platform station adapter. */
    private static boolean isProtocolType(StationType type) {
        return type == StationType.PASSIVE_STATION
                || type == StationType.PLACE_SURFACE
                || type == StationType.FURNACE_STATION
                || type == StationType.CRAFT_SURFACE;
    }

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
        if (StationProtocols.handles(level, pos)) {
            movedAny |= StationProtocols.cleanupInvalidContents(
                    level, villager, pos, storageBounds);
        }
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
