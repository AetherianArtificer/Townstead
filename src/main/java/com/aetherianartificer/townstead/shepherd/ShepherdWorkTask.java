package com.aetherianartificer.townstead.shepherd;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Shepherd: walk to a shearable sheep inside a Livestock Pen, swap to
 * shears, shear it, then deposit wool to any nearby chest. Targets are
 * pen-bound only — wild sheep stay wild. See
 * {@link ShepherdPenScanner} for target acquisition.
 */
public class ShepherdWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final double SHEAR_RANGE_SQ = 4.0;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 120;
    /** Ticks between shear actions on the same target so the swing reads
     *  as a deliberate cut rather than a hold-to-shear. Vanilla shears are
     *  effectively instant; the cooldown is for animation cadence. */
    private static final int SHEAR_COOLDOWN_TICKS = 12;
    private static final int STORAGE_DEPOSIT_RADIUS = 16;
    private static final int STORAGE_DEPOSIT_VERTICAL = 3;
    private static final double WOOL_PICKUP_RADIUS = 2.5;

    private enum Phase {
        PATH,
        SHEAR
    }

    @Nullable private Sheep target;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextShearTick;
    private ItemStack preWorkMainHand = ItemStack.EMPTY;
    private boolean swappedToShears;

    public ShepherdWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getVillagerData().getProfession() != VillagerProfession.SHEPHERD) return false;
        if (!ShepherdToolDamage.hasShears(villager)) return false;
        return ShepherdPenScanner.pickShearable(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ShepherdPenScanner.Pick pick = ShepherdPenScanner.pickShearable(level, villager);
        if (pick == null) return;
        target = pick.sheep();
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextShearTick = gameTime + SHEAR_COOLDOWN_TICKS;
        setWalkTarget(villager, target.blockPosition());
        equipShearsIfAvailable(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (target == null || !target.isAlive()) return false;
        if (!ShepherdPenScanner.isShearable(target)) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        villager.getLookControl().setLookAt(target, 30f, 30f);

        double dsq = villager.distanceToSqr(target);
        if (phase == Phase.PATH) {
            if (dsq <= SHEAR_RANGE_SQ) {
                phase = Phase.SHEAR;
                nextShearTick = gameTime + SHEAR_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                lastPathTick = gameTime;
                setWalkTarget(villager, target.blockPosition());
            }
            return;
        }

        // SHEAR phase
        if (dsq > SHEAR_RANGE_SQ * 1.5) {
            phase = Phase.PATH;
            setWalkTarget(villager, target.blockPosition());
            return;
        }
        if (gameTime < nextShearTick) return;
        performShear(level, villager, target);
        nextShearTick = gameTime + SHEAR_COOLDOWN_TICKS;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        restorePreWorkHand(villager);
    }

    private void performShear(ServerLevel level, VillagerEntityMCA villager, Sheep sheep) {
        villager.swing(InteractionHand.MAIN_HAND, true);
        sheep.shear(SoundSource.NEUTRAL);
        ShepherdToolDamage.consumeShearsUse(villager);

        // Vanilla Sheep#shear spawns wool as ItemEntities at the sheep's
        // position. Sweep them into the villager's inventory; whatever
        // doesn't fit gets pushed to nearby storage immediately, and any
        // remainder stays on the ground for the player to deal with.
        // Sheep#shear spawns wool with vanilla's default pickup delay so
        // players can't immediately re-grab their own drops; that delay is
        // irrelevant when the villager absorbs the items directly, so we
        // skip the hasPickUpDelay() check.
        AABB pickup = sheep.getBoundingBox().inflate(WOOL_PICKUP_RADIUS);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, pickup,
                ie -> ie.isAlive() && ie.getItem().is(ItemTags.WOOL));
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            ItemStack remainder = villager.getInventory().addItem(stack);
            if (!remainder.isEmpty()) {
                NearbyItemSources.insertIntoNearbyStorage(level, villager, remainder,
                        STORAGE_DEPOSIT_RADIUS, STORAGE_DEPOSIT_VERTICAL);
            }
            if (remainder.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(remainder);
            }
        }
        villager.getInventory().setChanged();

        deliverPendingWool(level, villager);
        awardXp(villager, 1, level.getGameTime());
    }

    /**
     * Push any wool currently in the villager's inventory to nearby
     * storage. Called after each shear so wool doesn't accumulate to the
     * point of dropping freshly-cut stacks on the ground when inventory
     * fills up. Silent no-op if no chest is in range.
     */
    private static void deliverPendingWool(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.WOOL)) continue;
            NearbyItemSources.insertIntoNearbyStorage(level, villager, stack,
                    STORAGE_DEPOSIT_RADIUS, STORAGE_DEPOSIT_VERTICAL);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();
    }

    private void equipShearsIfAvailable(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int slot = ShepherdShearToolCompatRegistry.findShearsSlot(inv);
        if (slot < 0) return;
        ItemStack current = villager.getMainHandItem();
        preWorkMainHand = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        villager.setItemInHand(InteractionHand.MAIN_HAND, inv.getItem(slot).copy());
        swappedToShears = true;
    }

    private void restorePreWorkHand(VillagerEntityMCA villager) {
        if (!swappedToShears) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, preWorkMainHand);
        preWorkMainHand = ItemStack.EMPTY;
        swappedToShears = false;
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ShepherdProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }
}
