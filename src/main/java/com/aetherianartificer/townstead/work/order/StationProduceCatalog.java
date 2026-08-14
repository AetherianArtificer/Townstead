package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteWork;
import com.aetherianartificer.townstead.work.site.Worksites;
import com.aetherianartificer.townstead.work.station.ProtocolRecipes;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The data-declared half of the order sheet: any workstation def that names a {@code work_task}
 * offers its {@code produces} lines wherever that trade works and its block actually stands.
 *
 * <p>This is what makes a pack's station orderable without a line of Java. The def already says
 * which block, which slots, what goes in and what comes out; naming the task says whose sheet it
 * belongs on, and physical presence says at which worksite. A def that names no task is
 * recognition only, so a station nobody's work drives never becomes an order that waits forever.</p>
 */
public final class StationProduceCatalog implements WorksiteCatalogs.Catalog {

    private StationProduceCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new StationProduceCatalog());
    }

    // taskType() stays null: one catalogue speaks for every def, so which trades it answers for
    // depends on the defs loaded, not on a single type declared here.

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<ResourceLocation> worked = WorksiteWork.typesAt(level, site, extent);
        if (worked.isEmpty()) return List.of();

        Map<ResourceLocation, BlockState> present = blocksIn(level, extent);
        Map<ResourceLocation, Integer> onHand = null;
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> out = new ArrayList<>();
        for (WorkstationDef def : Workstations.all()) {
            List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> declared =
                    declarationsForDef(level, site, extent, worked, def);
            if (declared.isEmpty()) continue;
            if (!isPresent(def, present)) continue;
            ResourceLocation icon = iconOf(def);
            String label = blockName(icon, def);
            if (onHand == null) onHand = StationCatalogs.stockIn(level, site, extent);
            // The claiming trades' own recipe filters decide what the family means HERE: the
            // crafting family is every recipe in the game, and an armorer's bench offers armor
            // because the armorer's declaration says so, not because the bench could make boats.
            // A declaration also names its stations, and that is part of the claim: the mason's
            // craft is stonecutter-only, so a crafting table in the mason's room drives nothing.
            for (DiscoveredRecipe recipe : ProtocolRecipes.discoverFor(def)) {
                if (!allowedByAny(declared, recipe)) continue;
                if (!seen.add(recipe.output())) continue;
                // A duplicating line is a service, not production: the option says so, and the
                // screen asks for the workpiece instead of adding a plain line.
                var produce = com.aetherianartificer.townstead.work.station.StationProtocols
                        .produceFor(def, recipe);
                if (produce != null && produce.copies() != null) {
                    var plain = StationCatalogs.option(recipe, label, icon, onHand);
                    out.add(com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload
                            .Option.commissioned(plain.output(), plain.stationLabel(),
                                    plain.stationIcon(), plain.available(), plain.blocker(),
                                    plain.makes(), plain.needs(), plain.missing(),
                                    "Copy " + StationCatalogs.itemNameOf(produce.copies())));
                    continue;
                }
                out.add(StationCatalogs.option(recipe, label, icon, onHand));
            }
            // A def whose outputs come from a recipe family rather than inline lines (the smoker's
            // smoking recipes) offers that family, which is exactly what the station will do.
            for (DiscoveredRecipe recipe : ProtocolRecipes.discoverByType(level, def)) {
                if (!allowedByAny(declared, recipe)) continue;
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.option(recipe, label, icon, onHand));
            }
            for (DiscoveredRecipe recipe : v2Recipes(level, def)) {
                if (!allowedByAny(declared, recipe)) continue;
                if (!seen.add(recipe.output())) continue;
                out.add(StationCatalogs.option(recipe, label, icon, onHand));
            }
        }
        return out;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<ResourceLocation> worked = WorksiteWork.typesAt(level, site, extent);
        if (worked.isEmpty()) return List.of();

        Map<ResourceLocation, BlockState> present = blocksIn(level, extent);
        List<Station> out = new ArrayList<>();
        for (WorkstationDef def : Workstations.all()) {
            List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> declared =
                    declarationsForDef(level, site, extent, worked, def);
            if (declared.isEmpty()) continue;
            // A station no claiming trade's declaration drives is not missing here — a mason's
            // room without a crafting table lacks nothing.
            ResourceLocation icon = iconOf(def);
            if (NO_ICON.equals(icon)) continue;
            // Absent stations stay listed: a butchery without its grinder should read as a
            // butchery missing a grinder, exactly as a kitchen reads its missing pot.
            out.add(new Station(blockName(icon, def), icon, isPresent(def, present)));
        }
        return out;
    }

    private static List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> declarationsForDef(
            ServerLevel level, Worksite site, Set<Long> extent, Set<ResourceLocation> worked,
            WorkstationDef def) {
        List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> out = new ArrayList<>();
        if (def.workTask() != null) {
            if (!worked.contains(def.workTask())) return List.of();
            for (var task : WorksiteWork.declaredTasksAt(level, site, extent, def.workTask())) {
                if (taskDrivesDef(task, def)) out.add(task);
            }
            return List.copyOf(out);
        }
        boolean v2 = def.blocks().stream().anyMatch(block -> Workstations.v2ByBlockId(block) != null);
        if (!v2) return List.of();
        for (ResourceLocation type : worked) {
            if (!com.aetherianartificer.townstead.profession.def.WorkTaskTypes.isStationDriven(type)) continue;
            for (var task : WorksiteWork.declaredTasksAt(level, site, extent, type)) {
                if (taskDrivesDef(task, def)) out.add(task);
            }
        }
        return List.copyOf(out);
    }

    private static List<DiscoveredRecipe> v2Recipes(ServerLevel level, WorkstationDef def) {
        Set<ResourceLocation> attached = new LinkedHashSet<>();
        for (ResourceLocation block : def.blocks()) {
            if (Workstations.v2ByBlockId(block) != null) {
                attached.addAll(com.aetherianartificer.townstead.work.station.WorkstationRecipeTypes
                        .forBlock(block));
            }
        }
        if (attached.isEmpty()) return List.of();
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (DiscoveredRecipe recipe : com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry
                .getRecipes(level)) {
            ResourceLocation type = com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry
                    .recipeTypeId(recipe);
            if (type != null && attached.contains(type)) out.add(recipe);
        }
        return List.copyOf(out);
    }

    /** Whether this declaration's workstation filter admits any block this def can be. */
    private static boolean taskDrivesDef(
            com.aetherianartificer.townstead.profession.def.WorkTaskDef task, WorkstationDef def) {
        if (task.anyWorkstation()) return true;
        for (ResourceLocation block : def.blocks()) {
            if (task.allowsBlock(block)) return true;
        }
        for (ResourceLocation tagId : def.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                if (task.allowsBlock(BuiltInRegistries.BLOCK.getKey(holder.value()))) return true;
            }
        }
        return false;
    }

    /** Offered when any claiming trade's declaration admits it; an empty claim list admits all. */
    private static boolean allowedByAny(
            List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> declared,
            DiscoveredRecipe recipe) {
        if (declared.isEmpty()) return true;
        for (com.aetherianartificer.townstead.profession.def.WorkTaskDef task : declared) {
            if (task.allowsRecipe(recipe.id(), recipe.output(), recipe.inputs())) return true;
        }
        return false;
    }

    /** Every distinct block standing in the extent, one representative state each. */
    private static Map<ResourceLocation, BlockState> blocksIn(ServerLevel level, Set<Long> extent) {
        Map<ResourceLocation, BlockState> out = new HashMap<>();
        for (long packed : extent) {
            BlockState state = level.getBlockState(BlockPos.of(packed));
            if (state.isAir()) continue;
            out.putIfAbsent(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state);
        }
        return out;
    }

    private static boolean isPresent(WorkstationDef def, Map<ResourceLocation, BlockState> present) {
        for (ResourceLocation block : def.blocks()) {
            if (present.containsKey(block)) return true;
        }
        for (ResourceLocation tagId : def.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (BlockState state : present.values()) {
                if (state.is(tag)) return true;
            }
        }
        return false;
    }

    private static final ResourceLocation NO_ICON =
            BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.AIR);

    /**
     * The def's first block that exists as an item, for the screen to draw. A def that names only
     * block tags (the smoker does) resolves through the tag's members — skipping those left the
     * option iconless and its label the raw def path, which is how "smoker" reached the screen in
     * lowercase. Falls back to air, which the screen renders as nothing — a null would fail the
     * packet write.
     */
    private static ResourceLocation iconOf(WorkstationDef def) {
        for (ResourceLocation block : def.blocks()) {
            if (BuiltInRegistries.ITEM.containsKey(block)) return block;
        }
        for (ResourceLocation tagId : def.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(holder.value());
                if (BuiltInRegistries.ITEM.containsKey(id)) return id;
            }
        }
        return NO_ICON;
    }

    private static String blockName(ResourceLocation icon, WorkstationDef def) {
        if (!NO_ICON.equals(icon) && BuiltInRegistries.ITEM.containsKey(icon)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(icon)).getHoverName().getString();
        }
        String words = def.id().getPath().replace('_', ' ');
        return words.isEmpty() ? words
                : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
