package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Butchers at Tier 2+ shops walk to whitelisted farm animals within shop
 * bounds and kill them, then spawn the appropriate Butchery fresh-carcass
 * block at a nearby {@code butchery:hook} inside the shop footprint. Downstream
 * {@link CarcassWorkTask} handles bleeding and processing.
 *
 * <p>Scope guards: {@link SlaughterPolicy} filters species, excludes babies /
 * named / pets, and enforces a per-villager throttle. Requires a hook
 * inside the shop bounds so the player has placed explicit slaughter
 * infrastructure; without one, this task never fires.
 */
public class SlaughterWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final double ATTACK_RANGE_SQ = 4.0;
    private static final float WALK_SPEED = 0.58f;
    private static final int PATH_TIMEOUT_TICKS = 120;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final float ATTACK_DAMAGE = 20.0f;

    private enum Phase { PATH, ATTACK }

    @Nullable private LivingEntity target;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextAttackTick;
    private ItemStack preSlaughterMainHand = ItemStack.EMPTY;
    private boolean swappedToKnife;

    public SlaughterWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (!SlaughterPolicy.slaughterEnabled()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        if (onThrottle(villager, level.getGameTime())) return false;
        Optional<ButcheryShopScanner.ShopRef> shop = ButcheryShopScanner.shopFor(level, villager);
        if (shop.isEmpty() || shop.get().tier() < ButcheryShopScanner.MIN_CARCASS_TIER) return false;
        if (findHook(level, shop.get()) == null) return false;
        return findTarget(level, villager, shop.get()) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Optional<ButcheryShopScanner.ShopRef> shop = ButcheryShopScanner.shopFor(level, villager);
        if (shop.isEmpty()) return;
        target = findTarget(level, villager, shop.get());
        if (target == null) return;
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
        setWalkTarget(villager, target.blockPosition());
        equipKnifeIfAvailable(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null || !target.isAlive()) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        villager.getLookControl().setLookAt(target, 30f, 30f);

        double dsq = villager.distanceToSqr(target);
        if (phase == Phase.PATH) {
            if (dsq <= ATTACK_RANGE_SQ) {
                phase = Phase.ATTACK;
                nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, target.blockPosition());
            }
            return;
        }

        // ATTACK phase
        if (dsq > ATTACK_RANGE_SQ * 1.5) {
            phase = Phase.PATH;
            setWalkTarget(villager, target.blockPosition());
            return;
        }
        if (gameTime < nextAttackTick) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        DamageSource source = level.damageSources().mobAttack(villager);
        BlockPos killPos = target.blockPosition();
        boolean killed = target.hurt(source, ATTACK_DAMAGE);
        nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
        if (killed && !target.isAlive()) {
            onTargetKilled(level, villager, target, killPos, gameTime);
            target = null;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        restorePreSlaughterHand(villager);
    }

    private void equipKnifeIfAvailable(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int knifeSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (WorkToolTicker.isKnife(inv.getItem(i))) {
                knifeSlot = i;
                break;
            }
        }
        if (knifeSlot < 0) return;
        ItemStack current = villager.getMainHandItem();
        // Only stash something worth restoring. If main hand is already a knife
        // or empty, there's nothing meaningful to return to after slaughter.
        preSlaughterMainHand = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        villager.setItemInHand(InteractionHand.MAIN_HAND, inv.getItem(knifeSlot).copy());
        swappedToKnife = true;
    }

    private void restorePreSlaughterHand(VillagerEntityMCA villager) {
        if (!swappedToKnife) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, preSlaughterMainHand);
        preSlaughterMainHand = ItemStack.EMPTY;
        swappedToKnife = false;
    }

    // --- helpers ---

    @Nullable
    private static LivingEntity findTarget(ServerLevel level, VillagerEntityMCA villager, ButcheryShopScanner.ShopRef shop) {
        BlockPos origin = villager.blockPosition();
        AABB search = AABB.ofSize(
                new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5),
                24, 8, 24);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, search,
                a -> shop.building().containsPos(a.blockPosition())
                        && SlaughterPolicy.canSlaughter(villager, a));
        LivingEntity best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Animal a : animals) {
            double dsq = a.distanceToSqr(villager);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = a;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findHook(ServerLevel level, ButcheryShopScanner.ShopRef shop) {
        //? if >=1.21 {
        ResourceLocation hookId = ResourceLocation.fromNamespaceAndPath("butchery", "hook");
        //?} else {
        /*ResourceLocation hookId = new ResourceLocation("butchery", "hook");
        *///?}
        // Quick scan of shop's tracked blocks: Building.getBlocks() maps
        // block-id → positions. If hook is registered there, return one.
        var positions = shop.building().getBlocks().get(hookId);
        if (positions == null || positions.isEmpty()) return null;
        return positions.get(0);
    }

    private static void onTargetKilled(ServerLevel level, VillagerEntityMCA villager,
                                       LivingEntity killed, BlockPos killPos, long gameTime) {
        Optional<ButcheryShopScanner.ShopRef> shop = ButcheryShopScanner.shopFor(level, villager);
        if (shop.isEmpty()) return;
        BlockPos hook = findHook(level, shop.get());
        BlockPos carcassPos = hook != null ? hook.below() : killPos;
        ResourceLocation carcassId = SlaughterPolicy.carcassIdFor(killed.getType());
        if (carcassId == null || !BuiltInRegistries.BLOCK.containsKey(carcassId)) return;
        Block carcass = BuiltInRegistries.BLOCK.get(carcassId);
        if (carcass == null) return;
        BlockState current = level.getBlockState(carcassPos);
        if (!current.isAir() && !current.canBeReplaced()) return;
        level.setBlock(carcassPos, carcass.defaultBlockState(), 3);

        markThrottle(villager, gameTime);
        awardXp(villager, 2, gameTime);
        announceFlavor(villager, level, "dialogue.chat.butcher_flavor.slaughter_done/"
                + (1 + level.random.nextInt(3)));
    }

    private static void announceFlavor(VillagerEntityMCA villager, ServerLevel level, String key) {
        if (level.getNearestPlayer(villager, 24.0) == null) return;
        villager.sendChatToAllAround(key);
    }

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong("townstead_lastSlaughterTick");
        return gameTime - last < SlaughterPolicy.throttleTicks();
    }

    private static void markThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        data.putLong("townstead_lastSlaughterTick", gameTime);
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        data.putLong("townstead_lastSlaughterTick", gameTime);
        villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
