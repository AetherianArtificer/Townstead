package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.clothing.*;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.temperature.*;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;

/** Whole-outfit thermal choices, including carried pieces and reversible slot swaps. */
public final class ThermalDressing {
    public static final float MIN_GAIN = .025f;
    private ThermalDressing() {}
    private record Placement(ItemStack displaced, java.util.function.Consumer<ItemStack> put) {}

    public static ThermalExposure exposure(ServerLevel level, VillagerEntityMCA villager) {
        var pos = villager.blockPosition();
        // Dress for the shift's workstation before reaching it; local wardrobe handling still
        // checks actual conditions before removing or donning a carried layer.
        if (com.aetherianartificer.townstead.shift.VillagerSchedules.currentActivity(villager)
                == net.minecraft.world.entity.schedule.Activity.WORK) {
            var job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (job.isPresent() && job.get().dimension().equals(level.dimension()) && level.isLoaded(job.get().pos()))
                pos = job.get().pos();
            var stand = ThermalBlocks.nearestStandable(level, pos, 2, p -> true);
            if (stand != null) pos = stand;
        }
        return ThermalExposure.at(level, villager, pos, 2);
    }

    private static java.util.List<Placement> placements(VillagerEntityMCA villager, ItemStack stack) {
        var out = new java.util.ArrayList<Placement>();
        for (var slot : CuriosCompat.slotSpecs(villager)) {
            if (!CuriosCompat.canEquip(villager, slot.id(), slot.index(), stack)) continue;
            var old = slot.handler().getStackInSlot(slot.index());
            if (!old.isEmpty() && !CuriosCompat.canUnequip(villager, slot.id(), slot.index(), old)) continue;
            out.add(new Placement(old, item -> slot.handler().setStackInSlot(slot.index(), item)));
        }
        if (!DressTask.armourManaged(villager)) {
            var entry = ClothingDefs.forStack(villager.level(), stack);
            EquipmentSlot slot = DressTask.armorSlot(entry == null ? null : entry.slot());
            // Native lining items cannot be worn as chestplates. Only real armor or clothing
            // from a wearable provider can use the legacy armor-slot fallback.
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            boolean lining = id.getNamespace().equals("legendarysurvivaloverhaul") && id.getPath().contains("coat");
            if (slot != null && !lining && (stack.getItem() instanceof net.minecraft.world.item.ArmorItem
                    || ClothingDefs.documented(villager.level(), stack) != null
                    || entry != null && entry.source().equals("weaversparadise"))) {
                if (com.aetherianartificer.townstead.inventory.AssignedArmor.protects(villager, slot)) return out;
                var old = villager.getItemBySlot(slot);
                if (old.isEmpty() || !bound(old))
                    out.add(new Placement(old, item -> villager.setItemSlot(slot, item)));
            }
        }
        return out;
    }

    public static boolean bound(ItemStack stack) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.has(stack,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
        //?} else {
        /*return net.minecraft.world.item.enchantment.EnchantmentHelper.hasBindingCurse(stack);
        *///?}
    }

    private static float gain(ThermalExposure exposure, ItemStack stack, Placement placement) {
        var replacement = exposure.protection().minus(Insulation.itemProtection(placement.displaced()))
                .plus(Insulation.itemProtection(stack));
        return exposure.outfitCost() - exposure.withProtection(replacement).outfitCost();
    }

    public static float gain(VillagerEntityMCA villager, ItemStack stack, ThermalExposure exposure) {
        if (stack.isEmpty() || ClothingDefs.forStack(villager.level(), stack) == null) return 0;
        float best = 0;
        for (var placement : placements(villager, stack)) best = Math.max(best, gain(exposure, stack, placement));
        return best;
    }

    public static boolean equip(VillagerEntityMCA villager, ItemStack stack, ThermalExposure exposure) {
        Placement best = null;
        float bestGain = MIN_GAIN;
        for (var placement : placements(villager, stack)) {
            float gain = gain(exposure, stack, placement);
            if (gain > bestGain) { bestGain = gain; best = placement; }
        }
        if (best == null) return false;
        ItemStack old = best.displaced().copy();
        best.put().accept(stack.split(1));
        com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.invalidateEquipment(villager);
        if (!old.isEmpty()) keep(villager, old);
        return true;
    }

    public static boolean safeToWear(VillagerEntityMCA villager, ItemStack stack, ThermalExposure exposure) {
        return placements(villager, stack).stream().anyMatch(p -> gain(exposure, stack, p) >= -.001f);
    }

    /** Remove a layer that is actively worsening exposure, even outdoors or during a work shift. */
    public static boolean shedHarmfulLayer(VillagerEntityMCA villager, ThermalExposure exposure) {
        WornPiece best = null;
        float improvement = MIN_GAIN;
        for (var piece : ClothingSources.worn(villager)) {
            if (!piece.isStack() || DressTask.armourManaged(villager) && DressDecision.ARMOR_SOURCE.equals(piece.source())) continue;
            var after = exposure.withProtection(exposure.protection().minus(Insulation.pieceProtection(villager.level(), piece)));
            float gain = exposure.outfitCost() - after.outfitCost();
            if (gain > improvement && canRemove(villager, piece, exposure)) { best = piece; improvement = gain; }
        }
        if (best == null) return false;
        var removed = DressTask.takeOff(villager, best);
        if (removed.isEmpty()) return false;
        keep(villager, removed);
        return true;
    }

    public static boolean equipCarried(VillagerEntityMCA villager, ThermalExposure exposure) {
        ItemStack best = ItemStack.EMPTY;
        float score = MIN_GAIN;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            var stack = villager.getInventory().getItem(i);
            float gain = gain(villager, stack, exposure);
            if (gain > score) { score = gain; best = stack; }
        }
        return !best.isEmpty() && equip(villager, best, exposure);
    }

    public static boolean canRemove(VillagerEntityMCA villager, WornPiece piece, ThermalExposure exposure) {
        if (piece.isStack() && com.aetherianartificer.townstead.inventory.AssignedArmor.protects(villager, piece.stack())) return false;
        var after = exposure.withProtection(exposure.protection().minus(Insulation.pieceProtection(villager.level(), piece)));
        if (after.outfitCost() > exposure.outfitCost() + .001f) return false;
        float body = TemperatureData.celsius(TownsteadVillagers.get(villager).needs().bodyTempTenths());
        float neutral = exposure.profile().neutral();
        if (Math.abs(body - neutral) > exposure.profile().band() * .5f
                && Math.abs(after.forecast(body, 60).body() - neutral) > Math.abs(exposure.forecast(body, 60).body() - neutral) + .01f)
            return false;
        return true;
    }

    public static void keep(VillagerEntityMCA villager, ItemStack stack) {
        var leftover = villager.getInventory().addItem(stack);
        if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
    }
}
