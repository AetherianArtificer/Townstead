package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.switchboard.Switchboard;

import com.aetherianartificer.townstead.hunger.*;
import com.aetherianartificer.townstead.work.ReachableTargetSelector;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import java.util.ArrayList;

/** One useful thermal serving, even when satiated; all benefits/remainders use the normal transaction. */
public final class ThermalSupplyTask extends Behavior<VillagerEntityMCA> {
    private static final String CADENCE = "thermal_supply", OWNER = "supply", CLAIM = "consumable";
    private NearbyItemSources.ContainerSlot source;
    private int carriedSlot = -1;
    private com.aetherianartificer.townstead.needs.Amenities.Candidate service;
    private boolean consumed;
    private long started;
    public ThermalSupplyTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 1200);
    }
    public static float score(VillagerEntityMCA villager, ItemStack stack) {
        if (stack.isEmpty() || !VillagerConsumptionManager.permitsManagedVillagerConsumption(stack)) return 0;
        if (stack.getUseAnimation() == UseAnim.EAT && !FoodSafety.isSafeToEat(stack, villager)) return 0;
        if (stack.getUseAnimation() != UseAnim.EAT && stack.getUseAnimation() != UseAnim.DRINK) return 0;
        // Reject mixed potions that happen to include warmth alongside a harmful effect.
        //? if >=1.21 {
        var potion = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (potion != null) for (var effect : potion.getAllEffects())
            if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) return 0;
        //?} else {
        /*for (var effect : net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack))
            if (effect.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) return 0;
        *///?}
        if (!(villager.level() instanceof ServerLevel level)) return 0;
        var exposure = ThermalExposure.at(level, villager, villager.blockPosition(), 0);
        return ThermalConsumables.preview(villager, stack).score(exposure,
                TemperatureData.celsius(TownsteadVillagers.get(villager).needs().bodyTempTenths()));
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (ThermalCare.interrupted(villager) || villager.isBaby() || villager.getVillagerBrain().isPanicking()
                || villager.getLastHurtByMob() != null || !ThermalCare.available(villager, OWNER)
                || VillagerConsumptionManager.isConsuming(villager)
                || !VillagerSearchCadence.isDue(level, villager, CADENCE)) return false;
        var needs = TownsteadVillagers.get(villager).needs();
        if (!needs.hasBodyTemp() || !ThermalExposure.enabled(villager)) return false;
        if (TemperatureData.tier(needs.bodyTempTenths(), ThermalProfile.of(villager)) == TemperatureData.Tier.COMFORTABLE) return false;
        source = null; service = null; carriedSlot = -1; consumed = false;
        float best = .01f;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            float score = score(villager, villager.getInventory().getItem(i));
            if (score > best) { best = score; carriedSlot = i; }
        }
        if (carriedSlot >= 0) return true;
        // A serving already carried is quick self-care. Travelling for one should meet
        // the same interruption threshold as a full relief break.
        if (!ThermalCare.needsBreak(villager,
                ThermalExposure.at(level, villager, villager.blockPosition(), ThermalExposure.activity(villager)))) return false;
        if (Switchboard.get(com.aetherianartificer.townstead.TownsteadConfig.ENABLE_CONTAINER_SOURCING)) {
            var candidates = new ArrayList<ReachableTargetSelector.Candidate<NearbyItemSources.ContainerSlot>>();
            NearbyItemSources.collectMatchingSlots(level, villager, 48, 8, stack -> score(villager, stack) > .01f,
                    stack -> Math.round(score(villager, stack) * 1000), villager.blockPosition(), slot -> {
                        if (!ConsumableTargetClaims.isClaimedByOtherSlot(level, villager.getUUID(), CLAIM, slot))
                            candidates.add(new ReachableTargetSelector.Candidate<>(slot, slot.pos()));
                    });
            source = ReachableTargetSelector.chooseReachable(level, villager, candidates, 2, 4, 200,
                    c -> -c.value().score() + Math.sqrt(c.value().distanceSqr()));

        }
        var amenities = new ArrayList<ReachableTargetSelector.Candidate<com.aetherianartificer.townstead.needs.Amenities.Candidate>>();
        for (var candidate : com.aetherianartificer.townstead.needs.Amenities.candidates(level, villager)) {
            if (serviceScore(level, villager, candidate) > .01f
                    && !ConsumableTargetClaims.isClaimedByOtherPos(level, villager.getUUID(), CLAIM, candidate.pos()))
                amenities.add(new ReachableTargetSelector.Candidate<>(candidate, candidate.pos()));
        }
        service = ReachableTargetSelector.chooseReachable(level, villager, amenities, 2, 4, 200,
                c -> -serviceScore(level, villager, c.value()) * 1000 + Math.sqrt(c.pos().distSqr(villager.blockPosition())));
        if (service != null && (source == null || serviceScore(level, villager, service) * 1000 >= source.score())) {
            source = null;
            if (ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(), CLAIM, service.pos(), level.getGameTime() + 1220))
                return true;
        }
        service = null;
        if (source != null && ConsumableTargetClaims.tryClaimSlot(level, villager.getUUID(), CLAIM, source,
                level.getGameTime() + 1220)) return true;
        VillagerSearchCadence.schedule(level, villager, CADENCE, 400, 40);
        return false;
    }
    private static float serviceScore(ServerLevel level, VillagerEntityMCA villager,
                                      com.aetherianartificer.townstead.needs.Amenities.Candidate candidate) {
        if (candidate.worldSource() != null) return score(villager, candidate.serving(level));
        if (candidate.definition() == null) return 0;
        var exposure = ThermalExposure.at(level, villager, villager.blockPosition(), 0);
        var benefit = ThermalBenefit.of(candidate.definition().projection());
        // Do not repeatedly buy the same influence while its effect is still active.
        if (benefit.ambientDelta() != 0 && ThermalConsumables.hasInfluence(villager, benefit.ambientDelta()))
            return 0;
        return benefit.score(exposure, TemperatureData.celsius(TownsteadVillagers.get(villager).needs().bodyTempTenths()));
    }
    @Override protected void start(ServerLevel level, VillagerEntityMCA villager, long now) {
        started = now;
        ThermalCare.hold(villager, OWNER);
        TownsteadVillagers.get(villager).needs().setReliefDebug("thermal_provision");
    }
    @Override protected void tick(ServerLevel level, VillagerEntityMCA villager, long now) {
        ThermalCare.hold(villager, OWNER);
        if (consumed) return;
        if (carriedSlot >= 0) {
            consume(villager, villager.getInventory().getItem(carriedSlot));
            consumed = true;
        } else if (service != null) {
            if (villager.distanceToSqr(service.pos().getX() + .5, service.pos().getY() + .5, service.pos().getZ() + .5) > 9) {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, service.pos(), .6f, 2);
                return;
            }
            if (serviceScore(level, villager, service) > .01f)
                com.aetherianartificer.townstead.needs.Amenities.use(level, villager, service);
            consumed = true;
        } else if (source != null) {
            if (villager.distanceToSqr(source.pos().getX() + .5, source.pos().getY() + .5, source.pos().getZ() + .5) > 9) {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, source.pos(), .6f, 2);
                return;
            }
            ItemStack serving = NearbyItemSources.extractOneFor(level, villager, source, stack -> score(villager, stack) > .01f);
            consume(villager, serving);
            if (!serving.isEmpty()) com.aetherianartificer.townstead.clothing.dress.ThermalDressing.keep(villager, serving);
            consumed = true;
        }
    }
    private void consume(VillagerEntityMCA villager, ItemStack stack) {
        if (score(villager, stack) <= .01f || !VillagerConsumptionManager.startConsuming(villager, stack,
                source == null ? null : source.pos())) return;
        var bridge = com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver.get();
        ItemStack remainder = bridge != null && bridge.isDrink(stack) ? bridge.onDrinkConsumed(stack) : ItemStack.EMPTY;
        if (remainder.isEmpty()) stack.shrink(1);
        else if (remainder != stack) {
            stack.shrink(1);
            com.aetherianartificer.townstead.clothing.dress.ThermalDressing.keep(villager, remainder);
        }
    }
    @Override protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long now) {
        return !ThermalCare.interrupted(villager)
                && ThermalExposure.enabled(villager) && now - started < 1200
                && (consumed || carriedSlot >= 0 || !recoveryComplete(villager))
                && (!consumed || VillagerConsumptionManager.isConsuming(villager));
    }

    private static boolean recoveryComplete(VillagerEntityMCA villager) {
        var needs = TownsteadVillagers.get(villager).needs();
        var profile = ThermalProfile.of(villager);
        return BodyHeat.reliefComplete(TemperatureData.celsius(needs.bodyTempTenths()),
                BodyHeat.target(needs.comfortLoad(), TemperatureSettings.get().comfortZone(), profile, false), profile);
    }
    @Override protected void stop(ServerLevel level, VillagerEntityMCA villager, long now) {
        if (source != null) ConsumableTargetClaims.releaseSlot(level, villager.getUUID(), CLAIM, source);
        if (service != null) ConsumableTargetClaims.releasePos(level, villager.getUUID(), CLAIM, service.pos());
        ThermalCare.release(villager, OWNER);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        source = null; service = null; carriedSlot = -1;
        VillagerSearchCadence.schedule(level, villager, CADENCE, 600, 40);
    }
}
