package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * place. Gates found already open on the path are closed behind the
 * villager too (villagers keep the pens shut), except gates held open by
 * redstone, which stay under the circuit's control.
 *
 * <p>This mixin lives in the navigation layer rather than as a brain
 * behavior because gate handling is fundamentally a property of "moving
 * along a path" — there's no decision to make outside of an active
 * navigation. Hooking {@code followThePath} means opening only fires
 * when the villager actually crosses a path node (i.e., once per block
 * of movement, not every server tick). Closing has two layers: while
 * pathing, gates more than 3 blocks behind close on node crossings; once
 * the path is done (or navigation stopped), a {@code tick} hook closes
 * each remaining gate the moment the villager is clear of it. The tick
 * hook is the authority on trailing gates because {@code stop()} is not
 * guaranteed to run when a path simply completes, and a villager may
 * still be standing in the gateway when it does.
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

    /**
     * Gates to close once they're behind the villager: ones this navigation opened,
     * plus ones crossed while already open (tracked, not touched, on the way in).
     */
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

    // 1.21.1: MCA 7.7.37+ owns fence gates end to end — MCAWalkNodeEvaluator classifies them
    // as walkable and rejects stepping onto closed ones, SmarterOpenDoorsTask opens every gate
    // in the villager's body clearance and closes it behind them, and both honour MCA's
    // villagersInteractWithFenceGates config. Every hook in this class stays uninjected there
    // so nothing of ours can override that config. The 1.20.1 branch keeps them for the
    // pre-floor MCA line, which has none of it.
    //? if >=1.21 {
    @Unique
    private void townstead$captureNavigationContext(Mob mob, Level level, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "<init>", at = @At("TAIL"))
    private void townstead$captureNavigationContext(Mob mob, Level level, CallbackInfo ci) {
    *///?}
        this.townstead$mob = mob;
        this.townstead$level = level;
    }

    //? if >=1.21 {
    @Unique
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
        if (prev != null) townstead$openGatesInBodyClearance(serverLevel, prev.asBlockPos());
        if (next != null) townstead$openGatesInBodyClearance(serverLevel, next.asBlockPos());
        townstead$closeFarGates(serverLevel, prev, next);
    }

    /**
     * A path node names the villager's feet, but a gate anywhere in the column they occupy
     * blocks them. Opening the whole body clearance matters more here than it does for a
     * vanilla-sized villager: Townstead's size genes and non-humanoid rigs produce villagers
     * over two blocks tall, whose head would otherwise walk into a closed upper gate.
     */
    @Unique
    private void townstead$openGatesInBodyClearance(ServerLevel serverLevel, BlockPos pathPos) {
        Mob mob = townstead$mob();
        if (mob == null) return;
        int bodyHeightBlocks = Math.max(1, Mth.ceil(mob.getBbHeight()));
        for (int offset = 0; offset < bodyHeightBlocks; offset++) {
            townstead$openIfFenceGate(serverLevel, pathPos.above(offset));
        }
    }

    //? if >=1.21 {
    @Unique
    private void townstead$resetOnNavStop(CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "m_26573_", remap = false, at = @At("HEAD"))
    private void townstead$resetOnNavStop(CallbackInfo ci) {
    *///?}
        // Closing happens in the tick hook, not here: a villager stopping while still
        // inside the gateway must stay tracked until they step away (clearing here
        // orphaned those gates open), and stop() isn't guaranteed to fire at all when
        // a path simply runs to completion.
        this.townstead$lastNodeIndex = -1;
    }

    // Close pending gates once no path is being followed. Gates a path ends beside
    // (within closeFarGates' 3-block band) and gates the villager was standing in at
    // stop() land here; each closes the moment the villager is clear of it. Runs every
    // tick but early-outs on the (almost always) empty gate set.
    //? if >=1.21 {
    @Unique
    private void townstead$closePendingGates(CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "m_7638_", remap = false, at = @At("TAIL"))
    private void townstead$closePendingGates(CallbackInfo ci) {
    *///?}
        if (this.townstead$openedGates == null || this.townstead$openedGates.isEmpty()) return;
        Path p = townstead$path();
        if (p != null && !p.isDone()) return;   // active pathing: closeFarGates owns close-behind
        Mob mob = townstead$mob();
        if (mob == null) return;
        if (!(townstead$level() instanceof ServerLevel serverLevel)) return;
        Iterator<BlockPos> it = this.townstead$openedGates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            double dx = mob.getX() - (pos.getX() + 0.5);
            double dy = mob.getY() - (pos.getY() + 0.5);
            double dz = mob.getZ() - (pos.getZ() + 0.5);
            // Still don't shut a gate on a villager standing in it.
            if (dx * dx + dy * dy + dz * dz > TOWNSTEAD_STANDING_IN_DISTANCE_SQ) {
                townstead$closeFenceGate(serverLevel, pos);
                it.remove();
            }
        }
    }

    @Unique
    private void townstead$openIfFenceGate(ServerLevel serverLevel, BlockPos pos) {
        if (!serverLevel.isLoaded(pos)) return;
        BlockState state = serverLevel.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock)) return;
        if (state.getValue(FenceGateBlock.OPEN)) {
            // Someone else's open gate on our path: track it so it closes behind us
            // like one we opened. Redstone-held gates stay the circuit's business.
            if (state.getValue(FenceGateBlock.POWERED)) return;
        } else {
            serverLevel.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
            serverLevel.playSound(null, pos,
                    SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS,
                    1.0f, serverLevel.random.nextFloat() * 0.1f + 0.9f);
        }
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
        // Powered since we tracked it: closing would fight the redstone circuit.
        if (state.getValue(FenceGateBlock.POWERED)) return;
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
