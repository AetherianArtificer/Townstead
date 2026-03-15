package com.aetherianartificer.townstead;

//? if forge {
/*
import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.farming.FarmingPolicyData;
import com.aetherianartificer.townstead.farming.FarmingPolicySetPayload;
import com.aetherianartificer.townstead.farming.FarmingPolicySyncPayload;
import com.aetherianartificer.townstead.hunger.*;
import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.shift.ShiftSyncPayload;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSetPayload;
import com.aetherianartificer.townstead.thirst.ThirstSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Function;

public final class TownsteadNetwork {
    private TownsteadNetwork() {}

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Townstead.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    public static void register() {
        // Server -> Client
        registerS2C(HungerSyncPayload.class, HungerSyncPayload::write, HungerSyncPayload::read,
                TownsteadNetwork::handleHungerSync);
        registerS2C(FarmStatusSyncPayload.class, FarmStatusSyncPayload::write, FarmStatusSyncPayload::read,
                TownsteadNetwork::handleFarmStatusSync);
        registerS2C(ButcherStatusSyncPayload.class, ButcherStatusSyncPayload::write, ButcherStatusSyncPayload::read,
                TownsteadNetwork::handleButcherStatusSync);
        registerS2C(FarmingPolicySyncPayload.class, FarmingPolicySyncPayload::write, FarmingPolicySyncPayload::read,
                TownsteadNetwork::handleFarmingPolicySync);
        registerS2C(ButcherPolicySyncPayload.class, ButcherPolicySyncPayload::write, ButcherPolicySyncPayload::read,
                TownsteadNetwork::handleButcherPolicySync);

        // Client -> Server
        registerC2S(HungerSetPayload.class, HungerSetPayload::write, HungerSetPayload::read,
                TownsteadNetwork::handleHungerSet);
        registerC2S(FarmingPolicySetPayload.class, FarmingPolicySetPayload::write, FarmingPolicySetPayload::read,
                TownsteadNetwork::handleFarmingPolicySet);
        registerC2S(ButcherPolicySetPayload.class, ButcherPolicySetPayload::write, ButcherPolicySetPayload::read,
                TownsteadNetwork::handleButcherPolicySet);

        if (ThirstWasTakenBridge.INSTANCE.isActive()) {
            registerS2C(ThirstSyncPayload.class, ThirstSyncPayload::write, ThirstSyncPayload::read,
                    TownsteadNetwork::handleThirstSync);
            registerC2S(ThirstSetPayload.class, ThirstSetPayload::write, ThirstSetPayload::read,
                    TownsteadNetwork::handleThirstSet);
        }

        // Shift management
        registerS2C(ShiftSyncPayload.class, ShiftSyncPayload::write, ShiftSyncPayload::read,
                TownsteadNetwork::handleShiftSync);
        registerC2S(ShiftSetPayload.class, ShiftSetPayload::write, ShiftSetPayload::read,
                TownsteadNetwork::handleShiftSet);
    }

    // ── Send helpers ──

    public static <T> void sendToPlayer(ServerPlayer player, T payload) {
        CHANNEL.sendTo(payload, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static <T> void sendToTrackingEntity(Entity entity, T payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload);
    }

    public static <T> void sendToServer(T payload) {
        CHANNEL.sendToServer(payload);
    }

    // ── Registration helpers ──

    @SuppressWarnings("unchecked")
    private static <T> void registerS2C(Class<T> clazz,
                                        java.util.function.BiConsumer<T, FriendlyByteBuf> encoder,
                                        Function<FriendlyByteBuf, T> decoder,
                                        java.util.function.Consumer<T> handler) {
        CHANNEL.registerMessage(nextId++, clazz,
                encoder::accept,
                decoder::apply,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> handler.accept(msg));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerC2S(Class<T> clazz,
                                        java.util.function.BiConsumer<T, FriendlyByteBuf> encoder,
                                        Function<FriendlyByteBuf, T> decoder,
                                        java.util.function.BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(nextId++, clazz,
                encoder::accept,
                decoder::apply,
                (msg, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer sp = ctx.get().getSender();
                        if (sp != null) handler.accept(msg, sp);
                    });
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    // ── Client-side handlers (S2C) ──

    private static void handleHungerSync(HungerSyncPayload payload) {
        HungerClientStore.set(
                payload.entityId(), payload.hunger(),
                payload.farmerTier(), payload.farmerXp(), payload.farmerXpToNext(),
                payload.butcherTier(), payload.butcherXp(), payload.butcherXpToNext(),
                payload.cookTier(), payload.cookXp(), payload.cookXpToNext()
        );
    }

    private static void handleThirstSync(ThirstSyncPayload payload) {
        if (!ThirstWasTakenBridge.INSTANCE.isActive()) return;
        ThirstClientStore.set(payload.entityId(), payload.thirst(), payload.quenched());
    }

    private static void handleFarmStatusSync(FarmStatusSyncPayload payload) {
        HungerClientStore.setFarmBlockedReason(payload.entityId(), payload.blockedReasonId());
    }

    private static void handleButcherStatusSync(ButcherStatusSyncPayload payload) {
        HungerClientStore.setButcherBlockedReason(payload.entityId(), payload.blockedReasonId());
    }

    private static void handleFarmingPolicySync(FarmingPolicySyncPayload payload) {
        FarmingPolicyClientStore.set(payload.patternId(), payload.tier(), payload.areaCount());
    }

    private static void handleButcherPolicySync(ButcherPolicySyncPayload payload) {
        ButcherPolicyClientStore.set(payload.profileId(), payload.tier(), payload.areaCount());
    }

    // ── Server-side handlers (C2S) ──

    private static void handleHungerSet(HungerSetPayload payload, ServerPlayer sp) {
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        int currentHunger = HungerData.getHunger(hunger);

        if (payload.hunger() == -1) {
            sendToPlayer(sp, Townstead.townstead$hungerSync(villager, hunger));
            return;
        }

        int newHunger = payload.hunger();
        Townstead.LOGGER.debug("HungerSet packet: entityId={}, target={}", payload.entityId(), newHunger);
        HungerData.setHunger(hunger, newHunger);
        if (newHunger > currentHunger) {
            HungerData.setSaturation(hunger, Math.min(newHunger, HungerData.MAX_SATURATION));
        }
        HungerData.setExhaustion(hunger, 0f);
        villager.getPersistentData().put("townstead_hunger", hunger);
        HungerSyncPayload sync = Townstead.townstead$hungerSync(villager, hunger);
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
        Townstead.LOGGER.debug("Hunger set: {} -> {}", currentHunger, sync.hunger());
    }

    private static void handleThirstSet(ThirstSetPayload payload, ServerPlayer sp) {
        if (!ThirstWasTakenBridge.INSTANCE.isActive()) return;
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
        int currentThirst = ThirstData.getThirst(thirst);

        if (payload.thirst() == -1) {
            sendToPlayer(sp, Townstead.townstead$thirstSync(villager, thirst));
            return;
        }

        int newThirst = payload.thirst();
        Townstead.LOGGER.debug("ThirstSet packet: entityId={}, target={}", payload.entityId(), newThirst);
        ThirstData.setThirst(thirst, newThirst);
        if (newThirst > currentThirst) {
            ThirstData.setQuenched(thirst, Math.min(newThirst, ThirstData.MAX_QUENCHED));
        }
        ThirstData.setExhaustion(thirst, 0f);
        villager.getPersistentData().put("townstead_thirst", thirst);
        ThirstSyncPayload sync = Townstead.townstead$thirstSync(villager, thirst);
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
    }

    private static void handleFarmingPolicySet(FarmingPolicySetPayload payload, ServerPlayer sp) {
        FarmingPolicyData data = FarmingPolicyData.get(sp.serverLevel());
        if (payload.tier() == -1) {
            sendToPlayer(sp, new FarmingPolicySyncPayload(
                    data.getDefaultPatternId(), data.getDefaultTier(), data.getAreas().size()
            ));
            return;
        }
        if (!sp.hasPermissions(2)) return;
        data.setDefaultPolicy(payload.patternId(), payload.tier());
        sendToPlayer(sp, new FarmingPolicySyncPayload(
                data.getDefaultPatternId(), data.getDefaultTier(), data.getAreas().size()
        ));
    }

    private static void handleButcherPolicySet(ButcherPolicySetPayload payload, ServerPlayer sp) {
        ButcherPolicyData data = ButcherPolicyData.get(sp.serverLevel());
        if (payload.tier() == -1) {
            sendToPlayer(sp, new ButcherPolicySyncPayload(
                    data.getDefaultProfileId(), data.getDefaultTier(), data.getAreas().size()
            ));
            return;
        }
        if (!sp.hasPermissions(2)) return;
        data.setDefaultPolicy(payload.profileId(), payload.tier());
        sendToPlayer(sp, new ButcherPolicySyncPayload(
                data.getDefaultProfileId(), data.getDefaultTier(), data.getAreas().size()
        ));
    }

    private static void handleShiftSync(ShiftSyncPayload payload) {
        ShiftClientStore.set(payload.villagerUuid(), payload.shifts());
    }

    private static void handleShiftSet(ShiftSetPayload payload, ServerPlayer sp) {
        if (!sp.hasPermissions(2)) return;

        VillagerEntityMCA villager = null;
        for (net.minecraft.server.level.ServerLevel level : sp.getServer().getAllLevels()) {
            Entity entity = level.getEntity(payload.villagerUuid());
            if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
        }
        if (villager == null) return;

        // Query mode
        if (payload.shifts().length == 0) {
            CompoundTag shiftTag = villager.getPersistentData().getCompound("townstead_shift");
            sendToPlayer(sp, new ShiftSyncPayload(payload.villagerUuid(), ShiftData.getShifts(shiftTag)));
            return;
        }

        if (payload.shifts().length != ShiftData.HOURS_PER_DAY) return;

        CompoundTag shiftTag = villager.getPersistentData().getCompound("townstead_shift");
        ShiftData.setShifts(shiftTag, payload.shifts());
        villager.getPersistentData().put("townstead_shift", shiftTag);

        ShiftScheduleApplier.apply(villager);

        ShiftSyncPayload sync = new ShiftSyncPayload(payload.villagerUuid(), payload.shifts());
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
    }
}
*///?}
