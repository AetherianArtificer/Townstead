package com.aetherianartificer.townstead;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?} else if forge {
/*import net.minecraftforge.common.ForgeConfigSpec;
*///?}
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TownsteadConfig {
    private TownsteadConfig() {}

    private static volatile ProtectedStorageRules protectedStorageRules = ProtectedStorageRules.empty();

    //? if neoforge {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_EATING;
    public static final ModConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_DRINKING;
    public static final ModConfigSpec.BooleanValue ENABLE_GROUND_ITEM_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_GROUND_ITEM_THIRST_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_THIRST_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CROP_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CROP_THIRST_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_THIRST;
    public static final ModConfigSpec.BooleanValue THIRST_LETHAL_FALLBACK;
    public static final ModConfigSpec.BooleanValue ENABLE_COOK_WATER_PURIFICATION;
    public static final ModConfigSpec.BooleanValue PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES;
    public static final ModConfigSpec.BooleanValue ENABLE_FARM_ASSIST;
    public static final ModConfigSpec.BooleanValue ENABLE_WORK_SUPPLY_AUTOMATION;
    public static final ModConfigSpec.BooleanValue ENABLE_HARVEST_OUTPUT_STORAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_FARMER_STABILITY_V2;
    public static final ModConfigSpec.IntValue FARMER_FARM_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_CELL_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue FARMER_PATHFAIL_MAX_RETRIES;
    public static final ModConfigSpec.IntValue FARMER_IDLE_BACKOFF_TICKS;
    public static final ModConfigSpec.IntValue FARMER_SEED_RESERVE;
    public static final ModConfigSpec.IntValue FARMER_MAX_CLUSTERS;
    public static final ModConfigSpec.IntValue FARMER_MAX_PLOTS;
    public static final ModConfigSpec.BooleanValue ENABLE_FARMER_WATER_PLACEMENT;
    public static final ModConfigSpec.IntValue FARMER_WATER_PLACEMENTS_PER_DAY;
    public static final ModConfigSpec.IntValue FARMER_WATER_SOURCE_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_WATER_SOURCE_VERTICAL_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_GROOM_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_GROOM_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue DEBUG_VILLAGER_AI;
    public static final ModConfigSpec.BooleanValue ENABLE_FARMER_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue FARMER_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_COOK_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue COOK_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_BARISTA_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue BARISTA_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_FISHERMAN_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue FISHERMAN_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue FISHERMAN_WATER_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue FISHERMAN_INVENTORY_FULL_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_SLAUGHTER;
    public static final ModConfigSpec.BooleanValue ALLOW_HUMANOID_SLAUGHTER;
    public static final ModConfigSpec.IntValue VILLAGER_SLAUGHTER_THROTTLE_TICKS;
    public static final ModConfigSpec.BooleanValue INCLUDE_EXOTIC_BUTCHERY_TRADES;
    public static final ModConfigSpec.BooleanValue HAMMER_TROPHY_HEADS;
    public static final ModConfigSpec.BooleanValue ENABLE_FEEDING_YOUNG;
    public static final ModConfigSpec.BooleanValue ENABLE_HYDRATING_YOUNG;
    public static final ModConfigSpec.BooleanValue ENABLE_NON_PARENT_CAREGIVERS;
    public static final ModConfigSpec.BooleanValue RESPECT_PROTECTED_STORAGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_TAGS;
    public static final ModConfigSpec.BooleanValue MUTE_MOOD_VOCALIZATIONS;
    public static final ModConfigSpec.BooleanValue USE_TOWNSTEAD_CATALOG;
    public static final ModConfigSpec.BooleanValue DIALOGUE_REDUCE_MOTION;
    public static final ModConfigSpec.BooleanValue DIALOGUE_DISABLE_PARTICLES;
    public static final ModConfigSpec.BooleanValue DIALOGUE_DISABLE_CAMERA;
    public static final ModConfigSpec.BooleanValue SPIRIT_COLORBLIND_PATTERNS;
    public static final ModConfigSpec.BooleanValue SPIRIT_NARRATION;
    public static final ModConfigSpec.BooleanValue SPIRIT_LARGER_HIT_TARGETS;
    public static final ModConfigSpec.BooleanValue SPIRIT_HIGH_CONTRAST;
    public static final ModConfigSpec.DoubleValue SPIRIT_FONT_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_TOWNSTEAD_COOK;
    public static final ModConfigSpec.BooleanValue ENABLE_VILLAGER_FATIGUE;
    public static final ModConfigSpec.BooleanValue ENABLE_FATIGUE_ALERTS;
    public static final ModConfigSpec.ConfigValue<Double> FATIGUE_NOCTURNAL_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<Double> FATIGUE_MISALIGNED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue DEBUG_VILLAGER_SLEEP;
    //?} else if forge {
    /*public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_EATING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_DRINKING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GROUND_ITEM_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GROUND_ITEM_THIRST_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAINER_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAINER_THIRST_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROP_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CROP_THIRST_SOURCING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_THIRST;
    public static final ForgeConfigSpec.BooleanValue THIRST_LETHAL_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_COOK_WATER_PURIFICATION;
    public static final ForgeConfigSpec.BooleanValue PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FARM_ASSIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORK_SUPPLY_AUTOMATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HARVEST_OUTPUT_STORAGE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FARMER_STABILITY_V2;
    public static final ForgeConfigSpec.IntValue FARMER_FARM_RADIUS;
    public static final ForgeConfigSpec.IntValue FARMER_CELL_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue FARMER_PATHFAIL_MAX_RETRIES;
    public static final ForgeConfigSpec.IntValue FARMER_IDLE_BACKOFF_TICKS;
    public static final ForgeConfigSpec.IntValue FARMER_SEED_RESERVE;
    public static final ForgeConfigSpec.IntValue FARMER_MAX_CLUSTERS;
    public static final ForgeConfigSpec.IntValue FARMER_MAX_PLOTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FARMER_WATER_PLACEMENT;
    public static final ForgeConfigSpec.IntValue FARMER_WATER_PLACEMENTS_PER_DAY;
    public static final ForgeConfigSpec.IntValue FARMER_WATER_SOURCE_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue FARMER_WATER_SOURCE_VERTICAL_RADIUS;
    public static final ForgeConfigSpec.IntValue FARMER_GROOM_RADIUS;
    public static final ForgeConfigSpec.IntValue FARMER_GROOM_SCAN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_VILLAGER_AI;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FARMER_REQUEST_CHAT;
    public static final ForgeConfigSpec.IntValue FARMER_REQUEST_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_COOK_REQUEST_CHAT;
    public static final ForgeConfigSpec.IntValue COOK_REQUEST_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BARISTA_REQUEST_CHAT;
    public static final ForgeConfigSpec.IntValue BARISTA_REQUEST_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FISHERMAN_REQUEST_CHAT;
    public static final ForgeConfigSpec.IntValue FISHERMAN_REQUEST_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue FISHERMAN_WATER_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue FISHERMAN_INVENTORY_FULL_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_SLAUGHTER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_HUMANOID_SLAUGHTER;
    public static final ForgeConfigSpec.IntValue VILLAGER_SLAUGHTER_THROTTLE_TICKS;
    public static final ForgeConfigSpec.BooleanValue INCLUDE_EXOTIC_BUTCHERY_TRADES;
    public static final ForgeConfigSpec.BooleanValue HAMMER_TROPHY_HEADS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FEEDING_YOUNG;
    public static final ForgeConfigSpec.BooleanValue ENABLE_HYDRATING_YOUNG;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NON_PARENT_CAREGIVERS;
    public static final ForgeConfigSpec.BooleanValue RESPECT_PROTECTED_STORAGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_TAGS;
    public static final ForgeConfigSpec.BooleanValue MUTE_MOOD_VOCALIZATIONS;
    public static final ForgeConfigSpec.BooleanValue USE_TOWNSTEAD_CATALOG;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE_REDUCE_MOTION;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE_DISABLE_PARTICLES;
    public static final ForgeConfigSpec.BooleanValue DIALOGUE_DISABLE_CAMERA;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_COLORBLIND_PATTERNS;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_NARRATION;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_LARGER_HIT_TARGETS;
    public static final ForgeConfigSpec.BooleanValue SPIRIT_HIGH_CONTRAST;
    public static final ForgeConfigSpec.DoubleValue SPIRIT_FONT_SCALE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TOWNSTEAD_COOK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_FATIGUE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FATIGUE_ALERTS;
    public static final ForgeConfigSpec.ConfigValue<Double> FATIGUE_NOCTURNAL_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<Double> FATIGUE_MISALIGNED_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue DEBUG_VILLAGER_SLEEP;
    *///?}

    static {
        //? if neoforge {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        //?} else if forge {
        /*ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        *///?}

        // ── Needs ──
        b.translation("townstead.configuration.needs").push("needs");
        b.translation("townstead.configuration.needs.hunger").push("hunger");
        ENABLE_SELF_INVENTORY_EATING = b
                .translation("townstead.configuration.needs.hunger.enableSelfInventoryEating")
                .comment("Allow villagers to eat from their own inventory.")
                .define("enableSelfInventoryEating", true);
        ENABLE_GROUND_ITEM_SOURCING = b
                .translation("townstead.configuration.needs.hunger.enableGroundItemSourcing")
                .comment("Allow villagers to collect food from ground items.")
                .define("enableGroundItemSourcing", true);
        ENABLE_CONTAINER_SOURCING = b
                .translation("townstead.configuration.needs.hunger.enableContainerSourcing")
                .comment("Allow villagers to pull food from containers / item handlers.")
                .define("enableContainerSourcing", true);
        ENABLE_CROP_SOURCING = b
                .translation("townstead.configuration.needs.hunger.enableCropSourcing")
                .comment("Allow villagers to harvest mature crops for food.")
                .define("enableCropSourcing", true);
        b.pop();
        if (ThirstBridgeResolver.anyThirstModLoaded()) {
            b.translation("townstead.configuration.needs.thirst").push("thirst");
            ENABLE_SELF_INVENTORY_DRINKING = b
                    .translation("townstead.configuration.needs.thirst.enableSelfInventoryDrinking")
                    .comment("Allow villagers to drink thirst-restoring items from their own inventory.")
                    .define("enableSelfInventoryDrinking", true);
            ENABLE_GROUND_ITEM_THIRST_SOURCING = b
                    .translation("townstead.configuration.needs.thirst.enableGroundItemThirstSourcing")
                    .comment("Allow villagers to collect thirst-restoring items from ground items when Thirst Was Taken is installed.")
                    .define("enableGroundItemThirstSourcing", true);
            ENABLE_CONTAINER_THIRST_SOURCING = b
                    .translation("townstead.configuration.needs.thirst.enableContainerThirstSourcing")
                    .comment("Allow villagers to pull thirst-restoring items from containers / item handlers.")
                    .define("enableContainerThirstSourcing", true);
            ENABLE_CROP_THIRST_SOURCING = b
                    .translation("townstead.configuration.needs.thirst.enableCropThirstSourcing")
                    .comment("Allow villagers to harvest mature crops for thirst-restoring food/drink items.")
                    .define("enableCropThirstSourcing", true);
            ENABLE_VILLAGER_THIRST = b
                    .translation("townstead.configuration.needs.thirst.enableVillagerThirst")
                    .comment("Enable villager thirst simulation when Thirst Was Taken is installed.")
                    .define("enableVillagerThirst", true);
            THIRST_LETHAL_FALLBACK = b
                    .translation("townstead.configuration.needs.thirst.thirstLethalFallback")
                    .comment("Allow dehydration to kill villagers when hardcore status cannot be detected.")
                    .define("thirstLethalFallback", false);
            ENABLE_COOK_WATER_PURIFICATION = b
                    .translation("townstead.configuration.needs.thirst.enableCookWaterPurification")
                    .comment("Allow cook villagers to opportunistically purify impure water bottles in available kitchen skillets.")
                    .define("enableCookWaterPurification", true);
            PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES = b
                    .translation("townstead.configuration.needs.thirst.preferKitchenStorageForEmptyBottles")
                    .comment("When villagers drink from bottles, prefer depositing empty bottles into kitchen storage.")
                    .define("preferKitchenStorageForEmptyBottles", true);
            b.pop();
        } else {
            ENABLE_SELF_INVENTORY_DRINKING = null;
            ENABLE_GROUND_ITEM_THIRST_SOURCING = null;
            ENABLE_CONTAINER_THIRST_SOURCING = null;
            ENABLE_CROP_THIRST_SOURCING = null;
            ENABLE_VILLAGER_THIRST = null;
            THIRST_LETHAL_FALLBACK = null;
            ENABLE_COOK_WATER_PURIFICATION = null;
            PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES = null;
        }
        // ── Fatigue ──
        b.translation("townstead.configuration.needs.fatigue").push("fatigue");
        ENABLE_VILLAGER_FATIGUE = b
                .translation("townstead.configuration.needs.fatigue.enableVillagerFatigue")
                .comment("Enable villager fatigue simulation. Villagers accumulate fatigue during activity and recover through rest.")
                .define("enableVillagerFatigue", true);
        ENABLE_FATIGUE_ALERTS = b
                .translation("townstead.configuration.needs.fatigue.enableFatigueAlerts")
                .comment("Show local chat alerts when villagers collapse from exhaustion or recover.")
                .define("enableFatigueAlerts", true);
        FATIGUE_NOCTURNAL_MULTIPLIER = b
                .translation("townstead.configuration.needs.fatigue.fatigueNocturnalMultiplier")
                .comment("Fatigue accumulation multiplier when working during aligned cycle hours.")
                .define("fatigueNocturnalMultiplier", 0.75);
        FATIGUE_MISALIGNED_MULTIPLIER = b
                .translation("townstead.configuration.needs.fatigue.fatigueMisalignedMultiplier")
                .comment("Fatigue accumulation multiplier when working during misaligned cycle hours.")
                .define("fatigueMisalignedMultiplier", 1.25);
        b.pop();
        b.pop();

        // ── Farming ──
        b.translation("townstead.configuration.farming").push("farming");
        ENABLE_FARM_ASSIST = b
                .translation("townstead.configuration.farming.enableFarmAssist")
                .comment("Enable lightweight farming assist: anti-trample and idle unstuck nudges for harvest chore.")
                .define("enableFarmAssist", true);
        ENABLE_FARMER_STABILITY_V2 = b
                .translation("townstead.configuration.farming.enableFarmerStabilityV2")
                .comment("Enable Townstead farmer stabilization (anti-thrash, retries, blocked states).")
                .define("enableFarmerStabilityV2", true);
        FARMER_FARM_RADIUS = b
                .translation("townstead.configuration.farming.farmerFarmRadius")
                .comment("Maximum horizontal farm radius around anchor used by farmer AI.")
                .defineInRange("farmerFarmRadius", 12, 4, 32);
        FARMER_CELL_COOLDOWN_TICKS = b
                .translation("townstead.configuration.farming.farmerCellCooldownTicks")
                .comment("Minimum ticks before reworking the same soil cell.")
                .defineInRange("farmerCellCooldownTicks", 120, 0, 2400);
        FARMER_PATHFAIL_MAX_RETRIES = b
                .translation("townstead.configuration.farming.farmerPathfailMaxRetries")
                .comment("How many times a target can fail pathing before temporary blacklist.")
                .defineInRange("farmerPathfailMaxRetries", 3, 1, 20);
        FARMER_IDLE_BACKOFF_TICKS = b
                .translation("townstead.configuration.farming.farmerIdleBackoffTicks")
                .comment("Ticks to wait before reacquiring work after no valid target.")
                .defineInRange("farmerIdleBackoffTicks", 60, 0, 1200);
        FARMER_SEED_RESERVE = b
                .translation("townstead.configuration.farming.farmerSeedReserve")
                .comment("Minimum seed count to keep before allowing expansion tilling.")
                .defineInRange("farmerSeedReserve", 8, 0, 64);
        FARMER_MAX_CLUSTERS = b
                .translation("townstead.configuration.farming.farmerMaxClusters")
                .comment("Maximum planned connected plot clusters per farm area.")
                .defineInRange("farmerMaxClusters", 6, 1, 64);
        FARMER_MAX_PLOTS = b
                .translation("townstead.configuration.farming.farmerMaxPlots")
                .comment("Maximum planned soil plot cells per farm area.")
                .defineInRange("farmerMaxPlots", 192, 16, 1024);
        ENABLE_FARMER_WATER_PLACEMENT = b
                .translation("townstead.configuration.farming.enableFarmerWaterPlacement")
                .comment("Allow farmers to place water sources in cells painted Water in the plot planner.")
                .define("enableFarmerWaterPlacement", true);
        FARMER_WATER_PLACEMENTS_PER_DAY = b
                .translation("townstead.configuration.farming.farmerWaterPlacementsPerDay")
                .comment("Maximum water source placements a farmer can perform per Minecraft day.")
                .defineInRange("farmerWaterPlacementsPerDay", 2, 0, 16);
        FARMER_WATER_SOURCE_SEARCH_RADIUS = b
                .translation("townstead.configuration.farming.farmerWaterSourceSearchRadius")
                .comment("Maximum horizontal distance farmers may travel to find water for bucket refills.")
                .defineInRange("farmerWaterSourceSearchRadius", 72, 8, 192);
        FARMER_WATER_SOURCE_VERTICAL_RADIUS = b
                .translation("townstead.configuration.farming.farmerWaterSourceVerticalRadius")
                .comment("Vertical search radius for nearby water sources when refilling buckets.")
                .defineInRange("farmerWaterSourceVerticalRadius", 8, 2, 32);
        FARMER_GROOM_RADIUS = b
                .translation("townstead.configuration.farming.farmerGroomRadius")
                .comment("Radius around planned farm cells where farmers may clear removable weeds.")
                .defineInRange("farmerGroomRadius", 1, 0, 4);
        FARMER_GROOM_SCAN_INTERVAL_TICKS = b
                .translation("townstead.configuration.farming.farmerGroomScanIntervalTicks")
                .comment("Ticks between farmer grooming target scans.")
                .defineInRange("farmerGroomScanIntervalTicks", 60, 20, 1200);
        ENABLE_FARMER_REQUEST_CHAT = b
                .translation("townstead.configuration.farming.enableFarmerRequestChat")
                .comment("Allow farmers to periodically announce missing supplies (seeds/tools/etc.) in local chat.")
                .define("enableFarmerRequestChat", true);
        FARMER_REQUEST_INTERVAL_TICKS = b
                .translation("townstead.configuration.farming.farmerRequestIntervalTicks")
                .comment("Minimum ticks between farmer shortage request messages.")
                .defineInRange("farmerRequestIntervalTicks", 3600, 200, 24000);
        b.pop();

        // ── Fishing ──
        b.translation("townstead.configuration.fishing").push("fishing");
        ENABLE_FISHERMAN_REQUEST_CHAT = b
                .translation("townstead.configuration.fishing.enableFishermanRequestChat")
                .comment("Allow fishermen to periodically announce missing rods or water in local chat.")
                .define("enableFishermanRequestChat", true);
        FISHERMAN_REQUEST_INTERVAL_TICKS = b
                .translation("townstead.configuration.fishing.fishermanRequestIntervalTicks")
                .comment("Minimum ticks between fisherman shortage request messages.")
                .defineInRange("fishermanRequestIntervalTicks", 3600, 200, 24000);
        FISHERMAN_WATER_SEARCH_RADIUS = b
                .translation("townstead.configuration.fishing.fishermanWaterSearchRadius")
                .comment("How many blocks away from the barrel to look for water when fishing.")
                .defineInRange("fishermanWaterSearchRadius", 16, 4, 48);
        FISHERMAN_INVENTORY_FULL_THRESHOLD = b
                .translation("townstead.configuration.fishing.fishermanInventoryFullThreshold")
                .comment("Number of items the fisherman carries before returning to deposit.")
                .defineInRange("fishermanInventoryFullThreshold", 16, 1, 64);
        b.pop();

        // ── Cooking ──
        if (ModCompat.isLoaded("farmersdelight")) {
            b.translation("townstead.configuration.cooking").push("cooking");
            ENABLE_COOK_REQUEST_CHAT = b
                    .translation("townstead.configuration.cooking.enableCookRequestChat")
                    .comment("Allow cooks to periodically announce missing kitchen supplies in local chat.")
                    .define("enableCookRequestChat", true);
            COOK_REQUEST_INTERVAL_TICKS = b
                    .translation("townstead.configuration.cooking.cookRequestIntervalTicks")
                    .comment("Minimum ticks between cook shortage request messages.")
                    .defineInRange("cookRequestIntervalTicks", 3600, 200, 24000);
            if (ModCompat.isLoaded("rusticdelight")) {
                b.translation("townstead.configuration.cooking.barista").push("barista");
                ENABLE_BARISTA_REQUEST_CHAT = b
                        .translation("townstead.configuration.cooking.barista.enableBaristaRequestChat")
                        .comment("Allow baristas to periodically announce missing cafe supplies in local chat.")
                        .define("enableBaristaRequestChat", true);
                BARISTA_REQUEST_INTERVAL_TICKS = b
                        .translation("townstead.configuration.cooking.barista.baristaRequestIntervalTicks")
                        .comment("Minimum ticks between barista shortage request messages.")
                        .defineInRange("baristaRequestIntervalTicks", 3600, 200, 24000);
                b.pop();
            } else {
                ENABLE_BARISTA_REQUEST_CHAT = null;
                BARISTA_REQUEST_INTERVAL_TICKS = null;
            }
            b.pop();
        } else {
            ENABLE_COOK_REQUEST_CHAT = null;
            COOK_REQUEST_INTERVAL_TICKS = null;
            ENABLE_BARISTA_REQUEST_CHAT = null;
            BARISTA_REQUEST_INTERVAL_TICKS = null;
        }

        // ── Butchery ──
        if (ModCompat.isLoaded("butchery")) {
            b.translation("townstead.configuration.butchery").push("butchery");
            ENABLE_VILLAGER_SLAUGHTER = b
                    .translation("townstead.configuration.butchery.enableVillagerSlaughter")
                    .comment("Allow butchers to slaughter whitelisted livestock inside their shop bounds.")
                    .define("enableVillagerSlaughter", true);
            ALLOW_HUMANOID_SLAUGHTER = b
                    .translation("townstead.configuration.butchery.allowHumanoidSlaughter")
                    .comment("Permit villager-driven slaughter of humanoid carcasses (villagers, pillagers, witches).",
                             "Off by default; the integration does not lean into this even when enabled.")
                    .define("allowHumanoidSlaughter", false);
            VILLAGER_SLAUGHTER_THROTTLE_TICKS = b
                    .translation("townstead.configuration.butchery.villagerSlaughterThrottleTicks")
                    .comment("Minimum ticks between kills for a single butcher villager.")
                    .defineInRange("villagerSlaughterThrottleTicks", 2400, 200, 24000);
            INCLUDE_EXOTIC_BUTCHERY_TRADES = b
                    .translation("townstead.configuration.butchery.includeExoticTrades")
                    .comment("Add a second Master-tier trade pool with exotic cuts (brain, tongue, kidney, sweetbread).")
                    .define("includeExoticTrades", false);
            HAMMER_TROPHY_HEADS = b
                    .translation("townstead.configuration.butchery.hammerTrophyHeads")
                    .comment("When true, the butcher auto-hammers rare / display-worthy heads (evoker, vindicator, pillager, warden, dragon, player, wither skull, ice skull) into their breakdown drops. Off by default so those heads stay whole for trophies and armor.")
                    .define("hammerTrophyHeads", false);
            b.pop();
        } else {
            ENABLE_VILLAGER_SLAUGHTER = null;
            ALLOW_HUMANOID_SLAUGHTER = null;
            VILLAGER_SLAUGHTER_THROTTLE_TICKS = null;
            INCLUDE_EXOTIC_BUTCHERY_TRADES = null;
            HAMMER_TROPHY_HEADS = null;
        }

        // ── Caregiving ──
        b.translation("townstead.configuration.caregiving").push("caregiving");
        ENABLE_FEEDING_YOUNG = b
                .translation("townstead.configuration.caregiving.enableFeedingYoung")
                .comment("Allow adults to feed hungry babies/toddlers/children.")
                .define("enableFeedingYoung", true);
        if (ThirstBridgeResolver.anyThirstModLoaded()) {
            ENABLE_HYDRATING_YOUNG = b
                    .translation("townstead.configuration.caregiving.enableHydratingYoung")
                    .comment("Allow adults to bring drinks to thirsty babies/toddlers/children.")
                    .define("enableHydratingYoung", true);
        } else {
            ENABLE_HYDRATING_YOUNG = null;
        }
        ENABLE_NON_PARENT_CAREGIVERS = b
                .translation("townstead.configuration.caregiving.enableNonParentCaregivers")
                .comment("Allow non-parent villagers to help feed children when parents are absent.")
                .define("enableNonParentCaregivers", true);
        b.pop();

        // ── Storage ──
        b.translation("townstead.configuration.storage").push("storage");
        ENABLE_WORK_SUPPLY_AUTOMATION = b
                .translation("townstead.configuration.storage.enableWorkSupplyAutomation")
                .comment("Allow chore supply restocking and output storage automation from nearby containers.")
                .define("enableWorkSupplyAutomation", false);
        ENABLE_HARVEST_OUTPUT_STORAGE = b
                .translation("townstead.configuration.storage.enableHarvestOutputStorage")
                .comment("Allow harvesting villagers to store gathered output in nearby containers.")
                .define("enableHarvestOutputStorage", true);
        RESPECT_PROTECTED_STORAGE = b
                .translation("townstead.configuration.storage.respectProtectedStorage")
                .comment("If true, villagers will not take food from protected storage blocks/tags.")
                .define("respectProtectedStorage", true);
        PROTECTED_STORAGE_BLOCKS = b
                .translation("townstead.configuration.storage.protectedStorageBlocks")
                .comment("Block IDs that villagers must not take food from.")
                .defineListAllowEmpty("protectedStorageBlocks", List.of(), TownsteadConfig::isValidResourceLocationString);
        PROTECTED_STORAGE_TAGS = b
                .translation("townstead.configuration.storage.protectedStorageTags")
                .comment("Block tags (e.g. modid:tag_name) treated as protected storage.")
                .defineListAllowEmpty("protectedStorageTags", List.of("townstead:protected_food_storage"),
                        TownsteadConfig::isValidResourceLocationString);
        b.pop();

        // ── Chef's Delight ──
        if (ModCompat.isLoaded("chefsdelight")) {
            b.translation("townstead.configuration.chefsdelight_compat").push("chefsdelight_compat");
            ENABLE_TOWNSTEAD_COOK = b
                    .translation("townstead.configuration.chefsdelight_compat.enableTownsteadCook")
                    .comment("When enabled, Townstead handles cook AI and profession assignment.",
                             "When disabled, Chef's Delight handles cooking instead.")
                    .define("enableTownsteadCook", true);
            b.pop();
        } else {
            ENABLE_TOWNSTEAD_COOK = null;
        }

        // ── Debug ──
        b.translation("townstead.configuration.debug").push("debug");
        DEBUG_VILLAGER_AI = b
                .translation("townstead.configuration.debug.debugVillagerAI")
                .comment("Enable debug chat messages for villager AI (farmer, cook, etc.).")
                .define("debugVillagerAI", false);
        DEBUG_VILLAGER_SLEEP = b
                .translation("townstead.configuration.debug.debugVillagerSleep")
                .comment("Enable sleep/rest debug logs and villager debug state updates.")
                .define("debugVillagerSleep", false);
        b.pop();

        SERVER_SPEC = b.build();

        // ── Client Settings ──

        //? if neoforge {
        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        //?} else if forge {
        /*ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        *///?}

        clientBuilder.translation("townstead.configuration.mood_audio").push("mood_audio");
        MUTE_MOOD_VOCALIZATIONS = clientBuilder
                .translation("townstead.configuration.mood_audio.muteMoodVocalizations")
                .comment("Mute villager mood vocalizations tied to laughter/celebration and crying.")
                .define("muteMoodVocalizations", true);
        clientBuilder.pop();

        clientBuilder.translation("townstead.configuration.catalog").push("catalog");
        USE_TOWNSTEAD_CATALOG = clientBuilder
                .translation("townstead.configuration.catalog.useTownsteadCatalog")
                .comment("Use the Townstead extended catalog with kitchen building tiers. Disable to use MCA's original catalog.")
                .define("useTownsteadCatalog", true);
        clientBuilder.pop();

        clientBuilder.translation("townstead.configuration.accessibility").push("accessibility");
        DIALOGUE_REDUCE_MOTION = clientBuilder
                .translation("townstead.configuration.accessibility.reduceMotion")
                .comment("Disable text animation effects (wave, shake, bounce, scale) in dialogue. Emotion colors still apply.")
                .define("reduceMotion", false);
        DIALOGUE_DISABLE_PARTICLES = clientBuilder
                .translation("townstead.configuration.accessibility.disableParticles")
                .comment("Disable screen-space and world-space particles during dialogue.")
                .define("disableParticles", false);
        DIALOGUE_DISABLE_CAMERA = clientBuilder
                .translation("townstead.configuration.accessibility.disableCameraMovement")
                .comment("Prevent the camera from rotating to face the villager during dialogue.")
                .define("disableCameraMovement", false);
        SPIRIT_COLORBLIND_PATTERNS = clientBuilder
                .translation("townstead.configuration.accessibility.spiritColorblindPatterns")
                .comment("Add distinct hatching patterns to Spirit page bars so they can be told apart without color.")
                .define("spiritColorblindPatterns", false);
        SPIRIT_NARRATION = clientBuilder
                .translation("townstead.configuration.accessibility.spiritNarration")
                .comment("Announce the hovered spirit row through the narrator (e.g., 'Nautical: 10 of 25 points, Dockside').")
                .define("spiritNarration", false);
        SPIRIT_LARGER_HIT_TARGETS = clientBuilder
                .translation("townstead.configuration.accessibility.spiritLargerHitTargets")
                .comment("Grow Spirit page row heights and bar thickness for easier clicking on touch or low-precision setups.")
                .define("spiritLargerHitTargets", false);
        SPIRIT_HIGH_CONTRAST = clientBuilder
                .translation("townstead.configuration.accessibility.spiritHighContrast")
                .comment("Use stronger borders and pure black/white text on the Spirit page.")
                .define("spiritHighContrast", false);
        SPIRIT_FONT_SCALE = clientBuilder
                .translation("townstead.configuration.accessibility.spiritFontScale")
                .comment("Text scale multiplier for the Spirit page (1.0 = default, 1.5 = 50% larger). Respects MC's overall GUI scale.")
                .defineInRange("spiritFontScale", 1.0, 1.0, 2.0);
        clientBuilder.pop();

        CLIENT_SPEC = clientBuilder.build();
    }

    public static boolean isTownsteadCookEnabled() {
        if (!ModCompat.isLoaded("farmersdelight")) return false;
        if (ENABLE_TOWNSTEAD_COOK == null) return true;
        return ENABLE_TOWNSTEAD_COOK.get();
    }

    public static boolean isSelfInventoryDrinkingEnabled() {
        return ENABLE_SELF_INVENTORY_DRINKING != null && ENABLE_SELF_INVENTORY_DRINKING.get();
    }

    public static boolean isGroundItemThirstSourcingEnabled() {
        return ENABLE_GROUND_ITEM_THIRST_SOURCING != null && ENABLE_GROUND_ITEM_THIRST_SOURCING.get();
    }

    public static boolean isContainerThirstSourcingEnabled() {
        return ENABLE_CONTAINER_THIRST_SOURCING != null && ENABLE_CONTAINER_THIRST_SOURCING.get();
    }

    public static boolean isCropThirstSourcingEnabled() {
        return ENABLE_CROP_THIRST_SOURCING != null && ENABLE_CROP_THIRST_SOURCING.get();
    }

    public static boolean isVillagerThirstEnabled() {
        return ENABLE_VILLAGER_THIRST != null && ENABLE_VILLAGER_THIRST.get();
    }

    public static boolean isThirstLethalFallbackEnabled() {
        return THIRST_LETHAL_FALLBACK != null && THIRST_LETHAL_FALLBACK.get();
    }

    public static boolean isCookWaterPurificationEnabled() {
        return ENABLE_COOK_WATER_PURIFICATION != null && ENABLE_COOK_WATER_PURIFICATION.get();
    }

    public static boolean isPreferKitchenStorageForEmptyBottlesEnabled() {
        return PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES != null && PREFER_KITCHEN_STORAGE_FOR_EMPTY_BOTTLES.get();
    }

    public static boolean isBaristaRequestChatEnabled() {
        return ENABLE_BARISTA_REQUEST_CHAT != null && ENABLE_BARISTA_REQUEST_CHAT.get();
    }

    public static boolean isMoodVocalizationMuteEnabled() {
        return MUTE_MOOD_VOCALIZATIONS.get();
    }

    private static boolean isValidResourceLocationString(final @NotNull Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    public static boolean isHydratingYoungEnabled() {
        return ENABLE_HYDRATING_YOUNG != null && ENABLE_HYDRATING_YOUNG.get();
    }

    public static boolean isVillagerFatigueEnabled() {
        return ENABLE_VILLAGER_FATIGUE.get();
    }

    public static boolean isVillagerSleepDebugEnabled() {
        return DEBUG_VILLAGER_SLEEP.get();
    }

    public static boolean isProtectedStorage(BlockState state) {
        if (!RESPECT_PROTECTED_STORAGE.get()) return false;
        return protectedStorageRules().matches(state);
    }

    private static ProtectedStorageRules protectedStorageRules() {
        List<? extends String> blockIds = PROTECTED_STORAGE_BLOCKS.get();
        List<? extends String> tagIds = PROTECTED_STORAGE_TAGS.get();
        ProtectedStorageRules current = protectedStorageRules;
        if (current.matchesInputs(blockIds, tagIds)) return current;
        synchronized (TownsteadConfig.class) {
            current = protectedStorageRules;
            if (current.matchesInputs(blockIds, tagIds)) return current;
            current = ProtectedStorageRules.compile(blockIds, tagIds);
            protectedStorageRules = current;
            return current;
        }
    }

    private static final class ProtectedStorageRules {
        private final List<String> blockIds;
        private final List<String> tagIds;
        private final Set<ResourceLocation> blocks;
        private final TagKey<Block>[] tags;

        private ProtectedStorageRules(List<String> blockIds, List<String> tagIds,
                                      Set<ResourceLocation> blocks, TagKey<Block>[] tags) {
            this.blockIds = blockIds;
            this.tagIds = tagIds;
            this.blocks = blocks;
            this.tags = tags;
        }

        static ProtectedStorageRules empty() {
            return new ProtectedStorageRules(List.of(), List.of(), Set.of(), emptyTags());
        }

        static ProtectedStorageRules compile(List<? extends String> rawBlockIds, List<? extends String> rawTagIds) {
            List<String> blockIds = List.copyOf(rawBlockIds);
            List<String> tagIds = List.copyOf(rawTagIds);
            Set<ResourceLocation> blocks = new HashSet<>();
            for (String id : blockIds) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) blocks.add(rl);
            }
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String id : tagIds) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) tags.add(TagKey.create(Registries.BLOCK, rl));
            }
            return new ProtectedStorageRules(
                    blockIds,
                    tagIds,
                    blocks,
                    tags.toArray(emptyTags())
            );
        }

        boolean matchesInputs(List<? extends String> rawBlockIds, List<? extends String> rawTagIds) {
            return blockIds.equals(rawBlockIds) && tagIds.equals(rawTagIds);
        }

        boolean matches(BlockState state) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId != null && blocks.contains(blockId)) return true;
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private static TagKey<Block>[] emptyTags() {
            return (TagKey<Block>[]) new TagKey<?>[0];
        }
    }
}
