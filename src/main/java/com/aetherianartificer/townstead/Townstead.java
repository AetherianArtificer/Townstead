package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.farming.pattern.FarmPatternRegistry;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternDataLoader;
import com.aetherianartificer.townstead.compat.ConditionalCompatPack;
import com.aetherianartificer.townstead.compat.DynamicFlowerPotTagPack;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.FatigueSetPayload;
import com.aetherianartificer.townstead.fatigue.FatigueSyncPayload;
import com.aetherianartificer.townstead.compat.thirst.RusticDelightThirstCompat;
import com.aetherianartificer.townstead.compat.cooking.BaristaTradesCompat;
import com.aetherianartificer.townstead.compat.cooking.CookTradesCompat;
import com.google.common.collect.ImmutableSet;
import com.aetherianartificer.townstead.farming.FarmingPolicyData;
import com.aetherianartificer.townstead.farming.FarmingPolicySetPayload;
import com.aetherianartificer.townstead.farming.FarmingPolicySyncPayload;
import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.profession.ProfessionClientStore;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.profession.ProfessionScanner;
import com.aetherianartificer.townstead.profession.ProfessionSetPayload;
import com.aetherianartificer.townstead.profession.ProfessionSyncPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.shift.ShiftSyncPayload;
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
import com.aetherianartificer.townstead.compat.thirst.PurificationCampfireRecipe;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSetPayload;
import com.aetherianartificer.townstead.thirst.ThirstSyncPayload;
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
import net.minecraft.server.packs.repository.Pack;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
//?} else if forge {
/*import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.DeferredRegister;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(Townstead.MOD_ID)
public class Townstead {
    public static final String MOD_ID = "townstead";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    //? if neoforge {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
    //?}
    private static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, MOD_ID);

    //? if neoforge {
    public static final Supplier<AttachmentType<CompoundTag>> HUNGER_DATA = ATTACHMENTS.register(
            "hunger_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );
    public static final Supplier<AttachmentType<CompoundTag>> THIRST_DATA = ATTACHMENTS.register(
            "thirst_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );
    public static final Supplier<AttachmentType<CompoundTag>> SHIFT_DATA = ATTACHMENTS.register(
            "shift_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );
    public static final Supplier<AttachmentType<CompoundTag>> FATIGUE_DATA = ATTACHMENTS.register(
            "fatigue_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );
    //?}

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

    public static final Supplier<VillagerProfession> BARISTA_PROFESSION = PROFESSIONS.register(
            "barista",
            () -> new VillagerProfession(
                    "barista",
                    PoiType.NONE,
                    PoiType.NONE,
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_BUTCHER
            )
    );

    //? if neoforge {
    public Townstead(IEventBus modBus, ModContainer modContainer) {
        ATTACHMENTS.register(modBus);
        PROFESSIONS.register(modBus);
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            RECIPE_SERIALIZERS.register("purification_campfire", () -> PurificationCampfireRecipe.Serializer.INSTANCE);
        }
        RECIPE_SERIALIZERS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, TownsteadConfig.SERVER_SPEC);
        townstead$registerClientConfigScreen(modContainer);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::addPackFinders);
        townstead$registerClientTooltipFactory(modBus);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        registerDialogueConditions();
        FarmPatternRegistry.bootstrap();
        ButcherProfileRegistry.bootstrap();
        LOGGER.info("Townstead loaded");
    }
    //?} else if forge {
    /*public Townstead() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PROFESSIONS.register(modBus);
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            RECIPE_SERIALIZERS.register("purification_campfire", () -> PurificationCampfireRecipe.Serializer.INSTANCE);
        }
        RECIPE_SERIALIZERS.register(modBus);
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        modContainer.addConfig(new net.minecraftforge.fml.config.ModConfig(ModConfig.Type.SERVER, TownsteadConfig.SERVER_SPEC, modContainer));
        townstead$registerClientConfigScreen(modContainer);
        TownsteadNetwork.register();
        townstead$registerClientTooltipFactory(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::addPackFinders);
        MinecraftForge.EVENT_BUS.addListener(this::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        registerDialogueConditions();
        FarmPatternRegistry.bootstrap();
        ButcherProfileRegistry.bootstrap();
        LOGGER.info("Townstead loaded");
    }
    *///?}

    //? if neoforge {
    private void addPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
            Pack pack = DynamicFlowerPotTagPack.create();
            if (pack != null) event.addRepositorySource(c -> c.accept(pack));
            Pack compatPack = ConditionalCompatPack.create();
            if (compatPack != null) event.addRepositorySource(c -> c.accept(compatPack));
        }
    }
    //?} else if forge {
    /*private void addPackFinders(net.minecraftforge.event.AddPackFindersEvent event) {
        if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
            Pack pack = DynamicFlowerPotTagPack.create();
            if (pack != null) event.addRepositorySource(c -> c.accept(pack));
            Pack compatPack = ConditionalCompatPack.create();
            if (compatPack != null) event.addRepositorySource(c -> c.accept(compatPack));
        }
    }
    *///?}

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModCompat.isLoaded("farmersdelight")) return;
            VillagerProfession cook = COOK_PROFESSION.get();
            // MCA drops non-important professions with no JOB_SITE memory.
            // Mark cook important so kitchen-assigned cooks are retained.
            //? if neoforge {
            ProfessionsMCA.IS_IMPORTANT.add(cook);
            ProfessionsMCA.CAN_NOT_TRADE.remove(cook);
            //?} else {
            /*ProfessionsMCA.isImportant.add(cook);
            ProfessionsMCA.canNotTrade.remove(cook);
            *///?}

            if (ModCompat.isLoaded("rusticdelight")) {
                VillagerProfession barista = BARISTA_PROFESSION.get();
                //? if neoforge {
                ProfessionsMCA.IS_IMPORTANT.add(barista);
                ProfessionsMCA.CAN_NOT_TRADE.remove(barista);
                //?} else {
                /*ProfessionsMCA.isImportant.add(barista);
                ProfessionsMCA.canNotTrade.remove(barista);
                *///?}
            }
        });
        event.enqueueWork(RusticDelightThirstCompat::register);
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FarmPatternDataLoader());
        event.addListener(new ButcherProfileDataLoader());
    }

    private void registerDialogueConditions() {
        GiftPredicate.register("hunger", (json, name) ->
                GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    //? if neoforge {
                    CompoundTag data = villager.getData(HUNGER_DATA);
                    //?} else if forge {
                    /*CompoundTag data = villager.getPersistentData().getCompound("townstead:hunger_data");
                    *///?}
                    int h = HungerData.getHunger(data);
                    HungerData.HungerState current = HungerData.getState(h);
                    return townstead$hungerAtLeast(current, state) ? 1.0f : 0.0f;
                });
        GiftPredicate.register("thirst", (json, name) ->
                        GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    if (!ThirstBridgeResolver.isActive()) return 0.0f;
                    //? if neoforge {
                    CompoundTag data = villager.getData(THIRST_DATA);
                    //?} else if forge {
                    /*CompoundTag data = villager.getPersistentData().getCompound("townstead:thirst_data");
                    *///?}
                    int t = ThirstData.getThirst(data);
                    ThirstData.ThirstState current = ThirstData.getState(t);
                    return townstead$thirstAtLeast(current, state) ? 1.0f : 0.0f;
                });
        GiftPredicate.register("fatigue", (json, name) ->
                        GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    if (!TownsteadConfig.isVillagerFatigueEnabled()) return 0.0f;
                    //? if neoforge {
                    CompoundTag data = villager.getData(FATIGUE_DATA);
                    //?} else if forge {
                    /*CompoundTag data = villager.getPersistentData().getCompound("townstead:fatigue_data");
                    *///?}
                    int f = FatigueData.getFatigue(data);
                    FatigueData.FatigueState current = FatigueData.getState(f);
                    return townstead$fatigueAtLeast(current, state) ? 1.0f : 0.0f;
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

    private static boolean townstead$thirstAtLeast(ThirstData.ThirstState current, String minimumState) {
        int currentSeverity = switch (current) {
            case QUENCHED -> 0;
            case HYDRATED -> 1;
            case THIRSTY -> 2;
            case PARCHED -> 3;
            case DEHYDRATED -> 4;
        };

        int requiredSeverity = switch (minimumState) {
            case "quenched" -> 0;
            case "hydrated" -> 1;
            case "thirsty" -> 2;
            case "parched" -> 3;
            case "dehydrated" -> 4;
            default -> Integer.MAX_VALUE;
        };

        return currentSeverity >= requiredSeverity;
    }

    private static boolean townstead$fatigueAtLeast(FatigueData.FatigueState current, String minimumState) {
        int currentSeverity = switch (current) {
            case RESTED -> 0;
            case ALERT -> 1;
            case TIRED -> 2;
            case DROWSY -> 3;
            case EXHAUSTED -> 4;
        };

        int requiredSeverity = switch (minimumState) {
            case "rested" -> 0;
            case "alert" -> 1;
            case "tired" -> 2;
            case "drowsy" -> 3;
            case "exhausted" -> 4;
            default -> Integer.MAX_VALUE;
        };

        return currentSeverity >= requiredSeverity;
    }

    //? if neoforge {
    private static void townstead$registerClientTooltipFactory(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) ->
                            event.register(
                                    com.aetherianartificer.townstead.fatigue.EnergyTooltipComponent.class,
                                    com.aetherianartificer.townstead.fatigue.ClientEnergyTooltipComponent::new
                            )
            );
        } catch (Exception ignored) {
            // Dedicated server: no tooltip rendering.
        }
    }
    //?} else {
    /*private static void townstead$registerClientTooltipFactory(Object modBus) {
        // Forge 1.20.1: tooltip component registration not supported
    }
    *///?}

    private static void townstead$registerClientConfigScreen(ModContainer modContainer) {
        //? if neoforge {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modContainer.registerConfig(ModConfig.Type.CLIENT, TownsteadConfig.CLIENT_SPEC);
            // Load TownsteadClient via reflection to avoid pulling in client-only
            // imports (ConfigScreenHandler, etc.) on dedicated servers.
            Class<?> clientClass = Class.forName("com.aetherianartificer.townstead.TownsteadClient");
            clientClass.getMethod("registerConfigScreen", ModContainer.class).invoke(null, modContainer);
        } catch (Exception ignored) {
            // Dedicated server: no client config screen.
        }
        //?} else if forge {
        /*// Forge 1.20.1: do not probe client classes from common mod init.
        // The previous reflective check still triggered RuntimeDistCleaner on
        // dedicated servers, which is visible in server logs.
        return;
        *///?}
    }

    //? if neoforge {
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID).versioned("1");
        boolean thirstAvailable = ThirstBridgeResolver.anyThirstModLoaded();
        registrar.playToClient(
                HungerSyncPayload.TYPE,
                HungerSyncPayload.STREAM_CODEC,
                this::handleHungerSync
        );
        if (thirstAvailable) {
            registrar.playToClient(
                    ThirstSyncPayload.TYPE,
                    ThirstSyncPayload.STREAM_CODEC,
                    this::handleThirstSync
            );
        }
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
        if (thirstAvailable) {
            registrar.playToServer(
                    ThirstSetPayload.TYPE,
                    ThirstSetPayload.STREAM_CODEC,
                    this::handleThirstSet
            );
        }
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
        registrar.playToClient(
                ShiftSyncPayload.TYPE,
                ShiftSyncPayload.STREAM_CODEC,
                this::handleShiftSync
        );
        registrar.playToServer(
                ShiftSetPayload.TYPE,
                ShiftSetPayload.STREAM_CODEC,
                this::handleShiftSet
        );
        registrar.playToServer(
                ProfessionQueryPayload.TYPE,
                ProfessionQueryPayload.STREAM_CODEC,
                this::handleProfessionQuery
        );
        registrar.playToClient(
                ProfessionSyncPayload.TYPE,
                ProfessionSyncPayload.STREAM_CODEC,
                this::handleProfessionSync
        );
        registrar.playToServer(
                ProfessionSetPayload.TYPE,
                ProfessionSetPayload.STREAM_CODEC,
                this::handleProfessionSet
        );
        registrar.playToClient(
                FatigueSyncPayload.TYPE,
                FatigueSyncPayload.STREAM_CODEC,
                this::handleFatigueSync
        );
        registrar.playToServer(
                FatigueSetPayload.TYPE,
                FatigueSetPayload.STREAM_CODEC,
                this::handleFatigueSet
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

    private void handleThirstSync(ThirstSyncPayload payload, IPayloadContext context) {
        if (!ThirstBridgeResolver.isActive()) return;
        context.enqueueWork(() -> ThirstClientStore.set(
                payload.entityId(),
                payload.thirst(),
                payload.quenched()
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

    private void handleThirstSet(ThirstSetPayload payload, IPayloadContext context) {
        if (!ThirstBridgeResolver.isActive()) return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity entity = sp.serverLevel().getEntity(payload.entityId());
            if (!(entity instanceof VillagerEntityMCA villager)) return;

            CompoundTag thirst = villager.getData(THIRST_DATA);
            int currentThirst = ThirstData.getThirst(thirst);

            if (payload.thirst() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$thirstSync(villager, thirst));
                return;
            }

            int newThirst = payload.thirst();
            LOGGER.debug("ThirstSet packet: entityId={}, target={}", payload.entityId(), newThirst);
            ThirstData.setThirst(thirst, newThirst);
            if (newThirst > currentThirst) {
                ThirstData.setQuenched(thirst, Math.min(newThirst, ThirstData.MAX_QUENCHED));
            }
            ThirstData.setExhaustion(thirst, 0f);
            villager.setData(THIRST_DATA, thirst);
            ThirstSyncPayload sync = townstead$thirstSync(villager, thirst);
            PacketDistributor.sendToPlayer(sp, sync);
            PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
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



            data.setDefaultPolicy(payload.profileId(), payload.tier());
            PacketDistributor.sendToPlayer(sp, new ButcherPolicySyncPayload(
                    data.getDefaultProfileId(),
                    data.getDefaultTier(),
                    data.getAreas().size()
            ));
        });
    }

    private void handleShiftSync(ShiftSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ShiftClientStore.set(payload.villagerUuid(), payload.shifts()));
    }

    private void handleShiftSet(ShiftSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            // Find the villager by UUID across all loaded dimensions
            VillagerEntityMCA villager = null;
            for (net.minecraft.server.level.ServerLevel level : sp.getServer().getAllLevels()) {
                Entity entity = level.getEntity(payload.villagerUuid());
                if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
            }
            if (villager == null) return;

            // Query mode: empty shifts array
            if (payload.shifts().length == 0) {
                CompoundTag shiftTag = villager.getData(SHIFT_DATA);
                PacketDistributor.sendToPlayer(sp, new ShiftSyncPayload(
                        payload.villagerUuid(), ShiftData.getShifts(shiftTag)));
                return;
            }

            // Validate and apply
            if (payload.shifts().length != ShiftData.HOURS_PER_DAY) return;

            CompoundTag shiftTag = villager.getData(SHIFT_DATA);
            ShiftData.setShifts(shiftTag, payload.shifts());
            villager.setData(SHIFT_DATA, shiftTag);

            // Apply schedule to the brain immediately
            ShiftScheduleApplier.apply(villager);

            // Sync back to all tracking players
            ShiftSyncPayload sync = new ShiftSyncPayload(payload.villagerUuid(), payload.shifts());
            PacketDistributor.sendToPlayer(sp, sync);
            PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
        });
    }

    private void handleProfessionQuery(ProfessionQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            ProfessionScanner.ScanResult scan = ProfessionScanner.scanAvailableProfessions(sp);
            PacketDistributor.sendToPlayer(sp, new ProfessionSyncPayload(scan.professionIds(), scan.usedSlots(), scan.maxSlots()));
        });
    }

    private void handleProfessionSync(ProfessionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ProfessionClientStore.set(payload.professionIds(), payload.usedSlots(), payload.maxSlots()));
    }

    private void handleProfessionSet(ProfessionSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            VillagerEntityMCA villager = null;
            for (net.minecraft.server.level.ServerLevel level : sp.getServer().getAllLevels()) {
                Entity entity = level.getEntity(payload.villagerUuid());
                if (entity instanceof VillagerEntityMCA v) { villager = v; break; }
            }
            if (villager == null) return;

            //? if >=1.21 {
            net.minecraft.resources.ResourceLocation profId = net.minecraft.resources.ResourceLocation.parse(payload.professionId());
            //?} else {
            /*net.minecraft.resources.ResourceLocation profId = new net.minecraft.resources.ResourceLocation(payload.professionId());
            *///?}
            net.minecraft.world.entity.npc.VillagerProfession newProf =
                    net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(profId);
            if (newProf == null) return;

            villager.setVillagerData(villager.getVillagerData().setProfession(newProf));
            if (newProf != net.minecraft.world.entity.npc.VillagerProfession.NONE) {
                if (villager.getVillagerData().getLevel() < 1) {
                    villager.setVillagerData(villager.getVillagerData().setLevel(1));
                }
            }
            villager.refreshBrain((net.minecraft.server.level.ServerLevel) villager.level());
        });
    }

    private void handleFatigueSync(FatigueSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FatigueClientStore.set(
                payload.entityId(),
                payload.fatigue(),
                payload.collapsed()
        ));
    }

    private void handleFatigueSet(FatigueSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity entity = sp.serverLevel().getEntity(payload.entityId());
            if (!(entity instanceof VillagerEntityMCA villager)) return;

            CompoundTag fatigue = villager.getData(FATIGUE_DATA);
            int currentFatigue = FatigueData.getFatigue(fatigue);

            if (payload.fatigue() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$fatigueSync(villager, fatigue));
                return;
            }

            int newFatigue = payload.fatigue();
            LOGGER.debug("FatigueSet packet: entityId={}, target={}", payload.entityId(), newFatigue);
            FatigueData.setFatigue(fatigue, newFatigue);
            // Clear collapse/gate flags when setting via editor
            if (newFatigue < FatigueData.COLLAPSE_THRESHOLD) {
                FatigueData.setCollapsed(fatigue, false);
            }
            if (newFatigue < FatigueData.RECOVERY_GATE) {
                FatigueData.setGated(fatigue, false);
            }
            villager.setData(FATIGUE_DATA, fatigue);
            FatigueSyncPayload sync = townstead$fatigueSync(villager, fatigue);
            PacketDistributor.sendToPlayer(sp, sync);
            PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
        });
    }

    //?}

    private void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof VillagerEntityMCA villager)) return;

        //? if neoforge {
        CompoundTag hunger = villager.getData(HUNGER_DATA);
        PacketDistributor.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
        if (ThirstBridgeResolver.isActive()) {
            CompoundTag thirst = villager.getData(THIRST_DATA);
            PacketDistributor.sendToPlayer(sp, townstead$thirstSync(villager, thirst));
        }
        PacketDistributor.sendToPlayer(sp, new FarmStatusSyncPayload(
                villager.getId(),
                HungerData.getFarmBlockedReason(hunger).id()
        ));
        PacketDistributor.sendToPlayer(sp, new ButcherStatusSyncPayload(
                villager.getId(),
                HungerData.getButcherBlockedReason(hunger).id()
        ));
        CompoundTag fatigue = villager.getData(FATIGUE_DATA);
        PacketDistributor.sendToPlayer(sp, townstead$fatigueSync(villager, fatigue));
        CompoundTag shift = villager.getData(SHIFT_DATA);
        if (ShiftData.hasCustomShifts(shift)) {
            PacketDistributor.sendToPlayer(sp, new ShiftSyncPayload(
                    villager.getUUID(), ShiftData.getShifts(shift)));
        }
        //?} else if forge {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        TownsteadNetwork.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
        if (ThirstBridgeResolver.isActive()) {
            CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
            TownsteadNetwork.sendToPlayer(sp, townstead$thirstSync(villager, thirst));
        }
        TownsteadNetwork.sendToPlayer(sp, new FarmStatusSyncPayload(
                villager.getId(),
                HungerData.getFarmBlockedReason(hunger).id()
        ));
        TownsteadNetwork.sendToPlayer(sp, new ButcherStatusSyncPayload(
                villager.getId(),
                HungerData.getButcherBlockedReason(hunger).id()
        ));
        CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        TownsteadNetwork.sendToPlayer(sp, townstead$fatigueSync(villager, fatigue));
        CompoundTag shift = villager.getPersistentData().getCompound("townstead_shift");
        if (ShiftData.hasCustomShifts(shift)) {
            TownsteadNetwork.sendToPlayer(sp, new ShiftSyncPayload(
                    villager.getUUID(), ShiftData.getShifts(shift)));
        }
        *///?}
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

    public static ThirstSyncPayload townstead$thirstSync(VillagerEntityMCA villager, CompoundTag thirst) {
        return new ThirstSyncPayload(
                villager.getId(),
                ThirstData.getThirst(thirst),
                ThirstData.getQuenched(thirst)
        );
    }

    public static FatigueSyncPayload townstead$fatigueSync(VillagerEntityMCA villager, CompoundTag fatigue) {
        return new FatigueSyncPayload(
                villager.getId(),
                FatigueData.getFatigue(fatigue),
                FatigueData.isCollapsed(fatigue)
        );
    }

}
