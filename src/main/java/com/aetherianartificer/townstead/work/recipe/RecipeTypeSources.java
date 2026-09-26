package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named readers for recipe types the vanilla recipe API cannot describe: a result that is only
 * chosen at craft time, an ingredient count that lives outside the ingredient. A compat package
 * registers a reader for its mod's recipe type, and any workstation document that declares that
 * {@code recipe_type} reads through it. The engine never learns which mod owns the type.
 */
public final class RecipeTypeSources {

    public interface Source {
        /** Every recipe of the def's type, shaped for the def's role and tier. */
        List<DiscoveredRecipe> discover(ServerLevel level, WorkstationDef def);
    }

    private static final Map<ResourceLocation, Source> SOURCES = new ConcurrentHashMap<>();

    private RecipeTypeSources() {}

    public static void register(ResourceLocation recipeType, Source source) {
        if (recipeType == null || source == null) return;
        SOURCES.put(recipeType, source);
    }

    public static @Nullable Source byType(@Nullable ResourceLocation recipeType) {
        return recipeType == null ? null : SOURCES.get(recipeType);
    }

    static void clear() {
        SOURCES.clear();
    }
}
