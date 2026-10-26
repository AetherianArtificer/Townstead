package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Need;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Station;
import com.aetherianartificer.townstead.work.recipe.RequirementLabels;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteWork;
import com.aetherianartificer.townstead.work.site.Worksites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The order-sheet view of data-authored block interactions.
 *
 * <p>This is the harvesting catalogue, but it contains no harvesting nouns.  A Job declares a
 * target block, the real item used on it, and the output ids which may physically emerge.  That
 * is enough to offer tapping a tree, clipping wool, filling a bottle or any future player-like
 * interaction on the same sheet as recipes, without teaching the screen about any of them.</p>
 */
public final class BlockInteractionOrderCatalog implements WorksiteCatalogs.Catalog {

    private static final ResourceLocation AIR = BuiltInRegistries.ITEM.getKey(Items.AIR);

    private BlockInteractionOrderCatalog() {}

    public static void bootstrap() {
        WorksiteCatalogs.register(new BlockInteractionOrderCatalog());
    }

    @Override
    public List<Option> optionsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<ResourceLocation> worked = WorksiteWork.typesAt(level, site, extent);
        if (worked.isEmpty()) return List.of();

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Option> result = new ArrayList<>();
        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.BLOCK_INTERACTION)) {
            if (!worked.contains(job.task()) || job.target() == null) continue;
            List<WorkTaskDef> declarations = WorksiteWork.declaredTasksAt(
                    level, site, extent, job.task());
            List<BlockPos> targets = matchingTargets(level, extent, job.target(), declarations);
            if (targets.isEmpty()) continue;

            ResourceLocation station = blockId(level, targets.get(0));
            String stationLabel = itemName(station);
            for (WorkJobDef.Interaction interaction : job.target().interactions()) {
                Need input = needFor(interaction.item());
                boolean inputAvailable = input == null || hasVillageStock(level, site, interaction.item());
                boolean targetReady = false;
                for (BlockPos target : targets) {
                    if (job.target().ready(level, target) && interaction.ready(level, target)) {
                        targetReady = true;
                        break;
                    }
                }
                List<Need> needs = input == null ? List.of() : List.of(input);
                List<Need> missing = inputAvailable ? List.of() : needs;
                String blocker = !targetReady ? "No target is ready."
                        : !inputAvailable ? StationCatalogs.describeMissing(missing) : "";

                for (ResourceLocation output : interaction.outputIds()) {
                    if (!allowedByAny(declarations, station, output) || !seen.add(output)) continue;
                    result.add(Option.item(output, stationLabel, station,
                            targetReady && inputAvailable, blocker,
                            interaction.expectedCount(), needs, missing));
                }
            }
        }
        return List.copyOf(result);
    }

    @Override
    public Order.CountScope defaultScopeFor(ServerLevel level, Worksite site,
                                            ResourceLocation output) {
        if (output == null) return null;
        for (Option option : optionsFor(level, site)) {
            if (output.equals(option.output())) {
                // Harvest belongs to the place it was gathered, but its stock commonly lives in
                // a separate store (the Apiary and Honey House are the first example). Counting
                // village shelves by default follows the physical delivery instead of demanding
                // duplicate storage beside every outdoor target.
                return Order.CountScope.VILLAGE;
            }
        }
        return null;
    }

    @Override
    public List<Station> stationsFor(ServerLevel level, Worksite site) {
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return List.of();
        Set<ResourceLocation> worked = WorksiteWork.typesAt(level, site, extent);
        Set<ResourceLocation> present = blocksIn(level, extent);
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Station> result = new ArrayList<>();

        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.BLOCK_INTERACTION)) {
            if (!worked.contains(job.task()) || job.target() == null) continue;
            List<WorkTaskDef> declarations = WorksiteWork.declaredTasksAt(
                    level, site, extent, job.task());
            for (ResourceLocation block : stationIcons(job.target())) {
                if (AIR.equals(block) || !allowedBlockByAny(declarations, block)
                        || !seen.add(block)) continue;
                result.add(new Station(itemName(block), block, present.contains(block)));
            }
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> matchingTargets(
            ServerLevel level, Set<Long> extent, WorkJobDef.BlockTarget target,
            List<WorkTaskDef> declarations) {
        List<BlockPos> result = new ArrayList<>();
        for (long packed : extent) {
            BlockPos pos = BlockPos.of(packed);
            ResourceLocation block = blockId(level, pos);
            if (target.matches(level, pos) && allowedBlockByAny(declarations, block)) {
                result.add(pos.immutable());
            }
        }
        return List.copyOf(result);
    }

    private static boolean allowedByAny(List<WorkTaskDef> declarations,
                                        ResourceLocation block, ResourceLocation output) {
        for (WorkTaskDef task : declarations) {
            if (task.allowsBlock(block) && task.allowsRecipe(null, output)) return true;
        }
        return false;
    }

    private static boolean allowedBlockByAny(List<WorkTaskDef> declarations,
                                             ResourceLocation block) {
        for (WorkTaskDef task : declarations) if (task.allowsBlock(block)) return true;
        return false;
    }

    private static @org.jetbrains.annotations.Nullable Need needFor(
            @org.jetbrains.annotations.Nullable String selector) {
        if (selector == null) return null;
        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) return null;
        if (!tag) {
            return BuiltInRegistries.ITEM.containsKey(id) ? new Need(id, 1) : null;
        }
        List<ResourceLocation> choices = new ArrayList<>();
        TagKey<Item> key = TagKey.create(Registries.ITEM, id);
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(holder.value());
            if (item != null && !AIR.equals(item)) choices.add(item);
        }
        return choices.isEmpty() ? null : new Need(choices, 1, RequirementLabels.tagName(id));
    }

    private static boolean hasVillageStock(ServerLevel level, Worksite site,
                                           @org.jetbrains.annotations.Nullable String selector) {
        if (selector == null) return true;
        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) return false;
        return tag
                ? WorksiteStock.countTag(level, site, id, Order.CountScope.VILLAGE) > 0
                : WorksiteStock.count(level, site, id, Order.CountScope.VILLAGE) > 0;
    }

    private static List<ResourceLocation> stationIcons(WorkJobDef.BlockTarget target) {
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation block : target.blocks()) {
            if (BuiltInRegistries.ITEM.containsKey(block)) result.add(block);
        }
        for (ResourceLocation tagId : target.blockTags()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                ResourceLocation block = BuiltInRegistries.BLOCK.getKey(holder.value());
                if (BuiltInRegistries.ITEM.containsKey(block)) {
                    result.add(block);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static Set<ResourceLocation> blocksIn(ServerLevel level, Set<Long> extent) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (long packed : extent) result.add(blockId(level, BlockPos.of(packed)));
        return result;
    }

    private static ResourceLocation blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString()
                : id.getPath().replace('_', ' ');
    }
}
