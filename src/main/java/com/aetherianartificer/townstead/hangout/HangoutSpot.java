package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Set;

/** Data-authored furniture/standing-place profile. Posture is an open adapter id. */
public record HangoutSpot(ResourceLocation id, Set<ResourceLocation> blocks,
                          Set<ResourceLocation> blockTags, ResourceLocation posture,
                          ResourceLocation adapter,
                          int capacity, BlockPos canonicalOffset, Set<BlockPos> linkedOffsets,
                          boolean facingRelativeLinkedOffsets,
                          Vec3 embodimentOffset, boolean facingRelativeEmbodimentOffset,
                          BlockCondition availableWhen, RestBonus rest) {
    /** Fatigue points recovered per ordinary 500-tick fatigue interval while using this spot. */
    public record RestBonus(float fatigueRecovery) {
        public RestBonus {
            if (!Float.isFinite(fatigueRecovery) || fatigueRecovery <= 0F) {
                throw new IllegalArgumentException("rest fatigue_recovery must be finite and positive");
            }
        }
    }
    public HangoutSpot {
        Objects.requireNonNull(id, "id");
        blocks = blocks == null ? Set.of() : Set.copyOf(blocks);
        blockTags = blockTags == null ? Set.of() : Set.copyOf(blockTags);
        Objects.requireNonNull(posture, "posture");
        adapter = adapter == null ? HangoutEmbodiment.VANILLA : adapter;
        canonicalOffset = canonicalOffset == null ? new BlockPos(0, 0, 0)
                : new BlockPos(canonicalOffset.getX(), canonicalOffset.getY(), canonicalOffset.getZ());
        linkedOffsets = linkedOffsets == null ? Set.of() : Set.copyOf(linkedOffsets);
        embodimentOffset = embodimentOffset == null ? new Vec3(0D, 0.05D, 0D) : embodimentOffset;
        if (blocks.isEmpty() && blockTags.isEmpty()) {
            throw new IllegalArgumentException("blocks or block_tags is required");
        }
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
    }

    /** Resolves linked cells; relative coordinates are [right, up, forward]. */
    public Set<BlockPos> linkedPositions(BlockState state, BlockPos anchor) {
        if (!facingRelativeLinkedOffsets) {
            return linkedOffsets.stream().map(anchor::offset).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        Property<?> property = state.getBlock().getStateDefinition().getProperty("facing");
        Comparable<?> raw = property == null ? null : state.getValue(cast(property));
        if (!(raw instanceof net.minecraft.core.Direction facing) || !facing.getAxis().isHorizontal()) return Set.of();
        return linkedOffsets.stream().map(offset -> facingRelative(anchor, offset, facing))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Absolute session-anchor position, with optional [right, up, forward] authoring. */
    public Vec3 embodimentPosition(BlockState state, BlockPos anchor) {
        double x = embodimentOffset.x;
        double z = embodimentOffset.z;
        if (facingRelativeEmbodimentOffset) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty("facing");
            Comparable<?> raw = property == null ? null : state.getValue(cast(property));
            if (raw instanceof Direction facing && facing.getAxis().isHorizontal()) {
                Direction right = facing.getClockWise();
                x = right.getStepX() * embodimentOffset.x + facing.getStepX() * embodimentOffset.z;
                z = right.getStepZ() * embodimentOffset.x + facing.getStepZ() * embodimentOffset.z;
            }
        }
        return new Vec3(anchor.getX() + 0.5D + x, anchor.getY() + embodimentOffset.y,
                anchor.getZ() + 0.5D + z);
    }

    /** Resolves an authored [right, up, forward] block offset. */
    public static BlockPos facingRelative(BlockPos anchor, BlockPos offset, Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("facing-relative hangout offsets require a horizontal facing");
        }
        Direction right = facing.getClockWise();
        return new BlockPos(
                anchor.getX() + right.getStepX() * offset.getX() + facing.getStepX() * offset.getZ(),
                anchor.getY() + offset.getY(),
                anchor.getZ() + right.getStepZ() * offset.getX() + facing.getStepZ() * offset.getZ());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Property cast(Property<?> property) { return property; }
}
