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

    private static void handleFishermanHookLink(FishermanHookLinkPayload payload) {
        com.aetherianartificer.townstead.hunger.FishermanHookLinkStore.link(payload.hookEntityId(), payload.villagerEntityId());
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
        if (!ThirstBridgeResolver.isActive()) return;
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

    private static void handleFatigueSync(FatigueSyncPayload payload) {
        FatigueClientStore.set(payload.entityId(), payload.fatigue(), payload.collapsed());
    }

    private static void handleFatigueSet(FatigueSetPayload payload, ServerPlayer sp) {
        Entity entity = sp.serverLevel().getEntity(payload.entityId());
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        int currentFatigue = FatigueData.getFatigue(fatigue);

        if (payload.fatigue() == -1) {
            sendToPlayer(sp, Townstead.townstead$fatigueSync(villager, fatigue));
            return;
        }

        int newFatigue = payload.fatigue();
        Townstead.LOGGER.debug("FatigueSet packet: entityId={}, target={}", payload.entityId(), newFatigue);
        FatigueData.setFatigue(fatigue, newFatigue);
        if (newFatigue < FatigueData.COLLAPSE_THRESHOLD) {
            FatigueData.setCollapsed(fatigue, false);
        }
        if (newFatigue < FatigueData.RECOVERY_GATE) {
            FatigueData.setGated(fatigue, false);
        }
        villager.getPersistentData().put("townstead_fatigue", fatigue);
        FatigueSyncPayload sync = Townstead.townstead$fatigueSync(villager, fatigue);
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
