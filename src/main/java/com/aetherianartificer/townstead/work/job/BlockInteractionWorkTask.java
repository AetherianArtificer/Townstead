package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
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
        registerSignal("block_interaction/has_worksite", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return declaresBlockInteraction(villager)
                    && !ProfessionSites.extentOf(level, villager, profession(villager)).isEmpty();
        });
        registerSignal("block_interaction/has_target", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, false, false, true) != null;
        });
        registerSignal("block_interaction/has_ready_target", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, true, false, true) != null;
        });
        registerSignal("block_interaction/has_input", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, true, true, true) != null;
        });
    }

    private static boolean declaresBlockInteraction(VillagerEntityMCA villager) {
        for (WorkTaskDef task : WorkTaskDeclarations.all(villager)) {
            for (WorkJobDef job : WorkJobs.forTask(task.type())) {
                if (WorkJobDef.BLOCK_INTERACTION.equals(job.type())) return true;
            }
        }
        return false;
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
        return findTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        interaction = null;
        worksite = Set.of();
        startedAt = gameTime;
        useAt = gameTime + WORK_DELAY;
        for (Candidate candidate : findTargets(level, villager, null, true, false, true)) {
            WorkJobDef.Interaction selected = selectInteraction(
                    level, villager, candidate.definition(), candidate.pos(), candidate.extent());
            if (selected == null) continue;
            target = candidate;
            interaction = selected;
            worksite = candidate.extent();
            break;
        }
        if (target == null) return;
        setWalkTarget(villager, target.pos());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return target != null && interaction != null
                && gameTime - startedAt <= MAX_DURATION
                && target.task().available(villager)
                && target.definition().matches(level, target.pos())
                && target.task().allowsBlock(blockId(level, target.pos()))
                && target.definition().ready(level, target.pos())
                && interaction.ready(level, target.pos())
                && hasMatching(level, target.pos(), villager.getInventory(), interaction);
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
        return findTarget(level, villager, null, true, false, true);
    }

    private static @Nullable Candidate findTarget(ServerLevel level, VillagerEntityMCA villager,
                                                   @Nullable ResourceLocation onlyTask,
                                                   boolean requireReady, boolean requireInput,
                                                   boolean respectOrders) {
        List<Candidate> candidates = findTargets(
                level, villager, onlyTask, requireReady, requireInput, respectOrders);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<Candidate> findTargets(ServerLevel level, VillagerEntityMCA villager,
                                               @Nullable ResourceLocation onlyTask,
                                               boolean requireReady, boolean requireInput,
                                               boolean respectOrders) {
        ProfessionDef profession = profession(villager);
        List<WorkTaskDef> declarations = WorkTaskDeclarations.all(villager);
        if (declarations.isEmpty()) return List.of();

        List<Candidate> ordered = new ArrayList<>();
        for (WorkTaskDef task : declarations) {
            if (onlyTask != null && !onlyTask.equals(task.type())) continue;
            if (respectOrders && !com.aetherianartificer.townstead.work.order.WorksiteOrders
                    .mayStart(level, villager, task.type())) continue;
            List<Candidate> taskCandidates = new ArrayList<>();
            for (WorkJobDef job : WorkJobs.forType(WorkJobDef.BLOCK_INTERACTION)) {
                if (!job.task().equals(task.type())) continue;
                WorkJobDef.BlockTarget target = job.target();
                if (target == null) continue;
                for (Set<Long> extent : targetExtents(level, villager, profession, target)) {
                    for (long packed : extent) {
                        BlockPos pos = BlockPos.of(packed);
                        ResourceLocation block = blockId(level, pos);
                        if (!target.matches(level, pos) || !task.allowsBlock(block)
                                || (requireReady && (!target.ready(level, pos)
                                || !hasReadyInteraction(level, pos, target)))
                                || (requireInput && !hasMatchingInteraction(
                                level, pos, villager.getInventory(), target))) continue;
                        taskCandidates.add(new Candidate(job, target, task, pos.immutable(), extent));
                    }
                }
            }
            taskCandidates.sort(java.util.Comparator.comparingDouble(candidate ->
                    villager.distanceToSqr(Vec3.atCenterOf(candidate.pos()))));
            ordered.addAll(taskCandidates);
        }
        return List.copyOf(ordered);
    }

    /** Read-only availability probe used by worksite orders and other task engines. */
    public static boolean hasWork(ServerLevel level, VillagerEntityMCA villager,
                                  ResourceLocation task) {
        return findTarget(level, villager, task, true, false, false) != null;
    }

    private static boolean hasReadyInteraction(ServerLevel level, BlockPos pos,
                                               WorkJobDef.BlockTarget target) {
        for (WorkJobDef.Interaction option : target.interactions()) {
            if (option.ready(level, pos)) return true;
        }
        return false;
    }

    private static boolean hasMatchingInteraction(ServerLevel level, BlockPos pos,
                                                   SimpleContainer inventory,
                                                   WorkJobDef.BlockTarget target) {
        for (WorkJobDef.Interaction option : target.interactions()) {
            if (hasMatching(level, pos, inventory, option)) return true;
        }
        return false;
    }

    private static @Nullable WorkJobDef.Interaction selectInteraction(
            ServerLevel level, VillagerEntityMCA villager, WorkJobDef.BlockTarget target,
            BlockPos center, Set<Long> extent) {
        for (WorkJobDef.Interaction candidate : target.interactions()) {
            if (hasMatching(level, center, villager.getInventory(), candidate)) return candidate;
        }
        for (WorkJobDef.Interaction candidate : target.interactions()) {
            if (!candidate.ready(level, center)) continue;
            StationSupplies.pullMatching(level, villager,
                    stack -> candidate.matches(level, center, stack), 1, center, extent);
            if (hasMatching(level, center, villager.getInventory(), candidate)) return candidate;
        }
        return null;
    }

    private static boolean perform(ServerLevel level, VillagerEntityMCA villager,
                                   Candidate target, WorkJobDef.Interaction interaction,
                                   Set<Long> extent, long gameTime) {
        ItemStack supplied = takeMatching(level, target.pos(), villager.getInventory(), interaction);
        if (interaction.requiresItem() && supplied.isEmpty()) return false;

        BlockActionContext context = new BlockActionContext(level, target.pos(), villager)
                .withItemRole("item", supplied);
        interaction.action().run(context);

        ItemStack itemRemainder = context.itemRole("item").copy();
        List<ItemStack> outputs = new ArrayList<>(context.returnedItems());
        if (context.succeeded()) {
            outputs.addAll(StationDropOutputs.collectWithinWorksite(
                    level, target.pos(), interaction.outputIds(), extent));
        }

        int produced = 0;
        ResourceLocation firstOutput = null;
        if (!itemRemainder.isEmpty()) StationProtocols.giveBack(villager, itemRemainder);
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) continue;
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (context.succeeded() && (interaction.outputIds().isEmpty()
                    || interaction.outputIds().contains(item))) {
                if (firstOutput == null) firstOutput = item;
                produced += stack.getCount();
                storeOutput(level, villager, target.pos(), extent, stack);
            } else {
                StationProtocols.giveBack(villager, stack);
            }
        }
        if (!context.succeeded()) return false;

        villager.swing(InteractionHand.MAIN_HAND, true);
        ResourceLocation career = ProfessionDefs.canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        CareerProgression.completeWork(villager, career, Math.max(1, interaction.xp()), gameTime,
                target.job().activityKey(), firstOutput, "item", Math.max(1, produced),
                java.util.Map.of("job", target.job().id().toString()));
        return true;
    }

    private static void storeOutput(ServerLevel level, VillagerEntityMCA villager, BlockPos center,
                                    Set<Long> extent, ItemStack stack) {
        WorkIngredients.storeOutputInWorksiteStorage(level, villager, stack, center, extent);
        if (!stack.isEmpty()) StationProtocols.giveBack(villager, stack);
    }

    private static boolean hasMatching(ServerLevel level, BlockPos pos,
                                       SimpleContainer inventory, WorkJobDef.Interaction interaction) {
        if (!interaction.requiresItem()) return interaction.ready(level, pos);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (interaction.matches(level, pos, inventory.getItem(slot))) return true;
        }
        return false;
    }

    private static ItemStack takeMatching(ServerLevel level, BlockPos pos, SimpleContainer inventory,
                                          WorkJobDef.Interaction interaction) {
        if (!interaction.requiresItem()) return ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (interaction.matches(level, pos, stack)) return stack.split(1);
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

    /** Work areas explicitly named by a Job, or the worker's assigned site when none are named. */
    private static List<Set<Long>> targetExtents(ServerLevel level, VillagerEntityMCA villager,
                                                  @Nullable ProfessionDef profession,
                                                  WorkJobDef.BlockTarget target) {
        if (target.buildings().isEmpty()) {
            Set<Long> assigned = ProfessionSites.extentOf(level, villager, profession);
            return assigned.isEmpty() ? List.of() : List.of(assigned);
        }
        var village = ProfessionCapacity.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        List<Set<Long>> result = new ArrayList<>();
        for (var building : McaBuildings.all(village.get())) {
            if (!building.isComplete() || !target.matchesBuilding(building.getType())) continue;
            Set<Long> extent = com.aetherianartificer.townstead.work.WorkSiteBounds
                    .workArea(level, building);
            if (!extent.isEmpty()) result.add(extent);
        }
        return List.copyOf(result);
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }

    private record Candidate(WorkJobDef job, WorkJobDef.BlockTarget definition,
                             WorkTaskDef task, BlockPos pos, Set<Long> extent) {}
}
