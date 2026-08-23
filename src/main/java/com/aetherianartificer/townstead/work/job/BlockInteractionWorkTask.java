package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.station.StationDropOutputs;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.StationSupplies;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Executes data-authored, player-like interactions with ready blocks in an assigned worksite.
 * The executor knows only the common transaction: find a matching block, obtain one declared
 * item, run a Pheno block action, return its remainder, collect only declared outputs, and record
 * completed work. The job definition owns every domain fact, so hives, taps, troughs, orchards,
 * cauldrons, and future interactive blocks all use this one behavior.
 */
public final class BlockInteractionWorkTask extends Behavior<VillagerEntityMCA> {

    private static final int MAX_DURATION = 300;
    private static final double USE_RANGE_SQ = 5.0;
    private static final float WALK_SPEED = 0.55f;
    private static final int WORK_DELAY = 16;

    private @Nullable Candidate target;
    private @Nullable WorkJobDef.Interaction interaction;
    private Set<Long> worksite = Set.of();
    private long startedAt;
    private long useAt;

    public BlockInteractionWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    /** Generic diagnostic facts for every JSON-authored block-interaction job. */
    public static void bootstrapFeedbackSignals() {
        registerSignal("interact/has_worksite", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return WorkTaskDeclarations.permitsTask(villager, WorkTaskTypes.INTERACT)
                    && !ProfessionSites.extentOf(level, villager, profession(villager)).isEmpty();
        });
        registerSignal("interact/has_target", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, false) != null;
        });
        registerSignal("interact/has_ready_target", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, true) != null;
        });
        registerSignal("interact/has_input", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            Candidate candidate = findTarget(level, villager, true);
            if (candidate == null) return false;
            for (WorkJobDef.Interaction option : candidate.definition().interactions()) {
                if (hasMatching(villager.getInventory(), option)) return true;
            }
            return false;
        });
    }

    private static void registerSignal(String path,
                                       java.util.function.Predicate<VillagerEntityMCA> signal) {
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("townstead_work", path);
        //?} else {
        /*ResourceLocation id = new ResourceLocation("townstead_work", path);
        *///?}
        com.aetherianartificer.townstead.work.feedback.WorkFeedbackSignals.register(id, signal);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return WorkTaskDeclarations.permitsTask(villager, WorkTaskTypes.INTERACT)
                && findTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = findTarget(level, villager);
        interaction = null;
        worksite = Set.of();
        startedAt = gameTime;
        useAt = gameTime + WORK_DELAY;
        if (target == null) return;
        worksite = ProfessionSites.extentOf(level, villager, profession(villager));
        interaction = selectInteraction(level, villager, target.definition(), target.pos(), worksite);
        if (interaction == null) {
            target = null;
            return;
        }
        setWalkTarget(villager, target.pos());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return target != null && interaction != null
                && gameTime - startedAt <= MAX_DURATION
                && target.task().available(villager)
                && target.definition().blocks().contains(blockId(level, target.pos()))
                && target.task().allowsBlock(blockId(level, target.pos()))
                && target.definition().ready(level, target.pos())
                && hasMatching(villager.getInventory(), interaction);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null || interaction == null) return;
        BlockPos pos = target.pos();
        villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > USE_RANGE_SQ) {
            setWalkTarget(villager, pos);
            useAt = gameTime + WORK_DELAY;
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (gameTime < useAt) return;
        perform(level, villager, target, interaction, worksite, gameTime);
        target = null;
        interaction = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        interaction = null;
        worksite = Set.of();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static @Nullable Candidate findTarget(ServerLevel level, VillagerEntityMCA villager) {
        return findTarget(level, villager, true);
    }

    private static @Nullable Candidate findTarget(ServerLevel level, VillagerEntityMCA villager,
                                                   boolean requireReady) {
        ProfessionDef profession = profession(villager);
        Set<Long> extent = ProfessionSites.extentOf(level, villager, profession);
        if (extent.isEmpty()) return null;
        List<WorkTaskDef> declarations = WorkTaskDeclarations.declared(villager, WorkTaskTypes.INTERACT);
        if (declarations == null || declarations.isEmpty()) return null;

        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.BLOCK_INTERACTION)) {
            WorkTaskDef task = declaration(declarations, job.task());
            if (task == null) continue;
            WorkJobDef.BlockTarget target = job.target();
            if (target == null) continue;
            for (long packed : extent) {
                BlockPos pos = BlockPos.of(packed);
                ResourceLocation block = blockId(level, pos);
                if (!target.blocks().contains(block) || !task.allowsBlock(block)
                        || (requireReady && !target.ready(level, pos))) continue;
                double distance = villager.distanceToSqr(Vec3.atCenterOf(pos));
                if (distance < bestDistance) {
                    best = new Candidate(job, target, task, pos.immutable());
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static @Nullable WorkTaskDef declaration(List<WorkTaskDef> declarations,
                                                     ResourceLocation type) {
        for (WorkTaskDef task : declarations) if (task.type().equals(type)) return task;
        return null;
    }

    private static @Nullable WorkJobDef.Interaction selectInteraction(
            ServerLevel level, VillagerEntityMCA villager, WorkJobDef.BlockTarget target,
            BlockPos center, Set<Long> extent) {
        for (WorkJobDef.Interaction candidate : target.interactions()) {
            if (hasMatching(villager.getInventory(), candidate)) return candidate;
        }
        for (WorkJobDef.Interaction candidate : target.interactions()) {
            StationSupplies.pullMatching(level, villager, candidate::matches, 1, center, extent);
            if (hasMatching(villager.getInventory(), candidate)) return candidate;
        }
        return null;
    }

    private static boolean perform(ServerLevel level, VillagerEntityMCA villager,
                                   Candidate target, WorkJobDef.Interaction interaction,
                                   Set<Long> extent, long gameTime) {
        ItemStack supplied = takeMatching(villager.getInventory(), interaction);
        if (supplied.isEmpty()) return false;

        BlockActionContext context = new BlockActionContext(level, target.pos(), villager)
                .withItemRole("item", supplied);
        interaction.action().run(context);

        List<ItemStack> returned = new ArrayList<>();
        returned.add(context.itemRole("item").copy());
        returned.addAll(context.returnedItems());
        if (context.succeeded()) {
            returned.addAll(StationDropOutputs.collectWithinWorksite(
                    level, target.pos(), interaction.outputIds(), extent));
        }

        int produced = 0;
        ResourceLocation firstOutput = null;
        for (ItemStack stack : returned) {
            if (stack.isEmpty()) continue;
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (context.succeeded() && interaction.outputIds().contains(item)) {
                if (firstOutput == null) firstOutput = item;
                produced += stack.getCount();
                storeOutput(level, villager, target.pos(), extent, stack);
            } else {
                StationProtocols.giveBack(villager, stack);
            }
        }
        if (!context.succeeded() || produced <= 0 || firstOutput == null) return false;

        villager.swing(InteractionHand.MAIN_HAND, true);
        ResourceLocation career = ProfessionDefs.canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        CareerProgression.completeWork(villager, career, Math.max(1, interaction.xp()), gameTime,
                target.job().activityKey(), firstOutput, "item", produced,
                java.util.Map.of("job", target.job().id().toString()));
        return true;
    }

    private static void storeOutput(ServerLevel level, VillagerEntityMCA villager, BlockPos center,
                                    Set<Long> extent, ItemStack stack) {
        WorkIngredients.storeOutputInWorksiteStorage(level, villager, stack, center, extent);
        if (!stack.isEmpty()) StationProtocols.giveBack(villager, stack);
    }

    private static boolean hasMatching(SimpleContainer inventory, WorkJobDef.Interaction interaction) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (interaction.matches(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private static ItemStack takeMatching(SimpleContainer inventory,
                                          WorkJobDef.Interaction interaction) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (interaction.matches(stack)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    private static @Nullable ProfessionDef profession(VillagerEntityMCA villager) {
        return ProfessionDefs.byId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
    }

    private static ResourceLocation blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }

    private record Candidate(WorkJobDef job, WorkJobDef.BlockTarget definition,
                             WorkTaskDef task, BlockPos pos) {}
}
