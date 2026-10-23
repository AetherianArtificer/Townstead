package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.producer.ProducerRole;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.producer.ProducerStationSessions.SessionSnapshot;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.RecipeSelector;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ProducerWorkSupport {
    private ProducerWorkSupport() {}

    public static boolean restrictToBeverages(ProducerRole role) {
        // A career name is not a recipe classifier. Beverage Artisan includes preparatory work
        // such as roasting coffee beans, and the profession's task declarations already say
        // exactly which recipes it may perform. Keep this false unless a future producer spec
        // explicitly introduces a recipe-scope policy independent of its career.
        return false;
    }

    public static boolean excludeBeverages(ProducerRole role, ServerLevel level, VillagerEntityMCA villager) {
        // Recipe ownership belongs to the role, not to whoever happens to be on shift nearby.
        // Cooks must never consume beverage-production inputs just because no Beverage Artisan is
        // currently considered "working" by the assignment scanner.
        return role == ProducerRole.COOK;
    }

    public static @Nullable DiscoveredRecipe pickRecipe(
            ProducerRole role,
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType stationType,
            @Nullable net.minecraft.core.BlockPos stationPos,
            Set<Long> worksiteBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil,
            Predicate<ResourceLocation> outputAllowed,
            ResourceLocation... taskTypes
    ) {
        return RecipeSelector.pickRecipe(
                level,
                villager,
                stationType,
                stationPos,
                worksiteBounds,
                recipeCooldownUntil,
                excludeBeverages(role, level, villager),
                restrictToBeverages(role),
                outputAllowed,
                taskTypes
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
        return WorkRecipeRegistry.getRecipesForStation(level, stationType);
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
