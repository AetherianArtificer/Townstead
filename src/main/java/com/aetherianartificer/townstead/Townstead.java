package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.block.FieldPostBlock;
import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import com.aetherianartificer.townstead.block.FieldPostMenu;
import com.aetherianartificer.townstead.compat.ConditionalCompatPack;
import com.aetherianartificer.townstead.compat.DynamicFlowerPotTagPack;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsPayload;
import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsStore;
import com.aetherianartificer.townstead.compat.travelerstitles.VillageEnterTitlePayload;
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
import com.aetherianartificer.townstead.shift.ShiftWeekSetPayload;
import com.aetherianartificer.townstead.shift.ShiftWeekSyncPayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlan;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanApplyPayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanClientStore;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanDeletePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanJsonLoader;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanRegistry;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSavePayload;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSavedData;
import com.aetherianartificer.townstead.shift.weekplan.WeekPlanSyncPayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateApplyPayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateClientStore;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateDeletePayload;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateJsonLoader;
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
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXpType;
import com.aetherianartificer.townstead.hunger.FarmStatusSyncPayload;
import com.aetherianartificer.townstead.hunger.ButcherStatusSyncPayload;
import com.aetherianartificer.townstead.hunger.FishermanHookLinkPayload;
import com.aetherianartificer.townstead.hunger.FishermanHookLinkStore;
import com.aetherianartificer.townstead.hunger.FishermanStatusSyncPayload;
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
import net.minecraft.server.MinecraftServer;
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
    public static final Supplier<AttachmentType<CompoundTag>> LIFE_DATA = ATTACHMENTS.register(
            "life_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );
    public static final Supplier<AttachmentType<CompoundTag>> PLAYER_ORIGIN_DATA = ATTACHMENTS.register(
            "player_origin_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .copyOnDeath()
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

    // ── Calendar block ──

    public static final Supplier<Block> CALENDAR_BLOCK = BLOCKS.register("calendar",
            () -> new com.aetherianartificer.townstead.block.CalendarBlock(
                    BlockBehaviour.Properties.of()
                            .strength(0.5f)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .noCollission()));

    public static final Supplier<Item> CALENDAR_ITEM = ITEMS.register("calendar",
            () -> new BlockItem(CALENDAR_BLOCK.get(), new Item.Properties()));

    // ── Immortality / mortality potions ──
    // Brewed like vanilla potions (Awkward + Enchanted Golden Apple -> Immortality;
    // Immortality + Fermented Spider Eye -> Mortality) and thrown as splash potions
    // at MCA villagers. The marker effect (LifePotionEffect) flips the immortality flag.

    private static final DeferredRegister<net.minecraft.world.effect.MobEffect> MOB_EFFECTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.MOB_EFFECT, MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.alchemy.Potion> POTIONS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.POTION, MOD_ID);

    //? if neoforge {
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect> IMMORTALITY_EFFECT =
            MOB_EFFECTS.register("immortality", () -> new com.aetherianartificer.townstead.item.LifePotionEffect(true, 0xE8C547));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect> MORTALITY_EFFECT =
            MOB_EFFECTS.register("mortality", () -> new com.aetherianartificer.townstead.item.LifePotionEffect(false, 0x9A9A9A));

    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> IMMORTALITY_POTION =
            POTIONS.register("immortality", () -> new net.minecraft.world.item.alchemy.Potion("townstead_immortality",
                    new net.minecraft.world.effect.MobEffectInstance(IMMORTALITY_EFFECT, 1)));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MORTALITY_POTION =
            POTIONS.register("mortality", () -> new net.minecraft.world.item.alchemy.Potion("townstead_mortality",
                    new net.minecraft.world.effect.MobEffectInstance(MORTALITY_EFFECT, 1)));
    //?} else {
    /*public static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.effect.MobEffect> IMMORTALITY_EFFECT =
            MOB_EFFECTS.register("immortality", () -> new com.aetherianartificer.townstead.item.LifePotionEffect(true, 0xE8C547));
    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.effect.MobEffect> MORTALITY_EFFECT =
            MOB_EFFECTS.register("mortality", () -> new com.aetherianartificer.townstead.item.LifePotionEffect(false, 0x9A9A9A));

    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.alchemy.Potion> IMMORTALITY_POTION =
            POTIONS.register("immortality", () -> new net.minecraft.world.item.alchemy.Potion("townstead_immortality",
                    new net.minecraft.world.effect.MobEffectInstance(IMMORTALITY_EFFECT.get(), 1)));
    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.alchemy.Potion> MORTALITY_POTION =
            POTIONS.register("mortality", () -> new net.minecraft.world.item.alchemy.Potion("townstead_mortality",
                    new net.minecraft.world.effect.MobEffectInstance(MORTALITY_EFFECT.get(), 1)));
    *///?}

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

    public static final Supplier<BlockEntityType<com.aetherianartificer.townstead.block.CalendarBlockEntity>> CALENDAR_BE =
            BLOCK_ENTITY_TYPES.register("calendar",
                    () -> BlockEntityType.Builder.of(
                            com.aetherianartificer.townstead.block.CalendarBlockEntity::new,
                            CALENDAR_BLOCK.get()).build(null));

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
                                output.accept(CALENDAR_ITEM.get());
                                townstead$addLifePotions(output);
                            })
                            .build());

    /** Adds the drinkable + splash variants of both life potions to the Townstead tab. */
    private static void townstead$addLifePotions(net.minecraft.world.item.CreativeModeTab.Output output) {
        //? if neoforge {
        output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, IMMORTALITY_POTION));
        output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.SPLASH_POTION, IMMORTALITY_POTION));
        output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, MORTALITY_POTION));
        output.accept(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.SPLASH_POTION, MORTALITY_POTION));
        //?} else {
        /*output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), IMMORTALITY_POTION.get()));
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), IMMORTALITY_POTION.get()));
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION), MORTALITY_POTION.get()));
        output.accept(net.minecraft.world.item.alchemy.PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION), MORTALITY_POTION.get()));
        *///?}
    }

    //? if forge {
    /*private static void townstead$registerLifeBrewing() {
        try {
            java.lang.reflect.Method addMix = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                    net.minecraft.world.item.alchemy.PotionBrewing.class, "m_43513_",
                    net.minecraft.world.item.alchemy.Potion.class, Item.class,
                    net.minecraft.world.item.alchemy.Potion.class);
            addMix.invoke(null, net.minecraft.world.item.alchemy.Potions.AWKWARD,
                    net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, IMMORTALITY_POTION.get());
            addMix.invoke(null, IMMORTALITY_POTION.get(),
                    net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE, MORTALITY_POTION.get());
        } catch (Exception e) {
            LOGGER.error("Failed to register Townstead potion brewing recipes", e);
        }
    }
    *///?}

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
        MOB_EFFECTS.register(modBus);
        POTIONS.register(modBus);
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
        townstead$registerHudOverlays(modBus);
        townstead$registerClientTooltipFactory(modBus);
        townstead$registerMenuScreens(modBus);
        townstead$registerAnimationReloadListener(modBus);
        townstead$registerBlockEntityRenderers(modBus);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
            townstead$profile("server.village_startup_seed", () ->
                    com.aetherianartificer.townstead.village.VillageStartupSeedScheduler.tick(e.getServer()));
            townstead$profile("server.village_spirit_query", () ->
                    com.aetherianartificer.townstead.spirit.VillageSpiritQueryScheduler.tick(e.getServer()));
            townstead$profile("server.world_calendar", () ->
                    com.aetherianartificer.townstead.calendar.WorldCalendarTicker.tick(e.getServer()));
            townstead$profile("server.memory_lifecycle", () ->
                    com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle.tick(e.getServer()));
            com.aetherianartificer.townstead.pheno.action.ActionScheduler.tick(e.getServer());
            com.aetherianartificer.townstead.pheno.field.CloudManager.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.aetherianartificer.townstead.calendar.AgeableCatchup.onEntityJoin(e.getEntity(), sl.getServer());
                if (e.getEntity() instanceof VillagerEntityMCA villager) {
                    com.aetherianartificer.townstead.villager.TownsteadVillagerState.root(villager);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent e) -> {
            net.minecraft.world.item.alchemy.PotionBrewing.Builder b = e.getBuilder();
            b.addMix(net.minecraft.world.item.alchemy.Potions.AWKWARD,
                    net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, IMMORTALITY_POTION);
            b.addMix(IMMORTALITY_POTION,
                    net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE, MORTALITY_POTION);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) -> {
            if (e.getTarget() instanceof VillagerEntityMCA villager
                    && com.aetherianartificer.townstead.item.VillagerPotionFeeding.isFeedable(e.getItemStack())) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(villager.level().isClientSide));
                if (!villager.level().isClientSide) {
                    com.aetherianartificer.townstead.item.VillagerPotionFeeding.feed(
                            e.getEntity(), villager, e.getItemStack(), e.getHand());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide) return;
            if (com.aetherianartificer.townstead.origin.Edibles.tryEat(e.getEntity(), e.getItemStack(), e.getHand())) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                townstead$seedBuildingRecognition(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                com.aetherianartificer.townstead.village.TownsteadVillageMigration.migrateServerIfNeeded(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                ShiftTemplateRegistry.setServer(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) ->
                ShiftTemplateRegistry.clearServer());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) ->
                WeekPlanRegistry.setServer(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) ->
                WeekPlanRegistry.clearServer());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) ->
                com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle.clearAll());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
                com.aetherianartificer.townstead.pheno.action.ActionScheduler.clear();
                com.aetherianartificer.townstead.pheno.field.CloudManager.clear();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                townstead$sendShiftTemplateSync(sp);
                townstead$sendWeekPlanSync(sp);
                PacketDistributor.sendToPlayer(sp, townstead$calendarSync(sp));
                PacketDistributor.sendToPlayer(sp,
                        com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(sp.serverLevel().getServer(), sp));
                com.aetherianartificer.townstead.origin.StartingEquipment.grant(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent e) -> {
            if (e.getPlayer() != null) {
                townstead$sendOriginData(e.getPlayer());
                PacketDistributor.sendToPlayer(e.getPlayer(), townstead$calendarSync(e.getPlayer()));
            } else {
                e.getPlayerList().getPlayers().forEach(sp -> {
                    townstead$sendOriginData(sp);
                    PacketDistributor.sendToPlayer(sp, townstead$calendarSync(sp));
                });
            }
        });
        ShiftTemplateRegistry.setChangeListener(Townstead::townstead$broadcastShiftTemplateSync);
        WeekPlanRegistry.setChangeListener(Townstead::townstead$broadcastWeekPlanSync);
        NeoForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(com.aetherianartificer.townstead.compat.butchery.ButcherTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (e.isWasDeath()) com.aetherianartificer.townstead.origin.KeepInventory.onClone(e.getOriginal(), e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ClientCapsStore.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.ActiveAbilities.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.ResourceValues.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.AbilityToggles.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.profession.skill.LearnedSkills.clear(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.emote.EmoteCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.reaction.command.ReactionCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.commands.CalendarCommands.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.commands.MemoryDiagnosticsCommands.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.origin.port.OriginsPortCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.pheno.lang.command.PhenoCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                com.aetherianartificer.townstead.origin.ability.GeneAbilityTicker.tick(sp);
                com.aetherianartificer.townstead.origin.attribute.GeneAttributeApplier.tick(sp);
                com.aetherianartificer.townstead.origin.ability.ResourceValues.tick(sp);
                com.aetherianartificer.townstead.origin.collection.CollectionValues.tick(sp);
                com.aetherianartificer.townstead.origin.ability.ResourceValues.syncTo(sp);
                com.aetherianartificer.townstead.origin.fx.OriginOverlays.syncTo(sp);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) -> {
            float modified = com.aetherianartificer.townstead.origin.damage.GeneDamageHandler.modify(
                    e.getEntity(), e.getSource(), e.getAmount());
            if (modified <= 0f) e.setCanceled(true);
            else e.setAmount(modified);
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onDamage(
                    e.getEntity(), e.getSource(), e.getAmount());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) -> {
            if (com.aetherianartificer.townstead.origin.prevent.Prevents.tryPreventDeath(e.getEntity())) {
                e.setCanceled(true);
                return;
            }
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onDeath(e.getEntity(), e.getSource());
            if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                com.aetherianartificer.townstead.profession.skill.LearnedSkills.clear(e.getEntity().getUUID());
                com.aetherianartificer.townstead.origin.collection.CollectionValues.onDeath(e.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start e) -> {
            if (com.aetherianartificer.townstead.origin.prevent.Prevents.prevents(e.getEntity(),
                    com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What.ITEM_USE, e.getItem())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent e) -> {
            if (e.getProblem() == null && com.aetherianartificer.townstead.origin.prevent.Prevents.prevents(e.getEntity(),
                    com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What.SLEEP)) {
                e.setProblem(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) -> {
            if (com.aetherianartificer.townstead.origin.mobsignore.MobsIgnore.shouldIgnore(
                    e.getEntity(), e.getNewAboutToBeSetTarget())
                    || com.aetherianartificer.townstead.origin.disposition.Dispositions.areFriendly(
                            e.getEntity(), e.getNewAboutToBeSetTarget())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.LivingEntity le && e.getSound() != null
                    && com.aetherianartificer.townstead.origin.sound.PreventSounds.shouldPrevent(le,
                            e.getSound().unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null))) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingFallEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onLand(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onWakeUp(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent e) -> {
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onJump(e.getEntity());
            com.aetherianartificer.townstead.origin.hook.PhenoHooks.jump(e.getEntity());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onItemUse(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onStruckByLightning(living);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onEquip(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingHealEvent e) -> {
            if (com.aetherianartificer.townstead.origin.NaturalRegen.isSuppressed(e.getEntity())) {
                e.setCanceled(true);
                return;
            }
            float scaled = com.aetherianartificer.townstead.origin.hook.PhenoHooks.heal(e.getEntity(), e.getAmount());
            if (scaled <= 0f) e.setCanceled(true);
            else e.setAmount(scaled);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed e) ->
                e.setNewSpeed(com.aetherianartificer.townstead.origin.ability.GeneAbilityTicker.aerialBreakSpeed(e.getEntity(),
                        com.aetherianartificer.townstead.origin.hook.PhenoHooks.breakSpeed(e.getEntity(), e.getNewSpeed()))));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.HarvestCheck e) -> {
            if (!e.canHarvest() && com.aetherianartificer.townstead.origin.harvest.ModifyHarvest.allows(
                    e.getEntity(), e.getTargetBlock())) {
                e.setCanHarvest(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerXpEvent.XpChange e) ->
                e.setAmount(com.aetherianartificer.townstead.origin.hook.PhenoHooks.xpGain(e.getEntity(), e.getAmount())));
        townstead$registerEmotePlaybackClear();
        registerDialogueConditions();
        LOGGER.info("Townstead loaded");
    }

    private static void townstead$registerEmotePlaybackClear() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
                        com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry.clear();
                        com.aetherianartificer.townstead.client.origin.OriginClientStore.clear();
                        com.aetherianartificer.townstead.client.origin.ResourceClientStore.clear();
                        com.aetherianartificer.townstead.client.origin.OverlayClientStore.clear();
                        com.aetherianartificer.townstead.client.attachment.AttachmentClient.clear();
                    });
        } catch (Exception ignored) {
            // Dedicated server: no client-side playback registry to clear.
        }
    }

    //?} else if forge {
    /*public Townstead() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PROFESSIONS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MOB_EFFECTS.register(modBus);
        POTIONS.register(modBus);
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
        townstead$registerKeybinds(modBus);
        townstead$registerHudOverlays(modBus);
        townstead$registerClientTooltipFactory(modBus);
        townstead$registerMenuScreens(modBus);
        townstead$registerAnimationReloadListener(modBus);
        townstead$registerBlockEntityRenderers(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::addPackFinders);
        MinecraftForge.EVENT_BUS.addListener(this::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                townstead$profile("server.village_startup_seed", () ->
                        com.aetherianartificer.townstead.village.VillageStartupSeedScheduler.tick(e.getServer()));
                townstead$profile("server.village_spirit_query", () ->
                        com.aetherianartificer.townstead.spirit.VillageSpiritQueryScheduler.tick(e.getServer()));
                townstead$profile("server.world_calendar", () ->
                        com.aetherianartificer.townstead.calendar.WorldCalendarTicker.tick(e.getServer()));
                townstead$profile("server.memory_lifecycle", () ->
                        com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle.tick(e.getServer()));
                com.aetherianartificer.townstead.pheno.action.ActionScheduler.tick(e.getServer());
                com.aetherianartificer.townstead.pheno.field.CloudManager.tick(e.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.aetherianartificer.townstead.calendar.AgeableCatchup.onEntityJoin(e.getEntity(), sl.getServer());
                if (e.getEntity() instanceof VillagerEntityMCA villager) {
                    com.aetherianartificer.townstead.villager.TownsteadVillagerState.root(villager);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) -> {
            if (e.getTarget() instanceof VillagerEntityMCA villager
                    && com.aetherianartificer.townstead.item.VillagerPotionFeeding.isFeedable(e.getItemStack())) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(villager.level().isClientSide));
                if (!villager.level().isClientSide) {
                    com.aetherianartificer.townstead.item.VillagerPotionFeeding.feed(
                            e.getEntity(), villager, e.getItemStack(), e.getHand());
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide) return;
            if (com.aetherianartificer.townstead.origin.Edibles.tryEat(e.getEntity(), e.getItemStack(), e.getHand())) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) ->
                townstead$seedBuildingRecognition(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) ->
                com.aetherianartificer.townstead.village.TownsteadVillageMigration.migrateServerIfNeeded(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) ->
                ShiftTemplateRegistry.setServer(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent e) ->
                ShiftTemplateRegistry.clearServer());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) ->
                WeekPlanRegistry.setServer(e.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent e) ->
                WeekPlanRegistry.clearServer());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent e) ->
                com.aetherianartificer.townstead.memory.TownsteadMemoryLifecycle.clearAll());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent e) -> {
                com.aetherianartificer.townstead.pheno.action.ActionScheduler.clear();
                com.aetherianartificer.townstead.pheno.field.CloudManager.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                TownsteadNetwork.sendShiftTemplateSync(sp);
                TownsteadNetwork.sendWeekPlanSync(sp);
                TownsteadNetwork.sendToPlayer(sp, townstead$calendarSync(sp));
                TownsteadNetwork.sendToPlayer(sp,
                        com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(sp.serverLevel().getServer(), sp));
                com.aetherianartificer.townstead.origin.StartingEquipment.grant(sp);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent e) -> {
            if (e.getPlayer() != null) {
                townstead$sendOriginData(e.getPlayer());
                TownsteadNetwork.sendToPlayer(e.getPlayer(), townstead$calendarSync(e.getPlayer()));
            } else {
                e.getPlayerList().getPlayers().forEach(sp -> {
                    townstead$sendOriginData(sp);
                    TownsteadNetwork.sendToPlayer(sp, townstead$calendarSync(sp));
                });
            }
        });
        ShiftTemplateRegistry.setChangeListener(TownsteadNetwork::broadcastShiftTemplateSync);
        WeekPlanRegistry.setChangeListener(TownsteadNetwork::broadcastWeekPlanSync);
        MinecraftForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(com.aetherianartificer.townstead.compat.butchery.ButcherTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (e.isWasDeath()) com.aetherianartificer.townstead.origin.KeepInventory.onClone(e.getOriginal(), e.getEntity());
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ClientCapsStore.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.ActiveAbilities.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.ResourceValues.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.origin.ability.AbilityToggles.clear(e.getEntity().getUUID());
            com.aetherianartificer.townstead.profession.skill.LearnedSkills.clear(e.getEntity().getUUID());
        });
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.emote.EmoteCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.reaction.command.ReactionCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.commands.CalendarCommands.register(
                                e.getDispatcher(), e.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.commands.MemoryDiagnosticsCommands.register(
                                e.getDispatcher(), e.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.origin.port.OriginsPortCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        com.aetherianartificer.townstead.pheno.lang.command.PhenoCommand.register(
                                e.getDispatcher(), e.getBuildContext()));
        try {
            Class.forName("net.minecraft.client.Minecraft");
            MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
                        com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry.clear();
                        com.aetherianartificer.townstead.client.origin.OriginClientStore.clear();
                        com.aetherianartificer.townstead.client.origin.ResourceClientStore.clear();
                        com.aetherianartificer.townstead.client.origin.OverlayClientStore.clear();
                        com.aetherianartificer.townstead.client.attachment.AttachmentClient.clear();
                    });
        } catch (Exception ignored) {
            // Dedicated server: no client-side playback registry to clear.
        }
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.PlayerTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer sp) {
                com.aetherianartificer.townstead.origin.ability.GeneAbilityTicker.tick(sp);
                com.aetherianartificer.townstead.origin.attribute.GeneAttributeApplier.tick(sp);
                com.aetherianartificer.townstead.origin.ability.ResourceValues.tick(sp);
                com.aetherianartificer.townstead.origin.collection.CollectionValues.tick(sp);
                com.aetherianartificer.townstead.origin.ability.ResourceValues.syncTo(sp);
                com.aetherianartificer.townstead.origin.fx.OriginOverlays.syncTo(sp);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHurtEvent e) -> {
            float modified = com.aetherianartificer.townstead.origin.damage.GeneDamageHandler.modify(
                    e.getEntity(), e.getSource(), e.getAmount());
            if (modified <= 0f) e.setCanceled(true);
            else e.setAmount(modified);
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onDamage(
                    e.getEntity(), e.getSource(), e.getAmount());
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDeathEvent e) -> {
            if (com.aetherianartificer.townstead.origin.prevent.Prevents.tryPreventDeath(e.getEntity())) {
                e.setCanceled(true);
                return;
            }
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onDeath(e.getEntity(), e.getSource());
            if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player)) {
                com.aetherianartificer.townstead.profession.skill.LearnedSkills.clear(e.getEntity().getUUID());
                com.aetherianartificer.townstead.origin.collection.CollectionValues.onDeath(e.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start e) -> {
            if (com.aetherianartificer.townstead.origin.prevent.Prevents.prevents(e.getEntity(),
                    com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What.ITEM_USE, e.getItem())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerSleepInBedEvent e) -> {
            if (e.getResultStatus() == null && com.aetherianartificer.townstead.origin.prevent.Prevents.prevents(e.getEntity(),
                    com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What.SLEEP)) {
                e.setResult(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) -> {
            if (com.aetherianartificer.townstead.origin.mobsignore.MobsIgnore.shouldIgnore(
                    e.getEntity(), e.getNewTarget())
                    || com.aetherianartificer.townstead.origin.disposition.Dispositions.areFriendly(
                            e.getEntity(), e.getNewTarget())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.PlayLevelSoundEvent.AtEntity e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.LivingEntity le && e.getSound() != null
                    && com.aetherianartificer.townstead.origin.sound.PreventSounds.shouldPrevent(le,
                            e.getSound().unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null))) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingFallEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onLand(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerWakeUpEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onWakeUp(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent e) -> {
            com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onJump(e.getEntity());
            com.aetherianartificer.townstead.origin.hook.PhenoHooks.jump(e.getEntity());
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onItemUse(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityStruckByLightningEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onStruckByLightning(living);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent e) ->
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.onEquip(e.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingHealEvent e) -> {
            if (com.aetherianartificer.townstead.origin.NaturalRegen.isSuppressed(e.getEntity())) {
                e.setCanceled(true);
                return;
            }
            float scaled = com.aetherianartificer.townstead.origin.hook.PhenoHooks.heal(e.getEntity(), e.getAmount());
            if (scaled <= 0f) e.setCanceled(true);
            else e.setAmount(scaled);
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed e) ->
                e.setNewSpeed(com.aetherianartificer.townstead.origin.ability.GeneAbilityTicker.aerialBreakSpeed(e.getEntity(),
                        com.aetherianartificer.townstead.origin.hook.PhenoHooks.breakSpeed(e.getEntity(), e.getNewSpeed()))));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.HarvestCheck e) -> {
            if (!e.canHarvest() && com.aetherianartificer.townstead.origin.harvest.ModifyHarvest.allows(
                    e.getEntity(), e.getTargetBlock())) {
                e.setCanHarvest(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerXpEvent.XpChange e) ->
                e.setAmount(com.aetherianartificer.townstead.origin.hook.PhenoHooks.xpGain(e.getEntity(), e.getAmount())));
        registerDialogueConditions();
        LOGGER.info("Townstead loaded");
    }
    *///?}

    private static void townstead$profile(String name, Runnable runnable) {
        if (!com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.enabled()) {
            runnable.run();
            return;
        }
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            com.aetherianartificer.townstead.diagnostics.TownsteadProfiler.record(name, System.nanoTime() - start);
        }
    }

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
            com.aetherianartificer.townstead.profession.PoilessTradingProfessions.register(COOK_PROFESSION);

            if (ModCompat.isLoaded("rusticdelight")) {
                VillagerProfession barista = BARISTA_PROFESSION.get();
                //? if neoforge {
                ProfessionsMCA.IS_IMPORTANT.add(barista);
                ProfessionsMCA.CAN_NOT_TRADE.remove(barista);
                //?} else {
                /*ProfessionsMCA.isImportant.add(barista);
                ProfessionsMCA.canNotTrade.remove(barista);
                *///?}
                com.aetherianartificer.townstead.profession.PoilessTradingProfessions.register(BARISTA_PROFESSION);
            }
        });
        event.enqueueWork(RusticDelightThirstCompat::register);
        //? if forge {
        /*event.enqueueWork(Townstead::townstead$registerLifeBrewing);
        *///?}
        event.enqueueWork(() -> {
            com.aetherianartificer.townstead.reaction.backend.ReactionBackends.register(
                    new com.aetherianartificer.townstead.reaction.backend.EmotecraftReactionBackend());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.GestureTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.TaskTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.ContextEnterTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.ContextPresentTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.IdleSpotTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.TriggerTypes.register(
                    new com.aetherianartificer.townstead.reaction.trigger.types.TimeTriggerType());
            com.aetherianartificer.townstead.reaction.trigger.event.MusicSourceProviders.register(
                    new com.aetherianartificer.townstead.reaction.trigger.event.JukeboxMusicSourceProvider());

            // Origin gene types (Apoli-style behavior backbone)
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ScaledPartGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.CosmeticFeatureGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.DietGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.HydrationGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ChronotypeGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.AttributeGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.StepHeightGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.SkinToneGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.AttachmentGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.BodyMetricGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ProportionsGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.DamageModifierGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.GlowGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.HideFeatureGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ActiveAbilityGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ParticleGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.RestrictEquipmentGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.CollectionGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.StartingEquipmentGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.AuraGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.EdibleGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ResourceGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.EffectImmunityGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.KeepInventoryGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ActionOverTimeGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.DisableRegenGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.TriggerGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.OverlayGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.PreventGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.MobsIgnoreGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.PreventSoundGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ScareMobGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ModifyHarvestGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.StackingEffectGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.PreventGameEventGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.ToggleGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.InventoryGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.RecipeGeneType());

            // Condition types that gate conditioned genes (Apoli entity-condition subset)
            registerConditionTypes();
            // Bi-entity conditions (actor/target relationships), consumed by if_bientity
            registerBiEntityConditionTypes();
            // Action types that active-ability genes run (Apoli entity-action subset)
            registerActionTypes();
            // Selector sources (the object form of an action's on)
            registerSelectorTypes();
            // The genetics source feeding the shared Power facade (professions will add another later)
            com.aetherianartificer.townstead.pheno.power.Powers.register(
                    new com.aetherianartificer.townstead.origin.GenePowerSource());
            // How entities react without factions, from data-pack group relations; the faction
            // system will register an authoritative source over this later.
            com.aetherianartificer.townstead.origin.disposition.Dispositions.register(
                    new com.aetherianartificer.townstead.origin.disposition.DataDispositionSource());
            // Read-side genetics feed for the capability layer (provenance for /pheno explain)
            com.aetherianartificer.townstead.pheno.capability.Capabilities.register(
                    new com.aetherianartificer.townstead.origin.capability.GeneCapabilitySource());
            // Learned-skill feed for the capability layer (professions blend with genetics)
            com.aetherianartificer.townstead.pheno.capability.Capabilities.register(
                    new com.aetherianartificer.townstead.profession.skill.ProfessionCapabilitySource());
            // Node schemas (one source of truth for normalization, validation, and generated docs)
            com.aetherianartificer.townstead.pheno.lang.schema.PhenoSchemas.registerAll();

            // Trait effect palette (data-pack traits compose these; see TraitJsonLoader)
            com.aetherianartificer.townstead.origin.trait.effect.TraitEffectTypes.register(
                    new com.aetherianartificer.townstead.origin.trait.effect.types.SetImmortalEffectType());
        });
    }

    private static void registerConditionTypes() {
        com.aetherianartificer.townstead.pheno.condition.types.StateConditionType[] states = {
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:in_rain", ctx -> ctx.level().isRainingAt(ctx.pos())),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:on_fire", ctx -> ctx.entity().isOnFire()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:sneaking", ctx -> ctx.entity().isShiftKeyDown()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:sprinting", ctx -> ctx.entity().isSprinting()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:moving", ctx -> ctx.entity().getDeltaMovement().horizontalDistanceSqr() > 1.0e-6),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:crawling", ctx -> ctx.entity().isVisuallyCrawling()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:exists", ctx -> true),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:submerged", ctx -> ctx.entity().isUnderWater()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:in_water", ctx -> ctx.entity().isInWater()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:exposed_to_sky", ctx -> ctx.level().canSeeSky(ctx.pos())),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:daytime", ctx -> ctx.level().isDay()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:on_ground", ctx -> ctx.entity().onGround()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:climbing", ctx -> ctx.entity().onClimbable()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:collided_horizontally", ctx -> ctx.entity().horizontalCollision),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:creative_flying", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:fall_flying", ctx -> ctx.entity().isFallFlying()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:glowing", ctx -> ctx.entity().isCurrentlyGlowing()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:invisible", ctx -> ctx.entity().isInvisible()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:swimming", ctx -> ctx.entity().isSwimming()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:using_item", ctx -> ctx.entity().isUsingItem()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:riding", ctx -> ctx.entity().isPassenger()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:passenger", ctx -> !ctx.entity().getPassengers().isEmpty()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:tamed", ctx -> ctx.entity() instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:in_thunderstorm", ctx -> ctx.level().isThundering() && ctx.level().isRainingAt(ctx.pos())),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:exposed_to_sun", ctx -> ctx.level().isDay() && !ctx.level().isRaining() && ctx.level().canSeeSky(ctx.pos())),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:raining", ctx -> ctx.level().isRaining()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:thundering", ctx -> ctx.level().isThundering()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:grounded", ctx -> ctx.entity().onGround()),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:hostile", ctx -> ctx.entity() instanceof net.minecraft.world.entity.monster.Enemy),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:in_snow", ctx -> ctx.level().isRaining() && ctx.level().canSeeSky(ctx.pos())
                                && ctx.level().getBiome(ctx.pos()).value().getPrecipitationAt(ctx.pos())
                                        == net.minecraft.world.level.biome.Biome.Precipitation.SNOW),
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:alive", ctx -> ctx.entity().isAlive()),
        };
        for (var state : states) {
            com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(state);
        }
        com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType[] numerics = {
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:air", ctx -> ctx.entity().getAirSupply()),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:fall_distance", ctx -> ctx.entity().fallDistance),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:food_level", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player p ? p.getFoodData().getFoodLevel() : Double.NaN),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:saturation_level", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player p ? p.getFoodData().getSaturationLevel() : Double.NaN),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:xp_levels", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player p ? p.experienceLevel : Double.NaN),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:xp_points", ctx -> ctx.entity() instanceof net.minecraft.world.entity.player.Player p ? p.totalExperience : Double.NaN),
                new com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType(
                        "pheno:max_health", ctx -> ctx.entity().getMaxHealth()),
        };
        for (var numeric : numerics) {
            com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(numeric);
        }
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.BrightnessConditionType());
        // Consolidated environment query (weather/exposure/time/biome/dimension/effects in one block)
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EnvironmentConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.TimeOfDayConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.DimensionConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.BiomeConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.BlockAtConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType("pheno:entity_type"));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType("pheno:in_tag"));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.GamemodeConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.SubmergedInConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.AttributeConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.DistanceFromCoordinatesConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EquippedItemConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.InventoryConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.VelocityConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StatusEffectTagConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StructureConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EntityInRadiusConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.BlockInRadiusConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.OnCooldownConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.CompareResourceConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.ToggledConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.ResourceConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.EntityGroupConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.AbilityConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.OriginConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.HealthConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StatusEffectConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:and",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.AND));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:or",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.OR));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:not",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.NOT));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.ChanceConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.ConstantConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionSizeConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionHasConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.FluidHeightConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.PassengerRecursiveConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.ScaleConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.CountConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.SelectionTestConditionType(
                        "pheno:any",
                        com.aetherianartificer.townstead.pheno.condition.types.SelectionTestConditionType.Mode.ANY));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.SelectionTestConditionType(
                        "pheno:none",
                        com.aetherianartificer.townstead.pheno.condition.types.SelectionTestConditionType.Mode.NONE));
    }

    private static void registerBiEntityConditionTypes() {
        com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType[] simple = {
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:attack_target",
                        (a, t) -> a instanceof net.minecraft.world.entity.Mob m && m.getTarget() == t),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:attacker", (a, t) -> a.getLastHurtByMob() == t),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:can_see", (a, t) -> a.hasLineOfSight(t)),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:owner",
                        (a, t) -> a instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() == t),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:riding", (a, t) -> a.getVehicle() == t),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:riding_root", (a, t) -> a.getRootVehicle() == t),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:riding_recursive", (a, t) -> {
                            net.minecraft.world.entity.Entity v = a.getVehicle();
                            while (v != null) {
                                if (v == t) return true;
                                v = v.getVehicle();
                            }
                            return false;
                        }),
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:equal", (a, t) -> a == t),
                // The actor is the target's kill-credit holder (Apugli prime_adversary).
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.SimpleBiConditionType(
                        "pheno:prime_adversary", (a, t) -> t.getKillCredit() == a),
        };
        for (var type : simple) {
            com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(type);
        }
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.DistanceBiConditionType());
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.RelativeRotationBiConditionType());

        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType(
                        "pheno:actor_condition", com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType.Scope.ACTOR));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType(
                        "pheno:target_condition", com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType.Scope.TARGET));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType(
                        "pheno:both", com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType.Scope.BOTH));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType(
                        "pheno:either", com.aetherianartificer.townstead.pheno.condition.bientity.types.EntityScopedBiConditionType.Scope.EITHER));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.DirectionalBiConditionType(
                        "pheno:invert", com.aetherianartificer.townstead.pheno.condition.bientity.types.DirectionalBiConditionType.Mode.INVERT));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.DirectionalBiConditionType(
                        "pheno:undirected", com.aetherianartificer.townstead.pheno.condition.bientity.types.DirectionalBiConditionType.Mode.UNDIRECTED));
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionContainsConditionType());
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionCountConditionType());
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.bientity.types.CompareDimensionsBiConditionType());
        com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes.register(
                new com.aetherianartificer.townstead.origin.condition.types.CompareScalesBiConditionType());
    }

    private static void registerSelectorTypes() {
        com.aetherianartificer.townstead.pheno.selector.SelectorTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionSelectorType());
        com.aetherianartificer.townstead.pheno.selector.BlockSelectorTypes.register(
                new com.aetherianartificer.townstead.origin.collection.CollectionBlockSelectorType());
        com.aetherianartificer.townstead.pheno.selector.SelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.CommandSelectorType());
        com.aetherianartificer.townstead.pheno.selector.SelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.RaySelectorType());
        com.aetherianartificer.townstead.pheno.selector.BlockSelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.RayBlockSelectorType());
        // Value sources (the object form of a number)
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.CountValueType());
    }

    private static void registerActionTypes() {
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.ChangeCollectionActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.ForEachActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.AtActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SpawnCloudActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.BeamActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.EmitGameEventActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ZombifyVillagerActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ApplyEffectActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.HealActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.DamageActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.VelocityActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.JumpActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.FireActionType(
                        com.aetherianartificer.townstead.pheno.action.types.FireActionType.IGNITE_KEY, true));
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.FireActionType(
                        com.aetherianartificer.townstead.pheno.action.types.FireActionType.EXTINGUISH_KEY, false));
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ChangeResourceActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.FreezeActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ExhaustActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.FireProjectileActionType());
        // Bi-entity actions (operate on actor=entity / target=other)
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ActorActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.TargetActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.InvertActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.MountActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.TameActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SetInLoveActionType());
        // Entity actions (operate on the actor; some are player-only)
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.AddXpActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ClearEffectActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.GiveActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.GainAirActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.RandomTeleportActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SetFallDistanceActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SwingHandActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.PlaySoundActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SpawnParticlesActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SpawnEntityActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ExplodeActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.DismountActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.DropInventoryActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.FeedActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ExecuteCommandActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.AreaOfEffectActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.PassengerActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.RidingActionType());
        // Meta actions (combine / gate other actions of the same family)
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.AndActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ChanceActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ChoiceActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.DelayActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.IfElseActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.IfElseListActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.NothingActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SideActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.IfBientityActionType());
        // Bridges: run a block action at the actor's position / an item action on equipped gear
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.RunBlockActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.EquippedItemActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SetNoGravityActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SpawnItemActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ResourceTransferActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.ItemCooldownActionType());
        registerBlockActionTypes();
        registerItemActionTypes();
    }

    private static void registerItemActionTypes() {
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.ConsumeItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.DamageItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.HolderActionItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.RemoveEnchantmentItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.CooldownItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.ChangeCollectionItemActionType());
    }

    private static void registerBlockActionTypes() {
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.SetBlockBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.AddBlockBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.BonemealBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.ExplodeBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.ModifyBlockStateBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.ExecuteCommandBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.SpawnEntityBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.AreaOfEffectBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.OffsetBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.DestroyBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.types.ScheduleTickBlockActionType());
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.origin.collection.ChangeCollectionBlockActionType());
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CatalogDataLoader());
        event.addListener(new com.aetherianartificer.townstead.reaction.ReactionDataLoader());
        event.addListener(new ShiftTemplateJsonLoader());
        event.addListener(new WeekPlanJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.calendar.CalendarProfileJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.SpeciesJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.AncestryJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.LineageJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.OriginJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.HeritageJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.chronotype.ChronotypeCatalogLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.gene.GeneJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.rig.RigJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.disposition.DispositionRelationsLoader());
        event.addListener(new com.aetherianartificer.townstead.profession.def.ProfessionDataLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.trait.TraitJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.attachment.AttachmentServerLoader());
        com.aetherianartificer.townstead.farming.CropProductResolver.invalidate();
    }

    /**
     * Populate BuildingRecognitionTracker with the current state of every
     * village on every level so the player's first blueprint action doesn't
     * get silenced as the "initial snapshot" — pre-existing buildings are
     * baseline, not news. Subsequent reconciles diff against this seed and
     * emit events only for real adds/upgrades.
     */
    private static void townstead$seedBuildingRecognition(MinecraftServer server) {
        com.aetherianartificer.townstead.village.VillageStartupSeedScheduler.enqueue(server);
    }

    private void registerDialogueConditions() {
        GiftPredicate.register("hunger", (json, name) ->
                GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    int h = TownsteadVillagers.get(villager).needs().hunger();
                    HungerData.HungerState current = HungerData.getState(h);
                    return townstead$hungerAtLeast(current, state) ? 1.0f : 0.0f;
                });
        GiftPredicate.register("thirst", (json, name) ->
                        GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    if (!ThirstBridgeResolver.isActive()) return 0.0f;
                    int t = TownsteadVillagers.get(villager).needs().thirst();
                    ThirstData.ThirstState current = ThirstData.getState(t);
                    return townstead$thirstAtLeast(current, state) ? 1.0f : 0.0f;
                });
        GiftPredicate.register("fatigue", (json, name) ->
                        GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                state -> (villager, stack, player) -> {
                    if (!TownsteadConfig.isVillagerFatigueEnabled()) return 0.0f;
                    int f = TownsteadVillagers.get(villager).needs().fatigue();
                    FatigueData.FatigueState current = FatigueData.getState(f);
                    return townstead$fatigueAtLeast(current, state) ? 1.0f : 0.0f;
                });
        GiftPredicate.register("village_life", (json, name) ->
                        GsonHelper.convertToString(json, name).toLowerCase(Locale.ROOT),
                topic -> (villager, stack, player) ->
                        townstead$villageHasLifeTopic(villager, topic) ? 1.0f : 0.0f);
    }

    private static boolean townstead$villageHasLifeTopic(VillagerEntityMCA villager, String topic) {
        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (village.isEmpty() || !village.get().isWithinBorder(villager)) {
            village = Village.findNearest(villager);
        }
        if (village.isEmpty() || !village.get().isWithinBorder(villager)) return false;

        for (net.conczin.mca.server.world.data.Building building : village.get().getBuildings().values()) {
            if (!building.isComplete()) continue;
            String type = building.getType();
            if (type == null) continue;
            if (townstead$buildingMatchesLifeTopic(type, topic)) return true;
        }
        return false;
    }

    private static boolean townstead$buildingMatchesLifeTopic(String type, String topic) {
        return switch (topic) {
            case "butchery" -> type.equals("butcher")
                    || type.equals("compat/butchery/butcher_shop_l1")
                    || type.equals("compat/butchery/butcher_shop_l2")
                    || type.equals("compat/butchery/butcher_shop_l3")
                    || type.equals("compat/butchery/slaughterhouse")
                    || type.equals("compat/butchery/smokehouse")
                    || type.equals("compat/butchery/tannery");
            case "butcher_shop" -> type.equals("butcher")
                    || type.equals("compat/butchery/butcher_shop_l1")
                    || type.equals("compat/butchery/butcher_shop_l2")
                    || type.equals("compat/butchery/butcher_shop_l3");
            case "slaughterhouse" -> type.equals("compat/butchery/slaughterhouse");
            case "smokehouse" -> type.equals("compat/butchery/smokehouse");
            case "tannery" -> type.equals("compat/butchery/tannery");
            default -> false;
        };
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
                    (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
                        event.register(com.aetherianartificer.townstead.client.TownsteadKeybinds.TALK);
                        for (net.minecraft.client.KeyMapping key :
                                com.aetherianartificer.townstead.client.TownsteadKeybinds.ABILITIES) {
                            event.register(key);
                        }
                    }
            );
        } catch (Exception ignored) {
            // Dedicated server: no keybinds.
        }
    }
    //?} else {
    /*private static void townstead$registerKeybinds(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.minecraftforge.client.event.RegisterKeyMappingsEvent event) -> {
                        event.register(com.aetherianartificer.townstead.client.TownsteadKeybinds.TALK);
                        for (net.minecraft.client.KeyMapping key :
                                com.aetherianartificer.townstead.client.TownsteadKeybinds.ABILITIES) {
                            event.register(key);
                        }
                    }
            );
        } catch (Exception ignored) {
            // Dedicated server: no keybinds.
        }
    }
    *///?}

    //? if neoforge {
    private static void townstead$registerHudOverlays(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) -> {
                event.registerAboveAll(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "origin_overlays"),
                        (guiGraphics, deltaTracker) ->
                                com.aetherianartificer.townstead.client.origin.OverlayHudOverlay.render(guiGraphics));
                event.registerAboveAll(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "resource_bars"),
                        (guiGraphics, deltaTracker) ->
                                com.aetherianartificer.townstead.client.origin.ResourceHudOverlay.render(guiGraphics));
            });
        } catch (Exception ignored) {
            // Dedicated server: no HUD.
        }
    }
    //?} else {
    /*private static void townstead$registerHudOverlays(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener((net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) -> {
                event.registerAboveAll("origin_overlays",
                        (gui, guiGraphics, partialTick, width, height) ->
                                com.aetherianartificer.townstead.client.origin.OverlayHudOverlay.render(guiGraphics));
                event.registerAboveAll("resource_bars",
                        (gui, guiGraphics, partialTick, width, height) ->
                                com.aetherianartificer.townstead.client.origin.ResourceHudOverlay.render(guiGraphics));
            });
        } catch (Exception ignored) {
            // Dedicated server: no HUD.
        }
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

    //? if neoforge {
    private static void townstead$registerBlockEntityRenderers(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                            event.registerBlockEntityRenderer(
                                    CALENDAR_BE.get(),
                                    com.aetherianartificer.townstead.client.render.block.CalendarBlockEntityRenderer::new)
            );
        } catch (Exception ignored) {
            // Dedicated server: no renderers to register.
        }
    }
    //?} else {
    /*private static void townstead$registerBlockEntityRenderers(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                            event.registerBlockEntityRenderer(
                                    CALENDAR_BE.get(),
                                    com.aetherianartificer.townstead.client.render.block.CalendarBlockEntityRenderer::new)
            );
        } catch (Exception ignored) {
            // Dedicated server: no renderers to register.
        }
    }
    *///?}

    //? if neoforge {
    private static void townstead$registerAnimationReloadListener(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                            event.registerReloadListener(
                                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                                            rm -> com.aetherianartificer.townstead.client.animation.McaAnimationBridge.onResourcesReloaded())
            );
        } catch (Exception ignored) {
            // Dedicated server: no client resource pack stack to track.
        }
    }
    //?} else {
    /*private static void townstead$registerAnimationReloadListener(IEventBus modBus) {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            modBus.addListener(
                    (net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) ->
                            event.registerReloadListener(
                                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                                            rm -> com.aetherianartificer.townstead.client.animation.McaAnimationBridge.onResourcesReloaded())
            );
        } catch (Exception ignored) {
            // Dedicated server: no client resource pack stack to track.
        }
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
                FishermanStatusSyncPayload.TYPE,
                FishermanStatusSyncPayload.STREAM_CODEC,
                this::handleFishermanStatusSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload.TYPE,
                com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload.STREAM_CODEC,
                this::handleVillageSpiritSync
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload.TYPE,
                com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload.STREAM_CODEC,
                this::handleVillageSpiritQuery
        );
        registrar.playToClient(
                FishermanHookLinkPayload.TYPE,
                FishermanHookLinkPayload.STREAM_CODEC,
                this::handleFishermanHookLink
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
        registrar.playToClient(
                ShiftTemplateSyncPayload.TYPE,
                ShiftTemplateSyncPayload.STREAM_CODEC,
                this::handleShiftTemplateSync
        );
        registrar.playToServer(
                ShiftTemplateSavePayload.TYPE,
                ShiftTemplateSavePayload.STREAM_CODEC,
                this::handleShiftTemplateSave
        );
        registrar.playToServer(
                ShiftTemplateDeletePayload.TYPE,
                ShiftTemplateDeletePayload.STREAM_CODEC,
                this::handleShiftTemplateDelete
        );
        registrar.playToServer(
                ShiftTemplateApplyPayload.TYPE,
                ShiftTemplateApplyPayload.STREAM_CODEC,
                this::handleShiftTemplateApply
        );
        registrar.playToClient(
                ShiftWeekSyncPayload.TYPE,
                ShiftWeekSyncPayload.STREAM_CODEC,
                this::handleShiftWeekSync
        );
        registrar.playToServer(
                ShiftWeekSetPayload.TYPE,
                ShiftWeekSetPayload.STREAM_CODEC,
                this::handleShiftWeekSet
        );
        registrar.playToClient(
                WeekPlanSyncPayload.TYPE,
                WeekPlanSyncPayload.STREAM_CODEC,
                this::handleWeekPlanSync
        );
        registrar.playToServer(
                WeekPlanSavePayload.TYPE,
                WeekPlanSavePayload.STREAM_CODEC,
                this::handleWeekPlanSave
        );
        registrar.playToServer(
                WeekPlanDeletePayload.TYPE,
                WeekPlanDeletePayload.STREAM_CODEC,
                this::handleWeekPlanDelete
        );
        registrar.playToServer(
                WeekPlanApplyPayload.TYPE,
                WeekPlanApplyPayload.STREAM_CODEC,
                this::handleWeekPlanApply
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
        // Traveler's Titles integration
        registrar.playToServer(
                ClientCapsPayload.TYPE,
                ClientCapsPayload.STREAM_CODEC,
                this::handleClientCaps
        );
        registrar.playToClient(
                VillageEnterTitlePayload.TYPE,
                VillageEnterTitlePayload.STREAM_CODEC,
                this::handleVillageEnterTitle
        );
        // Emotecraft animation triggers
        registrar.playToClient(
                com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload.TYPE,
                com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload.STREAM_CODEC,
                this::handleEmoteTriggerS2C
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload.TYPE,
                com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload.STREAM_CODEC,
                this::handleEmoteTriggerC2S
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload.TYPE,
                com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload.STREAM_CODEC,
                this::handleGestureNotifyC2S
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload.TYPE,
                com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload.STREAM_CODEC,
                this::handleDialogueStateC2S
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.calendar.CalendarSyncPayload.TYPE,
                com.aetherianartificer.townstead.calendar.CalendarSyncPayload.STREAM_CODEC,
                this::handleCalendarSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload.TYPE,
                com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload.STREAM_CODEC,
                this::handleVillagerLifeSync
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.calendar.VillagerLifeRequestC2SPayload.TYPE,
                com.aetherianartificer.townstead.calendar.VillagerLifeRequestC2SPayload.STREAM_CODEC,
                this::handleVillagerLifeRequest
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload.TYPE,
                com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload.STREAM_CODEC,
                this::handleStampSync
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload.TYPE,
                com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload.STREAM_CODEC,
                this::handleStampAction
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.OriginSetC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.OriginSetC2SPayload.STREAM_CODEC,
                this::handleOriginSet
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.SetGeneVariantC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.SetGeneVariantC2SPayload.STREAM_CODEC,
                this::handleSetGeneVariant
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.ability.ActivateAbilityC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.ability.ActivateAbilityC2SPayload.STREAM_CODEC,
                this::handleActivateAbility
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.trigger.KeyPressC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.trigger.KeyPressC2SPayload.STREAM_CODEC,
                this::handleKeyPress
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload.STREAM_CODEC,
                this::handleOriginSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.STREAM_CODEC,
                this::handleExpressedGenesSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.ability.ResourceSyncS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.ability.ResourceSyncS2CPayload.STREAM_CODEC,
                this::handleResourceSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.ability.AbilityTogglesS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.ability.AbilityTogglesS2CPayload.STREAM_CODEC,
                this::handleAbilityTogglesSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.fx.OverlayActiveS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.fx.OverlayActiveS2CPayload.STREAM_CODEC,
                this::handleOverlayActiveSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.attachment.AttachmentManifestS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.attachment.AttachmentManifestS2CPayload.STREAM_CODEC,
                this::handleAttachmentManifest
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.attachment.AttachmentRequestC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.attachment.AttachmentRequestC2SPayload.STREAM_CODEC,
                this::handleAttachmentRequest
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.attachment.AttachmentChunkS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.attachment.AttachmentChunkS2CPayload.STREAM_CODEC,
                this::handleAttachmentChunk
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload.TYPE,
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload.STREAM_CODEC,
                this::handleOriginCatalogSync
        );
        registrar.playToServer(
                com.aetherianartificer.townstead.origin.HeritageRequestC2SPayload.TYPE,
                com.aetherianartificer.townstead.origin.HeritageRequestC2SPayload.STREAM_CODEC,
                this::handleHeritageRequest
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.HeritageSyncPayload.TYPE,
                com.aetherianartificer.townstead.origin.HeritageSyncPayload.STREAM_CODEC,
                this::handleHeritageSync
        );
    }

    private void handleHeritageRequest(
            com.aetherianartificer.townstead.origin.HeritageRequestC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            VillagerEntityMCA villager = townstead$findVillager(sp.getServer(), payload.villagerUuid());
            PacketDistributor.sendToPlayer(sp, villager != null
                    ? com.aetherianartificer.townstead.origin.HeritageView.build(villager)
                    : com.aetherianartificer.townstead.origin.HeritageSyncPayload.unavailable(payload.villagerUuid()));
        });
    }

    private void handleHeritageSync(
            com.aetherianartificer.townstead.origin.HeritageSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.HeritageClientStore.setFrom(payload));
    }

    private void handleCalendarSync(
            com.aetherianartificer.townstead.calendar.CalendarSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> com.aetherianartificer.townstead.calendar.CalendarClientStore.setFrom(payload));
    }

    private void handleStampSync(
            com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> com.aetherianartificer.townstead.calendar.CalendarStampClientStore.setFrom(payload));
    }

    private void handleStampAction(
            com.aetherianartificer.townstead.calendar.CalendarStampActionC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (com.aetherianartificer.townstead.calendar.CalendarStampServer.apply(sp, payload)) {
                townstead$broadcastStampSync(sp.getServer());
            }
        });
    }

    private void handleVillagerLifeSync(
            com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> com.aetherianartificer.townstead.calendar.LifeClientStore.setFrom(payload));
    }

    private void handleVillagerLifeRequest(
            com.aetherianartificer.townstead.calendar.VillagerLifeRequestC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            VillagerEntityMCA villager = townstead$findVillager(sp.getServer(), payload.villagerUuid());
            if (villager == null) return;
            net.minecraft.server.MinecraftServer server = villager.getServer();
            if (server != null) {
                com.aetherianartificer.townstead.calendar.VillagerLifeStamper.ensureStamped(villager, server);
            }
            com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload sync = townstead$lifeSync(villager);
            // Re-key to the editor's preview entity so its client-side lookups match.
            if (sync != null) PacketDistributor.sendToPlayer(sp, sync.withEntityId(payload.previewEntityId()));
        });
    }

    private void handleOriginSet(
            com.aetherianartificer.townstead.origin.OriginSetC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            com.aetherianartificer.townstead.origin.OriginServerLogic.Result result =
                    com.aetherianartificer.townstead.origin.OriginServerLogic.applyOrRequest(
                            sp, payload.entityId(), payload.originId());
            if (result == null) return;
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload sync =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(result.targetId(), result.originId());
            PacketDistributor.sendToPlayer(sp, sync);
            if (result.targetId() != com.aetherianartificer.townstead.origin.OriginSetC2SPayload.SELF) {
                Entity tracked = sp.serverLevel().getEntity(result.targetId());
                if (tracked != null) {
                    PacketDistributor.sendToPlayersTrackingEntity(tracked, sync);
                    if (tracked instanceof net.minecraft.world.entity.LivingEntity living) {
                        com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload genes =
                                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(tracked.getId(), living);
                        PacketDistributor.sendToPlayer(sp, genes);
                        PacketDistributor.sendToPlayersTrackingEntity(tracked, genes);
                    }
                }
            } else {
                // Self-origin change: also re-key by the player's network id so their own
                // model (sent to themselves) and bystanders' views (tracking sync) re-tint.
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload entitySync =
                        new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(sp.getId(), result.originId());
                PacketDistributor.sendToPlayer(sp, entitySync);
                PacketDistributor.sendToPlayersTrackingEntity(sp, entitySync);
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload selfGenes =
                        com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(sp.getId(), sp);
                PacketDistributor.sendToPlayer(sp, selfGenes);
                PacketDistributor.sendToPlayersTrackingEntity(sp, selfGenes);
            }
        });
    }

    private void handleSetGeneVariant(
            com.aetherianartificer.townstead.origin.SetGeneVariantC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            int target = com.aetherianartificer.townstead.origin.OriginServerLogic.setVariant(
                    sp, payload.entityId(), payload.geneId(), payload.variantId());
            if (target == com.aetherianartificer.townstead.origin.OriginSetC2SPayload.NONE) return;
            Entity entity = sp.serverLevel().getEntity(target);
            if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload genes =
                        com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(target, living);
                PacketDistributor.sendToPlayer(sp, genes);
                PacketDistributor.sendToPlayersTrackingEntity(entity, genes);
            }
        });
    }

    private void handleActivateAbility(
            com.aetherianartificer.townstead.origin.ability.ActivateAbilityC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                com.aetherianartificer.townstead.origin.ability.ActiveAbilities.activate(sp, payload.slot());
            }
        });
    }

    private void handleKeyPress(
            com.aetherianartificer.townstead.origin.trigger.KeyPressC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                com.aetherianartificer.townstead.origin.trigger.GeneTriggers.firePress(sp, payload.key());
            }
        });
    }

    private void handleOriginSync(
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.OriginClientStore.set(payload.entityId(), payload.originId()));
    }

    private void handleExpressedGenesSync(
            com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.OriginClientStore.setExpressed(
                        payload.entityId(), payload.genes()));
    }

    private void handleResourceSync(
            com.aetherianartificer.townstead.origin.ability.ResourceSyncS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.ResourceClientStore.set(payload.bars()));
    }

    private void handleAbilityTogglesSync(
            com.aetherianartificer.townstead.origin.ability.AbilityTogglesS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.OriginClientStore.setToggles(
                        payload.entityId(), payload.geneIds()));
    }

    private void handleOverlayActiveSync(
            com.aetherianartificer.townstead.origin.fx.OverlayActiveS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.OverlayClientStore.set(payload.geneIds()));
    }

    private void handleAttachmentManifest(
            com.aetherianartificer.townstead.origin.attachment.AttachmentManifestS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.attachment.AttachmentClient.onManifest(
                        payload.defs(), payload.slots()));
    }

    private void handleAttachmentChunk(
            com.aetherianartificer.townstead.origin.attachment.AttachmentChunkS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.attachment.AttachmentClient.onChunk(
                        payload.sha1(), payload.index(), payload.total(), payload.kind(), payload.data()));
    }

    private void handleAttachmentRequest(
            com.aetherianartificer.townstead.origin.attachment.AttachmentRequestC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                com.aetherianartificer.townstead.origin.attachment.AttachmentSync.handleRequest(sp, payload.hashes());
            }
        });
    }

    private void handleOriginCatalogSync(
            com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.origin.OriginCatalogClient.setFrom(payload));
    }

    private void handleDialogueStateC2S(
            com.aetherianartificer.townstead.reaction.net.DialogueStateC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
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
        });
    }

    private void handleGestureNotifyC2S(
            com.aetherianartificer.townstead.reaction.net.GestureNotifyC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            com.aetherianartificer.townstead.reaction.trigger.event.GestureBroadcaster.broadcast(
                    sp.serverLevel(), sp, payload.emoteName());
        });
    }

    private void handleEmoteTriggerS2C(
            com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.client.animation.emote.EmoteClientHandler.handle(payload));
    }

    private void handleEmoteTriggerC2S(
            com.aetherianartificer.townstead.emote.EmoteTriggerC2SPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity target = sp.serverLevel().getEntity(payload.targetEntityId());
            if (!(target instanceof net.conczin.mca.entity.VillagerEntityMCA)
                    && !(target instanceof net.minecraft.world.entity.player.Player)) return;
            net.minecraft.resources.ResourceLocation id;
            try {
                id = net.minecraft.resources.ResourceLocation.parse(payload.emoteId());
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
        });
    }

    private void handleClientCaps(ClientCapsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            ClientCapsStore.setTravelersTitles(sp.getUUID(), payload.hasTravelersTitles());
        });
    }

    private void handleVillageEnterTitle(VillageEnterTitlePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.aetherianartificer.townstead.compat.travelerstitles.TravelersTitlesBridge
                        .displayVillageTitle(payload.title(), payload.population(), payload.subtitleKey()));
    }

    private void handleHungerSync(HungerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.set(
                payload.entityId(),
                payload.hunger(),
                payload.farmerTier(),
                payload.farmerXp(),
                payload.farmerXpToNext(),
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

    private void handleFishermanStatusSync(FishermanStatusSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.setFishermanBlockedReason(payload.entityId(), payload.blockedReasonId()));
    }

    private void handleVillageSpiritSync(com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.put(payload));
    }

    private void handleVillageSpiritQuery(com.aetherianartificer.townstead.spirit.VillageSpiritQueryPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Optional<Village> village = Village.findNearest(sp);
            if (village.isEmpty()) return;
            com.aetherianartificer.townstead.spirit.VillageSpiritQueryScheduler.enqueue(
                    sp.serverLevel(), village.get(), sp);
        });
    }

    private void handleFishermanHookLink(FishermanHookLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            FishermanHookLinkStore.link(payload.hookEntityId(), payload.villagerEntityId(),
                    payload.x(), payload.y(), payload.z());
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                LOGGER.info("[Fisherman] client got hook-link hookId={} villagerId={}", payload.hookEntityId(), payload.villagerEntityId());
            }
        });
    }

    private void handleHungerSet(HungerSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity entity = sp.serverLevel().getEntity(payload.entityId());
            if (!(entity instanceof VillagerEntityMCA villager)) return;

            TownsteadVillager state = TownsteadVillagers.get(villager);
            int currentHunger = state.needs().hunger();

            // hunger == -1 is a query: respond with current value, don't modify
            if (payload.hunger() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$hungerSync(villager, state.needs().hungerTag()));
                return;
            }

            int newHunger = payload.hunger();
            LOGGER.debug("HungerSet packet: entityId={}, target={}", payload.entityId(), newHunger);
            state.needs().setHunger(newHunger);
            // Reset saturation when increasing (for clean testing); leave it when decreasing
            if (newHunger > currentHunger) {
                state.needs().setSaturation(Math.min(newHunger, HungerData.MAX_SATURATION));
            }
            state.needs().setHungerExhaustion(0f);
            HungerSyncPayload sync = townstead$hungerSync(villager, state.needs().hungerTag());
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

            TownsteadVillager state = TownsteadVillagers.get(villager);
            int currentThirst = state.needs().thirst();

            if (payload.thirst() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$thirstSync(villager, state.needs().thirstTag()));
                return;
            }

            int newThirst = payload.thirst();
            LOGGER.debug("ThirstSet packet: entityId={}, target={}", payload.entityId(), newThirst);
            state.needs().setThirst(newThirst);
            if (newThirst > currentThirst) {
                state.needs().setQuenched(Math.min(newThirst, ThirstData.MAX_QUENCHED));
            }
            state.needs().setThirstExhaustion(0f);
            ThirstSyncPayload sync = townstead$thirstSync(villager, state.needs().thirstTag());
            PacketDistributor.sendToPlayer(sp, sync);
            PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
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

            // Query mode: empty shifts array. Reply with both the daily array
            // and the weekly state so the editor has everything in one round-trip.
            if (payload.shifts().length == 0) {
                CompoundTag shiftTag = TownsteadVillagers.get(villager).schedule().toTag();
                PacketDistributor.sendToPlayer(sp, new ShiftSyncPayload(
                        payload.villagerUuid(), ShiftData.getShifts(shiftTag)));
                PacketDistributor.sendToPlayer(sp, new ShiftWeekSyncPayload(
                        payload.villagerUuid(), ShiftData.getMode(shiftTag),
                        ShiftData.getWeekDayTemplates(shiftTag)));
                return;
            }

            // Validate and apply
            if (payload.shifts().length != ShiftData.HOURS_PER_DAY) return;
            townstead$writeVillagerShifts(villager, payload.shifts(), sp, true);
        });
    }

    private static void townstead$writeVillagerShifts(VillagerEntityMCA villager, int[] shifts,
                                                       ServerPlayer originator, boolean echoToOriginator) {
        townstead$writeVillagerShifts(villager, shifts, originator, echoToOriginator, "");
    }

    private static void townstead$writeVillagerShifts(VillagerEntityMCA villager, int[] shifts,
                                                       ServerPlayer originator, boolean echoToOriginator,
                                                       String templateId) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.schedule().setShifts(shifts);
        state.schedule().setTemplateId(templateId);
        ShiftScheduleApplier.apply(villager);
        // Flush now: schedule edits otherwise sit dirty in memory and never reach
        // the entity's data attachment before world save, so reloads lose them.
        TownsteadVillagers.flush(villager);

        ShiftSyncPayload sync = new ShiftSyncPayload(villager.getUUID(), shifts);
        if (echoToOriginator && originator != null) PacketDistributor.sendToPlayer(originator, sync);
        PacketDistributor.sendToPlayersTrackingEntity(villager, sync);

        // Resident roster carries the assignment too; resync so clients see the new label.
        if (originator != null) {
            VillageResidentsSyncPayload roster =
                    new VillageResidentsSyncPayload(VillageResidentRoster.snapshot(originator));
            PacketDistributor.sendToPlayer(originator, roster);
        }
    }

    private void handleShiftTemplateSync(ShiftTemplateSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ShiftTemplateClientStore.set(payload.templates()));
    }

    private void handleShiftTemplateSave(ShiftTemplateSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            ShiftTemplate template = townstead$buildUserTemplate(payload);
            if (template == null) return;
            ShiftTemplateSavedData.get(server).put(template);
            townstead$broadcastShiftTemplateSync(server);
        });
    }

    private void handleShiftTemplateDelete(ShiftTemplateDeletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            if (!ShiftTemplate.USER_NAMESPACE.equals(payload.id().getNamespace())) return; // refuse built-ins
            boolean removed = ShiftTemplateSavedData.get(server).remove(payload.id());
            if (removed) townstead$broadcastShiftTemplateSync(server);
        });
    }

    private void handleShiftTemplateApply(ShiftTemplateApplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            ShiftTemplate template = ShiftTemplateRegistry.resolve(server, payload.templateId()).orElse(null);
            if (template == null) {
                LOGGER.warn("Apply requested for unknown shift template {}", payload.templateId());
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
                townstead$writeVillagerShifts(villager, shifts, sp, true, templateIdStr);
            }
        });
    }

    private static ShiftTemplate townstead$buildUserTemplate(ShiftTemplateSavePayload payload) {
        if (payload.shifts() == null || payload.shifts().length != ShiftData.HOURS_PER_DAY) return null;
        for (int s : payload.shifts()) {
            if (s < 0 || s >= ShiftData.ORDINAL_TO_ACTIVITY.length) return null;
        }
        String name = payload.name() != null && !payload.name().isBlank() ? payload.name().trim() : "Untitled";
        if (name.length() > 64) name = name.substring(0, 64);
        net.minecraft.resources.ResourceLocation id;
        String idStr = payload.id();
        if (idStr == null || idStr.isBlank() || !idStr.startsWith(ShiftTemplate.USER_NAMESPACE + ":")) {
            String slug = "u_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            //? if >=1.21 {
            id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ShiftTemplate.USER_NAMESPACE, slug);
            //?} else {
            /*id = new net.minecraft.resources.ResourceLocation(ShiftTemplate.USER_NAMESPACE, slug);
            *///?}
        } else {
            try {
                //? if >=1.21 {
                id = net.minecraft.resources.ResourceLocation.parse(idStr);
                //?} else {
                /*id = new net.minecraft.resources.ResourceLocation(idStr);
                *///?}
            } catch (Exception ex) { return null; }
            if (!ShiftTemplate.USER_NAMESPACE.equals(id.getNamespace())) return null;
        }
        java.util.Optional<String> chrono = payload.chronotypeName();
        try {
            return new ShiftTemplate(id, name, payload.shifts(), chrono, false);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void townstead$sendShiftTemplateSync(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        java.util.List<ShiftTemplate> all = ShiftTemplateRegistry.combinedFor(server);
        PacketDistributor.sendToPlayer(sp, new ShiftTemplateSyncPayload(all));
    }

    private static void townstead$broadcastShiftTemplateSync(MinecraftServer server) {
        if (server == null) return;
        java.util.List<ShiftTemplate> all = ShiftTemplateRegistry.combinedFor(server);
        ShiftTemplateSyncPayload payload = new ShiftTemplateSyncPayload(all);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(sp, payload);
        }
    }

    // ---- Weekly schedule + week plan handlers ----

    private void handleShiftWeekSync(ShiftWeekSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ShiftClientStore.setWeek(payload.villagerUuid(), payload.mode(), payload.weekDays()));
    }

    private void handleShiftWeekSet(ShiftWeekSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            VillagerEntityMCA villager = townstead$findVillager(sp.getServer(), payload.villagerUuid());
            if (villager == null) return;
            townstead$writeVillagerWeek(villager, payload.mode(), payload.weekDays(), sp);
        });
    }

    private void handleWeekPlanSync(WeekPlanSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WeekPlanClientStore.set(payload.plans()));
    }

    private void handleWeekPlanSave(WeekPlanSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            WeekPlan plan = townstead$buildUserWeekPlan(payload);
            if (plan == null) return;
            WeekPlanSavedData.get(server).put(plan);
            townstead$broadcastWeekPlanSync(server);
        });
    }

    private void handleWeekPlanDelete(WeekPlanDeletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            if (!WeekPlan.USER_NAMESPACE.equals(payload.id().getNamespace())) return; // refuse built-ins
            if (WeekPlanSavedData.get(server).remove(payload.id())) townstead$broadcastWeekPlanSync(server);
        });
    }

    private void handleWeekPlanApply(WeekPlanApplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            WeekPlan plan = WeekPlanRegistry.resolve(server, payload.planId()).orElse(null);
            if (plan == null) {
                LOGGER.warn("Apply requested for unknown week plan {}", payload.planId());
                return;
            }
            java.util.List<String> days = plan.copyDays();
            for (java.util.UUID uuid : payload.villagerUuids()) {
                VillagerEntityMCA villager = townstead$findVillager(server, uuid);
                if (villager == null) continue;
                townstead$writeVillagerWeek(villager, ShiftData.MODE_WEEKLY, days, sp);
            }
        });
    }

    private static VillagerEntityMCA townstead$findVillager(MinecraftServer server, java.util.UUID uuid) {
        if (server == null) return null;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof VillagerEntityMCA v) return v;
        }
        return null;
    }

    private static void townstead$writeVillagerWeek(VillagerEntityMCA villager, String mode,
                                                    java.util.List<String> weekDays, ServerPlayer originator) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.schedule().setMode(mode);
        state.schedule().setWeekDayTemplates(weekDays);
        CompoundTag shiftTag = state.schedule().toTag();
        ShiftScheduleApplier.apply(villager);
        // Flush now: see writeVillagerShifts — dirty state would otherwise be
        // dropped on world reload.
        TownsteadVillagers.flush(villager);

        ShiftWeekSyncPayload sync = new ShiftWeekSyncPayload(
                villager.getUUID(), ShiftData.getMode(shiftTag), ShiftData.getWeekDayTemplates(shiftTag));
        if (originator != null) PacketDistributor.sendToPlayer(originator, sync);
        PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
    }

    private static WeekPlan townstead$buildUserWeekPlan(WeekPlanSavePayload payload) {
        String name = payload.name() != null && !payload.name().isBlank() ? payload.name().trim() : "Untitled";
        if (name.length() > 64) name = name.substring(0, 64);
        net.minecraft.resources.ResourceLocation id;
        String idStr = payload.id();
        if (idStr == null || idStr.isBlank() || !idStr.startsWith(WeekPlan.USER_NAMESPACE + ":")) {
            String slug = "u_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            //? if >=1.21 {
            id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(WeekPlan.USER_NAMESPACE, slug);
            //?} else {
            /*id = new net.minecraft.resources.ResourceLocation(WeekPlan.USER_NAMESPACE, slug);
            *///?}
        } else {
            try {
                //? if >=1.21 {
                id = net.minecraft.resources.ResourceLocation.parse(idStr);
                //?} else {
                /*id = new net.minecraft.resources.ResourceLocation(idStr);
                *///?}
            } catch (Exception ex) { return null; }
            if (!WeekPlan.USER_NAMESPACE.equals(id.getNamespace())) return null;
        }
        try {
            return new WeekPlan(id, name, payload.days() == null ? java.util.List.of() : payload.days(), false);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void townstead$sendWeekPlanSync(ServerPlayer sp) {
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        PacketDistributor.sendToPlayer(sp, new WeekPlanSyncPayload(WeekPlanRegistry.combinedFor(server)));
    }

    private static void townstead$broadcastWeekPlanSync(MinecraftServer server) {
        if (server == null) return;
        WeekPlanSyncPayload payload = new WeekPlanSyncPayload(WeekPlanRegistry.combinedFor(server));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(sp, payload);
        }
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
                                && villager.level() instanceof ServerLevel settledLevel
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

            TownsteadVillager state = TownsteadVillagers.get(villager);
            int currentFatigue = state.needs().fatigue();

            if (payload.fatigue() == -1) {
                PacketDistributor.sendToPlayer(sp, townstead$fatigueSync(villager, state.needs().fatigueTag()));
                return;
            }

            int newFatigue = payload.fatigue();
            LOGGER.debug("FatigueSet packet: entityId={}, target={}", payload.entityId(), newFatigue);
            state.needs().setFatigue(newFatigue);
            // Clear collapse/gate flags when setting via editor
            if (newFatigue < FatigueData.COLLAPSE_THRESHOLD) {
                state.needs().setCollapsed(false);
            }
            if (newFatigue < FatigueData.RECOVERY_GATE) {
                state.needs().setGated(false);
            }
            FatigueSyncPayload sync = townstead$fatigueSync(villager, state.needs().fatigueTag());
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

        // Tracked player: send their origin keyed by network id so the observer's
        // skin-tint layer can paint their genetics model. Players don't receive the
        // villager life/needs syncs that follow.
        if (event.getTarget() instanceof ServerPlayer trackedPlayer) {
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload pSync =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(
                            trackedPlayer.getId(),
                            com.aetherianartificer.townstead.origin.PlayerOrigin.getOriginId(trackedPlayer));
            com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload pGenes =
                    com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(trackedPlayer.getId(), trackedPlayer);
            //? if neoforge {
            PacketDistributor.sendToPlayer(sp, pSync);
            PacketDistributor.sendToPlayer(sp, pGenes);
            //?} else if forge {
            /*TownsteadNetwork.sendToPlayer(sp, pSync);
            TownsteadNetwork.sendToPlayer(sp, pGenes);
            *///?}
            return;
        }

        if (!(event.getTarget() instanceof VillagerEntityMCA villager)) return;

        // Make sure stage durations are rolled and a birth is stamped before the
        // life sync below is built, so the client snapshot is never empty when the
        // player can reach the villager's editor.
        net.minecraft.server.MinecraftServer trackingServer = villager.getServer();
        if (trackingServer != null) {
            com.aetherianartificer.townstead.calendar.VillagerLifeStamper.ensureStamped(villager, trackingServer);
        }

        //? if neoforge {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        CompoundTag hunger = state.needs().hungerTag();
        PacketDistributor.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
        if (ThirstBridgeResolver.isActive()) {
            CompoundTag thirst = state.needs().thirstTag();
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
        CompoundTag fatigue = state.needs().fatigueTag();
        PacketDistributor.sendToPlayer(sp, townstead$fatigueSync(villager, fatigue));
        CompoundTag shift = state.schedule().toTag();
        if (ShiftData.hasCustomShifts(shift)) {
            PacketDistributor.sendToPlayer(sp, new ShiftSyncPayload(
                    villager.getUUID(), ShiftData.getShifts(shift)));
        }
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync = townstead$lifeSync(villager);
        if (lifeSync != null) PacketDistributor.sendToPlayer(sp, lifeSync);
        // Origin id, so bystander clients can resolve the villager's skin tint.
        PacketDistributor.sendToPlayer(sp, new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(
                villager.getId(), state.life().originId()));
        PacketDistributor.sendToPlayer(sp,
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(villager.getId(), villager));
        //?} else if forge {
        /*TownsteadVillager state = TownsteadVillagers.get(villager);
        CompoundTag hunger = state.needs().hungerTag();
        TownsteadNetwork.sendToPlayer(sp, townstead$hungerSync(villager, hunger));
        if (ThirstBridgeResolver.isActive()) {
            CompoundTag thirst = state.needs().thirstTag();
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
        CompoundTag fatigue = state.needs().fatigueTag();
        TownsteadNetwork.sendToPlayer(sp, townstead$fatigueSync(villager, fatigue));
        CompoundTag shift = state.schedule().toTag();
        if (ShiftData.hasCustomShifts(shift)) {
            TownsteadNetwork.sendToPlayer(sp, new ShiftSyncPayload(
                    villager.getUUID(), ShiftData.getShifts(shift)));
        }
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync = townstead$lifeSync(villager);
        if (lifeSync != null) TownsteadNetwork.sendToPlayer(sp, lifeSync);
        // Origin id, so bystander clients can resolve the villager's skin tint.
        TownsteadNetwork.sendToPlayer(sp, new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(
                villager.getId(), state.life().originId()));
        TownsteadNetwork.sendToPlayer(sp,
                com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(villager.getId(), villager));
        *///?}
    }

    public static HungerSyncPayload townstead$hungerSync(VillagerEntityMCA villager, CompoundTag hunger) {
        TownsteadVillager.ProfessionMemory mem = TownsteadVillagers.get(villager).professionMemory();
        return new HungerSyncPayload(
                villager.getId(),
                HungerData.getHunger(hunger),
                ProfessionProgress.getTier(mem, ProfessionXpType.FARMER),
                ProfessionProgress.getXp(mem, ProfessionXpType.FARMER),
                ProfessionProgress.getXpToNextTier(mem, ProfessionXpType.FARMER),
                ProfessionProgress.getTier(mem, ProfessionXpType.COOK),
                ProfessionProgress.getXp(mem, ProfessionXpType.COOK),
                ProfessionProgress.getXpToNextTier(mem, ProfessionXpType.COOK)
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

    public static com.aetherianartificer.townstead.calendar.CalendarSyncPayload townstead$calendarSync(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        String locale = townstead$playerLocale(player);
        com.aetherianartificer.townstead.calendar.WorldCalendarSavedData data =
                com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.get(server);
        com.aetherianartificer.townstead.calendar.CalendarProfile profile =
                com.aetherianartificer.townstead.calendar.TownsteadCalendar.activeProfile(server);
        com.aetherianartificer.townstead.calendar.CalendarDate today =
                com.aetherianartificer.townstead.calendar.TownsteadCalendar.today(server);
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> todayYearMonths =
                profile != null ? profile.monthsForYear(today.year()) : java.util.List.of();
        String[] month = (profile != null
                && today.monthIndex() >= 1
                && today.monthIndex() <= todayYearMonths.size())
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(
                        todayYearMonths.get(today.monthIndex() - 1).commonName(), locale)
                : new String[] { "", "" };
        String[] prof = profile != null
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(profile.displayName(), locale)
                : new String[] { "", "" };
        String seasonKey = today.season() != null ? today.season().translationKey() : "";

        // Profile shape — packed for the client calendar UI so it can render
        // arbitrary months/years without further round-trips.
        int dpw = profile != null ? profile.daysPerWeek() : 7;
        int epoch = data.epochYearOffset();
        String[] suffix = (profile != null && profile.yearSuffix() != null)
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(profile.yearSuffix(), locale)
                : new String[] { "", "" };
        java.util.List<String> monthKeys = new java.util.ArrayList<>();
        java.util.List<String> monthFallbacks = new java.util.ArrayList<>();
        java.util.List<Integer> monthDays = new java.util.ArrayList<>();
        if (profile != null) {
            for (com.aetherianartificer.townstead.calendar.MonthDef m : profile.months()) {
                String[] mk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(m.commonName(), locale);
                monthKeys.add(mk[0]);
                monthFallbacks.add(mk[1]);
                monthDays.add(m.days());
            }
        }

        java.util.List<String> wdLongKeys = new java.util.ArrayList<>();
        java.util.List<String> wdLongFallbacks = new java.util.ArrayList<>();
        java.util.List<String> wdShortKeys = new java.util.ArrayList<>();
        java.util.List<String> wdShortFallbacks = new java.util.ArrayList<>();
        if (profile != null && profile.weekdays() != null) {
            for (com.aetherianartificer.townstead.calendar.WeekdayDef w : profile.weekdays()) {
                String[] lk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(w.longName(), locale);
                String[] sk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(w.shortName(), locale);
                wdLongKeys.add(lk[0]);
                wdLongFallbacks.add(lk[1]);
                wdShortKeys.add(sk[0]);
                wdShortFallbacks.add(sk[1]);
            }
        }

        java.util.List<String> eraNameKeys = new java.util.ArrayList<>();
        java.util.List<String> eraNameFallbacks = new java.util.ArrayList<>();
        java.util.List<Integer> eraStartYears = new java.util.ArrayList<>();
        java.util.List<Integer> eraFirstYearDisplayedAs = new java.util.ArrayList<>();
        java.util.List<Integer> eraDirections = new java.util.ArrayList<>();
        if (profile != null && profile.eras() != null) {
            for (com.aetherianartificer.townstead.calendar.Era era : profile.eras()) {
                String[] nk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(era.name(), locale);
                eraNameKeys.add(nk[0]);
                eraNameFallbacks.add(nk[1]);
                eraStartYears.add(era.startYear());
                eraFirstYearDisplayedAs.add(era.firstYearDisplayedAs());
                eraDirections.add(era.direction() == com.aetherianartificer.townstead.calendar.Era.Direction.DESCENDING ? 1 : 0);
            }
        }

        java.util.List<com.aetherianartificer.townstead.calendar.LeapRule> leapRules =
                profile != null && profile.leapRules() != null ? profile.leapRules() : java.util.List.of();

        return new com.aetherianartificer.townstead.calendar.CalendarSyncPayload(
                data.worldDayCounter(),
                today.year(), today.monthIndex(), today.dayOfMonth(),
                today.dayOfYear(), today.dayOfWeek(),
                month[0], month[1], prof[0], prof[1], seasonKey,
                dpw, epoch, suffix[0], suffix[1],
                monthKeys, monthFallbacks, monthDays,
                wdLongKeys, wdLongFallbacks, wdShortKeys, wdShortFallbacks,
                eraNameKeys, eraNameFallbacks, eraStartYears, eraFirstYearDisplayedAs, eraDirections,
                leapRules
        );
    }

    public static com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload townstead$lifeSync(VillagerEntityMCA villager) {
        MinecraftServer server = villager.getServer();
        if (server == null) return null;
        CompoundTag life = com.aetherianartificer.townstead.calendar.VillagerLifeStamper.peek(villager);
        if (life == null) return null;
        long birthDay = com.aetherianartificer.townstead.calendar.LifeData.getBirthWorldDay(life);
        boolean stamped = com.aetherianartificer.townstead.calendar.LifeData.isStamped(life);
        long lifeShift = com.aetherianartificer.townstead.calendar.TownsteadCalendar.lifeEpochShift(server);
        com.aetherianartificer.townstead.calendar.CalendarDate birth =
                com.aetherianartificer.townstead.calendar.TownsteadCalendar.dateOf(server, birthDay + lifeShift);
        int ageYears = com.aetherianartificer.townstead.calendar.TownsteadCalendar.ageYears(server, villager);
        com.aetherianartificer.townstead.calendar.CalendarProfile profile =
                com.aetherianartificer.townstead.calendar.TownsteadCalendar.activeProfile(server);
        java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> birthYearMonths =
                profile != null ? profile.monthsForYear(birth.year()) : java.util.List.of();
        String[] month = (profile != null
                && birth.monthIndex() >= 1
                && birth.monthIndex() <= birthYearMonths.size())
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(
                        birthYearMonths.get(birth.monthIndex() - 1).commonName())
                : new String[] { "", "" };
        com.aetherianartificer.townstead.villager.TownsteadVillager.Life lifeState =
                com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life();
        // Day used for apparent-age, stage, and senior-progress display. Frozen at the day
        // aging was disabled when "villagers do not age" is on, so the readouts stop climbing.
        long today = com.aetherianartificer.townstead.origin.LifeStageProgression.agingDisplayDay(
                lifeState, com.aetherianartificer.townstead.calendar.TownsteadCalendar.lifeDay(server));
        // Celebrated birthday (month/day) is decoupled from birthWorldDay/age: if set,
        // it overrides the age-derived month/day in the display. The year stays the
        // age-derived one (it's not shown anyway).
        int birthMonthIndex = birth.monthIndex();
        int birthDayOfMonth = birth.dayOfMonth();
        if (lifeState.hasCelebratedBirthday()) {
            birthMonthIndex = lifeState.birthMonth();
            birthDayOfMonth = lifeState.birthDay();
            java.util.List<com.aetherianartificer.townstead.calendar.MonthDef> yMonths =
                    profile != null ? profile.monthsForYear(birth.year()) : java.util.List.of();
            if (profile != null && birthMonthIndex >= 1 && birthMonthIndex <= yMonths.size()) {
                month = com.aetherianartificer.townstead.calendar.ComponentSync.extract(
                        yMonths.get(birthMonthIndex - 1).commonName());
            }
        }
        boolean isSenior = lifeState.isSenior();
        int seniorPermil = isSenior
                ? com.aetherianartificer.townstead.origin.LifeStageProgression.seniorProgressPermil(villager)
                : 0;

        int bioAgeDays = (int) Math.max(0L, today - birthDay);
        boolean immortal = lifeState.immortal()
                || com.aetherianartificer.townstead.origin.trait.TraitEffects.isImmortal(villager);

        net.minecraft.resources.ResourceLocation originId =
                net.minecraft.resources.ResourceLocation.tryParse(lifeState.originId());
        if (originId == null) originId = com.aetherianartificer.townstead.origin.OriginRegistry.DEFAULT_ID;
        com.aetherianartificer.townstead.origin.LifeCycle cycle =
                com.aetherianartificer.townstead.origin.OriginRegistry.effectiveLifeCycle(originId);

        int[] stageDays;
        String[] stageKeys;
        String[] stageFallbacks;
        float[] stageScales;
        int[] stageModelAges;
        float[] stageNarrativeMin;
        float[] stageNarrativeMax;
        int currentStageIndex = -1;
        int seniorStageIndex = -1;
        float narrativeAge = 0f;
        // Apparent-years per game-day. When a cycle has no explicit narrative_age,
        // apparent age derives as bioAgeDays * narrativeRate (the inverse of the
        // spawn-time aging scale). Synced so the client matches without bands.
        boolean derivesNarrative = cycle != null && !cycle.isEmpty() && cycle.derivesNarrative();
        float narrativeRate = derivesNarrative
                ? 1f / Math.max(0.0001f, com.aetherianartificer.townstead.origin.OriginSpawnHandler.agingScale(server))
                : 0f;
        if (cycle != null && !cycle.isEmpty()
                && lifeState.hasStageDays() && lifeState.stageDaysLength() == cycle.size()) {
            stageDays = lifeState.stageDays();
            int n = cycle.size();
            stageKeys = new String[n];
            stageFallbacks = new String[n];
            stageScales = new float[n];
            stageModelAges = new int[n];
            stageNarrativeMin = new float[n];
            stageNarrativeMax = new float[n];
            for (int i = 0; i < n; i++) {
                String[] parts = com.aetherianartificer.townstead.calendar.ComponentSync.extract(
                        cycle.stageAt(i).label());
                stageKeys[i] = parts[0];
                stageFallbacks[i] = parts[1];
                stageScales[i] = cycle.stageAt(i).scale();
                stageModelAges[i] = com.aetherianartificer.townstead.origin.LifeStageProgression
                        .representativeMcaAge(cycle.stageAt(i).presentsAs());
                stageNarrativeMin[i] = cycle.stageAt(i).narrativeStart();
                stageNarrativeMax[i] = cycle.stageAt(i).narrativeEnd();
                if (seniorStageIndex < 0
                        && cycle.stageAt(i).presentsAs() == com.aetherianartificer.townstead.origin.CanonicalStage.SENIOR) {
                    seniorStageIndex = i;
                }
            }
            if (immortal && !lifeState.currentStageId().isEmpty()) {
                // Immortal: report the frozen stage, not the calendar-derived one.
                for (int i = 0; i < cycle.size(); i++) {
                    if (cycle.stageAt(i).id().equals(lifeState.currentStageId())) {
                        currentStageIndex = i;
                        break;
                    }
                }
                if (currentStageIndex >= 0) {
                    // Immortal frozen: midpoint of the frozen stage's day-span,
                    // derived; or the authored band midpoint for override cycles.
                    if (derivesNarrative) {
                        long before = com.aetherianartificer.townstead.origin.LifeStageResolver
                                .cumulativeDaysBefore(stageDays, currentStageIndex);
                        long mid = before + Math.max(1, stageDays[currentStageIndex]) / 2L;
                        narrativeAge = mid * narrativeRate;
                    } else {
                        narrativeAge = cycle.stageAt(currentStageIndex).narrativeAgeAt(0.5f);
                    }
                }
            } else {
                com.aetherianartificer.townstead.origin.LifeStageResolver.Resolved resolved =
                        com.aetherianartificer.townstead.origin.LifeStageResolver.resolve(
                                cycle, stageDays, birthDay, today);
                if (resolved != null) {
                    currentStageIndex = resolved.stageIndex();
                    narrativeAge = derivesNarrative
                            ? bioAgeDays * narrativeRate
                            : resolved.stage().narrativeAgeAt(resolved.deltaInStage());
                }
            }
        } else {
            stageDays = new int[0];
            stageKeys = new String[0];
            stageFallbacks = new String[0];
            stageScales = new float[0];
            stageModelAges = new int[0];
            stageNarrativeMin = new float[0];
            stageNarrativeMax = new float[0];
        }

        return new com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload(
                villager.getId(),
                birth.year(), birthMonthIndex, birthDayOfMonth,
                month[0], month[1], ageYears, stamped,
                isSenior, seniorPermil,
                bioAgeDays, immortal, currentStageIndex,
                stageDays, stageKeys, stageFallbacks, narrativeAge, stageScales, stageModelAges,
                stageNarrativeMin, stageNarrativeMax, narrativeRate, seniorStageIndex
        );
    }

    public static void townstead$broadcastCalendarSync(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            townstead$sendCalendarSync(sp);
        }
    }

    public static void townstead$sendCalendarSync(ServerPlayer player) {
        if (player == null) return;
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, townstead$calendarSync(player));
        //?} else if forge {
        /*TownsteadNetwork.sendToPlayer(player, townstead$calendarSync(player));
        *///?}
    }

    private static String townstead$playerLocale(ServerPlayer player) {
        //? if >=1.21 {
        return player.clientInformation().language();
        //?} else {
        /*return player.getLanguage();
        *///?}
    }

    public static void townstead$broadcastStampSync(MinecraftServer server) {
        if (server == null) return;
        // Per-player: each gets their own private stamps plus all public ones.
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            com.aetherianartificer.townstead.calendar.CalendarStampSyncPayload payload =
                    com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(server, sp);
            //? if neoforge {
            PacketDistributor.sendToPlayer(sp, payload);
            //?} else if forge {
            /*TownsteadNetwork.sendToPlayer(sp, payload);
            *///?}
        }
    }

    /**
     * Send the origin catalog and the player's own current origin to one player.
     * Fired on login and on datapack reload (via OnDatapackSyncEvent), so the
     * picker can list/label origins even on a client whose datapack registry is
     * empty.
     */
    public static void townstead$sendOriginData(ServerPlayer sp) {
        if (sp == null) return;
        // Never let a cosmetic catalog/sync failure abort the player's login.
        try {
            com.aetherianartificer.townstead.origin.OriginCatalog.Snapshot originSnap =
                    com.aetherianartificer.townstead.origin.OriginCatalog.build();
            com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload catalog =
                    new com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload(
                            originSnap.origins(), originSnap.genes(), originSnap.traits(), originSnap.rigs());
            String selfOriginId = com.aetherianartificer.townstead.origin.PlayerOrigin.getOriginId(sp);
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload self =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(
                            com.aetherianartificer.townstead.origin.OriginSetC2SPayload.SELF, selfOriginId);
            // Also keyed by the player's network id so the skin-tint layer can paint their
            // own genetics model (the SELF entry is only used by the editor's picker).
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload selfEntity =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(sp.getId(), selfOriginId);
            com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload selfGenes =
                    com.aetherianartificer.townstead.origin.ExpressedGenesS2CPayload.forEntity(sp.getId(), sp);
            //? if neoforge {
            PacketDistributor.sendToPlayer(sp, catalog);
            PacketDistributor.sendToPlayer(sp, self);
            PacketDistributor.sendToPlayer(sp, selfEntity);
            PacketDistributor.sendToPlayer(sp, selfGenes);
            //?} else if forge {
            /*TownsteadNetwork.sendToPlayer(sp, catalog);
            TownsteadNetwork.sendToPlayer(sp, self);
            TownsteadNetwork.sendToPlayer(sp, selfEntity);
            TownsteadNetwork.sendToPlayer(sp, selfGenes);
            *///?}
            com.aetherianartificer.townstead.origin.attachment.AttachmentSync.sendManifest(sp);
        } catch (Exception ex) {
            LOGGER.error("Failed to send origin data to {}", sp.getName().getString(), ex);
        }
    }

    /**
     * Re-broadcast {@link com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload}
     * for every loaded VillagerEntityMCA to its tracking players. Used when
     * the calendar profile or epoch changes — birth-date strings are
     * pre-resolved server-side, so without a re-broadcast clients keep
     * showing the previous calendar's date.
     *
     * Cost: O(loaded villagers × tracking players per villager). Profile
     * changes are rare, so the one-shot cost is fine.
     */
    public static void townstead$broadcastAllVillagerLifeSync(MinecraftServer server) {
        if (server == null) return;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (!(entity instanceof VillagerEntityMCA villager)) continue;
                com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload payload =
                        townstead$lifeSync(villager);
                if (payload == null) continue;
                //? if neoforge {
                PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
                //?} else if forge {
                /*TownsteadNetwork.sendToTrackingEntity(villager, payload);
                *///?}
            }
        }
    }

}
