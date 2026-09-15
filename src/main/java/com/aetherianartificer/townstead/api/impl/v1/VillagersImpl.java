package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.VillagersApi;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.village.ResidentRegister;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class VillagersImpl implements VillagersApi {

    @Override
    public Optional<VillagerSnapshot> snapshot(Entity entity) {
        try {
            return Optional.ofNullable(ApiSnapshots.villager(entity));
        } catch (Throwable t) {
            ApiSupport.swallow("villagers.snapshot", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillagerSnapshot> snapshot(MinecraftServer server, UUID id) {
        LivingEntity entity = ApiSupport.findLoaded(server, id);
        return entity == null ? Optional.empty() : snapshot(entity);
    }

    @Override
    public Optional<NeedsSnapshot> needs(Entity entity) {
        try {
            VillagerEntityMCA villager = ApiSupport.villager(entity);
            if (villager == null) return Optional.empty();
            return Optional.of(ApiSnapshots.needs(TownsteadVillagers.get(villager).needs()));
        } catch (Throwable t) {
            ApiSupport.swallow("villagers.needs", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<VillagerRecord> record(MinecraftServer server, UUID id) {
        try {
            if (server == null || id == null) return Optional.empty();
            LivingEntity loaded = ApiSupport.findLoaded(server, id);
            if (loaded instanceof VillagerEntityMCA villager) ResidentRegister.get(server).update(villager);
            return ResidentRegister.get(server).record(server, id, loaded != null);
        } catch (Throwable t) {
            ApiSupport.swallow("villagers.record", t);
            return Optional.empty();
        }
    }

    @Override
    public List<String> needIds() {
        return List.of(NeedsSnapshot.HUNGER, NeedsSnapshot.SATURATION, NeedsSnapshot.THIRST,
                NeedsSnapshot.QUENCHED, NeedsSnapshot.ENERGY, NeedsSnapshot.TEMPERATURE);
    }

    @Override
    public NeedResult setNeed(Entity entity, String needId, int value, ResourceLocation source) {
        return mutate(entity, needId, value, false, source);
    }

    @Override
    public NeedResult adjustNeed(Entity entity, String needId, int delta, ResourceLocation source) {
        return mutate(entity, needId, delta, true, source);
    }

    private NeedResult mutate(Entity entity, String needId, int amount, boolean relative, ResourceLocation source) {
        try {
            if (needId == null || !needIds().contains(needId)) {
                return NeedResult.failed(NeedResult.Status.UNKNOWN_NEED, needId, "unknown need id");
            }
            if (!ApiSupport.writesAllowed(source)) {
                return NeedResult.failed(NeedResult.Status.DISABLED, needId, "writes from " + source + " are disabled");
            }
            VillagerEntityMCA villager = ApiSupport.villager(entity);
            if (villager == null) {
                return NeedResult.failed(NeedResult.Status.NOT_A_VILLAGER, needId, "not an MCA villager");
            }
            TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
            int min = 0;
            int max;
            int before;
            switch (needId) {
                case NeedsSnapshot.HUNGER -> {
                    max = HungerData.MAX_HUNGER;
                    before = needs.hunger();
                }
                case NeedsSnapshot.SATURATION -> {
                    max = (int) HungerData.MAX_SATURATION;
                    before = Math.round(needs.saturation());
                }
                case NeedsSnapshot.THIRST -> {
                    if (!NeedScales.thirstEnabled()) {
                        return NeedResult.failed(NeedResult.Status.GATED, needId, "thirst is not simulated");
                    }
                    max = ThirstData.MAX_THIRST;
                    before = needs.thirst();
                }
                case NeedsSnapshot.QUENCHED -> {
                    if (!NeedScales.thirstEnabled()) {
                        return NeedResult.failed(NeedResult.Status.GATED, needId, "thirst is not simulated");
                    }
                    max = ThirstData.MAX_QUENCHED;
                    before = needs.quenched();
                }
                case NeedsSnapshot.ENERGY -> {
                    max = FatigueData.MAX_FATIGUE;
                    before = FatigueData.MAX_FATIGUE - needs.fatigue();
                }
                case NeedsSnapshot.TEMPERATURE -> {
                    if (!NeedScales.temperatureEnabled() || !needs.hasBodyTemp()) {
                        return NeedResult.failed(NeedResult.Status.GATED, needId, "temperature is not simulated");
                    }
                    min = TemperatureData.MIN_BODY_TENTHS;
                    max = TemperatureData.MAX_BODY_TENTHS;
                    before = needs.bodyTempTenths();
                }
                default -> {
                    return NeedResult.failed(NeedResult.Status.UNKNOWN_NEED, needId, "unknown need id");
                }
            }
            int wanted = relative ? before + amount : amount;
            int target = Math.max(min, Math.min(max, wanted));
            if (target == before) {
                return new NeedResult(NeedResult.Status.NO_CHANGE, needId, before, before, min, max, "");
            }
            apply(villager, needs, needId, before, target);
            int after = read(needs, needId);
            TownsteadVillagers.flush(villager);
            return new NeedResult(after == before ? NeedResult.Status.NO_CHANGE : NeedResult.Status.APPLIED,
                    needId, before, after, min, max, "");
        } catch (Throwable t) {
            ApiSupport.swallow("villagers.mutate", t);
            return NeedResult.failed(NeedResult.Status.ERROR, needId, t.toString());
        }
    }

    private static void apply(VillagerEntityMCA villager, TownsteadVillager.Needs needs, String needId,
                              int before, int target) {
        switch (needId) {
            case NeedsSnapshot.HUNGER -> needs.setHunger(target);
            case NeedsSnapshot.SATURATION -> needs.setSaturation(target);
            case NeedsSnapshot.THIRST -> needs.setThirst(target);
            case NeedsSnapshot.QUENCHED -> needs.setQuenched(target);
            case NeedsSnapshot.ENERGY -> {
                int delta = target - before;
                if (delta > 0) {
                    needs.restoreEnergy(delta);
                    if (!needs.collapsed()) ApiEvents.recovered(villager, target);
                } else {
                    needs.addFatigue(-delta);
                }
            }
            case NeedsSnapshot.TEMPERATURE -> needs.setBodyTempTenths(target);
            default -> { }
        }
    }

    private static int read(TownsteadVillager.Needs needs, String needId) {
        return switch (needId) {
            case NeedsSnapshot.HUNGER -> needs.hunger();
            case NeedsSnapshot.SATURATION -> Math.round(needs.saturation());
            case NeedsSnapshot.THIRST -> needs.thirst();
            case NeedsSnapshot.QUENCHED -> needs.quenched();
            case NeedsSnapshot.ENERGY -> FatigueData.MAX_FATIGUE - needs.fatigue();
            case NeedsSnapshot.TEMPERATURE -> needs.bodyTempTenths();
            default -> 0;
        };
    }
}
