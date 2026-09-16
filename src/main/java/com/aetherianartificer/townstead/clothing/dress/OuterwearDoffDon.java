package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingSources;
import com.aetherianartificer.townstead.clothing.Weather;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A coat comes off indoors and goes back on outside, and stays with the villager in between.
 *
 * <p>Nobody walks to a chest for this. After a while in a warm room the warm outerwear moves
 * into the villager's own pockets; after a while back out in the cold it goes on again. Two dwell
 * times keep a doorway from flapping it. Pieces MCA's equipment task manages are never touched.</p>
 */
public final class OuterwearDoffDon {

    /** Ticks in shelter before a coat comes off. */
    public static final long DOFF_AFTER_TICKS = 600L;
    /** Ticks back outside before a coat goes on. */
    public static final long DON_AFTER_TICKS = 100L;

    /** The two dwell clocks, kept by the wardrobe ticker per villager. */
    public static final class Dwell {
        public long shelteredSince = -1;
        public long exposedSince = -1;
    }

    private OuterwearDoffDon() {}

    public static void tick(ServerLevel level, VillagerEntityMCA villager, Dwell dwell, long gameTime) {
        if (villager == null || dwell == null || villager.isBaby()) return;
        boolean sheltered = villager.isSleeping() || Weather.sheltered(level, villager.blockPosition());
        if (sheltered) {
            dwell.exposedSince = -1;
            if (dwell.shelteredSince < 0) dwell.shelteredSince = gameTime;
            if (gameTime - dwell.shelteredSince >= DOFF_AFTER_TICKS) doff(villager);
            return;
        }
        dwell.shelteredSince = -1;
        if (dwell.exposedSince < 0) dwell.exposedSince = gameTime;
        if (gameTime - dwell.exposedSince < DON_AFTER_TICKS) return;
        if (Weather.outdoorKind(level, villager.blockPosition()) == Weather.Kind.COLD || feelsCold(villager)) {
            don(villager);
        }
    }

    private static boolean feelsCold(VillagerEntityMCA villager) {
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!needs.hasBodyTemp()) return false;
        TemperatureData.Tier tier = TemperatureData.Tier.values()[Math.min(6, Math.max(0, needs.thermalTier()))];
        return tier.isCold() && tier.wantsRelief();
    }

    /** Every worn warm outerwear piece the dress rules may move goes into the pockets. */
    static void doff(VillagerEntityMCA villager) {
        boolean managed = DressTask.armourManaged(villager);
        for (WornPiece piece : ClothingSources.worn(villager)) {
            if (!piece.isStack() || piece.layer() != ClothingLayer.OUTERWEAR) continue;
            if (piece.entry() == null || !piece.entry().isWarm()) continue;
            if (managed && DressDecision.ARMOR_SOURCE.equals(piece.source())) continue;
            ItemStack removed = DressTask.takeOff(villager, piece);
            if (removed.isEmpty()) continue;
            ItemStack leftover = villager.getInventory().addItem(removed);
            if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
        }
    }

    /** The warmest carried outerwear goes back on when nothing warm is worn. */
    static void don(VillagerEntityMCA villager) {
        for (WornPiece piece : ClothingSources.worn(villager)) {
            if (piece.isStack() && piece.layer() == ClothingLayer.OUTERWEAR
                    && piece.entry() != null && piece.entry().isWarm()) {
                return;
            }
        }
        ItemStack best = ItemStack.EMPTY;
        float bestWarmth = 0f;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            ClothingEntry entry = entryOf(villager, stack);
            if (entry == null || entry.layer() != ClothingLayer.OUTERWEAR || !entry.isWarm()) continue;
            float warmth = entry.thermal() == null ? 0f
                    : entry.thermal().offset() + entry.thermal().coldResistance();
            if (best.isEmpty() || warmth > bestWarmth) {
                best = stack;
                bestWarmth = warmth;
            }
        }
        if (!best.isEmpty()) DressTask.wear(villager, best);
    }

    private static @Nullable ClothingEntry entryOf(VillagerEntityMCA villager, ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ClothingDefs.forStack(villager.level(), stack);
    }
}
