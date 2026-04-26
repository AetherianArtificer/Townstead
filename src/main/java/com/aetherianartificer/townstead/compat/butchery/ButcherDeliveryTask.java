package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.hunger.ButcherSupplyManager;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Butcher carries production output to its destination by walking, rather
 * than magically teleporting items into a remote container. One trip per
 * task session:
 *
 * <ul>
 *   <li>Skins ({@code butchery:skins} tag) → {@code butchery:skin_rack}
 *   <li>Organs ({@code butchery:organs} tag) → {@code butchery:pestle_and_mortar}
 *   <li>Anything else flagged by {@link ButcherSupplyManager#isButcherOutput}
 *       (cooked meats, mince, meat scraps, glass bottle returns) → first
 *       container in a carcass-capable shop with room
 * </ul>
 *
 * <p>Hides get first priority (players notice uncured hides piling up
 * most), then organs, then generic output. The task keeps running while
 * anything is still in inventory, one destination at a time.
 *
 * <p>Runs at priority 76, after all production tasks but before idle
 * housekeeping (sausage hooks, blood cleanup, head hammering) so
 * inventory doesn't accumulate faster than the butcher can empty it.
 */
public class ButcherDeliveryTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 400;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final float WALK_SPEED = 0.52f;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int DEPOSIT_COOLDOWN_TICKS = 15;

    //? if >=1.21 {
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:skins"));
    private static final TagKey<Item> ORGANS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:organs"));
    private static final ResourceLocation SKIN_RACK_ID =
            ResourceLocation.parse("butchery:skin_rack");
    private static final ResourceLocation PESTLE_ID =
            ResourceLocation.parse("butchery:pestle_and_mortar");
    private static final TagKey<Block> BUTCHER_STORAGE_TAG = TagKey.create(
            Registries.BLOCK, ResourceLocation.parse("townstead:compat/butchery/butcher_shop_storage"));
    private static final ResourceLocation SOUND_ITEM_PICKUP =
            ResourceLocation.parse("entity.item.pickup");
    //?} else {
    /*private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "skins"));
    private static final TagKey<Item> ORGANS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "organs"));
    private static final ResourceLocation SKIN_RACK_ID =
            new ResourceLocation("butchery", "skin_rack");
    private static final ResourceLocation PESTLE_ID =
            new ResourceLocation("butchery", "pestle_and_mortar");
    private static final TagKey<Block> BUTCHER_STORAGE_TAG = TagKey.create(
            Registries.BLOCK, new ResourceLocation("townstead", "compat/butchery/butcher_shop_storage"));
    private static final ResourceLocation SOUND_ITEM_PICKUP =
            new ResourceLocation("entity.item.pickup");
    *///?}

    private enum Phase { PATH, DEPOSIT }

    private enum Category { SKINS, ORGANS, GENERIC }

    private record Delivery(BlockPos container, Category category) {}

    @Nullable private BlockPos targetPos;
    @Nullable private BlockPos standPos;
    @Nullable private Category category;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextDepositTick;

    public ButcherDeliveryTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        if (CarcassWorkTask.hasActionableWork(level, villager)) return false;
        return planDelivery(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Delivery delivery = planDelivery(level, villager);
        if (delivery == null) return;
        targetPos = delivery.container;
        category = delivery.category;
        standPos = findStandPos(level, villager, targetPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextDepositTick = gameTime + DEPOSIT_COOLDOWN_TICKS;
        setWalkTarget(villager, standPos != null ? standPos : targetPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null || category == null) return false;
        if (!(level.getBlockEntity(targetPos) instanceof Container container)) return false;
        if (!hasDeliverableFor(villager, category)) return false;
        if (!canAcceptAnyDeliverable(container, villager, category)) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null || category == null) return;
        villager.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetPos;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.DEPOSIT;
                nextDepositTick = gameTime + DEPOSIT_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, anchor);
            }
            return;
        }

        if (gameTime < nextDepositTick) return;
        executeDeposit(level, villager);
        targetPos = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetPos = null;
        standPos = null;
        category = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Deposit action ──

    private void executeDeposit(ServerLevel level, VillagerEntityMCA villager) {
        if (!(level.getBlockEntity(targetPos) instanceof Container container)) return;
        SimpleContainer inv = villager.getInventory();
        boolean movedAny = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!matchesCategory(stack, category)) continue;
            ItemStack moving = stack.copy();
            ItemStack remaining = insertIntoContainer(container, moving);
            int deposited = stack.getCount() - remaining.getCount();
            if (deposited <= 0) continue;
            stack.shrink(deposited);
            movedAny = true;
            if (!canAcceptAnyDeliverable(container, villager, category)) break;
        }
        if (!movedAny) return;
        container.setChanged();
        inv.setChanged();
        villager.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, targetPos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_ITEM_PICKUP),
                SoundSource.NEUTRAL, 0.3f, 1.0f);
    }

    /**
     * Vanilla-compatible insertion without hopper-side filtering: fill
     * existing matching stacks first, then land in empty slots. Returns
     * the portion that wouldn't fit.
     */
    private static ItemStack insertIntoContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int size = container.getContainerSize();
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) continue;
            //? if >=1.21 {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            //?} else {
            /*if (!ItemStack.isSameItemSameTags(existing, stack)) continue;
            *///?}
            int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize()) - existing.getCount();
            if (room <= 0) continue;
            int move = Math.min(room, stack.getCount());
            existing.grow(move);
            stack.shrink(move);
        }
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!container.getItem(i).isEmpty()) continue;
            ItemStack placed = stack.copy();
            int cap = Math.min(placed.getMaxStackSize(), container.getMaxStackSize());
            placed.setCount(Math.min(cap, stack.getCount()));
            stack.shrink(placed.getCount());
            container.setItem(i, placed);
        }
        return stack;
    }

    private static boolean canAcceptAnyDeliverable(Container container, VillagerEntityMCA villager, Category category) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !matchesCategory(stack, category)) continue;
            if (canAccept(container, stack)) return true;
        }
        return false;
    }

    private static boolean canAccept(Container container, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) return true;
            //? if >=1.21 {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            //?} else {
            /*if (!ItemStack.isSameItemSameTags(existing, stack)) continue;
            *///?}
            if (existing.getCount() < Math.min(existing.getMaxStackSize(), container.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    // ── Planning ──

    @Nullable
    private static Delivery planDelivery(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        boolean hasSkins = inventoryHasTag(inv, SKINS_TAG);
        boolean hasOrgans = inventoryHasTag(inv, ORGANS_TAG);
        boolean hasGeneric = inventoryHasGenericOutput(inv);

        if (hasSkins) {
            BlockPos rack = findStationWithRoom(level, villager, SKIN_RACK_ID, Category.SKINS);
            if (rack != null) return new Delivery(rack, Category.SKINS);
        }
        if (hasOrgans) {
            BlockPos pestle = findStationWithRoom(level, villager, PESTLE_ID, Category.ORGANS);
            if (pestle != null) return new Delivery(pestle, Category.ORGANS);
        }
        if (hasGeneric) {
            BlockPos chest = findGenericStorageWithRoom(level, villager);
            if (chest != null) return new Delivery(chest, Category.GENERIC);
        }
        return null;
    }

    @Nullable
    private static BlockPos findStationWithRoom(
            ServerLevel level,
            VillagerEntityMCA villager,
            ResourceLocation stationId,
            Category category
    ) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            Building building = ref.building();
            List<BlockPos> stations = building.getBlocks().get(stationId);
            if (stations == null) continue;
            for (BlockPos pos : stations) {
                if (!(level.getBlockEntity(pos) instanceof Container container)) continue;
                if (!canAcceptAnyDeliverable(container, villager, category)) continue;
                double dsq = pos.distSqr(origin);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findGenericStorageWithRoom(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.finishedGoodsStorageShops(level, villager)) {
            Building building = ref.building();
            for (List<BlockPos> positions : building.getBlocks().values()) {
                for (BlockPos pos : positions) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof Container container)) continue;
                    // Skip the specialty stations — those have their own
                    // category routing above and shouldn't catch generic
                    // overflow. Also skip the carcass / grinder that have
                    // in-use slots we'd clog up.
                    ResourceLocation blockId = BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(pos).getBlock());
                    if (SKIN_RACK_ID.equals(blockId)) continue;
                    if (PESTLE_ID.equals(blockId)) continue;
                    if (!level.getBlockState(pos).is(BUTCHER_STORAGE_TAG)) continue;
                    if (!canAcceptAnyDeliverable(container, villager, Category.GENERIC)) continue;
                    double dsq = pos.distSqr(origin);
                    if (dsq < bestDsq) {
                        bestDsq = dsq;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean inventoryHasTag(SimpleContainer inv, TagKey<Item> tag) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(tag)) return true;
        }
        return false;
    }

    private static boolean inventoryHasGenericOutput(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(SKINS_TAG) || stack.is(ORGANS_TAG)) continue;
            if (ButcherSupplyManager.isButcherOutput(stack)) return true;
        }
        return false;
    }

    private static boolean hasDeliverableFor(VillagerEntityMCA villager, Category category) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && matchesCategory(stack, category)) return true;
        }
        return false;
    }

    private static boolean matchesCategory(ItemStack stack, Category category) {
        return switch (category) {
            case SKINS -> stack.is(SKINS_TAG);
            case ORGANS -> stack.is(ORGANS_TAG);
            case GENERIC -> !stack.is(SKINS_TAG)
                    && !stack.is(ORGANS_TAG)
                    && ButcherSupplyManager.isButcherOutput(stack);
        };
    }

    // ── Stand / walk helpers ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos target) {
        BlockPos[] candidates = {
                target.north(), target.south(), target.east(), target.west()
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
}
