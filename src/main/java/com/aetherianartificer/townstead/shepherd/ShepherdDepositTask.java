package com.aetherianartificer.townstead.shepherd;

import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.storage.PhysicalStorageDelivery;
import com.aetherianartificer.townstead.storage.StoragePreference;
import com.aetherianartificer.townstead.storage.StorageUse;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Carries wool a worker holds to storage, for professions that declare the {@code store} task.
 * Wool goes to a {@code townstead:textiles} store such as a Wool Shed first, then follows the
 * profession's own preferred roles, then ordinary storage beside the worksite.
 */
public class ShepherdDepositTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final double ARRIVAL_RANGE_SQ = 5.0;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final Predicate<ItemStack> WOOL = stack -> stack.is(ItemTags.WOOL);
    private static final ResourceLocation TEXTILES = ResourceLocation.tryParse("townstead:textiles");

    @Nullable private BlockPos targetStorage;
    private Set<Long> worksite = Set.of();
    private final Set<Long> rejected = new HashSet<>();
    private long startedTick;
    private long lastPathTick;

    public ShepherdDepositTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!WorkTaskDeclarations.permitsTask(villager, WorkTaskTypes.STORE)) return false;
        if (!ShepherdInventory.hasWool(villager)) return false;
        return destination(level, villager, extent(level, villager), Set.of()) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        rejected.clear();
        worksite = extent(level, villager);
        targetStorage = destination(level, villager, worksite, rejected);
        startedTick = gameTime;
        lastPathTick = gameTime;
        if (targetStorage != null) setWalkTarget(villager, targetStorage);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (targetStorage == null || !ShepherdInventory.hasWool(villager)) return false;
        return gameTime - lastPathTick <= PATH_TIMEOUT_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetStorage == null) return;
        villager.getLookControl().setLookAt(
                targetStorage.getX() + 0.5, targetStorage.getY() + 0.5, targetStorage.getZ() + 0.5);
        if (villager.distanceToSqr(Vec3.atCenterOf(targetStorage)) > ARRIVAL_RANGE_SQ) {
            lastPathTick = gameTime;
            setWalkTarget(villager, targetStorage);
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (PhysicalStorageDelivery.depositMatchingAt(level, villager, targetStorage, WOOL) <= 0) {
            rejected.add(targetStorage.asLong());
        }
        villager.swing(InteractionHand.MAIN_HAND, true);
        targetStorage = destination(level, villager, worksite, rejected);
        lastPathTick = gameTime;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetStorage = null;
        worksite = Set.of();
        rejected.clear();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static @Nullable BlockPos destination(ServerLevel level, VillagerEntityMCA villager,
                                                  Set<Long> worksite, Set<Long> excluded) {
        return PhysicalStorageDelivery.findDestination(level, villager, worksite, WOOL, excluded,
                StorageUse.OUTPUT, StoragePreference.forVillager(villager).preferring(TEXTILES));
    }

    private static Set<Long> extent(ServerLevel level, VillagerEntityMCA villager) {
        return ProfessionSites.extentOf(level, villager, ProfessionDefs.byId(
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())));
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
