package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Butcher villager cuts up a {@code butchery:iron_golem} (or its
 * {@code repaired_iron_golem} sibling) block with a hacksaw, matching
 * {@code IronGolemCutUpProcedure} stroke-for-stroke: ten strokes, sparks
 * + hacksaw sound per stroke, four body-part loot drops at strokes 3/6/9
 * and the terminal stroke 10 that removes the block.
 *
 * <p>Golem sourcing is player-driven by design: the villager never kills
 * iron golems (see {@link SlaughterPolicy}). Player kills the golem, the
 * mod's {@code IronGolemDropProcedure} drops an {@code iron_golem} item,
 * player places it in a carcass-capable shop, butcher processes it here.
 */
public class GolemProcessingTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final double ARRIVAL_DISTANCE_SQ = 4.0; // golem is a 2-block body; stand a little further
    private static final float WALK_SPEED = 0.55f;
    private static final int STROKE_COOLDOWN_TICKS = 30;
    private static final int PATH_TIMEOUT_TICKS = 200;

    //? if >=1.21 {
    private static final ResourceLocation SOUND_HACKSAW =
            ResourceLocation.parse("butchery:hacksaw");
    private static final ResourceLocation PARTICLE_SPARKS_ID =
            ResourceLocation.parse("butchery:sparks");
    //?} else {
    /*private static final ResourceLocation SOUND_HACKSAW =
            new ResourceLocation("butchery", "hacksaw");
    private static final ResourceLocation PARTICLE_SPARKS_ID =
            new ResourceLocation("butchery", "sparks");
    *///?}

    private enum Phase { PATH, PROCESS }

    @Nullable private BlockPos targetGolem;
    @Nullable private BlockPos standPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextStrokeTick;
    private boolean stalled;
    private boolean missingHacksaw;

    public GolemProcessingTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        return findGolemAcrossShops(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetGolem = findGolemAcrossShops(level, villager);
        if (targetGolem == null) return;
        standPos = findStandPos(level, villager, targetGolem);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextStrokeTick = gameTime + STROKE_COOLDOWN_TICKS;
        stalled = false;
        missingHacksaw = false;
        setWalkTarget(villager, standPos != null ? standPos : targetGolem);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetGolem == null) return false;
        BlockState state = level.getBlockState(targetGolem);
        if (!GolemStateMachine.isProcessable(level, state, targetGolem)) return false;
        if (gameTime - startedTick > MAX_DURATION) { stalled = true; return false; }
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) {
            stalled = true;
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetGolem == null) return;
        // Look at the middle of the golem body.
        villager.getLookControl().setLookAt(
                targetGolem.getX() + 0.5, targetGolem.getY() + 1.0, targetGolem.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetGolem;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.PROCESS;
                nextStrokeTick = gameTime + STROKE_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, anchor);
            }
            return;
        }

        if (gameTime < nextStrokeTick) return;

        if (!ButcherToolDamage.hasHacksaw(villager)) {
            emitNoHacksawChat(level, villager, gameTime);
            missingHacksaw = true;
            targetGolem = null;
            return;
        }

        // Make sure the hacksaw is what the villager visibly holds when
        // they swing. WorkToolTicker matches cleaver / knife / hacksaw
        // equally as butcher tools, so a straight setItemInHand sticks
        // until the task ends.
        equipHacksaw(villager);

        BlockState current = level.getBlockState(targetGolem);
        GolemStateMachine.Stroke stroke = GolemStateMachine.nextStroke(
                GolemStateMachine.readSaw(level, targetGolem));
        if (stroke == null) {
            targetGolem = null;
            return;
        }

        villager.swing(InteractionHand.MAIN_HAND, true);
        playHacksawSound(level, targetGolem);
        emitSparks(level, targetGolem);

        List<ItemStack> drops = GolemStateMachine.advance(level, targetGolem);
        deposit(level, villager, drops);
        ButcherToolDamage.consumeHacksawUse(villager);
        awardXp(villager, GolemStateMachine.STROKE_XP, gameTime);
        nextStrokeTick = gameTime + STROKE_COOLDOWN_TICKS;

        if (stroke.terminal()) {
            targetGolem = null;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stalled && !missingHacksaw) emitStuckChat(level, villager, gameTime);
        stalled = false;
        missingHacksaw = false;
        targetGolem = null;
        standPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Sound / particle / hand ──

    private static void playHacksawSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_HACKSAW),
                SoundSource.NEUTRAL, 0.3f, 1f);
    }

    private static void emitSparks(ServerLevel level, BlockPos pos) {
        ParticleOptions sparks = resolveSparksParticle();
        if (sparks == null) return;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;
        level.sendParticles(sparks, cx, cy, cz, 40, 0.3, 0.3, 0.3, 0.8);
        level.sendParticles(sparks, cx - 0.25, cy + 0.5, cz - 0.25, 20, 0.2, 0.2, 0.2, 0.6);
        level.sendParticles(sparks, cx + 0.25, cy - 0.5, cz + 0.25, 20, 0.2, 0.2, 0.2, 0.6);
    }

    @Nullable
    private static ParticleOptions resolveSparksParticle() {
        // BuiltInRegistries.PARTICLE_TYPE exists on both 1.21 NeoForge and
        // 1.20.1 Forge; butchery:sparks is a SimpleParticleType which
        // implements ParticleOptions, so this cast succeeds when the
        // mod is loaded and is null-safe when it isn't.
        var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                .get(PARTICLE_SPARKS_ID);
        return type instanceof ParticleOptions po ? po : null;
    }

    private static void equipHacksaw(VillagerEntityMCA villager) {
        if (WorkToolTicker.isHacksaw(villager.getMainHandItem())) return;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (WorkToolTicker.isHacksaw(stack)) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                return;
            }
        }
    }

    // ── Inventory / XP ──

    private static void deposit(ServerLevel level, VillagerEntityMCA villager, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemStack remaining = villager.getInventory().addItem(stack);
            if (!remaining.isEmpty()) {
                ItemEntity ie = new ItemEntity(level,
                        villager.getX(), villager.getY() + 0.25, villager.getZ(), remaining);
                ie.setPickUpDelay(10);
                level.addFreshEntity(ie);
            }
        }
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.GainResult result = ButcherProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
        if (result.tierUp()) {
            ButcherTradeLevelSync.syncToTier(villager, result.tierAfter());
        }
    }

    // ── Scanning ──

    /**
     * Iron golem blocks aren't listed in any building type's block
     * requirements, so {@code building.getBlocks()} doesn't index them.
     * Iterate the shop's bounding volume instead; golems are player-placed
     * and rare, so the cost is fine. Nearest golem across all carcass-
     * capable shops wins.
     */
    @Nullable
    private static BlockPos findGolemAcrossShops(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            Building building = ref.building();
            BlockPos candidate = findGolemIn(level, building);
            if (candidate == null) continue;
            double dsq = candidate.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findGolemIn(ServerLevel level, Building building) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        if (p0 == null || p1 == null) return null;
        int x0 = Math.min(p0.getX(), p1.getX());
        int x1 = Math.max(p0.getX(), p1.getX());
        int y0 = Math.min(p0.getY(), p1.getY());
        int y1 = Math.max(p0.getY(), p1.getY());
        int z0 = Math.min(p0.getZ(), p1.getZ());
        int z1 = Math.max(p0.getZ(), p1.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (GolemStateMachine.isProcessable(level, state, cursor)) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    // ── Stand / walk ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos golem) {
        // Golem block is 2 tall visually; scan cardinal neighbors at
        // golem's Y level for a walkable spot with a solid block below.
        BlockPos[] candidates = {
                golem.north(), golem.south(), golem.east(), golem.west(),
                golem.north().east(), golem.north().west(),
                golem.south().east(), golem.south().west()
        };
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        BlockPos villagerPos = villager.blockPosition();
        for (BlockPos c : candidates) {
            if (!isStandable(level, c)) continue;
            double dsq = c.distSqr(villagerPos);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = c;
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !at.canBeReplaced()) return false;
        BlockState head = level.getBlockState(pos.above());
        if (!head.isAir() && !head.canBeReplaced()) return false;
        BlockState floor = level.getBlockState(pos.below());
        return !floor.isAir();
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos target) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), WALK_SPEED, 1));
    }

    // ── Chat ──

    private static void emitNoHacksawChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        String key = "dialogue.chat.butcher_request.no_hacksaw/"
                + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(key);
        data.putLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static void emitStuckChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        String key = "dialogue.chat.butcher_request.carcass_stuck/" + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(key);
        data.putLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }
}
