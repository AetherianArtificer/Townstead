package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.ai.work.producer.ProducerStationSessions.SessionSnapshot;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.RecipeSelector;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ProducerWorkSupport {
    private ProducerWorkSupport() {}

    public static boolean beveragesOnly(ProducerRole role) {
        return role == ProducerRole.BARISTA;
    }

    public static boolean excludeBeverages(ProducerRole role, ServerLevel level, VillagerEntityMCA villager) {
        return role == ProducerRole.COOK && ProducerWorkPolicy.cookExcludesBeverages(level, villager);
    }

    public static @Nullable DiscoveredRecipe pickRecipe(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType stationType,
            @Nullable net.minecraft.core.BlockPos stationPos,
            Set<Long> worksiteBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil
    ) {
        return RecipeSelector.pickRecipe(
                level,
                villager,
                stationType,
                stationPos,
                worksiteBounds,
                recipeCooldownUntil,
                excludeBeverages(role, level, villager),
                beveragesOnly(role)
        );
    }

    public static @Nullable DiscoveredRecipe findSessionRecipe(
            ProducerRole role,
            ServerLevel level,
            @Nullable SessionSnapshot session,
            @Nullable StationType stationType
    ) {
        if (level == null || session == null || stationType == null) return null;
        return matchSessionRecipe(recipesForRole(level, role, stationType), session.recipeId(), session.recipeOutputId());
    }

    static List<DiscoveredRecipe> recipesForRole(ServerLevel level, ProducerRole role, StationType stationType) {
        if (beveragesOnly(role)) {
            return ModRecipeRegistry.getBeverageRecipesForStation(level, stationType);
        }
        return ModRecipeRegistry.getRecipesForStation(level, stationType);
    }

    public static @Nullable DiscoveredRecipe matchSessionRecipe(
            List<DiscoveredRecipe> recipes,
            @Nullable ResourceLocation recipeId,
            @Nullable ResourceLocation recipeOutputId
    ) {
        return matchSessionValue(recipes, recipeId, recipeOutputId, DiscoveredRecipe::id, DiscoveredRecipe::output);
    }

    static <T, K> @Nullable T matchSessionValue(
            List<T> values,
            @Nullable K recipeId,
            @Nullable K recipeOutputId,
            Function<T, K> recipeIdGetter,
            Function<T, K> recipeOutputGetter
    ) {
        if (values == null || values.isEmpty() || recipeOutputId == null) return null;
        if (recipeId != null) {
            for (T value : values) {
                if (recipeId.equals(recipeIdGetter.apply(value))) return value;
            }
        }
        for (T value : values) {
            if (recipeOutputId.equals(recipeOutputGetter.apply(value))) return value;
        }
        return null;
    }
}
