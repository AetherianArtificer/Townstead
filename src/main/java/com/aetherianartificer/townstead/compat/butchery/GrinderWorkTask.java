package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.hunger.ButcherSupplyManager;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
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
 * Butcher villager operates {@code butchery:meat_grinder} the same way a
 * player would through the mod's GUI: load the correct slots, let the mod's
 * own scheduled tick count {@code craftingProgress} up to 150, then collect
 * the output. One grinder action per task session (load OR collect OR wait).
 *
 * <p>Priority order for recipes: blood sausage > sausage > lamb mince >
 * beef mince > meat scraps. The mod itself runs whichever recipe its
 * procedure matches against the loaded slots, so the villager's job is
 * simply to stock the right combination.
 *
 * <p>Raw sausage and raw blood sausage output flows back through the
 * smoker via {@link com.aetherianartificer.townstead.hunger.ButcherWorkTask}
 * naturally — those items have {@code minecraft:smoking} recipes in
 * Butchery's data, so our existing {@code isRawInput} picks them up as
 * smoker inputs with no extra plumbing.
 */
public class GrinderWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 400;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int STAND_SEARCH_RADIUS = 2;
    private static final int ACT_COOLDOWN_TICKS = 20;

    //? if >=1.21 {
    private static final ResourceLocation SOUND_STONE_PLACE =
            ResourceLocation.parse("block.stone.place");
    private static final ResourceLocation SOUND_ITEM_PICKUP =
            ResourceLocation.parse("entity.item.pickup");
    //?} else {
    /*private static final ResourceLocation SOUND_STONE_PLACE =
            new ResourceLocation("block.stone.place");
    private static final ResourceLocation SOUND_ITEM_PICKUP =
            new ResourceLocation("entity.item.pickup");
    *///?}

    private enum Phase { PATH, ACT }

    private enum Action { COLLECT, LOAD }

    @Nullable private BlockPos grinderPos;
    @Nullable private BlockPos standPos;
    @Nullable private Action action;
    @Nullable private GrinderStateMachine.Recipe pendingRecipe;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextActTick;
    private boolean stalled;

    public GrinderWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        return findGrinderWithWork(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        grinderPos = findGrinderWithWork(level, villager);
        if (grinderPos == null) return;
        // Re-plan with storage pulls allowed. If the cheap check saw a
        // recipe by pinning on inventory meat, this is where we pull the
        // casings from nearby chests so the staging call can place them.
        Plan plan = planAndStage(level, villager, grinderPos);
        if (plan == null) {
            grinderPos = null;
            return;
        }
        action = plan.action;
        pendingRecipe = plan.recipe;
        standPos = findStandPos(level, villager, grinderPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextActTick = gameTime + ACT_COOLDOWN_TICKS;
        stalled = false;
        setWalkTarget(villager, standPos != null ? standPos : grinderPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (grinderPos == null || action == null) return false;
        BlockState state = level.getBlockState(grinderPos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!GrinderStateMachine.MEAT_GRINDER_ID.equals(id)) return false;
        if (gameTime - startedTick > MAX_DURATION) { stalled = true; return false; }
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) {
            stalled = true;
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (grinderPos == null || action == null) return;
        villager.getLookControl().setLookAt(
                grinderPos.getX() + 0.5, grinderPos.getY() + 0.6, grinderPos.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : grinderPos;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.ACT;
                nextActTick = gameTime + ACT_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, anchor);
            }
            return;
        }

        if (gameTime < nextActTick) return;
        switch (action) {
            case COLLECT -> executeCollect(level, villager, gameTime);
            case LOAD -> executeLoad(level, villager, gameTime);
        }
        // Whichever branch ran above has done its single action and left
        // targetXxx null to signal completion.
        grinderPos = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stalled) emitStuckChat(level, villager, gameTime);
        stalled = false;
        grinderPos = null;
        standPos = null;
        action = null;
        pendingRecipe = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Actions ──

    private void executeCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Container grinder = GrinderStateMachine.container(level, grinderPos);
        if (grinder == null) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, grinderPos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_ITEM_PICKUP),
                SoundSource.NEUTRAL, 0.4f, 1.2f);

        collectSlot(grinder, GrinderStateMachine.SLOT_OUTPUT, level, villager, gameTime);
        collectSlot(grinder, GrinderStateMachine.SLOT_BOTTLE_RETURN, level, villager, gameTime);
        grinder.setChanged();
        awardXp(villager, 1, gameTime);
    }

    private void collectSlot(Container grinder, int slot, ServerLevel level,
                             VillagerEntityMCA villager, long gameTime) {
        ItemStack stack = grinder.getItem(slot);
        if (stack.isEmpty()) return;
        ItemStack moving = stack.copy();
        grinder.setItem(slot, ItemStack.EMPTY);
        ItemStack remaining = villager.getInventory().addItem(moving);
        if (!remaining.isEmpty()) {
            BlockPos dropAt = villager.blockPosition();
            ItemEntity ie = new ItemEntity(level,
                    dropAt.getX() + 0.5, dropAt.getY() + 0.25, dropAt.getZ() + 0.5, remaining);
            ie.setPickUpDelay(10);
            level.addFreshEntity(ie);
        }
        // Best-effort offload: the carcass pipeline already knows how to
        // route mince / scraps to nearby storage.
        ButcherSupplyManager.offloadOutput(level, villager, grinderPos);
    }

    private void executeLoad(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (pendingRecipe == null) return;
        Container grinder = GrinderStateMachine.container(level, grinderPos);
        if (grinder == null) return;

        villager.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, grinderPos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_STONE_PLACE),
                SoundSource.NEUTRAL, 0.6f, 1.2f);

        boolean loaded = false;
        loaded |= moveOneInto(grinder, GrinderStateMachine.SLOT_INPUT, villager,
                stack -> GrinderStateMachine.isInputForRecipe(stack, pendingRecipe));
        if (pendingRecipe.requiresCasings) {
            loaded |= moveOneInto(grinder, GrinderStateMachine.SLOT_INTESTINES, villager,
                    GrinderStateMachine::isIntestines);
            loaded |= moveOneInto(grinder, GrinderStateMachine.SLOT_ATTACHMENT, villager,
                    GrinderStateMachine::isSausageAttachment);
        }
        if (pendingRecipe == GrinderStateMachine.Recipe.BLOOD_SAUSAGE) {
            loaded |= moveOneInto(grinder, GrinderStateMachine.SLOT_BLOOD, villager,
                    GrinderStateMachine::isBloodBottle);
        }
        if (loaded) {
            grinder.setChanged();
            // Safety: nudge the block to ensure the mod's scheduled tick is
            // queued. Normally the block self-reschedules every tick, but
            // after chunk reloads a stale grinder may be dormant.
            level.scheduleTick(grinderPos, level.getBlockState(grinderPos).getBlock(), 1);
        }
    }

    /**
     * Moves one matching item from the villager's inventory into the given
     * grinder slot. Skips if the slot is already populated (recipe procedure
     * checks exact item identity, not stack size, so one item per slot per
     * batch is all we need to stage).
     */
    private static boolean moveOneInto(Container grinder, int slot,
                                       VillagerEntityMCA villager,
                                       java.util.function.Predicate<ItemStack> matcher) {
        if (!grinder.getItem(slot).isEmpty() && matcher.test(grinder.getItem(slot))) return false;
        if (!grinder.getItem(slot).isEmpty()) return false;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!matcher.test(stack)) continue;
            //? if >=1.21 {
            ItemStack one = stack.copyWithCount(1);
            //?} else {
            /*ItemStack one = stack.copy(); one.setCount(1);
            *///?}
            grinder.setItem(slot, one);
            stack.shrink(1);
            return true;
        }
        return false;
    }

    // ── Scanning / planning ──

    private record Plan(Action action, @Nullable GrinderStateMachine.Recipe recipe) {}

    /**
     * Return the nearest grinder that has an actionable state based on a
     * read-only snapshot of inventory + grinder state. Deliberately does
     * NOT pull from storage here so checkExtraStartConditions stays cheap.
     * Storage pulls happen in {@link #planAndStage} during start().
     */
    @Nullable
    private static BlockPos findGrinderWithWork(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            Building building = ref.building();
            List<BlockPos> grinders = building.getBlocks().get(GrinderStateMachine.MEAT_GRINDER_ID);
            if (grinders == null) continue;
            for (BlockPos gp : grinders) {
                if (planReadOnly(level, villager, gp) == null) continue;
                double dsq = gp.distSqr(origin);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = gp.immutable();
                }
            }
        }
        return best;
    }

    /**
     * Cheap read-only plan: "does this grinder have something actionable
     * right now given the villager's current inventory?" Used by the
     * brain's fast start-condition check.
     */
    @Nullable
    private static Plan planReadOnly(ServerLevel level, VillagerEntityMCA villager, BlockPos gp) {
        Container grinder = GrinderStateMachine.container(level, gp);
        if (grinder == null) return null;
        if (!grinder.getItem(GrinderStateMachine.SLOT_OUTPUT).isEmpty()
                || !grinder.getItem(GrinderStateMachine.SLOT_BOTTLE_RETURN).isEmpty()) {
            return new Plan(Action.COLLECT, null);
        }
        if (!grinder.getItem(GrinderStateMachine.SLOT_INPUT).isEmpty()) return null;
        SimpleContainer inv = villager.getInventory();
        // Fully loaded inventory beats "carries input only"; check the
        // complete-recipe case first so we prefer recipes the villager can
        // stage end-to-end without any pull.
        for (GrinderStateMachine.Recipe r : GrinderStateMachine.Recipe.values()) {
            if (ButcherSupplyManager.hasRecipeInputs(inv, r)) return new Plan(Action.LOAD, r);
        }
        // Fallback: villager at least carries the raw meat (likely from
        // their own carcass work). Walking over is still worth it because
        // start() will try to pull missing casings from nearby storage.
        for (GrinderStateMachine.Recipe r : GrinderStateMachine.Recipe.values()) {
            if (anyMatches(inv, s -> GrinderStateMachine.isInputForRecipe(s, r))) {
                return new Plan(Action.LOAD, r);
            }
        }
        return null;
    }

    /**
     * Side-effectful plan called during start(): may pull missing
     * ingredients from nearby storage so we can commit to a recipe on
     * this trip. Returns null if nothing useful is possible even after
     * pulls.
     */
    @Nullable
    private static Plan planAndStage(ServerLevel level, VillagerEntityMCA villager, BlockPos gp) {
        Container grinder = GrinderStateMachine.container(level, gp);
        if (grinder == null) return null;
        if (!grinder.getItem(GrinderStateMachine.SLOT_OUTPUT).isEmpty()
                || !grinder.getItem(GrinderStateMachine.SLOT_BOTTLE_RETURN).isEmpty()) {
            return new Plan(Action.COLLECT, null);
        }
        if (!grinder.getItem(GrinderStateMachine.SLOT_INPUT).isEmpty()) return null;
        SimpleContainer inv = villager.getInventory();
        for (GrinderStateMachine.Recipe r : GrinderStateMachine.Recipe.values()) {
            if (tryPrepareRecipe(level, villager, inv, gp, r)) {
                return new Plan(Action.LOAD, r);
            }
        }
        return null;
    }

    private static boolean tryPrepareRecipe(
            ServerLevel level,
            VillagerEntityMCA villager,
            SimpleContainer inv,
            BlockPos grinderPos,
            GrinderStateMachine.Recipe recipe
    ) {
        if (ButcherSupplyManager.hasRecipeInputs(inv, recipe)) return true;
        // Missing at least one ingredient; try to pull the specific pieces.
        if (recipe.requiresCasings) {
            if (!anyMatches(inv, GrinderStateMachine::isIntestines)
                    && !ButcherSupplyManager.pullIntestines(level, villager, grinderPos)) return false;
            if (!anyMatches(inv, GrinderStateMachine::isSausageAttachment)
                    && !ButcherSupplyManager.pullSausageAttachment(level, villager, grinderPos)) return false;
        }
        if (recipe == GrinderStateMachine.Recipe.BLOOD_SAUSAGE
                && !anyMatches(inv, GrinderStateMachine::isBloodBottle)
                && !ButcherSupplyManager.pullBloodBottle(level, villager, grinderPos)) return false;
        if (!anyMatches(inv, s -> GrinderStateMachine.isInputForRecipe(s, recipe))
                && !ButcherSupplyManager.pullGrinderInput(level, villager, grinderPos, recipe)) return false;
        return ButcherSupplyManager.hasRecipeInputs(inv, recipe);
    }

    private static boolean anyMatches(SimpleContainer inv,
                                      java.util.function.Predicate<ItemStack> matcher) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return true;
        }
        return false;
    }

    // ── Stand + walk helpers ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos grinder) {
        BlockPos[] candidates = {
                grinder.north(), grinder.south(), grinder.east(), grinder.west(),
                grinder.north().east(), grinder.north().west(),
                grinder.south().east(), grinder.south().west()
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

    // ── Chat + XP ──

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

    private static void emitStuckChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        // Reuse the existing carcass_stuck line until grinder-specific chat lands.
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
