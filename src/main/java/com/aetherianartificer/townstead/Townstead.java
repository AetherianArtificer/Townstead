package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.farming.pattern.FarmPatternRegistry;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternDataLoader;
import com.aetherianartificer.townstead.compat.cooking.CookTradesCompat;
import com.google.common.collect.ImmutableSet;
import com.aetherianartificer.townstead.farming.FarmingPolicyData;
import com.aetherianartificer.townstead.farming.FarmingPolicySetPayload;
import com.aetherianartificer.townstead.farming.FarmingPolicySyncPayload;
import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.hunger.profile.ButcherProfileDataLoader;
import com.aetherianartificer.townstead.hunger.profile.ButcherProfileRegistry;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.FarmerProgressData;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.hunger.CookProgressData;
import com.aetherianartificer.townstead.hunger.ButcherPolicyClientStore;
import com.aetherianartificer.townstead.hunger.ButcherPolicyData;
import com.aetherianartificer.townstead.hunger.ButcherPolicySetPayload;
import com.aetherianartificer.townstead.hunger.ButcherPolicySyncPayload;
import com.aetherianartificer.townstead.hunger.FarmStatusSyncPayload;
import com.aetherianartificer.townstead.hunger.ButcherStatusSyncPayload;
import com.aetherianartificer.townstead.hunger.HungerSetPayload;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.gifts.GiftPredicate;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.sounds.SoundEvents;
import java.util.Locale;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(Townstead.MOD_ID)
public class Townstead {
    public static final String MOD_ID = "townstead";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, MOD_ID);

    public static final Supplier<AttachmentType<CompoundTag>> HUNGER_DATA = ATTACHMENTS.register(
            "hunger_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );

    public static final Supplier<VillagerProfession> COOK_PROFESSION = PROFESSIONS.register(
            "cook",
            () -> new VillagerProfession(
                    "cook",
                    PoiType.NONE,
                    PoiType.NONE,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_BUTCHER
            )
    );

    public Townstead(IEventBus modBus, ModContainer modContainer) {
        ATTACHMENTS.register(modBus);
        PROFESSIONS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, TownsteadConfig.SERVER_SPEC);
        townstead$registerClientConfigScreen(modContainer);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onClientDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        registerDialogueConditions();
        FarmPatternRegistry.bootstrap();
        ButcherProfileRegistry.bootstrap();
        LOGGER.info("Townstead loaded");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            VillagerProfession cook = COOK_PROFESSION.get();
            // MCA drops non-important professions with no JOB_SITE memory.
            // Mark cook important so kitchen-assigned cooks are retained.
            ProfessionsMCA.IS_IMPORTANT.add(cook);
            ProfessionsMCA.CAN_NOT_TRADE.remove(cook);
        });
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FarmPatternDataLoader());
        event.addListener(new ButcherProfileDataLoader());
    }

    private void registerDialogueConditions() {
        GiftPredicate.register("hunger", (json, name) ->
                GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    CompoundTag data = villager.getData(HUNGER_DATA);
                    int h = HungerData.getHunger(data);
                    HungerData.HungerState current = HungerData.getState(h);
                    return townstead$hungerAtLeast(current, state) ? 1.0f : 0.0f;
                });
    }

    private static boolean townstead$hungerAtLeast(HungerData.HungerState current, String minimumState) {
        int currentSeverity = switch (current) {
            case WELL_FED -> 0;
            case ADEQUATE -> 1;
            case HUNGRY -> 2;
            case FAMISHED -> 3;
            case STARVING -> 4;
        };

        int requiredSeverity = switch (minimumState) {
            case "well_fed" -> 0;
            case "adequate" -> 1;
            case "hungry" -> 2;
            case "famished" -> 3;
            case "starving" -> 4;
            default -> Integer.MAX_VALUE;
        };

        return currentSeverity >= requiredSeverity;
    }

    private static void townstead$registerClientConfigScreen(ModContainer modContainer) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modContainer.registerConfig(ModConfig.Type.CLIENT, TownsteadConfig.CLIENT_SPEC);
            TownsteadClient.registerConfigScreen(modContainer);
        } catch (ClassNotFoundException ignored) {
            // Dedicated server: no client config screen.
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID).versioned("1");
        registrar.playToClient(
                HungerSyncPayload.TYPE,
                HungerSyncPayload.STREAM_CODEC,
                this::handleHungerSync
        );
        registrar.playToClient(
                FarmStatusSyncPayload.TYPE,
                FarmStatusSyncPayload.STREAM_CODEC,
                this::handleFarmStatusSync
        );
        registrar.playToClient(
                ButcherStatusSyncPayload.TYPE,
                ButcherStatusSyncPayload.STREAM_CODEC,
                this::handleButcherStatusSync
        );
        registrar.playToClient(
                FarmingPolicySyncPayload.TYPE,
                FarmingPolicySyncPayload.STREAM_CODEC,
                this::handleFarmingPolicySync
        );
        registrar.playToClient(
                ButcherPolicySyncPayload.TYPE,
                ButcherPolicySyncPayload.STREAM_CODEC,
                this::handleButcherPolicySync
        );
        registrar.playToServer(
                HungerSetPayload.TYPE,
                HungerSetPayload.STREAM_CODEC,
                this::handleHungerSet
        );
        registrar.playToServer(
                FarmingPolicySetPayload.TYPE,
                FarmingPolicySetPayload.STREAM_CODEC,
                this::handleFarmingPolicySet
        );
        registrar.playToServer(
                ButcherPolicySetPayload.TYPE,
                ButcherPolicySetPayload.STREAM_CODEC,
                this::handleButcherPolicySet
        );
    }

    private void handleHungerSync(HungerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.set(
                payload.entityId(),
                payload.hunger(),
                payload.farmerTier(),
                payload.farmerXp(),
                payload.farmerXpToNext(),
                payload.butcherTier(),
                payload.butcherXp(),
                payload.butcherXpToNext(),
                payload.cookTier(),
                payload.cookXp(),
                payload.cookXpToNext()
        ));
    }

    private void handleFarmStatusSync(FarmStatusSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.setFarmBlockedReason(payload.entityId(), payload.blockedReasonId()));
    }

    private void handleButcherStatusSync(ButcherStatusSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.setButcherBlockedReason(payload.entityId(), payload.blockedReasonId()));
    }

    private void handleFarmingPolicySync(FarmingPolicySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FarmingPolicyClientStore.set(payload.patternId(), payload.tier(), payload.areaCount()));
    }

    private void handleButcherPolicySync(ButcherPolicySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ButcherPolicyClientStore.set(payload.profileId(), payload.tier(), payload.areaCount()));
    }

    private void handleHungerSet(HungerSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity entity = sp.serverLevel().getEntity(payload.entityId());
            if (!(entity instanceof VillagerEntityMCA villager)) return;

            CompoundTag hunger = villager.getData(HUNGER_DATA);
            int currentHunger = HungerData.getHunger(hunger);

            // hunger == -1 is a query: respond with current value, don't modify
            if (payload.hunger() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
                return;
            }

            int newHunger = payload.hunger();
            LOGGER.debug("HungerSet packet: entityId={}, target={}", payload.entityId(), newHunger);
            HungerData.setHunger(hunger, newHunger);
            // Reset saturation when increasing (for clean testing); leave it when decreasing
            if (newHunger > currentHunger) {
                HungerData.setSaturation(hunger, Math.min(newHunger, HungerData.MAX_SATURATION));
            }
            HungerData.setExhaustion(hunger, 0f);
            villager.setData(HUNGER_DATA, hunger);
            HungerSyncPayload sync = townstead$hungerSync(villager, hunger);
            PacketDistributor.sendToPlayer(sp, sync);
            PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
            int syncedHunger = sync.hunger();
            LOGGER.debug("Hunger set: {} -> {}", currentHunger, syncedHunger);
        });
    }

    private void handleFarmingPolicySet(FarmingPolicySetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            FarmingPolicyData data = FarmingPolicyData.get(sp.serverLevel());
            if (payload.tier() == -1) {
                PacketDistributor.sendToPlayer(sp, new FarmingPolicySyncPayload(
                        data.getDefaultPatternId(),
                        data.getDefaultTier(),
                        data.getAreas().size()
                ));
                return;
            }

            // Keep policy writes gated to privileged users for now.
            if (!sp.hasPermissions(2)) return;

            data.setDefaultPolicy(payload.patternId(), payload.tier());
            PacketDistributor.sendToPlayer(sp, new FarmingPolicySyncPayload(
                    data.getDefaultPatternId(),
                    data.getDefaultTier(),
                    data.getAreas().size()
            ));
        });
    }

    private void handleButcherPolicySet(ButcherPolicySetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            ButcherPolicyData data = ButcherPolicyData.get(sp.serverLevel());
            if (payload.tier() == -1) {
                PacketDistributor.sendToPlayer(sp, new ButcherPolicySyncPayload(
                        data.getDefaultProfileId(),
                        data.getDefaultTier(),
                        data.getAreas().size()
                ));
                return;
            }

            if (!sp.hasPermissions(2)) return;

            data.setDefaultPolicy(payload.profileId(), payload.tier());
            PacketDistributor.sendToPlayer(sp, new ButcherPolicySyncPayload(
                    data.getDefaultProfileId(),
                    data.getDefaultTier(),
                    data.getAreas().size()
            ));
        });
    }

    private void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof VillagerEntityMCA villager)) return;

        CompoundTag hunger = villager.getData(HUNGER_DATA);
        PacketDistributor.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
        PacketDistributor.sendToPlayer(sp, new FarmStatusSyncPayload(
                villager.getId(),
                HungerData.getFarmBlockedReason(hunger).id()
        ));
        PacketDistributor.sendToPlayer(sp, new ButcherStatusSyncPayload(
                villager.getId(),
                HungerData.getButcherBlockedReason(hunger).id()
        ));
    }

    public static HungerSyncPayload townstead$hungerSync(VillagerEntityMCA villager, CompoundTag hunger) {
        return new HungerSyncPayload(
                villager.getId(),
                HungerData.getHunger(hunger),
                FarmerProgressData.getTier(hunger),
                FarmerProgressData.getXp(hunger),
                FarmerProgressData.getXpToNextTier(hunger),
                ButcherProgressData.getTier(hunger),
                ButcherProgressData.getXp(hunger),
                ButcherProgressData.getXpToNextTier(hunger),
                CookProgressData.getTier(hunger),
                CookProgressData.getXp(hunger),
                CookProgressData.getXpToNextTier(hunger)
        );
    }

    private void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        HungerClientStore.clear();
        FarmingPolicyClientStore.clear();
        ButcherPolicyClientStore.clear();
    }
}
