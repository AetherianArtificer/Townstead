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
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
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

    public enum RequirementState {
        NOT_APPLICABLE, SATISFIED, PROVISIONABLE, MISSING_SOURCE, MISSING_INPUT
    }

    private static final int MAX_DURATION = 300;
    private static final double USE_RANGE_SQ = 5.0;
    private static final float WALK_SPEED = 0.55f;
    private static final int WORK_DELAY = 16;

    private @Nullable Candidate target;
    private @Nullable WorkJobDef.Interaction interaction;
    private List<RequirementSession> requirements = List.of();
    private int requirementIndex;
    private Phase phase = Phase.PREPARE;
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
            return findTarget(level, villager, null, false, false, false, true) != null;
        });
        registerSignal("block_interaction/has_ready_target", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, true, false, false, true) != null;
        });
        registerSignal("block_interaction/has_ready_interaction", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, true, true, false, true) != null;
        });
        registerSignal("block_interaction/has_input", villager -> {
            if (!(villager.level() instanceof ServerLevel level)) return false;
            return findTarget(level, villager, null, true, true, true, true) != null;
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
        for (Candidate candidate : findTargets(level, villager, null, true, true, false, true)) {
            if (hasAvailableInteractionInput(level, villager, candidate.pos(), candidate.extent(),
                    candidate.definition())
                    && requirementsAvailable(level, villager, candidate)) return true;
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        interaction = null;
        worksite = Set.of();
        requirements = List.of();
        requirementIndex = 0;
        phase = Phase.PREPARE;
        startedAt = gameTime;
        useAt = gameTime + WORK_DELAY;
        for (Candidate candidate : findTargets(level, villager, null, true, true, false, true)) {
            WorkJobDef.Interaction selected = selectInteraction(
                    level, villager, candidate.definition(), candidate.pos(), candidate.extent());
            if (selected == null) continue;
            List<RequirementSession> planned = planRequirements(level, villager, candidate);
            if (planned == null) continue;
            target = candidate;
            interaction = selected;
            requirements = new ArrayList<>(planned);
            worksite = candidate.extent();
            break;
        }
        if (target == null) return;
        moveForCurrentPhase(villager, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null || interaction == null || gameTime - startedAt > MAX_DURATION) return false;
        if (phase == Phase.CLEANUP) return true;
        return target.task().available(villager)
                && target.definition().matches(level, target.pos())
                && target.task().allowsBlock(blockId(level, target.pos()))
                && target.definition().ready(level, target.pos())
                && interaction.ready(level, target.pos())
                && hasMatching(level, target.pos(), villager.getInventory(), interaction);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null || interaction == null) return;
        renewRequirements(level, villager);
        if (phase == Phase.PREPARE) {
            tickPreparation(level, villager, gameTime);
            return;
        }
        if (phase == Phase.CLEANUP) {
            tickCleanup(level, villager, gameTime);
            return;
        }
        BlockPos pos = target.pos();
        villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > USE_RANGE_SQ) {
            setWalkTarget(villager, pos);
            useAt = gameTime + WORK_DELAY;
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (gameTime < useAt) return;
        for (WorkJobDef.ManagedRequirement requirement : target.definition().requirements()) {
            if (!requirement.satisfied(level, target.pos())) {
                target = null;
                return;
            }
        }
        if (!perform(level, villager, target, interaction, worksite, gameTime)) {
            target = null;
            return;
        }
        phase = Phase.CLEANUP;
        requirementIndex = requirements.size() - 1;
        moveForCurrentPhase(villager, gameTime);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseAllRequirements(level, villager);
        target = null;
        interaction = null;
        requirements = List.of();
        worksite = Set.of();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static @Nullable Candidate findTarget(ServerLevel level, VillagerEntityMCA villager) {
        return findTarget(level, villager, null, true, true, false, true);
    }

    private static @Nullable Candidate findTarget(ServerLevel level, VillagerEntityMCA villager,
                                                   @Nullable ResourceLocation onlyTask,
                                                   boolean requireTargetReady,
                                                   boolean requireInteractionReady,
                                                   boolean requireInput,
                                                   boolean respectOrders) {
        List<Candidate> candidates = findTargets(
                level, villager, onlyTask, requireTargetReady, requireInteractionReady,
                requireInput, respectOrders);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<Candidate> findTargets(ServerLevel level, VillagerEntityMCA villager,
                                               @Nullable ResourceLocation onlyTask,
                                               boolean requireTargetReady,
                                               boolean requireInteractionReady,
                                               boolean requireInput,
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
                                || (requireTargetReady && !target.ready(level, pos))
                                || (requireInteractionReady
                                && !hasReadyInteraction(level, pos, target))
                                || (requireInput && !hasAvailableInteractionInput(
                                level, villager, pos, extent, target))) continue;
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
        for (Candidate candidate : findTargets(level, villager, task, true, true, false, false)) {
            if (requirementsAvailable(level, villager, candidate)) return true;
        }
        return false;
    }

    private static boolean requirementsAvailable(ServerLevel level, VillagerEntityMCA villager,
                                                  Candidate candidate) {
        for (WorkJobDef.ManagedRequirement requirement : candidate.definition().requirements()) {
            if (requirement.satisfied(level, candidate.pos())) continue;
            WorkJobDef.Provision provision = requirement.provision();
            if (provision == null) return false;
            boolean available = false;
            for (BlockPos source : provision.at().select(SelectorContext.ofBlock(
                    level, candidate.pos(), villager))) {
                if (provisionInputAvailable(level, villager, source, candidate.extent(), provision)) {
                    available = true;
                    break;
                }
            }
            if (!available) return false;
        }
        return true;
    }

    /** Exact authored requirement state for profession feedback; never infers one condition from another. */
    public static RequirementState requirementState(ServerLevel level, VillagerEntityMCA villager,
                                                    ResourceLocation jobId, String requirementId) {
        for (Candidate candidate : findTargets(level, villager, null, true, false, false, true)) {
            if (!candidate.job().id().equals(jobId)) continue;
            for (WorkJobDef.ManagedRequirement requirement : candidate.definition().requirements()) {
                if (!requirement.id().equals(requirementId)) continue;
                if (requirement.satisfied(level, candidate.pos())) return RequirementState.SATISFIED;
                WorkJobDef.Provision provision = requirement.provision();
                if (provision == null) return RequirementState.MISSING_SOURCE;
                List<BlockPos> sources = provision.at().select(
                        SelectorContext.ofBlock(level, candidate.pos(), villager));
                if (sources.isEmpty()) return RequirementState.MISSING_SOURCE;
                for (BlockPos source : sources) {
                    if (provisionInputAvailable(level, villager, source, candidate.extent(), provision)) {
                        return RequirementState.PROVISIONABLE;
                    }
                }
                return RequirementState.MISSING_INPUT;
            }
        }
        return RequirementState.NOT_APPLICABLE;
    }

    private static boolean provisionInputAvailable(ServerLevel level, VillagerEntityMCA villager,
                                                    BlockPos source, Set<Long> extent,
                                                    WorkJobDef.Provision provision) {
        if (!provision.requiresItem()) {
            return provision.start().canRun(new BlockActionContext(level, source, villager));
        }
        return WorkIngredients.matchingToolAvailable(level, villager,
                stack -> provision.matches(level, source, stack), source, extent);
    }

    private static @Nullable List<RequirementSession> planRequirements(
            ServerLevel level, VillagerEntityMCA villager, Candidate candidate) {
        List<RequirementSession> result = new ArrayList<>();
        ManagedRequirementLeases ledger = ManagedRequirementLeases.get(level.getServer());
        for (WorkJobDef.ManagedRequirement requirement : candidate.definition().requirements()) {
            WorkJobDef.Provision provision = requirement.provision();
            List<BlockPos> sources = provision == null ? List.of() : provision.at().select(
                    SelectorContext.ofBlock(level, candidate.pos(), villager));
            if (requirement.satisfied(level, candidate.pos())) {
                if (provision != null) {
                    ManagedRequirementLeases.Key lease = ledger.acquireExisting(level,
                            candidate.job().id(), requirement.id(), sources, villager.getUUID());
                    if (lease != null) result.add(new RequirementSession(
                            requirement, BlockPos.of(lease.source()), lease, false));
                }
                continue;
            }
            if (provision == null) {
                releasePlanned(level, villager, result);
                return null;
            }
            BlockPos selected = null;
            for (BlockPos source : sources) {
                if (provisionInputAvailable(level, villager, source, candidate.extent(), provision)) {
                    selected = source.immutable();
                    break;
                }
            }
            if (selected == null) {
                releasePlanned(level, villager, result);
                return null;
            }
            result.add(new RequirementSession(requirement, selected, null, true));
        }
        return List.copyOf(result);
    }

    private static void releasePlanned(ServerLevel level, VillagerEntityMCA villager,
                                       List<RequirementSession> planned) {
        ManagedRequirementLeases ledger = ManagedRequirementLeases.get(level.getServer());
        for (RequirementSession session : planned) {
            if (session.lease != null) ledger.release(level.getServer(), session.lease, villager.getUUID());
        }
    }

    private void tickPreparation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        while (requirementIndex < requirements.size()
                && !requirements.get(requirementIndex).needsStart) requirementIndex++;
        if (requirementIndex >= requirements.size()) {
            phase = Phase.WORK;
            moveForCurrentPhase(villager, gameTime);
            return;
        }
        RequirementSession session = requirements.get(requirementIndex);
        if (!approach(villager, session.source, gameTime)) return;
        if (!startProvision(level, villager, session)) {
            target = null;
            return;
        }
        WorkJobDef.Provision provision = session.requirement.provision();
        if (target == null || provision == null
                || !session.requirement.satisfied(level, target.pos())
                || !provision.sourceManaged(level, session.source)) {
            if (provision != null && provision.sourceManaged(level, session.source)) {
                provision.stop().run(new BlockActionContext(level, session.source, villager));
            }
            target = null;
            return;
        }
        ManagedRequirementLeases.Key lease = ManagedRequirementLeases.get(level.getServer())
                .acquireNew(level, target.job().id(), session.requirement.id(), target.pos(),
                        session.source, villager.getUUID());
        requirements.set(requirementIndex, new RequirementSession(
                session.requirement, session.source, lease, false));
        requirementIndex++;
        moveForCurrentPhase(villager, gameTime);
    }

    private boolean startProvision(ServerLevel level, VillagerEntityMCA villager,
                                   RequirementSession session) {
        WorkJobDef.Provision provision = session.requirement.provision();
        if (provision == null) return false;
        if (provision.requiresItem()) {
            StationSupplies.pullMatching(level, villager,
                    stack -> provision.matches(level, session.source, stack), 1,
                    session.source, worksite);
        }
        ItemStack supplied = takeProvisionItem(level, villager, session.source, provision);
        if (provision.requiresItem() && supplied.isEmpty()) return false;
        BlockActionContext context = new BlockActionContext(level, session.source, villager)
                .withItemRole("item", supplied);
        provision.start().run(context);
        StationProtocols.giveBack(villager, context.itemRole("item").copy());
        for (ItemStack returned : context.returnedItems()) StationProtocols.giveBack(villager, returned);
        if (context.succeeded()) villager.swing(InteractionHand.MAIN_HAND, true);
        return context.succeeded();
    }

    private static ItemStack takeProvisionItem(ServerLevel level, VillagerEntityMCA villager,
                                               BlockPos source, WorkJobDef.Provision provision) {
        if (!provision.requiresItem()) return ItemStack.EMPTY;
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (provision.matches(level, source, stack)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    private void tickCleanup(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        while (requirementIndex >= 0 && requirements.get(requirementIndex).lease == null) {
            requirementIndex--;
        }
        if (requirementIndex < 0) {
            target = null;
            interaction = null;
            requirements = List.of();
            return;
        }
        RequirementSession session = requirements.get(requirementIndex);
        if (!approach(villager, session.source, gameTime)) return;
        ManagedRequirementLeases.get(level.getServer()).release(
                level.getServer(), session.lease, villager.getUUID());
        requirements.set(requirementIndex, new RequirementSession(
                session.requirement, session.source, null, false));
        villager.swing(InteractionHand.MAIN_HAND, true);
        requirementIndex--;
        moveForCurrentPhase(villager, gameTime);
    }

    private boolean approach(VillagerEntityMCA villager, BlockPos pos, long gameTime) {
        villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (villager.distanceToSqr(Vec3.atCenterOf(pos)) > USE_RANGE_SQ) {
            setWalkTarget(villager, pos);
            useAt = gameTime + WORK_DELAY;
            return false;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return gameTime >= useAt;
    }

    private void moveForCurrentPhase(VillagerEntityMCA villager, long gameTime) {
        BlockPos next = target == null ? null : target.pos();
        if (phase == Phase.PREPARE && requirementIndex < requirements.size()) {
            next = requirements.get(requirementIndex).source;
        } else if (phase == Phase.CLEANUP && requirementIndex >= 0
                && requirementIndex < requirements.size()) {
            next = requirements.get(requirementIndex).source;
        }
        useAt = gameTime + WORK_DELAY;
        if (next != null) setWalkTarget(villager, next);
    }

    private void renewRequirements(ServerLevel level, VillagerEntityMCA villager) {
        ManagedRequirementLeases ledger = ManagedRequirementLeases.get(level.getServer());
        for (RequirementSession session : requirements) {
            if (session.lease != null) ledger.renew(level.getServer(), session.lease, villager.getUUID());
        }
    }

    private void releaseAllRequirements(ServerLevel level, VillagerEntityMCA villager) {
        ManagedRequirementLeases ledger = ManagedRequirementLeases.get(level.getServer());
        for (RequirementSession session : requirements) {
            if (session.lease != null) ledger.release(level.getServer(), session.lease, villager.getUUID());
        }
    }

    private static boolean hasReadyInteraction(ServerLevel level, BlockPos pos,
                                               WorkJobDef.BlockTarget target) {
        for (WorkJobDef.Interaction option : target.interactions()) {
            if (option.ready(level, pos)) return true;
        }
        return false;
    }

    private static boolean hasAvailableInteractionInput(
            ServerLevel level, VillagerEntityMCA villager, BlockPos pos, Set<Long> extent,
            WorkJobDef.BlockTarget target) {
        for (WorkJobDef.Interaction option : target.interactions()) {
            if (!option.ready(level, pos)) continue;
            if (!option.requiresItem()) return true;
            if (WorkIngredients.matchingToolAvailable(level, villager,
                    stack -> option.matches(level, pos, stack), pos, extent)) return true;
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
        if (!itemRemainder.isEmpty()) {
            ResourceLocation remainderId = BuiltInRegistries.ITEM.getKey(itemRemainder.getItem());
            if (context.succeeded() && interaction.outputIds().contains(remainderId)) {
                // Player-like interactions may replace the input in hand rather than drop an
                // entity (a glass bottle becomes a honey bottle). That replacement is output,
                // not a reusable tool remainder, and belongs in the same storage/history path.
                outputs.add(itemRemainder);
            } else {
                StationProtocols.giveBack(villager, itemRemainder);
            }
        }
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

    private enum Phase { PREPARE, WORK, CLEANUP }

    private record RequirementSession(WorkJobDef.ManagedRequirement requirement, BlockPos source,
                                      @Nullable ManagedRequirementLeases.Key lease,
                                      boolean needsStart) {}
}
