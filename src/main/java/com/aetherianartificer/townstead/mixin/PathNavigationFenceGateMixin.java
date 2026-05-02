package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Make MCA villagers open and close fence gates as a natural part of
 * path traversal — companion to {@link WalkNodeEvaluatorFenceGateMixin}
 * which gets fence gates routed through the pathfinder in the first
 * place.
 *
 * <p>This mixin lives in the navigation layer rather than as a brain
 * behavior because gate handling is fundamentally a property of "moving
 * along a path" — there's no decision to make outside of an active
 * navigation. Hooking {@code followThePath} means the work only fires
 * when the villager actually crosses a path node (i.e., once per block
 * of movement, not every server tick), and when there's no active path
 * the mixin is dormant entirely. The matching {@code stop} hook handles
 * close-behind on path completion.
 *
 * <p>State is kept in {@code @Unique} fields on the per-mob {@code
 * PathNavigation} instance, so each villager has their own opened-gate
 * tracking with no shared maps or behavior cooldown machinery.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationFenceGateMixin {
    @Unique
    private Mob townstead$mob;
    @Unique
    private Level townstead$level;
    @Unique
    private static Field townstead$mobField;
    @Unique
    private static Field townstead$levelField;
    @Unique
    private static Field townstead$pathField;

    /** Gates this navigation has opened, closed once they're behind the villager. */
    @Unique
    private Set<BlockPos> townstead$openedGates;
    /** Last observed next-node index — when this changes the villager just crossed a node. */
    @Unique
    private int townstead$lastNodeIndex = -1;

    @Unique
    private static final double TOWNSTEAD_CLOSE_DISTANCE = 3.0;
    @Unique
    private static final double TOWNSTEAD_CLOSE_DISTANCE_SQ =
            TOWNSTEAD_CLOSE_DISTANCE * TOWNSTEAD_CLOSE_DISTANCE;
    @Unique
    private static final double TOWNSTEAD_STANDING_IN_DISTANCE_SQ = 1.5 * 1.5;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void townstead$captureNavigationContext(Mob mob, Level level, CallbackInfo ci) {
        this.townstead$mob = mob;
        this.townstead$level = level;
    }

    //? if >=1.21 {
    @Inject(method = "followThePath", at = @At("RETURN"))
    private void townstead$handleFenceGatesOnAdvance(CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "m_7636_", remap = false, at = @At("RETURN"))
    private void townstead$handleFenceGatesOnAdvance(CallbackInfo ci) {
    *///?}
        Mob mob = townstead$mob();
        if (!(mob instanceof VillagerEntityMCA)) return;
        if (!(townstead$level() instanceof ServerLevel serverLevel)) return;
        Path p = townstead$path();
        if (p == null || p.isDone()) return;
        int idx = p.getNextNodeIndex();
        if (idx == this.townstead$lastNodeIndex) return;
        this.townstead$lastNodeIndex = idx;

        Node prev = p.getPreviousNode();
        Node next = p.getNextNode();
        if (prev != null) townstead$openIfFenceGate(serverLevel, prev.asBlockPos());
        if (next != null) townstead$openIfFenceGate(serverLevel, next.asBlockPos());
        townstead$closeFarGates(serverLevel, prev, next);
    }

    //? if >=1.21 {
    @Inject(method = "stop", at = @At("HEAD"))
    private void townstead$closeOnNavStop(CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "m_26573_", remap = false, at = @At("HEAD"))
    private void townstead$closeOnNavStop(CallbackInfo ci) {
    *///?}
        Mob mob = townstead$mob();
        if (!(mob instanceof VillagerEntityMCA)) return;
        if (this.townstead$openedGates == null || this.townstead$openedGates.isEmpty()) return;
        if (!(townstead$level() instanceof ServerLevel serverLevel)) return;
        for (BlockPos pos : this.townstead$openedGates) {
            double dx = mob.getX() - (pos.getX() + 0.5);
            double dy = mob.getY() - (pos.getY() + 0.5);
            double dz = mob.getZ() - (pos.getZ() + 0.5);
            // Don't shut a gate the villager is currently standing in.
            if (dx * dx + dy * dy + dz * dz > TOWNSTEAD_STANDING_IN_DISTANCE_SQ) {
                townstead$closeFenceGate(serverLevel, pos);
            }
        }
        this.townstead$openedGates.clear();
        this.townstead$lastNodeIndex = -1;
    }

    @Unique
    private void townstead$openIfFenceGate(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isLoaded(pos)) return;
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock)) return;
        if (state.getValue(FenceGateBlock.OPEN)) return;
        serverLevel.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
        serverLevel.playSound(null, pos,
                SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS,
                1.0f, serverLevel.random.nextFloat() * 0.1f + 0.9f);
        if (this.townstead$openedGates == null) this.townstead$openedGates = new HashSet<>();
        this.townstead$openedGates.add(pos.immutable());
    }

    @Unique
    private void townstead$closeFarGates(ServerLevel serverLevel, @Nullable Node prev, @Nullable Node next) {
        if (this.townstead$openedGates == null || this.townstead$openedGates.isEmpty()) return;
        Mob mob = townstead$mob();
        if (mob == null) return;
        Iterator<BlockPos> it = this.townstead$openedGates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            boolean stillOnPath = (prev != null && prev.asBlockPos().equals(pos))
                    || (next != null && next.asBlockPos().equals(pos));
            if (stillOnPath) continue;
            double dx = mob.getX() - (pos.getX() + 0.5);
            double dy = mob.getY() - (pos.getY() + 0.5);
            double dz = mob.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz > TOWNSTEAD_CLOSE_DISTANCE_SQ) {
                townstead$closeFenceGate(serverLevel, pos);
                it.remove();
            }
        }
    }

    @Unique
    private static void townstead$closeFenceGate(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isLoaded(pos)) return;
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock)) return;
        if (!state.getValue(FenceGateBlock.OPEN)) return;
        serverLevel.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
        serverLevel.playSound(null, pos,
                SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS,
                1.0f, serverLevel.random.nextFloat() * 0.1f + 0.9f);
    }

    @Unique
    private Mob townstead$mob() {
        if (this.townstead$mob != null) return this.townstead$mob;
        Object value = townstead$getField("mob", "f_26494_", townstead$mobField);
        if (value instanceof Mob mob) {
            this.townstead$mob = mob;
            return mob;
        }
        return null;
    }

    @Unique
    private Level townstead$level() {
        if (this.townstead$level != null) return this.townstead$level;
        Object value = townstead$getField("level", "f_26495_", townstead$levelField);
        if (value instanceof Level level) {
            this.townstead$level = level;
            return level;
        }
        return null;
    }

    @Unique
    private Path townstead$path() {
        Path publicPath = ((PathNavigation) (Object) this).getPath();
        if (publicPath != null) return publicPath;
        Object value = townstead$getField("path", "f_26496_", townstead$pathField);
        return value instanceof Path path ? path : null;
    }

    @Unique
    private Object townstead$getField(String named, String srg, @Nullable Field cached) {
        try {
            Field field = cached;
            if (field == null) {
                field = townstead$findField(named, srg);
                if (field == null) return null;
                if ("mob".equals(named)) townstead$mobField = field;
                else if ("level".equals(named)) townstead$levelField = field;
                else if ("path".equals(named)) townstead$pathField = field;
            }
            return field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private Field townstead$findField(String named, String srg) {
        Class<?> type = PathNavigation.class;
        while (type != null) {
            try {
                Field field = type.getDeclaredField(named);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            try {
                Field field = type.getDeclaredField(srg);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
