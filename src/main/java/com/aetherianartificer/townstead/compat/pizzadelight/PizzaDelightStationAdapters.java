package com.aetherianartificer.townstead.compat.pizzadelight;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;

import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.StationAdapters.StationPhase;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Pizza Delight's two method-bound station interactions, mirrored move for move. The basin
 * takes milk and a fermenting item by right-click (its item handler refuses insertion), so the
 * adapter invokes those same interaction methods with their documented nullable player —
 * consuming the villager's real milk bucket, handing the empty bucket back, shrinking the real
 * fermenting item. The finished pizza is lifted exactly as a peel-holding player lifts it:
 * {@code pickUpPizza} (no Player parameter) airs the block and throws the pizza item into the
 * world for pickup. Everything reflective degrades to a refusal, never a crash.
 */
final class PizzaDelightStationAdapters {

    static final String BASIN_ADAPTER = "townstead:pizzadelight_basin";
    static final String PIZZA_ADAPTER = "townstead:pizzadelight_pizza";

    private static final TagKey<net.minecraft.world.item.Item> FERMENTING_ITEMS =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.tryParse("pizzadelight:fermenting_items"));

    private PizzaDelightStationAdapters() {}

    static void bootstrap() {
        StationAdapters.register(BASIN_ADAPTER, new BasinAdapter());
        StationAdapters.register(PIZZA_ADAPTER, new PizzaHarvestAdapter());
    }

    /** Basin content string from the block entity's own save data ("air"/"milk"/"fermenting_milk"/"cheese"). */
    private static @Nullable String basinContent(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        try {
            //? if >=1.21 {
            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            //?} else {
            /*CompoundTag tag = be.saveWithoutMetadata();
            *///?}
            return tag.contains("BasinContent") ? tag.getString("BasinContent") : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static final class BasinAdapter implements StationAdapters.Adapter {

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            String content = basinContent(level, anchor);
            if (content == null) return StationPhase.FOREIGN;
            return switch (content) {
                case "air" -> StationPhase.IDLE;
                case "milk", "fermenting_milk" -> StationPhase.WORKING;
                case "cheese" -> StationPhase.READY;
                default -> StationPhase.FOREIGN;
            };
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            BlockEntity be = level.getBlockEntity(anchor);
            if (be == null || !"air".equals(basinContent(level, anchor))) return false;
            // Pour the milk: consume the villager's real bucket, keep the empty one, exactly
            // as the player interaction does on its own side of the null-player split.
            ItemStack milk = com.aetherianartificer.townstead.work.station.StationProtocols
                    .takeOne(villager, Items.MILK_BUCKET);
            if (milk.isEmpty()) return false;
            try {
                be.getClass().getMethod("addMilk", Level.class, Player.class, InteractionHand.class)
                        .invoke(be, level, null, null);
            } catch (Throwable t) {
                com.aetherianartificer.townstead.work.station.StationProtocols
                        .giveBack(villager, milk);
                return false;
            }
            com.aetherianartificer.townstead.work.station.StationProtocols
                    .giveBack(villager, new ItemStack(Items.BUCKET));

            // Drop in the fermenting item; the method shrinks the held stack itself.
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack stack = villager.getInventory().getItem(i);
                if (stack.isEmpty() || !stack.is(FERMENTING_ITEMS)) continue;
                try {
                    be.getClass().getMethod("useFermentingItem", ItemStack.class, Level.class, Player.class)
                            .invoke(be, stack, level, null);
                } catch (Throwable t) {
                    return false;
                }
                return "fermenting_milk".equals(basinContent(level, anchor));
            }
            return false;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            // The finished cheese sits in the basin's capability slot; extracting it resets
            // the basin, the same state transition the player's take performs.
            StationAdapters.Adapter fallback = StationAdapters.byName(StationAdapters.DEFAULT_ITEM_HANDLER);
            return fallback != null && fallback.collect(level, villager, anchor, def, recipe);
        }
    }

    static final class PizzaHarvestAdapter implements StationAdapters.Adapter {

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            // Place-surface phases are derived from the block ids by the protocol engine.
            return StationPhase.FOREIGN;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            // Composition runs through the placed block's own item handler in the engine.
            return false;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            BlockState state = level.getBlockState(anchor);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!blockId.equals(def.doneBlock())) return false;
            try {
                state.getBlock().getClass()
                        .getMethod("pickUpPizza", Level.class, BlockPos.class, BlockState.class,
                                net.minecraft.core.Direction.class)
                        .invoke(state.getBlock(), level, anchor, state, villager.getDirection().getOpposite());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
