package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.block.FieldPostBlock;
import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import com.aetherianartificer.townstead.block.FieldPostMenu;
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
import com.aetherianartificer.townstead.farming.FieldPostConfigSetPayload;
import com.aetherianartificer.townstead.farming.FieldPostConfigSyncPayload;
import com.aetherianartificer.townstead.farming.FieldPostGridSyncPayload;
import com.aetherianartificer.townstead.farming.GridScanner;
import com.aetherianartificer.townstead.farming.CropProductResolver;
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
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
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
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.sounds.SoundEvents;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.DistExecutor;
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
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MOD_ID);

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

    // ── Field Post block ──

    public static final Supplier<Block> FIELD_POST = BLOCKS.register("field_post",
            () -> new FieldPostBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final Supplier<Item> FIELD_POST_ITEM = ITEMS.register("field_post",
            () -> new BlockItem(FIELD_POST.get(), new Item.Properties()));

    private static final String[] FIELD_POST_WOOD_VARIANTS = {
            "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "bamboo", "crimson", "warped"
    };

    public static final java.util.List<Supplier<Block>> FIELD_POST_VARIANTS = new java.util.ArrayList<>();
    public static final java.util.List<Supplier<Item>> FIELD_POST_VARIANT_ITEMS = new java.util.ArrayList<>();

    static {
        for (String wood : FIELD_POST_WOOD_VARIANTS) {
            String id = "field_post_" + wood;
            Supplier<Block> block = BLOCKS.register(id,
                    () -> new FieldPostBlock(BlockBehaviour.Properties.of()
                            .strength(2.0f)
                            .sound(SoundType.WOOD)
                            .noOcclusion()));
            FIELD_POST_VARIANTS.add(block);
            FIELD_POST_VARIANT_ITEMS.add(ITEMS.register(id,
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    public static final Supplier<BlockEntityType<FieldPostBlockEntity>> FIELD_POST_BE =
            BLOCK_ENTITY_TYPES.register("field_post",
                    () -> {
                        Block[] blocks = new Block[1 + FIELD_POST_VARIANTS.size()];
                        blocks[0] = FIELD_POST.get();
                        for (int i = 0; i < FIELD_POST_VARIANTS.size(); i++) {
                            blocks[i + 1] = FIELD_POST_VARIANTS.get(i).get();
                        }
                        return BlockEntityType.Builder.of(FieldPostBlockEntity::new, blocks).build(null);
                    });

    public static final Supplier<net.minecraft.world.item.CreativeModeTab> TOWNSTEAD_TAB =
            CREATIVE_MODE_TABS.register("main",
                    () -> net.minecraft.world.item.CreativeModeTab.builder()
                            .title(net.minecraft.network.chat.Component.translatable("itemGroup.townstead"))
                            .icon(() -> new net.minecraft.world.item.ItemStack(FIELD_POST_ITEM.get()))
                            .displayItems((params, output) -> {
                                output.accept(FIELD_POST_ITEM.get());
                                for (Supplier<Item> variant : FIELD_POST_VARIANT_ITEMS) {
                                    output.accept(variant.get());
                                }
                            })
                            .build());

    //? if neoforge {
    public static final Supplier<MenuType<FieldPostMenu>> FIELD_POST_MENU =
            MENU_TYPES.register("field_post",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(FieldPostMenu::clientFactory));
    //?} else if forge {
    /*public static final Supplier<MenuType<FieldPostMenu>> FIELD_POST_MENU =
            MENU_TYPES.register("field_post",
                    () -> net.minecraftforge.common.extensions.IForgeMenuType.create(FieldPostMenu::clientFactory));
    *///?}

    //? if neoforge {
    public Townstead(IEventBus modBus, ModContainer modContainer) {
        ATTACHMENTS.register(modBus);
        PROFESSIONS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            RECIPE_SERIALIZERS.register("purification_campfire", () -> PurificationCampfireRecipe.Serializer.INSTANCE);
        }
        RECIPE_SERIALIZERS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, TownsteadConfig.SERVER_SPEC);
        townstead$registerClientConfigScreen(modContainer);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::addPackFinders);
        townstead$registerKeybinds(modBus);
        townstead$registerClientTooltipFactory(modBus);
        townstead$registerMenuScreens(modBus);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        registerDialogueConditions();
        ButcherProfileRegistry.bootstrap();
        LOGGER.info("Townstead loaded");
    }
    //?} else if forge {
    /*public Townstead() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PROFESSIONS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            RECIPE_SERIALIZERS.register("purification_campfire", () -> PurificationCampfireRecipe.Serializer.INSTANCE);
        }
        RECIPE_SERIALIZERS.register(modBus);
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        modContainer.addConfig(new net.minecraftforge.fml.config.ModConfig(ModConfig.Type.SERVER, TownsteadConfig.SERVER_SPEC, modContainer));
        townstead$registerClientConfigScreen(modContainer);
        TownsteadNetwork.register();
        townstead$registerClientTooltipFactory(modBus);
        townstead$registerMenuScreens(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::addPackFinders);
        MinecraftForge.EVENT_BUS.addListener(this::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        registerDialogueConditions();
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
        event.addListener(new ButcherProfileDataLoader());
        event.addListener(new CatalogDataLoader());
        com.aetherianartificer.townstead.farming.CropProductResolver.invalidate();
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
    private static void townstead$registerKeybinds(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) ->
                            event.register(com.aetherianartificer.townstead.client.TownsteadKeybinds.TALK)
            );
        } catch (Exception ignored) {
            // Dedicated server: no keybinds.
        }
    }
    //?} else {
    /*private static void townstead$registerKeybinds(Object modBus) {
        // Forge 1.20.1: keybinds not yet supported for RPG dialogue
    }
    *///?}

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

    // Screen is opened client-side directly via FieldPostScreenOpener (no menu registration needed)
    private static void townstead$registerMenuScreens(Object modBus) {}

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
        /*modContainer.addConfig(new net.minecraftforge.fml.config.ModConfig(ModConfig.Type.CLIENT, TownsteadConfig.CLIENT_SPEC, modContainer));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class<?> clientClass = Class.forName("com.aetherianartificer.townstead.TownsteadClient");
                clientClass.getMethod("registerConfigScreen", ModContainer.class).invoke(null, modContainer);
            } catch (Exception ignored) {
                // Client-only bootstrap must not crash mod init.
            }
        });
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
        registrar.playToClient(
                VillageResidentsSyncPayload.TYPE,
                VillageResidentsSyncPayload.STREAM_CODEC,
                this::handleVillageResidentsSync
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
        // Field Post
        registrar.playToServer(
                FieldPostConfigSetPayload.TYPE,
                FieldPostConfigSetPayload.STREAM_CODEC,
                this::handleFieldPostConfigSet
        );
        registrar.playToClient(
                FieldPostConfigSyncPayload.TYPE,
                FieldPostConfigSyncPayload.STREAM_CODEC,
                this::handleFieldPostConfigSync
        );
        registrar.playToClient(
                FieldPostGridSyncPayload.TYPE,
                FieldPostGridSyncPayload.STREAM_CODEC,
                this::handleFieldPostGridSync
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
        context.enqueueWork(() -> {
            ShiftClientStore.set(payload.villagerUuid(), payload.shifts());
            VillageResidentClientStore.updateShifts(payload.villagerUuid(), payload.shifts());
        });
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
            PacketDistributor.sendToPlayer(sp, new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(sp)));
        });
    }

    private void handleProfessionSync(ProfessionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ProfessionClientStore.set(payload.professionIds(), payload.usedSlots(), payload.maxSlots()));
    }

    private void handleVillageResidentsSync(VillageResidentsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            VillageResidentClientStore.set(payload.residents());
            for (VillageResidentClientStore.Resident resident : payload.residents()) {
                ShiftClientStore.set(resident.villagerUuid(), resident.shifts());
            }
        });
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

            ProfessionScanner.ScanResult preScan = ProfessionScanner.scanAvailableProfessions(sp);
            String targetProfessionId = payload.professionId();
            String currentProfessionId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()) != null
                    ? net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()).toString()
                    : "minecraft:none";
            if (!targetProfessionId.equals(currentProfessionId)) {
                int targetIndex = preScan.professionIds().indexOf(targetProfessionId);
                if (targetIndex >= 0) {
                    int maxSlots = preScan.maxSlots().get(targetIndex);
                    int usedSlots = preScan.usedSlots().get(targetIndex);
                    if (maxSlots >= 0 && usedSlots >= maxSlots) {
                        PacketDistributor.sendToPlayer(sp, new ProfessionSyncPayload(preScan.professionIds(), preScan.usedSlots(), preScan.maxSlots()));
                        PacketDistributor.sendToPlayer(sp, new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(sp)));
                        return;
                    }
                }
            }

            townstead$assignProfession(sp, villager, newProf);
            ProfessionScanner.ScanResult scan = ProfessionScanner.scanAvailableProfessions(sp);
            String finalProfessionId = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()) != null
                    ? net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()).toString()
                    : "minecraft:none";
            PacketDistributor.sendToPlayer(sp, new ProfessionSyncPayload(scan.professionIds(), scan.usedSlots(), scan.maxSlots()));
            PacketDistributor.sendToPlayer(sp, new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(sp)));
        });
    }

    static void townstead$assignProfession(ServerPlayer sp, VillagerEntityMCA villager, VillagerProfession newProf) {
        if (!(villager.level() instanceof ServerLevel level)) return;

        VillagerProfession oldProf = villager.getVillagerData().getProfession();
        townstead$clearProfessionState(villager);

        BlockPos claimedJobSite = null;
        if (townstead$requiresJobSite(newProf)) {
            claimedJobSite = townstead$claimJobSite(sp, villager, newProf);
            if (claimedJobSite == null) {
                LOGGER.debug(
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
        LOGGER.debug(
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
        if (!(villager.level() instanceof ServerLevel level)) return null;
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
            townstead$releaseStaleJobSites(sp, level, village, profession);
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

        BlockPos claimed = poiManager.take(
                profession.heldJobSite(),
                (holder, pos) -> pos.equals(targetJobSite),
                targetJobSite,
                1
        ).orElse(null);
        return claimed;
    }

    private static void townstead$releaseStaleJobSites(ServerPlayer sp, ServerLevel level, Village village, VillagerProfession profession) {
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
                LOGGER.debug("Released stale job-site ticket for {} at {}", profession, pos);
            }
        });
    }

    private static boolean townstead$professionOwnsJobSite(VillagerProfession holderProfession, VillagerProfession targetProfession) {
        if (holderProfession == null || targetProfession == null) return false;
        if (holderProfession == targetProfession) return true;
        return holderProfession.heldJobSite().equals(targetProfession.heldJobSite());
    }

    private static String professionKey(VillagerProfession profession) {
        if (profession == null) return "minecraft:none";
        var key = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null ? key.toString() : "minecraft:none";
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

    private void handleFieldPostConfigSet(FieldPostConfigSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            net.minecraft.world.level.block.entity.BlockEntity be =
                    sp.serverLevel().getBlockEntity(payload.pos());
            if (!(be instanceof com.aetherianartificer.townstead.block.FieldPostBlockEntity fieldPost)) return;
            if (sp.distanceToSqr(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5) > 64.0) return;

            // Resolve CLAIM markers against the live world before applying.
            com.aetherianartificer.townstead.farming.cellplan.CellPlan resolvedPlan =
                    com.aetherianartificer.townstead.farming.cellplan.ClaimResolver.resolveAll(
                            sp.serverLevel(), payload.pos(), payload.config().cellPlan());
            com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig resolvedConfig =
                    payload.config().withCellPlan(resolvedPlan);
            fieldPost.applyConfig(resolvedConfig);
            LOGGER.debug("Field Post config set at {} by {}", payload.pos(), sp.getName().getString());

            PacketDistributor.sendToPlayer(sp, new FieldPostConfigSyncPayload(
                    payload.pos(), fieldPost.toConfig(),
                    fieldPost.getEffectivePatternId(), 0, 0, 0, 0
            ));
        });
    }

    private void handleFieldPostConfigSync(FieldPostConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(payload.pos());
            if (be instanceof com.aetherianartificer.townstead.block.FieldPostBlockEntity fieldPost) {
                fieldPost.applyConfig(payload.config());
            }
        });
    }

    private void handleFieldPostGridSync(FieldPostGridSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof com.aetherianartificer.townstead.client.gui.fieldpost.FieldPostScreen screen
                    && screen.getPostPos().equals(payload.pos())) {
                screen.applyServerSnapshot(payload.snapshot(), payload.cropPalette(), payload.villageSeedCounts(),
                        payload.seedSoilCompat(),
                        payload.farmerCount(), payload.totalPlots(), payload.tilledPlots(), payload.hydrationPercent());
            }
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
