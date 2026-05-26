package com.aetherianartificer.townstead;

//? if forge {
/*
import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsPayload;
import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsStore;
import com.aetherianartificer.townstead.compat.travelerstitles.TravelersTitlesBridge;
import com.aetherianartificer.townstead.compat.travelerstitles.VillageEnterTitlePayload;
import com.aetherianartificer.townstead.farming.FieldPostConfigSetPayload;
import com.aetherianartificer.townstead.farming.FieldPostConfigSyncPayload;
import com.aetherianartificer.townstead.farming.FieldPostGridSyncPayload;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.FatigueSetPayload;
import com.aetherianartificer.townstead.fatigue.FatigueSyncPayload;
import com.aetherianartificer.townstead.hunger.*;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.profession.ProfessionClientStore;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.profession.ProfessionScanner;
import com.aetherianartificer.townstead.profession.ProfessionSlotRules;
import com.aetherianartificer.townstead.profession.ProfessionSetPayload;
import com.aetherianartificer.townstead.profession.ProfessionSyncPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.shift.ShiftSyncPayload;
import com.aetherianartificer.townstead.shift.ShiftWeekSetPayload;
import com.aetherianartificer.townstead.shift.ShiftWeekSyncPayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlan;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanApplyPayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanClientStore;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanDeletePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanRegistry;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSavePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSavedData;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSyncPayload;
import com.aetherianartificer.townstead.shift.template.Chronotype;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateApplyPayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateClientStore;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateDeletePayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateSavePayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateSavedData;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateSyncPayload;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagerState;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import com.aetherianartificer.townstead.village.VillageResidentRoster;
import com.aetherianartificer.townstead.village.VillageResidentsSyncPayload;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSetPayload;
import com.aetherianartificer.townstead.thirst.ThirstSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
        registerS2C(FishermanStatusSyncPayload.class, FishermanStatusSyncPayload::write, FishermanStatusSyncPayload::read,
                TownsteadNetwork::handleFishermanStatusSync);
        registerS2C(com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload.class,
                (p, buf) -> com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload.write(buf, p),
                com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload::read,
                TownsteadNetwork::handleVillageSpiritSync);
        registerC2S(com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload.class,
                (p, buf) -> p.write(buf),
                com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload::read,
                TownsteadNetwork::handleVillageSpiritQuery);
        registerS2C(FishermanHookLinkPayload.class, FishermanHookLinkPayload::write, FishermanHookLinkPayload::read,
                TownsteadNetwork::handleFishermanHookLink);

        // Client -> Server
        registerC2S(HungerSetPayload.class, HungerSetPayload::write, HungerSetPayload::read,
                TownsteadNetwork::handleHungerSet);

        if (ThirstBridgeResolver.anyThirstModLoaded()) {
            registerS2C(ThirstSyncPayload.class, ThirstSyncPayload::write, ThirstSyncPayload::read,
                    TownsteadNetwork::handleThirstSync);
            registerC2S(ThirstSetPayload.class, ThirstSetPayload::write, ThirstSetPayload::read,
                    TownsteadNetwork::handleThirstSet);
        }

        // Fatigue management
        registerS2C(FatigueSyncPayload.class, FatigueSyncPayload::write, FatigueSyncPayload::read,
                TownsteadNetwork::handleFatigueSync);
        registerC2S(FatigueSetPayload.class, FatigueSetPayload::write, FatigueSetPayload::read,
                TownsteadNetwork::handleFatigueSet);

        // Shift management
        registerS2C(ShiftSyncPayload.class, ShiftSyncPayload::write, ShiftSyncPayload::read,
                TownsteadNetwork::handleShiftSync);
        registerC2S(ShiftSetPayload.class, ShiftSetPayload::write, ShiftSetPayload::read,
                TownsteadNetwork::handleShiftSet);

        // Shift templates
        registerS2C(ShiftTemplateSyncPayload.class, ShiftTemplateSyncPayload::write, ShiftTemplateSyncPayload::read,
                TownsteadNetwork::handleShiftTemplateSync);
        registerC2S(ShiftTemplateSavePayload.class, ShiftTemplateSavePayload::write, ShiftTemplateSavePayload::read,
                TownsteadNetwork::handleShiftTemplateSave);
        registerC2S(ShiftTemplateDeletePayload.class, ShiftTemplateDeletePayload::write, ShiftTemplateDeletePayload::read,
                TownsteadNetwork::handleShiftTemplateDelete);
        registerC2S(ShiftTemplateApplyPayload.class, ShiftTemplateApplyPayload::write, ShiftTemplateApplyPayload::read,
                TownsteadNetwork::handleShiftTemplateApply);

        // Weekly schedule + week plans
        registerS2C(ShiftWeekSyncPayload.class, ShiftWeekSyncPayload::write, ShiftWeekSyncPayload::read,
                TownsteadNetwork::handleShiftWeekSync);
        registerC2S(ShiftWeekSetPayload.class, ShiftWeekSetPayload::write, ShiftWeekSetPayload::read,
                TownsteadNetwork::handleShiftWeekSet);
        registerS2C(WeekPlanSyncPayload.class, WeekPlanSyncPayload::write, WeekPlanSyncPayload::read,
                TownsteadNetwork::handleWeekPlanSync);
        registerC2S(WeekPlanSavePayload.class, WeekPlanSavePayload::write, WeekPlanSavePayload::read,
                TownsteadNetwork::handleWeekPlanSave);
        registerC2S(WeekPlanDeletePayload.class, WeekPlanDeletePayload::write, WeekPlanDeletePayload::read,
                TownsteadNetwork::handleWeekPlanDelete);
        registerC2S(WeekPlanApplyPayload.class, WeekPlanApplyPayload::write, WeekPlanApplyPayload::read,
                TownsteadNetwork::handleWeekPlanApply);

        // Profession management
        registerC2S(ProfessionQueryPayload.class, ProfessionQueryPayload::write, ProfessionQueryPayload::read,
                TownsteadNetwork::handleProfessionQuery);
        registerS2C(ProfessionSyncPayload.class, ProfessionSyncPayload::write, ProfessionSyncPayload::read,
                TownsteadNetwork::handleProfessionSync);
        registerS2C(VillageResidentsSyncPayload.class, VillageResidentsSyncPayload::write, VillageResidentsSyncPayload::read,
                TownsteadNetwork::handleVillageResidentsSync);
        registerC2S(ProfessionSetPayload.class, ProfessionSetPayload::write, ProfessionSetPayload::read,
                TownsteadNetwork::handleProfessionSet);

        // Field Post
        registerC2S(FieldPostConfigSetPayload.class, FieldPostConfigSetPayload::write, FieldPostConfigSetPayload::read,
                TownsteadNetwork::handleFieldPostConfigSet);
        registerS2C(FieldPostConfigSyncPayload.class, FieldPostConfigSyncPayload::write, FieldPostConfigSyncPayload::read,
                TownsteadNetwork::handleFieldPostConfigSync);
        registerS2C(FieldPostGridSyncPayload.class, FieldPostGridSyncPayload::write, FieldPostGridSyncPayload::read,
                TownsteadNetwork::handleFieldPostGridSync);

        // Traveler's Titles integration
        registerC2S(ClientCapsPayload.class, ClientCapsPayload::write, ClientCapsPayload::read,
                TownsteadNetwork::handleClientCaps);
        registerS2C(VillageEnterTitlePayload.class, VillageEnterTitlePayload::write, VillageEnterTitlePayload::read,
                TownsteadNetwork::handleVillageEnterTitle);

        // Emotecraft animation triggers
        registerS2C(com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload.class,
                com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload::write,
                com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload::read,
                TownsteadNetwork::handleEmoteTriggerS2C);
        registerC2S(com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload.class,
                com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload::write,
                com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload::read,
                TownsteadNetwork::handleEmoteTriggerC2S);
        registerC2S(com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload.class,
                com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload::write,
                com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload::read,
                TownsteadNetwork::handleGestureNotifyC2S);
        registerC2S(com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload.class,
                com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload::write,
                com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload::read,
                TownsteadNetwork::handleDialogueStateC2S);
        registerS2C(com.aetherianartificer.townstead.calendar.CalendarSyncPayload.class,
                com.aetherianartificer.townstead.calendar.CalendarSyncPayload::write,
                com.aetherianartificer.townstead.calendar.CalendarSyncPayload::read,
                TownsteadNetwork::handleCalendarSync);
        registerS2C(com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload.class,
                com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload::write,
                com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload::read,
                TownsteadNetwork::handleVillagerLifeSync);
        registerS2C(com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload.class,
                com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload::write,
                com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload::read,
                TownsteadNetwork::handleStampSync);
        registerC2S(com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload.class,
                com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload::write,
                com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload::read,
                TownsteadNetwork::handleStampAction);

        // Origins
        registerC2S(com.aetherianartificer.townstead.origin.OriginSetC2SPayload.class,
                com.aetherianartificer.townstead.origin.OriginSetC2SPayload::write,
                com.aetherianartificer.townstead.origin.OriginSetC2SPayload::read,
                TownsteadNetwork::handleOriginSet);
        registerS2C(com.aetherianartificer.townstead.origin.OriginSyncS2CPayload.class,
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload::write,
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload::read,
                TownsteadNetwork::handleOriginSync);
        registerS2C(com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload.class,
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload::write,
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload::read,
                TownsteadNetwork::handleOriginCatalogSync);
    }

    private static void handleOriginSet(
            com.aetherianartificer.townstead.origin.OriginSetC2SPayload payload, ServerPlayer sp) {
        com.aetherianartificer.townstead.origin.OriginServerLogic.Result result =
                com.aetherianartificer.townstead.origin.OriginServerLogic.applyOrRequest(
                        sp, payload.entityId(), payload.originId());
        if (result == null) return;
        com.aetherianartificer.townstead.origin.OriginSyncS2CPayload sync =
                new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(result.targetId(), result.originId());
        sendToPlayer(sp, sync);
        if (result.targetId() != com.aetherianartificer.townstead.origin.OriginSetC2SPayload.SELF) {
            Entity tracked = sp.serverLevel().getEntity(result.targetId());
            if (tracked != null) sendToTrackingEntity(tracked, sync);
        }
    }

    private static void handleOriginSync(com.aetherianartificer.townstead.origin.OriginSyncS2CPayload payload) {
        com.aetherianartificer.townstead.client.origin.OriginClientStore.set(payload.entityId(), payload.originId());
    }

    private static void handleOriginCatalogSync(com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload payload) {
        com.aetherianartificer.townstead.client.origin.OriginCatalogClient.setFrom(payload);
    }

    private static void handleCalendarSync(com.aetherianartificer.townstead.calendar.CalendarSyncPayload payload) {
        com.aetherianartificer.townstead.calendar.CalendarClientStore.setFrom(payload);
    }

    private static void handleVillagerLifeSync(com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload payload) {
        com.aetherianartificer.townstead.calendar.LifeClientStore.setFrom(payload);
    }

    private static void handleStampSync(com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload payload) {
        com.aetherianartificer.townstead.calendar.CalendarStampClientStore.setFrom(payload);
    }

    private static void handleStampAction(
            com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload payload, ServerPlayer sp) {
        if (com.aetherianartificer.townstead.calendar.CalendarStampServer.apply(sp, payload)) {
            broadcastStampSync(sp.getServer());
        }
    }

    public static void broadcastStampSync(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        // Per-player: each gets their own private stamps plus all public ones.
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendToPlayer(sp, com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(server, sp));
        }
    }

    private static void handleDialogueStateC2S(
            com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload payload,
            ServerPlayer sp
    ) {
        Entity target = sp.serverLevel().getEntity(payload.villagerEntityId());
        if (!(target instanceof net.minecraft.world.entity.LivingEntity villager)) return;
        long gameTime = sp.serverLevel().getGameTime();
        if (payload.isOpen()) {
            com.aetherianartificer.townstead.reaction.trigger.event.DialogueStateTracker.onOpen(
                    villager, sp.getUUID(), gameTime);
        } else {
            com.aetherianartificer.townstead.reaction.trigger.event.DialogueStateTracker.onClose(
                    villager, gameTime);
        }
    }

    private static void handleGestureNotifyC2S(
            com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload payload,
            ServerPlayer sp
    ) {
        com.aetherianartificer.townstead.reaction.trigger.event.GestureBroadcaster.broadcast(
                sp.serverLevel(), sp, payload.emoteName());
    }

    private static void handleEmoteTriggerS2C(com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload payload) {
        com.aetherianartificer.townstead.client.animation.emote.EmoteClientHandler.handle(payload);
    }

    private static void handleEmoteTriggerC2S(
            com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload payload,
            ServerPlayer sp
    ) {
        Entity target = sp.serverLevel().getEntity(payload.targetEntityId());
        if (!(target instanceof VillagerEntityMCA) && !(target instanceof net.minecraft.world.entity.player.Player)) return;
        ResourceLocation id;
        try {
            id = new ResourceLocation(payload.emoteId());
        } catch (Exception e) {
            return;
        }
        com.aetherianartificer.townstead.emote.AiEmoteScheduler.playEmote(
                (net.minecraft.world.entity.LivingEntity) target,
                id,
                payload.loopOverride(),
                payload.speed());
        com.aetherianartificer.townstead.reaction.trigger.event.GestureBroadcaster.broadcast(
                sp.serverLevel(), target, id.getPath());
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

    public static <T> void sendToAll(T payload) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), payload);
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
                payload.cookTier(), payload.cookXp(), payload.cookXpToNext()
        );
    }

    private static void handleThirstSync(ThirstSyncPayload payload) {
        if (!ThirstBridgeResolver.isActive()) return;
        ThirstClientStore.set(payload.entityId(), payload.thirst(), payload.quenched());
    }

    private static void handleFarmStatusSync(FarmStatusSyncPayload payload) {
        HungerClientStore.setFarmBlockedReason(payload.entityId(), payload.blockedReasonId());
    }

    private static void handleButcherStatusSync(ButcherStatusSyncPayload payload) {
        HungerClientStore.setButcherBlockedReason(payload.entityId(), payload.blockedReasonId());
    }

    private static void handleFishermanStatusSync(FishermanStatusSyncPayload payload) {
        HungerClientStore.setFishermanBlockedReason(payload.entityId(), payload.blockedReasonId());
    }

    private static void handleVillageSpiritSync(com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload payload) {
        com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.put(payload);
    }

    private static void handleVillageSpiritQuery(
            com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload payload,
            ServerPlayer sp) {
        Optional<Village> village = Village.findNearest(sp);
        if (village.isEmpty()) return;
        com.aetherianartificer.townstead.spirit.VillageSpiritQueryScheduler.enqueue(
                sp.serverLevel(), village.get(), sp);
    }

    private static void handleFishermanHookLink(FishermanHookLinkPayload payload) {
        com.aetherianartificer.townstead.hunger.FishermanHookLinkStore.link(payload.hookEntityId(), payload.villagerEntityId(),
                payload.x(), payload.y(), payload.z());
    }

    // ── Server-side handlers (C2S) ──

    private static void handleHungerSet(HungerSetPayload payload, ServerPlayer sp) {
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        int currentHunger = state.needs().hunger();

        if (payload.hunger() == -1) {
            sendToPlayer(sp, Townstead.townstead$hungerSync(villager, state.needs().hungerTag()));
            return;
        }

        int newHunger = payload.hunger();
        Townstead.LOGGER.debug("HungerSet packet: entityId={}, target={}", payload.entityId(), newHunger);
        state.needs().setHunger(newHunger);
        if (newHunger > currentHunger) {
            state.needs().setSaturation(Math.min(newHunger, HungerData.MAX_SATURATION));
        }
        state.needs().setHungerExhaustion(0f);
        HungerSyncPayload sync = Townstead.townstead$hungerSync(villager, state.needs().hungerTag());
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
        Townstead.LOGGER.debug("Hunger set: {} -> {}", currentHunger, sync.hunger());
    }

    private static void handleThirstSet(ThirstSetPayload payload, ServerPlayer sp) {
        if (!ThirstBridgeResolver.isActive()) return;
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        int currentThirst = state.needs().thirst();

        if (payload.thirst() == -1) {
            sendToPlayer(sp, Townstead.townstead$thirstSync(villager, state.needs().thirstTag()));
            return;
        }

        int newThirst = payload.thirst();
        Townstead.LOGGER.debug("ThirstSet packet: entityId={}, target={}", payload.entityId(), newThirst);
        state.needs().setThirst(newThirst);
        if (newThirst > currentThirst) {
            state.needs().setQuenched(Math.min(newThirst, ThirstData.MAX_QUENCHED));
        }
        state.needs().setThirstExhaustion(0f);
        ThirstSyncPayload sync = Townstead.townstead$thirstSync(villager, state.needs().thirstTag());
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
    }

    private static void handleFatigueSync(FatigueSyncPayload payload) {
        FatigueClientStore.set(payload.entityId(), payload.fatigue(), payload.collapsed());
    }

    private static void handleFatigueSet(FatigueSetPayload payload, ServerPlayer sp) {
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        int currentFatigue = state.needs().fatigue();

        if (payload.fatigue() == -1) {
            sendToPlayer(sp, Townstead.townstead$fatigueSync(villager, state.needs().fatigueTag()));
            return;
        }

        int newFatigue = payload.fatigue();
        Townstead.LOGGER.debug("FatigueSet packet: entityId={}, target={}", payload.entityId(), newFatigue);
        state.needs().setFatigue(newFatigue);
        if (newFatigue < FatigueData.COLLAPSE_THRESHOLD) {
            state.needs().setCollapsed(false);
        }
        if (newFatigue < FatigueData.RECOVERY_GATE) {
            state.needs().setGated(false);
        }
        FatigueSyncPayload sync = Townstead.townstead$fatigueSync(villager, state.needs().fatigueTag());
        sendToPlayer(sp, sync);
        sendToTrackingEntity(villager, sync);
    }

    private static void handleShiftSync(ShiftSyncPayload payload) {
        ShiftClientStore.set(payload.villagerUuid(), payload.shifts());
        VillageResidentClientStore.updateShifts(payload.villagerUuid(), payload.shifts());
    }

    private static void handleShiftSet(ShiftSetPayload payload, ServerPlayer sp) {

        VillagerEntityMCA villager = null;
        for (net.minecraft.server.level.ServerLevel level : sp.getServer().getAllLevels()) {
            Entity entity = level.getEntity(payload.villagerUuid());
            if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
        }
        if (villager == null) return;

        // Query mode: reply with the daily array and the weekly state together.
        if (payload.shifts().length == 0) {
            TownsteadVillager state = TownsteadVillagers.get(villager);
            CompoundTag shiftTag = state.schedule().toTag();
            sendToPlayer(sp, new ShiftSyncPayload(payload.villagerUuid(), ShiftData.getShifts(shiftTag)));
            sendToPlayer(sp, new ShiftWeekSyncPayload(payload.villagerUuid(),
                    ShiftData.getMode(shiftTag), ShiftData.getWeekDayTemplates(shiftTag)));
            return;
        }

        if (payload.shifts().length != ShiftData.HOURS_PER_DAY) return;

        writeVillagerShifts(villager, payload.shifts(), sp);
    }

    private static void writeVillagerShifts(VillagerEntityMCA villager, int[] shifts, ServerPlayer originator) {
        writeVillagerShifts(villager, shifts, originator, "");
    }

    private static void writeVillagerShifts(VillagerEntityMCA villager, int[] shifts, ServerPlayer originator,
                                            String templateId) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.schedule().setShifts(shifts);
        state.schedule().setTemplateId(templateId);

        ShiftScheduleApplier.apply(villager);

        ShiftSyncPayload sync = new ShiftSyncPayload(villager.getUUID(), shifts);
        if (originator != null) sendToPlayer(originator, sync);
        sendToTrackingEntity(villager, sync);

        if (originator != null) {
            sendToPlayer(originator,
                    new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(originator)));
        }
    }

    private static void handleShiftTemplateSync(ShiftTemplateSyncPayload payload) {
        ShiftTemplateClientStore.set(payload.templates());
    }

    private static void handleShiftTemplateSave(ShiftTemplateSavePayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        if (payload.shifts() == null || payload.shifts().length != ShiftData.HOURS_PER_DAY) return;
        for (int s : payload.shifts()) {
            if (s < 0 || s >= ShiftData.ORDINAL_TO_ACTIVITY.length) return;
        }
        String name = payload.name() != null && !payload.name().isBlank() ? payload.name().trim() : "Untitled";
        if (name.length() > 64) name = name.substring(0, 64);
        ResourceLocation id;
        String idStr = payload.id();
        if (idStr == null || idStr.isBlank() || !idStr.startsWith(ShiftTemplate.USER_NAMESPACE + ":")) {
            String slug = "u_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            id = new ResourceLocation(ShiftTemplate.USER_NAMESPACE, slug);
        } else {
            try {
                id = new ResourceLocation(idStr);
            } catch (Exception ex) { return; }
            if (!ShiftTemplate.USER_NAMESPACE.equals(id.getNamespace())) return;
        }
        java.util.Optional<Chronotype> chrono = payload.chronotypeName().map(Chronotype::fromName);
        ShiftTemplate template;
        try {
            template = new ShiftTemplate(id, name, payload.shifts(), chrono, false);
        } catch (IllegalArgumentException ex) { return; }
        ShiftTemplateSavedData.get(server).put(template);
        broadcastShiftTemplateSync(server);
    }

    private static void handleShiftTemplateDelete(ShiftTemplateDeletePayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        if (!ShiftTemplate.USER_NAMESPACE.equals(payload.id().getNamespace())) return;
        if (ShiftTemplateSavedData.get(server).remove(payload.id())) {
            broadcastShiftTemplateSync(server);
        }
    }

    private static void handleShiftTemplateApply(ShiftTemplateApplyPayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        ShiftTemplate template = ShiftTemplateRegistry.resolve(server, payload.templateId()).orElse(null);
        if (template == null) {
            Townstead.LOGGER.warn("Apply requested for unknown shift template {}", payload.templateId());
            return;
        }
        int[] shifts = template.copyShifts();
        String templateIdStr = template.id().toString();
        for (java.util.UUID uuid : payload.villagerUuids()) {
            VillagerEntityMCA villager = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
            }
            if (villager == null) continue;
            writeVillagerShifts(villager, shifts, sp, templateIdStr);
        }
    }

    public static void sendShiftTemplateSync(ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        java.util.List<ShiftTemplate> all = ShiftTemplateRegistry.combinedFor(server);
        sendToPlayer(sp, new ShiftTemplateSyncPayload(all));
    }

    public static void broadcastShiftTemplateSync(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        java.util.List<ShiftTemplate> all = ShiftTemplateRegistry.combinedFor(server);
        ShiftTemplateSyncPayload payload = new ShiftTemplateSyncPayload(all);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendToPlayer(sp, payload);
        }
    }

    // ── Weekly schedule + week plan handlers ──

    private static void handleShiftWeekSync(ShiftWeekSyncPayload payload) {
        ShiftClientStore.setWeek(payload.villagerUuid(), payload.mode(), payload.weekDays());
    }

    private static void handleShiftWeekSet(ShiftWeekSetPayload payload, ServerPlayer sp) {
        VillagerEntityMCA villager = findVillager(sp.getServer(), payload.villagerUuid());
        if (villager == null) return;
        writeVillagerWeek(villager, payload.mode(), payload.weekDays(), sp);
    }

    private static void handleWeekPlanSync(WeekPlanSyncPayload payload) {
        WeekPlanClientStore.set(payload.plans());
    }

    private static void handleWeekPlanSave(WeekPlanSavePayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        String name = payload.name() != null && !payload.name().isBlank() ? payload.name().trim() : "Untitled";
        if (name.length() > 64) name = name.substring(0, 64);
        ResourceLocation id;
        String idStr = payload.id();
        if (idStr == null || idStr.isBlank() || !idStr.startsWith(WeekPlan.USER_NAMESPACE + ":")) {
            String slug = "u_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            id = new ResourceLocation(WeekPlan.USER_NAMESPACE, slug);
        } else {
            try {
                id = new ResourceLocation(idStr);
            } catch (Exception ex) { return; }
            if (!WeekPlan.USER_NAMESPACE.equals(id.getNamespace())) return;
        }
        WeekPlan plan;
        try {
            plan = new WeekPlan(id, name, payload.days() == null ? java.util.List.of() : payload.days(), false);
        } catch (IllegalArgumentException ex) { return; }
        WeekPlanSavedData.get(server).put(plan);
        broadcastWeekPlanSync(server);
    }

    private static void handleWeekPlanDelete(WeekPlanDeletePayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        if (!WeekPlan.USER_NAMESPACE.equals(payload.id().getNamespace())) return;
        if (WeekPlanSavedData.get(server).remove(payload.id())) broadcastWeekPlanSync(server);
    }

    private static void handleWeekPlanApply(WeekPlanApplyPayload payload, ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        WeekPlan plan = WeekPlanRegistry.resolve(server, payload.planId()).orElse(null);
        if (plan == null) {
            Townstead.LOGGER.warn("Apply requested for unknown week plan {}", payload.planId());
            return;
        }
        java.util.List<String> days = plan.copyDays();
        for (java.util.UUID uuid : payload.villagerUuids()) {
            VillagerEntityMCA villager = findVillager(server, uuid);
            if (villager == null) continue;
            writeVillagerWeek(villager, ShiftData.MODE_WEEKLY, days, sp);
        }
    }

    private static VillagerEntityMCA findVillager(net.minecraft.server.MinecraftServer server, java.util.UUID uuid) {
        if (server == null) return null;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof VillagerEntityMCA v) return v;
        }
        return null;
    }

    private static void writeVillagerWeek(VillagerEntityMCA villager, String mode,
                                          java.util.List<String> weekDays, ServerPlayer originator) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.schedule().setMode(mode);
        state.schedule().setWeekDayTemplates(weekDays);
        CompoundTag shiftTag = state.schedule().toTag();

        ShiftScheduleApplier.apply(villager);

        ShiftWeekSyncPayload sync = new ShiftWeekSyncPayload(
                villager.getUUID(), ShiftData.getMode(shiftTag), ShiftData.getWeekDayTemplates(shiftTag));
        if (originator != null) sendToPlayer(originator, sync);
        sendToTrackingEntity(villager, sync);
    }

    public static void sendWeekPlanSync(ServerPlayer sp) {
        net.minecraft.server.MinecraftServer server = sp.getServer();
        if (server == null) return;
        sendToPlayer(sp, new WeekPlanSyncPayload(WeekPlanRegistry.combinedFor(server)));
    }

    public static void broadcastWeekPlanSync(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        WeekPlanSyncPayload payload = new WeekPlanSyncPayload(WeekPlanRegistry.combinedFor(server));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendToPlayer(sp, payload);
        }
    }

    private static void handleProfessionQuery(ProfessionQueryPayload payload, ServerPlayer sp) {
        ProfessionScanner.ScanResult scan = ProfessionScanner.scanAvailableProfessions(sp);
        sendToPlayer(sp, new ProfessionSyncPayload(scan.professionIds(), scan.usedSlots(), scan.maxSlots()));
        sendToPlayer(sp, new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(sp)));
    }

    private static void handleProfessionSync(ProfessionSyncPayload payload) {
        ProfessionClientStore.set(payload.professionIds(), payload.usedSlots(), payload.maxSlots());
    }

    private static void handleVillageResidentsSync(VillageResidentsSyncPayload payload) {
        VillageResidentClientStore.set(payload.residents());
        for (VillageResidentClientStore.Resident resident : payload.residents()) {
            ShiftClientStore.set(resident.villagerUuid(), resident.shifts());
        }
    }

    private static void handleProfessionSet(ProfessionSetPayload payload, ServerPlayer sp) {

        VillagerEntityMCA villager = null;
        for (net.minecraft.server.level.ServerLevel level : sp.getServer().getAllLevels()) {
            Entity entity = level.getEntity(payload.villagerUuid());
            if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
        }
        if (villager == null) return;

        net.minecraft.resources.ResourceLocation profId = new net.minecraft.resources.ResourceLocation(payload.professionId());
        net.minecraft.world.entity.npc.VillagerProfession newProf =
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(profId);
        if (newProf == null) return;

        townstead$assignProfession(sp, villager, newProf);
        ProfessionScanner.ScanResult scan = ProfessionScanner.scanAvailableProfessions(sp);
        sendToPlayer(sp, new ProfessionSyncPayload(scan.professionIds(), scan.usedSlots(), scan.maxSlots()));
        sendToPlayer(sp, new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(sp)));
    }

    private static void townstead$assignProfession(ServerPlayer sp, VillagerEntityMCA villager, VillagerProfession newProf) {
        if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

        VillagerProfession oldProf = villager.getVillagerData().getProfession();
        townstead$clearProfessionState(villager);

        BlockPos claimedJobSite = null;
        if (townstead$requiresJobSite(newProf)) {
            claimedJobSite = townstead$claimJobSite(sp, villager, newProf);
            if (claimedJobSite == null) {
                Townstead.LOGGER.debug(
                        "Manual profession assignment skipped: villager={} uuid={} target={} has no claimable job site",
                        villager.getName().getString(),
                        villager.getUUID(),
                        newProf
                );
                villager.refreshBrain(level);
                return;
            }
        }

        villager.setVillagerData(villager.getVillagerData().setProfession(newProf).setLevel(1));
        villager.setVillagerXp(0);
        MerchantOffers offers = villager.getOffers();
        if (offers != null) {
            offers.clear();
        }

        if (claimedJobSite != null) {
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), claimedJobSite));
        }

        villager.refreshBrain(level);
        Townstead.LOGGER.debug(
                "Manual profession assignment: villager={} uuid={} {} -> {} jobSite={}",
                villager.getName().getString(),
                villager.getUUID(),
                oldProf,
                newProf,
                claimedJobSite
        );

        // A villager promoted at runtime keeps idling until a world reload rebuilds its brain:
        // MCA derives the brain's schedule during refreshBrain, but at this synchronous moment the
        // freshly-claimed job site / residency state hasn't settled, so it lands on a schedule with
        // no WORK window. Nothing recomputes the schedule afterward, so the work AI never starts
        // (HarvestWorkTask gates on getSchedule().getActivityAt() == WORK). Rebuild the brain again a
        // few ticks later, once that state has caught up, so the schedule comes back correct without
        // requiring the player to reload the world.
        if (townstead$requiresJobSite(newProf) && !villager.isBaby()) {
            level.getServer().tell(new net.minecraft.server.TickTask(
                    level.getServer().getTickCount() + 3,
                    () -> {
                        if (villager.isAlive()
                                && villager.level() instanceof net.minecraft.server.level.ServerLevel settledLevel
                                && villager.getVillagerData().getProfession() == newProf) {
                            villager.refreshBrain(settledLevel);
                        }
                    }));
        }
    }

    private static void townstead$clearProfessionState(VillagerEntityMCA villager) {
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    private static boolean townstead$requiresJobSite(VillagerProfession profession) {
        return ProfessionSlotRules.requiresJobSite(profession);
    }

    private static BlockPos townstead$claimJobSite(ServerPlayer sp, VillagerEntityMCA villager, VillagerProfession profession) {
        if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        if (!townstead$requiresJobSite(profession)) return null;

        Optional<Village> villageOpt = Village.findNearest(sp);
        if (villageOpt.isEmpty() || !villageOpt.get().isWithinBorder(villager)) return null;
        Village village = villageOpt.get();

        PoiManager poiManager = level.getPoiManager();
        BlockPos center = new BlockPos(village.getCenter());
        BlockPos closest = poiManager.findClosest(
                profession.heldJobSite(),
                pos -> village.isWithinBorder(pos, Village.BORDER_MARGIN),
                center,
                128,
                PoiManager.Occupancy.HAS_SPACE
        ).orElse(null);
        if (closest == null) {
            townstead$releaseStaleJobSites(level, village, profession);
            closest = poiManager.findClosest(
                    profession.heldJobSite(),
                    pos -> village.isWithinBorder(pos, Village.BORDER_MARGIN),
                    center,
                    128,
                    PoiManager.Occupancy.HAS_SPACE
            ).orElse(null);
        }
        if (closest == null) return null;
        BlockPos targetJobSite = closest;

        return poiManager.take(
                profession.heldJobSite(),
                (holder, pos) -> pos.equals(targetJobSite),
                targetJobSite,
                1
        ).orElse(null);
    }

    private static void townstead$releaseStaleJobSites(net.minecraft.server.level.ServerLevel level, Village village, VillagerProfession profession) {
        PoiManager poiManager = level.getPoiManager();
        Set<BlockPos> liveClaims = new HashSet<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!resident.isAlive()) continue;
            if (!townstead$professionOwnsJobSite(resident.getVillagerData().getProfession(), profession)) {
                continue;
            }
            resident.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(globalPos -> globalPos.dimension().equals(level.dimension()))
                    .map(GlobalPos::pos)
                    .ifPresent(liveClaims::add);
        }

        poiManager.findAll(
                profession.heldJobSite(),
                pos -> village.isWithinBorder(pos, Village.BORDER_MARGIN),
                new BlockPos(village.getCenter()),
                128,
                PoiManager.Occupancy.ANY
        ).forEach(pos -> {
            if (liveClaims.contains(pos)) return;
            if (poiManager.getFreeTickets(pos) > 0) return;
            if (poiManager.release(pos)) {
                Townstead.LOGGER.debug("Released stale job-site ticket for {} at {}", profession, pos);
            }
        });
    }

    // ── Field Post handlers ──

    private static void handleFieldPostConfigSet(FieldPostConfigSetPayload payload, ServerPlayer sp) {
        net.minecraft.world.level.block.entity.BlockEntity be =
                sp.serverLevel().getBlockEntity(payload.pos());
        if (!(be instanceof com.aetherianartificer.townstead.block.FieldPostBlockEntity fieldPost)) return;
        if (sp.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;

        com.aetherianartificer.townstead.farming.cellplan.CellPlan resolvedPlan =
                com.aetherianartificer.townstead.farming.cellplan.ClaimResolver.resolveAll(
                        sp.serverLevel(), payload.pos(), payload.config().cellPlan());
        com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig resolvedConfig =
                payload.config().withCellPlan(resolvedPlan);
        fieldPost.applyConfig(resolvedConfig);
        Townstead.LOGGER.debug("Field Post config set at {} by {}", payload.pos(), sp.getName().getString());

        sendToPlayer(sp, new FieldPostConfigSyncPayload(
                payload.pos(), fieldPost.toConfig(),
                fieldPost.getEffectivePatternId(), 0, 0, 0, 0
        ));
    }

    private static void handleFieldPostConfigSync(FieldPostConfigSyncPayload payload) {
        // Client-side: no-op, screen reads from block entity directly.
    }

    private static void handleFieldPostGridSync(FieldPostGridSyncPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof com.aetherianartificer.townstead.client.gui.fieldpost.FieldPostScreen screen
                && screen.getPostPos().equals(payload.pos())) {
            screen.applyServerSnapshot(payload.snapshot(), payload.cropPalette(), payload.villageSeedCounts(),
                    payload.seedSoilCompat(),
                    payload.farmerCount(), payload.totalPlots(), payload.tilledPlots(), payload.hydrationPercent());
        }
    }

    private static void handleClientCaps(ClientCapsPayload payload, ServerPlayer sp) {
        ClientCapsStore.setTravelersTitles(sp.getUUID(), payload.hasTravelersTitles());
    }

    private static void handleVillageEnterTitle(VillageEnterTitlePayload payload) {
        TravelersTitlesBridge.displayVillageTitle(payload.title(), payload.population(), payload.subtitleKey());
    }

    private static boolean townstead$professionOwnsJobSite(VillagerProfession holderProfession, VillagerProfession targetProfession) {
        if (holderProfession == null || targetProfession == null) return false;
        if (holderProfession == targetProfession) return true;
        return holderProfession.heldJobSite().equals(targetProfession.heldJobSite());
    }

    private static String townstead$professionKey(VillagerProfession profession) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null ? key.toString() : "minecraft:none";
    }
}
*///?}
