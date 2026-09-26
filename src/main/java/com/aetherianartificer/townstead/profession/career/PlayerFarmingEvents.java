package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import com.aetherianartificer.townstead.block.FieldPostIndex;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import com.aetherianartificer.townstead.hunger.HarvestWorkTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
//?} else if forge {
/*import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * Player-side farming attribution. The harvest engine credits villagers for each field action;
 * these hooks give a player the same XP and Chronicle activity for the same actions, so a Farmer
 * career advances whether the hands belong to a villager or the player. Amounts mirror the
 * engine's own: harvest 3, irrigate 4, plant 2, till 1, groom 1.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class PlayerFarmingEvents {
    private static final int XP_HARVEST = 3;
    private static final int XP_IRRIGATE = 4;
    private static final int XP_PLANT = 2;
    private static final int XP_TILL = 1;
    private static final int XP_GROOM = 1;

    private PlayerFarmingEvents() {}

    private static boolean realPlayer(Object entity) {
        return entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
    }

    /**
     * Breaking a fully grown crop is a harvest. An immature crop earns nothing. Clearing a weed
     * from a cell a Field Post plans as soil, or from the ring around one, is grooming.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!realPlayer(event.getPlayer())) return;
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            award((ServerPlayer) event.getPlayer(), level, XP_HARVEST, "townstead:harvested");
        } else if (HarvestWorkTask.isRemovableWeed(state)
                && (plannedOrAdjacentSoil(level, event.getPos().below()) || plannedSoil(level, event.getPos()))) {
            award((ServerPlayer) event.getPlayer(), level, XP_GROOM, "townstead:groomed");
        }
    }

    /** Placing a crop block, on farmland or into water, is a planting. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!realPlayer(event.getEntity())) return;
        if (event.getPlacedBlock().getBlock() instanceof CropBlock) {
            award((ServerPlayer) event.getEntity(), level, XP_PLANT, "townstead:planted");
        }
    }

    /** A hoe turning ground into farmland is a tilling. */
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!realPlayer(event.getPlayer())) return;
        //? if neoforge {
        if (event.getItemAbility() != ItemAbilities.HOE_TILL) return;
        //?} else if forge {
        /*if (event.getToolAction() != ToolActions.HOE_TILL) return;
        *///?}
        BlockState result = event.getFinalState();
        if (result == null || !(result.getBlock() instanceof FarmBlock)) return;
        if (event.getState().getBlock() instanceof FarmBlock) return;
        award((ServerPlayer) event.getPlayer(), level, XP_TILL, "townstead:tilled");
    }

    /**
     * Emptying a water bucket onto a cell that a Field Post plan marks as Water is irrigation.
     * Bucket use fires no placement event, so the right-click hook calls this and the award is
     * confirmed one tick later, once the water is actually there.
     */
    public static void onRightClickWithBucket(ServerPlayer player, ServerLevel level, BlockPos clicked,
                                              Direction face, ItemStack held) {
        if (held.isEmpty() || !held.is(Items.WATER_BUCKET) || player instanceof FakePlayer) return;
        BlockState clickedState = level.getBlockState(clicked);
        BlockPos target = clickedState.getBlock() instanceof LiquidBlockContainer
                || clickedState.canBeReplaced(Fluids.WATER) ? clicked : clicked.relative(face);
        if (level.getFluidState(target).is(FluidTags.WATER)) return;
        if (!plannedWater(level, target)) return;
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!player.isAlive() || player.serverLevel() != level) return;
            if (!level.getFluidState(target).is(FluidTags.WATER)) return;
            award(player, level, XP_IRRIGATE, "townstead:irrigated");
        }));
    }

    private static boolean plannedSoil(ServerLevel level, BlockPos pos) {
        for (FieldPostBlockEntity post : FieldPostIndex.findAllInRange(level, pos, FieldPostBlockEntity.DEFAULT_RADIUS)) {
            BlockPos origin = post.getBlockPos();
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (Math.abs(dx) > post.getRadius() || Math.abs(dz) > post.getRadius()) continue;
            if (Math.abs(pos.getY() - origin.getY()) > 2) continue;
            SoilType soil = post.getCellPlan().soilAt(dx, dz);
            if (soil != null && soil != SoilType.NONE && soil != SoilType.WATER && soil != SoilType.PROTECTED) return true;
        }
        return false;
    }

    private static boolean plannedOrAdjacentSoil(ServerLevel level, BlockPos pos) {
        if (plannedSoil(level, pos)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (plannedSoil(level, pos.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    private static boolean plannedWater(ServerLevel level, BlockPos pos) {
        for (FieldPostBlockEntity post : FieldPostIndex.findAllInRange(level, pos, FieldPostBlockEntity.DEFAULT_RADIUS)) {
            BlockPos origin = post.getBlockPos();
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            if (Math.abs(dx) > post.getRadius() || Math.abs(dz) > post.getRadius()) continue;
            if (post.getCellPlan().soilAt(dx, dz) == SoilType.WATER) return true;
        }
        return false;
    }

    private static void award(ServerPlayer player, ServerLevel level, int xp, String verb) {
        CareerProgression.completeWork(player, Careers.FARMER, xp, level.getGameTime(), verb, null, null, xp);
    }
}
