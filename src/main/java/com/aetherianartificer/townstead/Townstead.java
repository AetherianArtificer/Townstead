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
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.aetherianartificer.townstead.calendar.AgeableCatchup.onEntityJoin(e.getEntity(), sl.getServer());
                if (e.getEntity() instanceof VillagerEntityMCA villager) {
                    com.aetherianartificer.townstead.villager.TownsteadVillagerState.root(villager);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e) -> {
            if (e.getEntity() instanceof VillagerEntityMCA villager && !villager.level().isClientSide) {
                com.aetherianartificer.townstead.origin.OriginSpawnHandler.onTrueSpawn(villager);
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
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                townstead$sendShiftTemplateSync(sp);
                townstead$sendWeekPlanSync(sp);
                PacketDistributor.sendToPlayer(sp, townstead$calendarSync(sp.serverLevel().getServer()));
                PacketDistributor.sendToPlayer(sp,
                        com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(sp.serverLevel().getServer(), sp));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent e) -> {
            if (e.getPlayer() != null) {
                townstead$sendOriginData(e.getPlayer());
            } else {
                e.getPlayerList().getPlayers().forEach(Townstead::townstead$sendOriginData);
            }
        });
        ShiftTemplateRegistry.setChangeListener(Townstead::townstead$broadcastShiftTemplateSync);
        WeekPlanRegistry.setChangeListener(Townstead::townstead$broadcastWeekPlanSync);
        NeoForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(com.aetherianartificer.townstead.compat.butchery.ButcherTradesCompat::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
                ClientCapsStore.clear(e.getEntity().getUUID()));
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
        townstead$registerEmotePlaybackClear();
        registerDialogueConditions();
        LOGGER.info("Townstead loaded");
    }

    private static void townstead$registerEmotePlaybackClear() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                            com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry.clear());
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
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn e) -> {
            if (e.getEntity() instanceof VillagerEntityMCA villager && !villager.level().isClientSide) {
                com.aetherianartificer.townstead.origin.OriginSpawnHandler.onTrueSpawn(villager);
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
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                TownsteadNetwork.sendShiftTemplateSync(sp);
                TownsteadNetwork.sendWeekPlanSync(sp);
                TownsteadNetwork.sendToPlayer(sp, townstead$calendarSync(sp.serverLevel().getServer()));
                TownsteadNetwork.sendToPlayer(sp,
                        com.aetherianartificer.townstead.calendar.CalendarStampServer.snapshotFor(sp.serverLevel().getServer(), sp));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent e) -> {
            if (e.getPlayer() != null) {
                townstead$sendOriginData(e.getPlayer());
            } else {
                e.getPlayerList().getPlayers().forEach(Townstead::townstead$sendOriginData);
            }
        });
        ShiftTemplateRegistry.setChangeListener(TownsteadNetwork::broadcastShiftTemplateSync);
        WeekPlanRegistry.setChangeListener(TownsteadNetwork::broadcastWeekPlanSync);
        MinecraftForge.EVENT_BUS.addListener(CookTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(BaristaTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener(com.aetherianartificer.townstead.compat.butchery.ButcherTradesCompat::onVillagerTrades);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
                ClientCapsStore.clear(e.getEntity().getUUID()));
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
        try {
            Class.forName("net.minecraft.client.Minecraft");
            MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                            com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry.clear());
        } catch (Exception ignored) {
            // Dedicated server: no client-side playback registry to clear.
        }
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
                    new com.aetherianartificer.townstead.origin.gene.types.SkinToneGeneType());
            com.aetherianartificer.townstead.origin.gene.GeneTypes.register(
                    new com.aetherianartificer.townstead.origin.gene.types.AttachmentGeneType());

            // Trait effect palette (data-pack traits compose these; see TraitJsonLoader)
            com.aetherianartificer.townstead.origin.trait.effect.TraitEffectTypes.register(
                    new com.aetherianartificer.townstead.origin.trait.effect.types.SetImmortalEffectType());
        });
    }

    private void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CatalogDataLoader());
        event.addListener(new com.aetherianartificer.townstead.reaction.ReactionDataLoader());
        event.addListener(new ShiftTemplateJsonLoader());
        event.addListener(new WeekPlanJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.calendar.CalendarProfileJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.SpeciesJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.AncestryJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.HeritageJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.OriginJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.gene.GeneJsonLoader());
        event.addListener(new com.aetherianartificer.townstead.origin.trait.TraitJsonLoader());
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
                    (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) ->
                            event.register(com.aetherianartificer.townstead.client.TownsteadKeybinds.TALK)
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
                    (net.minecraftforge.client.event.RegisterKeyMappingsEvent event) ->
                            event.register(com.aetherianartificer.townstead.client.TownsteadKeybinds.TALK)
            );
        } catch (Exception ignored) {
            // Dedicated server: no keybinds.
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
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload.TYPE,
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload.STREAM_CODEC,
                this::handleOriginSync
        );
        registrar.playToClient(
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload.TYPE,
                com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload.STREAM_CODEC,
                this::handleOriginCatalogSync
        );
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
                if (tracked != null) PacketDistributor.sendToPlayersTrackingEntity(tracked, sync);
            } else {
                // Self-origin change: also re-key by the player's network id so their own
                // model (sent to themselves) and bystanders' views (tracking sync) re-tint.
                com.aetherianartificer.townstead.origin.OriginSyncS2CPayload entitySync =
                        new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(sp.getId(), result.originId());
                PacketDistributor.sendToPlayer(sp, entitySync);
                PacketDistributor.sendToPlayersTrackingEntity(sp, entitySync);
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
        java.util.Optional<com.aetherianartificer.townstead.shift.template.Chronotype> chrono =
                payload.chronotypeName().map(com.aetherianartificer.townstead.shift.template.Chronotype::fromName);
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
            //? if neoforge {
            PacketDistributor.sendToPlayer(sp, pSync);
            //?} else if forge {
            /*TownsteadNetwork.sendToPlayer(sp, pSync);
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

    public static com.aetherianartificer.townstead.calendar.CalendarSyncPayload townstead$calendarSync(MinecraftServer server) {
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
                        todayYearMonths.get(today.monthIndex() - 1).commonName())
                : new String[] { "", "" };
        String[] prof = profile != null
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(profile.displayName())
                : new String[] { "", "" };
        String seasonKey = today.season() != null ? today.season().translationKey() : "";

        // Profile shape — packed for the client calendar UI so it can render
        // arbitrary months/years without further round-trips.
        int dpw = profile != null ? profile.daysPerWeek() : 7;
        int epoch = data.epochYearOffset();
        String[] suffix = (profile != null && profile.yearSuffix() != null)
                ? com.aetherianartificer.townstead.calendar.ComponentSync.extract(profile.yearSuffix())
                : new String[] { "", "" };
        java.util.List<String> monthKeys = new java.util.ArrayList<>();
        java.util.List<String> monthFallbacks = new java.util.ArrayList<>();
        java.util.List<Integer> monthDays = new java.util.ArrayList<>();
        if (profile != null) {
            for (com.aetherianartificer.townstead.calendar.MonthDef m : profile.months()) {
                String[] mk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(m.commonName());
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
                String[] lk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(w.longName());
                String[] sk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(w.shortName());
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
                String[] nk = com.aetherianartificer.townstead.calendar.ComponentSync.extract(era.name());
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

        long today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.lifeDay(server);
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
        com.aetherianartificer.townstead.calendar.CalendarSyncPayload payload = townstead$calendarSync(server);
        //? if neoforge {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(sp, payload);
        }
        //?} else if forge {
        /*TownsteadNetwork.sendToAll(payload);
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
                            originSnap.origins(), originSnap.genes(), originSnap.traits());
            String selfOriginId = com.aetherianartificer.townstead.origin.PlayerOrigin.getOriginId(sp);
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload self =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(
                            com.aetherianartificer.townstead.origin.OriginSetC2SPayload.SELF, selfOriginId);
            // Also keyed by the player's network id so the skin-tint layer can paint their
            // own genetics model (the SELF entry is only used by the editor's picker).
            com.aetherianartificer.townstead.origin.OriginSyncS2CPayload selfEntity =
                    new com.aetherianartificer.townstead.origin.OriginSyncS2CPayload(sp.getId(), selfOriginId);
            //? if neoforge {
            PacketDistributor.sendToPlayer(sp, catalog);
            PacketDistributor.sendToPlayer(sp, self);
            PacketDistributor.sendToPlayer(sp, selfEntity);
            //?} else if forge {
            /*TownsteadNetwork.sendToPlayer(sp, catalog);
            TownsteadNetwork.sendToPlayer(sp, self);
            TownsteadNetwork.sendToPlayer(sp, selfEntity);
            *///?}
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
