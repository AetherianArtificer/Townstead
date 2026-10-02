package com.aetherianartificer.townstead.block.haze;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A short-lived cell of whatever {@link HazeKind} its state names: no collision, no outline and
 * replaceable (except a {@code solid} kind, which is a temporary wall), and thinning one density
 * step at a time until it clears. Scheduled ticks persist
 * with the chunk, so a cell whose chunk unloads mid-life still clears when it loads again, and a
 * cell whose kind is no longer loaded clears on its first tick.
 */
public class HazeBlock extends Block {

    public static final int MAX_DENSITY = 8;
    public static final int MAX_KIND = 63;
    public static final IntegerProperty DENSITY = IntegerProperty.create("density", 1, MAX_DENSITY);
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, MAX_KIND);

    public HazeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DENSITY, MAX_DENSITY).setValue(KIND, 0));
    }

    /**
     * The kind for shape queries, which arrive without a side (a pathfinding region, a
     * collision sweep). Both sides hold the same list once synced; the server's is preferred
     * wherever it is loaded.
     */
    @Nullable
    private static HazeKind kindForShape(BlockState state) {
        HazeKind kind = kindOf(state, false);
        return kind != null || !HazeKinds.server().all().isEmpty() ? kind : kindOf(state, true);
    }

    private static boolean solid(BlockState state) {
        HazeKind kind = kindForShape(state);
        return kind != null && kind.shape() == HazeKind.Shape.SOLID;
    }

    @Nullable
    public static HazeKind kindOf(BlockState state, boolean clientSide) {
        return state.getBlock() instanceof HazeBlock
                ? HazeKinds.forSide(clientSide).byIndex(state.getValue(KIND)) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DENSITY, KIND);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return solid(state) ? Shapes.block() : Shapes.empty();
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !solid(state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        HazeKind kind = kindOf(state, level.isClientSide());
        if (kind == null || !kind.shape().grounded()) return true;
        // Any floor will do, so a spill still lands on a path, farmland or a slab.
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide() || oldState.is(this)) return;
        HazeKind kind = kindOf(state, false);
        if (kind == null || !canSurvive(state, level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        scheduleStep(level, pos, kind);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        HazeKind kind = kindOf(state, false);
        if (kind == null) {
            level.removeBlock(pos, false);
            return;
        }
        if (kind.conceals()) HazeConcealment.sweep(level, pos);
        int density = state.getValue(DENSITY);
        if (density <= 1) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlock(pos, state.setValue(DENSITY, density - 1), Block.UPDATE_CLIENTS);
        scheduleStep(level, pos, kind);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof LivingEntity living)) return;
        HazeKind kind = kindOf(state, false);
        if (kind == null || kind.insideAction() == null) return;
        if (living.tickCount % kind.insideInterval() != 0) return;
        if (kind.insideCondition() != null && !kind.insideCondition().test(new ConditionContext(living))) return;
        kind.insideAction().run(new ActionContext(living));
    }

    /**
     * The client samples a nearby cell only every couple of seconds, so each call leaves puffs that
     * outlive the gap: large, slow, tinted to the kind. Both how many and how strong follow the
     * density, so the cloud thins evenly to nothing.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        HazeKind kind = kindOf(state, true);
        if (kind == null || kind.shape() != HazeKind.Shape.CLOUD) return;
        int density = state.getValue(DENSITY);
        float strength = (float) density / MAX_DENSITY;
        // A concealing cloud has to hide whoever is inside from anyone outside, so it packs in
        // more, heavier puffs than a cloud that is only for show.
        float expected = kind.conceals() ? density / 1.5f : density / 3f;
        int puffs = (int) expected + (random.nextFloat() < expected - (int) expected ? 1 : 0);
        for (int i = 0; i < puffs; i++) {
            level.addParticle(HazeParticles.PUFF.get(),
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    kind.color() & 0xFFFFFF, strength, kind.conceals() ? 1.0 : 0.0);
        }
        if (kind.particle() != null && random.nextInt(3) == 0
                && BuiltInRegistries.PARTICLE_TYPE.get(kind.particle()) instanceof SimpleParticleType accent) {
            level.addParticle(accent,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0.0, 0.01, 0.0);
        }
    }

    private void scheduleStep(Level level, BlockPos pos, HazeKind kind) {
        // Jitter so a cloud frays at its edges instead of vanishing in one frame.
        int step = kind.stepTicks();
        int jitter = step / 3;
        level.scheduleTick(pos, this, step + level.getRandom().nextInt(jitter * 2 + 1) - jitter);
    }
}
