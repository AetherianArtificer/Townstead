package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import com.aetherianartificer.townstead.temperature.Insulation;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.temperature.ThermalProfile;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.aetherianartificer.townstead.temperature.ThermalComfort;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drifts each villager's body temperature toward a target set by the ambient at its own position,
 * its activity, wetness, clothing, and its root's thermal genes; writes mood pressure and a speed
 * penalty by tier; taps the Chronicle on the edge into a crisis.
 */
public final class TemperatureVillagerTicker {
    //? if >=1.21 {
    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "temperature_speed_penalty");
    //?} else {
    /*private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            new ResourceLocation(Townstead.MOD_ID, "temperature_speed_penalty");
    *///?}
    //? if forge {
    /*private static final java.util.UUID TOWNSTEAD_SPEED_PENALTY_UUID =
            java.util.UUID.nameUUIDFromBytes("townstead:temperature_speed_penalty".getBytes());
    *///?}

    private static final long PROFILE_TTL_TICKS = 200L;
    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();

    private TemperatureVillagerTicker() {}

    public static void clear() { STATE.clear(); }

    public static void forget(int entityId) {
        STATE.remove(entityId);
    }

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isVillagerTemperatureEnabled()) {
            TickState previous = STATE.remove(self.getId());
            if (previous != null) restoreWaterCost(self, previous);
            TownsteadVillagers.get(self).needs().setSeekingRelief(false);
            removeSpeedModifier(self);
            return;
        }

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        TownsteadVillager.Needs needs = TownsteadVillagers.get(self).needs();
        long gameTime = level.getGameTime();
        if (state.lastComfortTick < 0) needs.setSeekingRelief(false);

        if (state.profile == null || gameTime >= state.profileExpiresAt) {
            state.aquatic = com.aetherianartificer.townstead.root.ExpressedGenes.instancesOf(self,
                    com.aetherianartificer.townstead.root.gene.types.AbilityGeneType.Instance.class).stream()
                    .anyMatch(gene -> gene.ability() == com.aetherianartificer.townstead.root.ability.Ability.WATER_BREATHING
                            || gene.ability() == com.aetherianartificer.townstead.root.ability.Ability.SWIMMING);
            state.profile = ThermalProfile.of(self);
            state.suppressed = state.profile.suppressed() || NeedSuppression.suppressesTemperature(self);
            state.profileExpiresAt = gameTime + PROFILE_TTL_TICKS;
        }
        ThermalProfile profile = state.profile;

        // A climate:any root does not thermoregulate: pin the body at neutral so nothing fires.
        if (state.suppressed || com.aetherianartificer.townstead.compat.temperature.ToughAsNailsEntityCompat.climateClemency(self)) {
            state.lastStepGameTime = gameTime;
            state.lastMoodGameTime = gameTime;
            state.lastComfortTick = gameTime;
            state.nextAmbientSampleTick = gameTime;
            int neutral = profile.neutralTenths();
            if (needs.bodyTempTenths() != neutral) needs.setBodyTempTenths(neutral);
            needs.setSeekingRelief(false);
            needs.setThermalComfort(0, 0, 0);
            needs.setThermalTier(3);
            needs.setCoreThermalTier(3);
            needs.setThermalCrisis(false);
            restoreWaterCost(self, state);
            removeSpeedModifier(self);
            syncIfChanged(self, needs, state, false);
            return;
        }

        if (!needs.hasBodyTemp()) needs.setBodyTempTenths(profile.neutralTenths());

        boolean changed = false;
        if (gameTime >= state.nextAmbientSampleTick) {
            state.nextAmbientSampleTick = gameTime + TemperatureData.AMBIENT_SAMPLE_TICKS;
            // Half-degree steps: enough for the body model, and far fewer change-gated syncs as the villager walks.
            int ambient = TemperatureData.quantiseAmbient(TemperatureData.ambientCelsiusFor(level, self));
            if (ambient != needs.ambientTenths()) { needs.setAmbientTenths(ambient); changed = true; }
        }
        if (gameTime >= state.nextClothingSampleTick) {
            state.clothing = Insulation.clothingProtection(self);
            state.nextClothingSampleTick = gameTime + TemperatureData.AMBIENT_SAMPLE_TICKS;
        }
        float seconds = state.lastComfortTick < 0 ? 0.05f : Math.max(0, Math.min(1, (gameTime - state.lastComfortTick) / 20f));
        state.lastComfortTick = gameTime;
        ThermalProtection protection = state.clothing.plus(com.aetherianartificer.townstead.compat.temperature.ToughAsNailsEntityCompat.protection(self))
                .plus(com.aetherianartificer.townstead.compat.temperature.LsoEntityCompat.effects(self).protection());
        float ambientCelsius = com.aetherianartificer.townstead.temperature.ThermalConsumables.internalAmbient(self, TemperatureData.celsius(needs.ambientTenths()));
        boolean immersed = self.isInWater();
        float targetLoad = ThermalComfort.load(ambientCelsius, immersed ? 1 : needs.thermalWetness(),
                immersed, Math.max(0, activityHeat(self) * 10), protection, profile);
        ThermalComfort.State comfort = ThermalComfort.update(new ThermalComfort.State(needs.thermalWetness(),
                needs.comfortLoad(), needs.thermalStrainSeconds()), targetLoad, immersed,
                level.isRainingAt(self.blockPosition()), ambientCelsius, seconds,
                TemperatureSettings.get().dryingSeconds(), TemperatureSettings.get().comfortBreakSeconds());
        needs.setThermalComfort(comfort.wetness(), comfort.load(), comfort.strainSeconds());

        if (state.lastStepGameTime < 0) state.lastStepGameTime = gameTime;
        int steps = 0;
        while (gameTime - state.lastStepGameTime >= TemperatureData.ACCUMULATION_INTERVAL && steps < 100) {
            state.lastStepGameTime += TemperatureData.ACCUMULATION_INTERVAL;
            changed |= step(self, needs, profile, protection, state.drift);
            steps++;
        }

        TemperatureData.Tier coreTier = TemperatureData.tier(needs.bodyTempTenths(), profile);
        needs.setCoreThermalTier(coreTier.ordinal());
        TemperatureData.Tier tier = ThermalComfort.tier(needs.comfortLoad());
        if (tier.ordinal() != needs.thermalTier()) needs.setThermalTier(tier.ordinal());
        updateWaterCost(self, state, profile, ambientCelsius);

        if (state.lastMoodGameTime < 0) state.lastMoodGameTime = gameTime;
        if (gameTime - state.lastMoodGameTime >= TemperatureData.MOOD_CHECK_INTERVAL) {
            state.lastMoodGameTime = gameTime;
            float drift = needs.temperatureMoodDrift() + tier.moodPressure();
            int moodDelta = drift >= 1f ? (int) Math.floor(drift) : drift <= -1f ? (int) Math.ceil(drift) : 0;
            if (moodDelta != 0) {
                self.getVillagerBrain().modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            needs.setTemperatureMoodDrift(drift);
        }

        boolean crisis = coreTier.isCrisis();
        if (crisis && !needs.thermalCrisis()) {
            ChronicleTaps.survival(self, coreTier.isCold() ? ChronicleTapKeys.FREEZING : ChronicleTapKeys.SWELTERING, Map.of());
        }
        if (crisis != needs.thermalCrisis()) needs.setThermalCrisis(crisis);

        if (!self.isBaby()) updateSpeedModifier(self, coreTier.speedPenalty(), state);

        syncIfChanged(self, needs, state, changed);

        if (!self.isAlive() || self.isRemoved()) STATE.remove(self.getId());
    }

    /** One accumulation interval of drift toward the target. Returns true when the reading moved. */
    private static boolean step(VillagerEntityMCA self, TownsteadVillager.Needs needs, ThermalProfile profile, ThermalProtection protection,
                                com.aetherianartificer.townstead.temperature.BodyTemperatureDrift drift) {
        float ambient = com.aetherianartificer.townstead.temperature.ThermalConsumables.internalAmbient(self, TemperatureData.celsius(needs.ambientTenths()));
        ambient = protection.protectAmbient(ambient, TemperatureData.AMBIENT_REFERENCE);
        float clothing = protection.offset();
        float target;
        if (profile.ectotherm()) {
            target = profile.neutral() + (ambient - profile.neutral()) * TemperatureData.ECTOTHERM_FOLLOW;
        } else {
            float pull = (ambient - TemperatureData.AMBIENT_REFERENCE) * TemperatureData.AMBIENT_PULL_PER_DEGREE;
            pull *= pull < 0 ? profile.cold() : profile.heat();
            target = profile.neutral() + pull + activityHeat(self);
        }
        if (needs.wet()) target += TemperatureData.WETNESS_COLD * profile.cold();
        if (self.isSleeping() && target < profile.neutral()) {
            target = profile.neutral() + (target - profile.neutral()) * TemperatureData.SLEEP_COLD_FACTOR;
        }
        boolean hotSide = ambient > TemperatureData.AMBIENT_REFERENCE;
        float innate = profile.insulation();
        if (innate > 0 && hotSide && profile.sheds()) innate = 0f;
        // Worn clothing is opened or set aside as the day warms: full effect at 20 C, none by 25 C. Leather is everyday wear.
        float clothingScale = hotSide ? Math.max(0f, 1f - (ambient - TemperatureData.AMBIENT_REFERENCE) / TemperatureData.CLOTHING_FADE_DEGREES) : 1f;
        float offset = innate + Math.max(0f, clothing) * clothingScale + Math.min(0f, clothing);
        offset = Math.max(-TemperatureData.CLOTHING_CLAMP, Math.min(TemperatureData.CLOTHING_CLAMP, offset));
        target += offset;

        int nextTenths = drift.step(needs.bodyTempTenths(), target, TemperatureData.RATE);
        if (nextTenths == needs.bodyTempTenths()) return false;
        needs.setBodyTempTenths(nextTenths);
        return true;
    }

    private static float activityHeat(VillagerEntityMCA self) {
        VillagerBrain<?> brain = self.getVillagerBrain();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) return TemperatureData.ACTIVITY_COMBAT;
        if (self.swinging) return TemperatureData.ACTIVITY_WORK;
        if (self.getDeltaMovement().horizontalDistanceSqr() > 0.0004) return 0.2f;
        return 0;
    }

    private static void updateWaterCost(VillagerEntityMCA self, TickState state, ThermalProfile profile, float ambient) {
        //? if >=1.21 {
        var water = net.minecraft.world.level.pathfinder.PathType.WATER;
        //?} else {
        /*var water = net.minecraft.world.level.pathfinder.BlockPathTypes.WATER;
        *///?}
        boolean emergency = self.getVillagerBrain().isPanicking() || (self.getLastHurtByMob() != null
                && self.tickCount - self.getLastHurtByMobTimestamp() < 100);
        boolean avoid = com.aetherianartificer.townstead.temperature.ThermalPathPolicy.avoidColdWater(
                ambient, profile.cold(), state.aquatic, emergency);
        float current = self.getPathfindingMalus(water);
        if (!avoid) { restoreWaterCost(self, state); return; }
        if (!state.waterCostRaised) {
            if (current < 0) return;
            state.originalWaterCost = current;
            state.waterCostRaised = true;
        } else if (current != state.appliedWaterCost) {
            // Respect a new base policy supplied by another mod.
            state.originalWaterCost = current;
        }
        float desired = com.aetherianartificer.townstead.temperature.ThermalPathPolicy.waterCost(
                state.originalWaterCost, true, self.isInWater());
        state.appliedWaterCost = desired;
        if (current != desired) {
            self.setPathfindingMalus(water, desired);
            self.getNavigation().recomputePath();
        }
    }

    private static void restoreWaterCost(VillagerEntityMCA self, TickState state) {
        if (!state.waterCostRaised) return;
        //? if >=1.21 {
        var water = net.minecraft.world.level.pathfinder.PathType.WATER;
        //?} else {
        /*var water = net.minecraft.world.level.pathfinder.BlockPathTypes.WATER;
        *///?}
        if (self.getPathfindingMalus(water) == state.appliedWaterCost) {
            self.setPathfindingMalus(water, state.originalWaterCost);
            self.getNavigation().recomputePath();
        }
        state.waterCostRaised = false;
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static void syncIfChanged(VillagerEntityMCA self, TownsteadVillager.Needs needs, TickState state, boolean force) {
        int body = needs.bodyTempTenths();
        int ambient = needs.ambientTenths();
        int flags = needs.temperatureFlags();
        if (!force && body == state.lastSyncedBody && ambient == state.lastSyncedAmbient && flags == state.lastSyncedFlags) return;
        state.lastSyncedBody = body;
        state.lastSyncedAmbient = ambient;
        state.lastSyncedFlags = flags;
        push(self, needs.temperatureTag());
    }

    private static void push(VillagerEntityMCA self, net.minecraft.nbt.CompoundTag temperature) {
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$temperatureSync(self, temperature));
        //?} else if forge {
        /*TownsteadNetwork.sendToTrackingEntity(self, Townstead.townstead$temperatureSync(self, temperature));
        *///?}
    }

    private static void updateSpeedModifier(VillagerEntityMCA self, double penalty, TickState state) {
        if (penalty == state.lastPenalty) return;
        state.lastPenalty = penalty;
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        //? if >=1.21 {
        if (speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY) != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
        if (penalty != 0.0) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    TOWNSTEAD_SPEED_PENALTY, penalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        //?} else {
        /*if (speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY_UUID) != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        if (penalty != 0.0) {
            speedAttr.addTransientModifier(new AttributeModifier(
                    TOWNSTEAD_SPEED_PENALTY_UUID, "townstead:temperature_speed_penalty", penalty,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        *///?}
    }

    private static void removeSpeedModifier(VillagerEntityMCA self) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        //? if >=1.21 {
        if (speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY) != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
        //?} else {
        /*if (speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY_UUID) != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY_UUID);
        *///?}
        TickState state = STATE.get(self.getId());
        if (state != null) state.lastPenalty = 0.0;
    }

    private static final class TickState {
        private long lastComfortTick = -1;
        private boolean aquatic;
        private boolean waterCostRaised;
        private float originalWaterCost;
        private float appliedWaterCost;
        private final com.aetherianartificer.townstead.temperature.BodyTemperatureDrift drift =
                new com.aetherianartificer.townstead.temperature.BodyTemperatureDrift();
        private ThermalProfile profile;
        private boolean suppressed;
        private long profileExpiresAt;
        private long nextAmbientSampleTick;
        private long nextClothingSampleTick;
        private long lastStepGameTime = -1;
        private long lastMoodGameTime = -1;
        private ThermalProtection clothing = ThermalProtection.NONE;
        private double lastPenalty;
        private int lastSyncedBody = Integer.MIN_VALUE;
        private int lastSyncedAmbient = Integer.MIN_VALUE;
        private int lastSyncedFlags = -1;
    }
}
