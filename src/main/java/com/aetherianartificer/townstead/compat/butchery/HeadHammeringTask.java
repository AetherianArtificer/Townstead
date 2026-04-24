package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.storage.loot.LootParams;
//? if >=1.21 {
import net.minecraft.world.level.storage.loot.LootTable;
//?}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Butcher villager breaks placed head / skull blocks with a hammer,
 * matching {@code BreakheadProcedure} stroke-for-stroke: swing,
 * {@code block.sniffer_egg.crack} + {@code block.honey_block.break}
 * sounds at vol 1, {@code world.destroyBlock(false)}, roll the block's
 * breakdown loot table, and if the head is in
 * {@link HammerHelper#ARMOR_DROPS} also drop its armor helmet.
 *
 * <p>Trophy protection: blocks flagged by
 * {@link HammerHelper#shouldAutoHammer} stay untouched unless
 * {@link TownsteadConfig#HAMMER_TROPHY_HEADS} is true, so the player's
 * evoker/vindicator/pillager/warden/dragon/player/wither/ice trophies
 * survive by default. Regular animal heads (cow, pig, sheep, chicken,
 * etc., plus the various mob heads without special significance) get
 * auto-hammered.
 *
 * <p>One head per task session. Runs at priority 78 (after blood
 * cleanup) since head breakdown is later-stage housekeeping.
 */
public class HeadHammeringTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 400;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final float WALK_SPEED = 0.55f;
    private static final int ACT_COOLDOWN_TICKS = 24;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int STROKE_XP = 1;

    //? if >=1.21 {
    private static final ResourceLocation SOUND_CRACK =
            ResourceLocation.parse("block.sniffer_egg.crack");
    private static final ResourceLocation SOUND_HONEY_BREAK =
            ResourceLocation.parse("block.honey_block.break");
    //?} else {
    /*private static final ResourceLocation SOUND_CRACK =
            new ResourceLocation("block.sniffer_egg.crack");
    private static final ResourceLocation SOUND_HONEY_BREAK =
            new ResourceLocation("block.honey_block.break");
    *///?}

    private enum Phase { PATH, ACT }

    @Nullable private BlockPos targetPos;
    @Nullable private BlockPos standPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextActTick;
    private boolean missingHammer;

    public HeadHammeringTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        return findHammerTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetPos = findHammerTarget(level, villager);
        if (targetPos == null) return;
        standPos = findStandPos(level, villager, targetPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextActTick = gameTime + ACT_COOLDOWN_TICKS;
        missingHammer = false;
        setWalkTarget(villager, standPos != null ? standPos : targetPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null) return false;
        BlockState state = level.getBlockState(targetPos);
        if (HammerHelper.classify(state) == null) return false;
        if (!HammerHelper.shouldAutoHammer(state, hammerTrophyHeads())) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null) return;
        villager.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetPos;
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

        if (!ButcherToolDamage.hasHammer(villager)) {
            emitNoHammerChat(level, villager, gameTime);
            missingHammer = true;
            targetPos = null;
            return;
        }

        BlockState state = level.getBlockState(targetPos);
        HammerHelper.Category category = HammerHelper.classify(state);
        if (category == null) {
            targetPos = null;
            return;
        }

        equipHammer(villager);
        villager.swing(InteractionHand.MAIN_HAND, true);
        playCrackSounds(level, targetPos);

        List<ItemStack> drops = new ArrayList<>();
        rollLoot(level, category.lootPath, drops);
        ResourceLocation armorId = HammerHelper.armorDropFor(state);
        if (armorId != null) {
            ItemStack armor = HammerHelper.resolveArmorStack(armorId);
            if (!armor.isEmpty()) drops.add(armor);
        }

        level.destroyBlock(targetPos, false);
        deposit(level, villager, drops);
        ButcherToolDamage.consumeHammerUse(villager);
        awardXp(villager, STROKE_XP, gameTime);
        targetPos = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        missingHammer = false;
        targetPos = null;
        standPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Sound / equip / drops ──

    private static void playCrackSounds(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_CRACK),
                SoundSource.NEUTRAL, 1f, 1f);
        level.playSound(null, pos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_HONEY_BREAK),
                SoundSource.NEUTRAL, 1f, 1f);
    }

    private static void equipHammer(VillagerEntityMCA villager) {
        if (WorkToolTicker.isHammer(villager.getMainHandItem())) return;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (WorkToolTicker.isHammer(stack)) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                return;
            }
        }
    }

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

    private static void rollLoot(ServerLevel level, String lootTableId, List<ItemStack> out) {
        MinecraftServer server = level.getServer();
        if (server == null) return;
        //? if >=1.21 {
        ResourceKey<LootTable> key = ResourceKey.create(
                Registries.LOOT_TABLE, ResourceLocation.parse(lootTableId));
        LootTable table = server.reloadableRegistries().getLootTable(key);
        if (table == null) return;
        out.addAll(table.getRandomItems(
                new LootParams.Builder(level).create(LootContextParamSets.EMPTY)));
        //?} else {
        /*net.minecraft.world.level.storage.loot.LootTable table = server.getLootData()
                .getLootTable(new ResourceLocation(lootTableId));
        if (table == null || table == net.minecraft.world.level.storage.loot.LootTable.EMPTY) return;
        out.addAll(table.getRandomItems(
                new LootParams.Builder(level).create(LootContextParamSets.EMPTY)));
        *///?}
    }

    // ── Scanning ──

    @Nullable
    private static BlockPos findHammerTarget(ServerLevel level, VillagerEntityMCA villager) {
        boolean allowTrophy = hammerTrophyHeads();
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            BlockPos found = scanBuilding(level, ref.building(), allowTrophy);
            if (found == null) continue;
            double dsq = found.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = found;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos scanBuilding(ServerLevel level, Building building, boolean allowTrophy) {
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
                    if (HammerHelper.shouldAutoHammer(state, allowTrophy)) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean hammerTrophyHeads() {
        return TownsteadConfig.HAMMER_TROPHY_HEADS != null
                && TownsteadConfig.HAMMER_TROPHY_HEADS.get();
    }

    // ── Stand / walk helpers ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos head) {
        BlockPos[] candidates = {
                head.north(), head.south(), head.east(), head.west()
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

    // ── Chat ──

    private static void emitNoHammerChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        String key = "dialogue.chat.butcher_request.no_hammer/" + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(key);
        data.putLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }
}
