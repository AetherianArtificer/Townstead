package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.WorkPathing;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognising workstations in the world and standing at them.
 *
 * <p>Everything here answers from workstation defs rather than from block ids, so it knows nothing
 * about which mod added a block or which profession will work it. That is the point of the split:
 * these questions used to be answered by naming {@code farmersdelight:stove} and friends directly,
 * which meant a modded station could never be recognised however correctly it was declared.</p>
 *
 * <p>How many jobs a station can take is deliberately NOT here. That needs each block's own
 * internals (a campfire's four slots, a stove's inventory, whether a skillet is hot), so it stays
 * with the mod compat that knows how to ask.</p>
 */
public final class Stations {

    private static final class Tags {
        /** Blocks a villager should not stand on to work, even when they are otherwise walkable. */
        private static final TagKey<Block> AVOID_STANDING = TagKey.create(
                Registries.BLOCK,
                //? if >=1.21 {
                ResourceLocation.fromNamespaceAndPath("townstead", "avoid_standing")
                //?} else {
                /*new ResourceLocation("townstead", "avoid_standing")
                *///?}
        );
    }

    private Stations() {}

    /** One station a villager could claim: where it is, what role it plays, how many jobs it holds. */
    public record StationSlot(BlockPos pos, StationType type, ResourceLocation blockId, int capacity) {}

    /**
     * Stable identity used by profession/worksite filters for a station slot.
     *
     * <p>A place-surface slot is an empty cell until work begins. Calling the empty cell
     * {@code minecraft:air} makes every profession reject it before the protocol can place its
     * work block. Such a slot therefore identifies as the block declared by {@code places}; all
     * ordinary stations continue to identify as their block in the world.</p>
     */
    public static ResourceLocation slotBlockId(ServerLevel level, BlockPos anchor, StationType type) {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        WorkstationDef def = type == StationType.PLACE_SURFACE
                ? StationProtocols.defAt(level, anchor) : null;
        return slotBlockId(actual, type, def == null ? null : def.places());
    }

    static ResourceLocation slotBlockId(ResourceLocation actual, StationType type,
                                        @Nullable ResourceLocation placedBlock) {
        return type == StationType.PLACE_SURFACE && placedBlock != null ? placedBlock : actual;
    }

    // ── Recognition ──

    public static @Nullable StationType stationType(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // An open-topped station with something sitting on it is not a station right now.
        if (coverBlocksWork(level, pos, state)) return null;
        // A V2 station may declare an exceptional preparation interaction which changes a
        // transient requirement (lighting the stove below, opening a machine). Keep that physical
        // station schedulable so its data-driven adapter can perform the preparation. Stations
        // with no such action remain unavailable when their requirements fail.
        if (!supportSatisfied(level, pos, state)) {
            WorkstationV2Def v2 = Workstations.v2ByState(state);
            if (v2 == null || (!v2.hasPreparationAction() && !v2.hasReservation())) return null;
        }
        // An empty placement anchor (free cell above a declared place-surface) is a station a
        // villager can create; the block-state overload cannot see it, only this one can.
        if (state.isAir() && StationProtocols.surfaceDefBelow(level, pos) != null) {
            return StationType.PLACE_SURFACE;
        }
        return stationType(state);
    }

    public static @Nullable StationType stationType(BlockState state) {
        WorkstationDef def = Workstations.byState(state);
        return def != null ? def.role() : null;
    }

    public static boolean isStation(ServerLevel level, BlockPos pos) {
        return stationType(level, pos) != null;
    }

    public static boolean isStation(BlockState state) {
        return stationType(state) != null;
    }

    // ── Open-topped stations ──

    /**
     * Whether something resting on this station's top face stops it working. Only asked of
     * stations whose def says they cook on top; anything else is unaffected by what sits above it.
     */
    public static boolean coverBlocksWork(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos == null || state == null) return false;
        WorkstationDef def = Workstations.byState(state);
        if (def == null || !def.openTop()) return false;
        return coveredAbove(level, pos);
    }

    /** True when the cell above holds another station or anything else solid enough to sit there. */
    public static boolean coveredAbove(ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        // A station stacked on a station (a pot or pan set down on the fire) occupies it, and so
        // does any block that would not simply be replaced by placing something.
        if (stationType(above) != null) return true;
        return !above.canBeReplaced();
    }

    /**
     * Whether whatever this station needs beneath it is there. A def that asks for nothing is
     * always satisfied, which is every station that does not sit on a fire.
     */
    public static boolean supportSatisfied(ServerLevel level, BlockPos pos, BlockState state) {
        WorkstationV2Def v2 = Workstations.v2ByState(state);
        if (v2 != null) return v2.isOperational(level, pos);
        WorkstationDef def = Workstations.byState(state);
        return def == null || supportSatisfied(level, pos, def);
    }

    /** The same question asked of a known def, for callers that already resolved one. */
    public static boolean supportSatisfied(ServerLevel level, BlockPos pos, WorkstationDef def) {
        if (def.supportBelow().isEmpty() && def.supportBelowTags().isEmpty()) return true;
        BlockState below = level.getBlockState(pos.below());
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(below.getBlock());
        if (id != null && def.supportBelow().contains(id)) return true;
        for (ResourceLocation tag : def.supportBelowTags()) {
            if (below.is(TagKey.create(Registries.BLOCK, tag))) return true;
        }
        return false;
    }

    // ── Standing ──

    /** Author-declared stand cells for a station (workstation def "stands" offsets), safety-checked. */
    public static List<BlockPos> preferredStands(BlockGetter level, BlockPos anchor) {
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        // An empty placement anchor carries no block; its stands come from the place-surface def
        // whose surface sits below.
        if (def == null && level instanceof ServerLevel serverLevel
                && level.getBlockState(anchor).isAir()) {
            def = StationProtocols.surfaceDefBelow(serverLevel, anchor);
        }
        if (def == null || def.stands().isEmpty()) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (Vec3i offset : def.stands()) {
            BlockPos pos = anchor.offset(offset);
            if (WorkPathing.isSafeStandPosition(level, pos)) out.add(pos.immutable());
        }
        return out;
    }

    /** The declared stand nearest this villager, or null when none are declared or safe. */
    public static @Nullable BlockPos nearestStand(VillagerEntityMCA villager, List<BlockPos> stands) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos stand : stands) {
            double dist = villager.distanceToSqr(stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = stand;
            }
        }
        return best;
    }

    /**
     * Standing on a station is standing in your own way, and standing on storage means opening it
     * from on top of it. Which blocks count is a tag so packs can add their own containers.
     */
    public static boolean avoidStandingSurface(BlockState surface) {
        return isStation(surface) || surface.is(Tags.AVOID_STANDING);
    }

    public static @Nullable BlockPos findStandingPosition(ServerLevel level, VillagerEntityMCA villager,
                                                          BlockPos anchor) {
        BlockPos best = nearestStand(villager, preferredStands(level, anchor));
        if (best != null) return best;
        best = WorkPathing.nearestStandCandidate(level, villager, anchor, null);
        if (best != null) return best;

        // Last resort: if the villager already stands somewhere sane within reach, stay put
        // rather than abandoning a station over a pathing quibble.
        BlockPos current = villager.blockPosition();
        double stationDist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        if (stationDist > 9.0d) return null;
        BlockState below = level.getBlockState(current.below());
        if (WorkPathing.isSafeStandPosition(level, current)
                && below.isFaceSturdy(level, current.below(), Direction.UP)
                && !avoidStandingSurface(below)) {
            return current.immutable();
        }
        return null;
    }
}
