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

    public static final ModConfigSpec.BooleanValue ENABLE_SELF_INVENTORY_EATING;
    public static final ModConfigSpec.BooleanValue ENABLE_GROUND_ITEM_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_CROP_SOURCING;
    public static final ModConfigSpec.BooleanValue ENABLE_FARM_ASSIST;
    public static final ModConfigSpec.BooleanValue ENABLE_WORK_SUPPLY_AUTOMATION;
    public static final ModConfigSpec.BooleanValue ENABLE_HARVEST_OUTPUT_STORAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_FEEDING_YOUNG;
    public static final ModConfigSpec.BooleanValue ENABLE_NON_PARENT_CAREGIVERS;
    public static final ModConfigSpec.BooleanValue RESPECT_PROTECTED_STORAGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_STORAGE_TAGS;

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

        SERVER_SPEC = b.build();
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
