package com.aetherianartificer.townstead;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TownsteadConfig {
    private TownsteadConfig() {}

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_EATING;
    public static final ModConfigSpec.BooleanValue ENABLE_GROUND_ITEM_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CROP_SOURCING;
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
    public static final ModConfigSpec.IntValue FARMER_HYDRATION_MIN_PERCENT;
    public static final ModConfigSpec.IntValue FARMER_WATER_SOURCE_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_WATER_SOURCE_VERTICAL_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_GROOM_RADIUS;
    public static final ModConfigSpec.IntValue FARMER_GROOM_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue DEBUG_VILLAGER_AI;
    public static final ModConfigSpec.BooleanValue ENABLE_FARMER_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue FARMER_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_COOK_REQUEST_CHAT;
    public static final ModConfigSpec.IntValue COOK_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_FEEDING_YOUNG;
    public static final ModConfigSpec.BooleanValue ENABLE_NON_PARENT_CAREGIVERS;
    public static final ModConfigSpec.BooleanValue RESPECT_PROTECTED_STORAGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_TAGS;
    public static final ModConfigSpec.BooleanValue MUTE_MOOD_VOCALIZATIONS;
    public static final ModConfigSpec.BooleanValue USE_TOWNSTEAD_CATALOG;
    public static final ModConfigSpec.ConfigValue<String> COOK_HANDLER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("food_sources");
        ENABLE_SELF_INVENTORY_EATING = b
                .comment("Allow villagers to eat from their own inventory.")
                .define("enableSelfInventoryEating", true);
        ENABLE_GROUND_ITEM_SOURCING = b
                .comment("Allow villagers to collect food from ground items.")
                .define("enableGroundItemSourcing", true);
        ENABLE_CONTAINER_SOURCING = b
                .comment("Allow villagers to pull food from containers / item handlers.")
                .define("enableContainerSourcing", true);
        ENABLE_CROP_SOURCING = b
                .comment("Allow villagers to harvest mature crops for food.")
                .define("enableCropSourcing", true);
        ENABLE_FARM_ASSIST = b
                .comment("Enable lightweight farming assist: anti-trample and idle unstuck nudges for harvest chore.")
                .define("enableFarmAssist", true);
        ENABLE_WORK_SUPPLY_AUTOMATION = b
                .comment("Allow chore supply restocking and output storage automation from nearby containers.")
                .define("enableWorkSupplyAutomation", false);
        ENABLE_HARVEST_OUTPUT_STORAGE = b
                .comment("Allow harvesting villagers to store gathered output in nearby containers.")
                .define("enableHarvestOutputStorage", true);
        ENABLE_FARMER_STABILITY_V2 = b
                .comment("Enable Townstead farmer stabilization (anti-thrash, retries, blocked states).")
                .define("enableFarmerStabilityV2", true);
        FARMER_FARM_RADIUS = b
                .comment("Maximum horizontal farm radius around anchor used by farmer AI.")
                .defineInRange("farmerFarmRadius", 12, 4, 32);
        FARMER_CELL_COOLDOWN_TICKS = b
                .comment("Minimum ticks before reworking the same soil cell.")
                .defineInRange("farmerCellCooldownTicks", 120, 0, 2400);
        FARMER_PATHFAIL_MAX_RETRIES = b
                .comment("How many times a target can fail pathing before temporary blacklist.")
                .defineInRange("farmerPathfailMaxRetries", 3, 1, 20);
        FARMER_IDLE_BACKOFF_TICKS = b
                .comment("Ticks to wait before reacquiring work after no valid target.")
                .defineInRange("farmerIdleBackoffTicks", 60, 0, 1200);
        FARMER_SEED_RESERVE = b
                .comment("Minimum seed count to keep before allowing expansion tilling.")
                .defineInRange("farmerSeedReserve", 8, 0, 64);
        FARMER_MAX_CLUSTERS = b
                .comment("Maximum planned connected plot clusters per farm area.")
                .defineInRange("farmerMaxClusters", 6, 1, 64);
        FARMER_MAX_PLOTS = b
                .comment("Maximum planned soil plot cells per farm area.")
                .defineInRange("farmerMaxPlots", 192, 16, 1024);
        ENABLE_FARMER_WATER_PLACEMENT = b
                .comment("Allow farmers to place water sources in planned farm tiles when hydration is insufficient.")
                .define("enableFarmerWaterPlacement", true);
        FARMER_WATER_PLACEMENTS_PER_DAY = b
                .comment("Maximum water source placements a farmer can perform per Minecraft day.")
                .defineInRange("farmerWaterPlacementsPerDay", 2, 0, 16);
        FARMER_HYDRATION_MIN_PERCENT = b
                .comment("Minimum planned farm hydration coverage percent required before expansion tilling.")
                .defineInRange("farmerHydrationMinPercent", 35, 0, 100);
        FARMER_WATER_SOURCE_SEARCH_RADIUS = b
                .comment("Maximum horizontal distance farmers may travel to find water for bucket refills.")
                .defineInRange("farmerWaterSourceSearchRadius", 72, 8, 192);
        FARMER_WATER_SOURCE_VERTICAL_RADIUS = b
                .comment("Vertical search radius for nearby water sources when refilling buckets.")
                .defineInRange("farmerWaterSourceVerticalRadius", 8, 2, 32);
        FARMER_GROOM_RADIUS = b
                .comment("Radius around planned farm cells where farmers may clear removable weeds.")
                .defineInRange("farmerGroomRadius", 1, 0, 4);
        FARMER_GROOM_SCAN_INTERVAL_TICKS = b
                .comment("Ticks between farmer grooming target scans.")
                .defineInRange("farmerGroomScanIntervalTicks", 60, 20, 1200);
        ENABLE_FARMER_REQUEST_CHAT = b
                .comment("Allow farmers to periodically announce missing supplies (seeds/tools/etc.) in local chat.")
                .define("enableFarmerRequestChat", true);
        FARMER_REQUEST_INTERVAL_TICKS = b
                .comment("Minimum ticks between farmer shortage request messages.")
                .defineInRange("farmerRequestIntervalTicks", 3600, 200, 24000);
        ENABLE_COOK_REQUEST_CHAT = b
                .comment("Allow cooks to periodically announce missing kitchen supplies in local chat.")
                .define("enableCookRequestChat", true);
        COOK_REQUEST_INTERVAL_TICKS = b
                .comment("Minimum ticks between cook shortage request messages.")
                .defineInRange("cookRequestIntervalTicks", 3600, 200, 24000);
        b.pop();

        b.push("caregiving");
        ENABLE_FEEDING_YOUNG = b
                .comment("Allow adults to feed hungry babies/toddlers/children.")
                .define("enableFeedingYoung", true);
        ENABLE_NON_PARENT_CAREGIVERS = b
                .comment("Allow non-parent villagers to help feed children when parents are absent.")
                .define("enableNonParentCaregivers", true);
        b.pop();

        b.push("shared_storage");
        RESPECT_PROTECTED_STORAGE = b
                .comment("If true, villagers will not take food from protected storage blocks/tags.")
                .define("respectProtectedStorage", true);
        PROTECTED_STORAGE_BLOCKS = b
                .comment("Block IDs that villagers must not take food from.")
                .defineListAllowEmpty("protectedStorageBlocks", List.of(), TownsteadConfig::isValidResourceLocationString);
        PROTECTED_STORAGE_TAGS = b
                .comment("Block tags (e.g. modid:tag_name) treated as protected storage.")
                .defineListAllowEmpty("protectedStorageTags", List.of("townstead:protected_food_storage"),
                        TownsteadConfig::isValidResourceLocationString);
        b.pop();

        b.push("chefsdelight_compat");
        COOK_HANDLER = b
                .comment("Which mod handles cook AI and profession assignment.",
                         "\"townstead\" = Townstead cook AI (default).",
                         "\"chefsdelight\" = Chef's Delight handles cooking; Townstead cook logic is disabled.",
                         "Only relevant when Chef's Delight is installed.")
                .define("cookHandler", "townstead",
                        s -> "townstead".equals(s) || "chefsdelight".equals(s));
        b.pop();

        b.push("debug");
        DEBUG_VILLAGER_AI = b
                .comment("Enable debug chat messages for villager AI (farmer, cook, etc.).")
                .define("debugVillagerAI", false);
        b.pop();

        SERVER_SPEC = b.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        clientBuilder.push("mood_audio");
        MUTE_MOOD_VOCALIZATIONS = clientBuilder
                .comment("Mute villager mood vocalizations tied to laughter/celebration and crying.")
                .define("muteMoodVocalizations", true);
        clientBuilder.pop();

        clientBuilder.push("catalog");
        USE_TOWNSTEAD_CATALOG = clientBuilder
                .comment("Use the Townstead extended catalog with kitchen building tiers. Disable to use MCA's original catalog.")
                .define("useTownsteadCatalog", true);
        clientBuilder.pop();

        CLIENT_SPEC = clientBuilder.build();
    }

    public static boolean isTownsteadCookEnabled() {
        return "townstead".equals(COOK_HANDLER.get());
    }

    public static boolean isMoodVocalizationMuteEnabled() {
        return MUTE_MOOD_VOCALIZATIONS.get();
    }

    private static boolean isValidResourceLocationString(final @NotNull Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    public static boolean isProtectedStorage(BlockState state) {
        if (!RESPECT_PROTECTED_STORAGE.get()) return false;

        for (String id : PROTECTED_STORAGE_BLOCKS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && rl.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return true;
        }
        for (String id : PROTECTED_STORAGE_TAGS.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            if (state.is(TagKey.create(Registries.BLOCK, rl))) return true;
        }
        return false;
    }
}
