package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.clothing.BodyClothingResolver;
import com.aetherianartificer.townstead.clothing.ClothingChannel;
import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingSources;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.clothing.policy.WardrobeResolver;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.compat.mca.McaRegistryCompat;
import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.CultureClothing;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.shift.VillagerSchedules;
import com.aetherianartificer.townstead.storage.PhysicalStorageDelivery;
import com.aetherianartificer.townstead.storage.StorageUse;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.aetherianartificer.townstead.tick.WardrobeVillagerTicker;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Dresses a villager: walks to a shelf holding a piece the wardrobe plan asks for and puts it
 * on, or takes off a piece the plan no longer wants and shelves it. Curios slots first, then a
 * free armor slot on a villager whose armor MCA does not manage.
 *
 * <p>Runs off shift, and on shift only for a required layer or a temperature crisis. Never
 * touches a piece MCA's equipment task owns.</p>
 */
public class DressTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "dress";
    private static final int SEARCH_RADIUS = 48;
    private static final int VERTICAL_RADIUS = 8;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 1200;
    private static final int REFRACTORY_TICKS = 600;
    private static final int SEARCH_RETRY_TICKS = 400;
    private static final int REASSERT_TICKS = 40;

    private enum Phase { FETCH, STOW }

    private Phase phase;
    private DressDecision.Action action;
    private NearbyItemSources.ContainerSlot source;
    private BlockPos target;
    private ItemStack carried = ItemStack.EMPTY;
    private long lastReassert;
    private com.aetherianartificer.townstead.temperature.ThermalExposure thermalPlan;

    public DressTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (com.aetherianartificer.townstead.temperature.ThermalCare.interrupted(villager) || villager.isBaby()) return false;
        if (villager.getLastHurtByMob() != null || villager.getVillagerBrain().isPanicking()) return false;
        if (com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.isConsuming(villager)) return false;
        if (!com.aetherianartificer.townstead.temperature.ThermalCare.available(villager, "dress")) return false;
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;

        thermalPlan = null;
        if (com.aetherianartificer.townstead.temperature.ThermalExposure.enabled(villager)) {
            var exposure = ThermalDressing.exposure(level, villager);
            if (ThermalDressing.shedHarmfulLayer(villager, exposure) || ThermalDressing.equipCarried(villager, exposure)) {
                VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, 100, 20);
                return false;
            }
            if (exposure.outfitCost() > ThermalDressing.MIN_GAIN) {
                java.util.List<com.aetherianartificer.townstead.work.ReachableTargetSelector.Candidate<NearbyItemSources.ContainerSlot>> slots = new ArrayList<>();
                NearbyItemSources.collectMatchingSlots(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                        stack -> ThermalDressing.gain(villager, stack, exposure) > ThermalDressing.MIN_GAIN,
                        stack -> Math.round(ThermalDressing.gain(villager, stack, exposure) * 1000), villager.blockPosition(),
                        slot -> {
                            if (!com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.isClaimedByOtherSlot(
                                    level, villager.getUUID(), "clothing", slot))
                                slots.add(new com.aetherianartificer.townstead.work.ReachableTargetSelector.Candidate<>(slot, slot.pos()));
                        });
                var best = com.aetherianartificer.townstead.work.ReachableTargetSelector.chooseReachable(level, villager,
                        slots, CLOSE_ENOUGH, 4, 200, candidate -> -candidate.value().score() + Math.sqrt(candidate.value().distanceSqr()));
                if (best != null && com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.tryClaimSlot(
                        level, villager.getUUID(), "clothing", best, level.getGameTime() + MAX_DURATION + 20)) {
                    thermalPlan = exposure;
                    phase = Phase.FETCH; source = best; target = best.pos(); carried = ItemStack.EMPTY;
                    return true;
                }
                if (!"relief".equals(com.aetherianartificer.townstead.temperature.ThermalCare.owner(villager)))
                    TownsteadVillagers.get(villager).needs().setReliefDebug("no_wearable_protection");
            }
        }
        if ("relief".equals(com.aetherianartificer.townstead.temperature.ThermalCare.owner(villager))) return false;
        if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, SEARCH_RETRY_TICKS, 40);
            return false;
        }

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        TemperatureData.Tier tier = null;
        if (TownsteadConfig.isVillagerTemperatureEnabled() && needs.hasBodyTemp()) {
            tier = TemperatureData.Tier.values()[Math.min(6, Math.max(0, needs.thermalTier()))];
        }
        boolean onShift = VillagerSchedules.currentActivity(villager) == Activity.WORK;
        WardrobeResolver.Plan plan = WardrobeVillagerTicker.planOf(villager);
        List<WornPiece> worn = new ArrayList<>(ClothingSources.worn(villager));
        worn.addAll(ClothingSources.carried(villager));
        Culture culture = CultureAssignment.recorded(villager);
        CultureClothing cultureClothing = culture == null ? CultureClothing.NONE : culture.clothing();
        List<ClothingEntry> fitted = fitted(villager);

        List<DressDecision.Action> actions = DressDecision.decide(plan, worn, armourManaged(villager), tier,
                onShift, cultureClothing, fitted);
        if (actions.isEmpty()) {
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, SEARCH_RETRY_TICKS, 100);
            return false;
        }
        boolean crisis = tier != null && tier.isCrisis();
        for (DressDecision.Action candidate : actions) {
            if (onShift && !candidate.required() && !crisis) continue;
            if (candidate.kind() == DressDecision.Kind.STOW) {
                if (beginStow(level, villager, candidate)) return true;
            } else if (beginFetch(level, villager, candidate, cultureClothing, fitted)) {
                return true;
            }
        }
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, SEARCH_RETRY_TICKS, 100);
        return false;
    }

    private boolean beginFetch(ServerLevel level, VillagerEntityMCA villager, DressDecision.Action candidate,
                               CultureClothing culture, List<ClothingEntry> fitted) {
        NearbyItemSources.ContainerSlot slot = NearbyItemSources.findBestNearbySlot(level, villager, SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> {
                    ClothingEntry entry = ClothingDefs.forStack(level, stack);
                    if (entry == null || entry.layer() != candidate.layer()) return false;
                    if (com.aetherianartificer.townstead.temperature.ThermalExposure.enabled(villager)
                            && !ThermalDressing.safeToWear(villager, stack, ThermalDressing.exposure(level, villager))) return false;
                    return ClothingSelectors.admits(candidate.selector(), entry, culture, fitted);
                },
                stack -> {
                    ClothingEntry entry = ClothingDefs.forStack(level, stack);
                    ThermalProtection thermal = entry == null ? null : entry.thermal();
                    if (thermal == null) return 1;
                    return 1 + Math.round(Math.abs(thermal.offset() + thermal.coldResistance() + thermal.heatResistance()) * 10);
                });
        if (slot == null || !com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.tryClaimSlot(
                level, villager.getUUID(), "clothing", slot, level.getGameTime() + MAX_DURATION + 20)) return false;
        phase = Phase.FETCH;
        action = candidate;
        source = slot;
        target = slot.pos();
        carried = ItemStack.EMPTY;
        return true;
    }

    private boolean beginStow(ServerLevel level, VillagerEntityMCA villager, DressDecision.Action candidate) {
        WornPiece piece = candidate.piece();
        if (piece == null || !piece.isStack()) return false;
        if (!ClothingSources.CARRIED_SOURCE.equals(piece.source())
                && com.aetherianartificer.townstead.temperature.ThermalExposure.enabled(villager)
                && !ThermalDressing.canRemove(villager, piece,
                com.aetherianartificer.townstead.temperature.ThermalExposure.at(level, villager, villager.blockPosition(), 0))) return false;
        ItemStack removed;
        if (ClothingSources.CARRIED_SOURCE.equals(piece.source())) {
            // Already in the pockets: nothing to take off, only somewhere to put it.
            removed = piece.stack().copy();
        } else {
            removed = takeOff(villager, piece);
            if (removed.isEmpty()) return false;
            ItemStack leftover = villager.getInventory().addItem(removed);
            if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
        }
        carried = removed;
        BlockPos destination = PhysicalStorageDelivery.findDestination(level, villager, Set.of(),
                stack -> sameStack(stack,removed), Set.of(), StorageUse.OUTPUT);
        phase = Phase.STOW;
        action = candidate;
        source = null;
        target = destination;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        lastReassert = gameTime - REASSERT_TICKS;
        com.aetherianartificer.townstead.temperature.ThermalCare.hold(villager, "dress");
        TownsteadVillagers.get(villager).needs().setReliefDebug(phase == Phase.FETCH ? "dress_fetch" : "dress_stow");
        if (target != null) walk(villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        com.aetherianartificer.townstead.temperature.ThermalCare.hold(villager, "dress");
        if (target == null) {
            // Nothing to walk to: a stow with no shelf keeps the piece in the villager's pockets.
            finish(level, villager);
            return;
        }
        double distSq = villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
            villager.getNavigation().stop();
            if (phase == Phase.FETCH) fetchAndWear(level, villager);
            else stow(level, villager);
            finish(level, villager);
            return;
        }
        if (gameTime - lastReassert >= REASSERT_TICKS) {
            lastReassert = gameTime;
            walk(villager);
        }
    }

    private void fetchAndWear(ServerLevel level, VillagerEntityMCA villager) {
        var exposure = ThermalDressing.exposure(level, villager);
        ItemStack stack = NearbyItemSources.extractOneFor(level, villager, source, item -> {
            if (thermalPlan != null) return ThermalDressing.gain(villager, item, exposure) > ThermalDressing.MIN_GAIN;
            var entry = ClothingDefs.forStack(level, item);
            return entry != null && entry.layer() == action.layer()
                    && (!com.aetherianartificer.townstead.temperature.ThermalExposure.enabled(villager)
                    || ThermalDressing.safeToWear(villager, item, exposure));
        });
        if (stack == null || stack.isEmpty()) return;
        if (!(thermalPlan != null ? ThermalDressing.equip(villager, stack, ThermalDressing.exposure(level, villager)) : wear(villager, stack))) {
            // Nothing free to wear it in: put it straight back rather than carry it around.
            if (!NearbyItemSources.insertIntoNearbyStorage(level, villager, stack, SEARCH_RADIUS, VERTICAL_RADIUS,
                    source.pos(), StorageUse.OUTPUT)) {
                ItemStack leftover = villager.getInventory().addItem(stack);
                if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
            }
        }
    }

    private void stow(ServerLevel level, VillagerEntityMCA villager) {
        ItemStack piece = carried;
        PhysicalStorageDelivery.depositMatchingAt(level, villager, target,
                stack -> sameStack(stack,piece), StorageUse.OUTPUT);
    }

    /** Curios first, then a free armor slot, only when MCA does not manage this villager's armor. */
    static boolean wear(VillagerEntityMCA villager, ItemStack stack) {
        if (CuriosCompat.equipFirstFree(villager, stack)) {
            stack.shrink(1);
            com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.invalidateEquipment(villager);
            return true;
        }
        if (armourManaged(villager)) return false;
        ClothingEntry entry = ClothingDefs.forStack(villager.level(), stack);
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId.getNamespace().equals("legendarysurvivaloverhaul") && itemId.getPath().contains("coat")) return false;
        EquipmentSlot slot = armorSlot(entry == null ? null : entry.slot());
        if (slot == null || !villager.getItemBySlot(slot).isEmpty()) return false;
        villager.setItemSlot(slot, stack.split(1));
        com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.invalidateEquipment(villager);
        return true;
    }

    static ItemStack takeOff(VillagerEntityMCA villager, WornPiece piece) {
        com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.invalidateEquipment(villager);
        ItemStack worn = piece.stack();
        if (worn == null || worn.isEmpty()) return ItemStack.EMPTY;
        if (com.aetherianartificer.townstead.inventory.AssignedArmor.protects(villager, worn)) return ItemStack.EMPTY;
        ItemStack[] removed = {ItemStack.EMPTY};
        if (DressDecision.ARMOR_SOURCE.equals(piece.source())) {
            if (ThermalDressing.bound(worn)) return ItemStack.EMPTY;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (villager.getItemBySlot(slot) == worn) {
                    removed[0] = worn.copy();
                    villager.setItemSlot(slot, ItemStack.EMPTY);
                    return removed[0];
                }
            }
            return ItemStack.EMPTY;
        }
        CuriosCompat.removeWhere(villager, stack -> stack == worn, stack -> removed[0] = stack);
        return removed[0];
    }

    static boolean sameStack(ItemStack a, ItemStack b) {
        //? if >=1.21 {
        return ItemStack.isSameItemSameComponents(a, b);
        //?} else {
        /*return ItemStack.isSameItemSameTags(a, b);
        *///?}
    }

    static boolean armourManaged(VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getArmorWear()
                || McaRegistryCompat.isGuardOrArcher(villager.getVillagerData().getProfession());
    }

    static @Nullable EquipmentSlot armorSlot(@Nullable ClothingChannel channel) {
        if (channel == null) return null;
        switch (channel) {
            case HEAD: return EquipmentSlot.HEAD;
            case BODY: return EquipmentSlot.CHEST;
            case LEGS: return EquipmentSlot.LEGS;
            case FEET: return EquipmentSlot.FEET;
            default: return null;
        }
    }

    private static List<ClothingEntry> fitted(VillagerEntityMCA villager) {
        var life = TownsteadVillagers.get(villager).life();
        Heritage heritage = life.hasHeritage() ? life.heritage() : null;
        return BodyClothingResolver.fitted(DataPackLang.parseId(life.rootId()), heritage);
    }

    private void finish(ServerLevel level, VillagerEntityMCA villager) {
        if (source != null) com.aetherianartificer.townstead.hunger.ConsumableTargetClaims.releaseSlot(
                level, villager.getUUID(), "clothing", source);
        target = null;
        source = null;
        carried = ItemStack.EMPTY;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (com.aetherianartificer.townstead.temperature.ThermalCare.interrupted(villager)) return false;
        return target != null && level.isLoaded(target);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        finish(level, villager);
        com.aetherianartificer.townstead.temperature.ThermalCare.release(villager, "dress");
        TownsteadVillagers.get(villager).needs().setReliefDebug("wardrobe_checked");
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, REFRACTORY_TICKS, 40);
    }

    private void walk(VillagerEntityMCA villager) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, target, WALK_SPEED, CLOSE_ENOUGH);
    }
}
