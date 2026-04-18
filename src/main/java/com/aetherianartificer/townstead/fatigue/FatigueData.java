package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public final class FatigueData {

    // --- Defaults ---
    public static final int DEFAULT_FATIGUE = 0;

    // --- Bounds (0-20 scale, matching thirst) ---
    public static final int MAX_FATIGUE = 20;

    // --- Accumulation rates (per 500-tick interval) ---
    // At 0.5/interval, 16h aligned work → Drowsy; 10h → high Alert
    public static final float RATE_WORK = 0.5f;
    public static final float RATE_MEET = 0.25f;
    public static final float RATE_IDLE = 0.1f;

    // --- Recovery rates (per 500-tick interval, subtracted) ---
    // Aligned bed: -1.25/interval → full recovery (20pts) in 16 intervals = 8 MC hours
    public static final float RECOVERY_BED_ALIGNED = -1.25f;
    // Misaligned bed: -0.6/interval → full recovery in ~17 MC hours
    public static final float RECOVERY_BED_MISALIGNED = -0.6f;
    public static final float RECOVERY_REST_NO_BED = -0.05f;
    // -0.4/interval → clears collapse in ~5 gameTime intervals ≈ 2 real minutes
    public static final float RECOVERY_COLLAPSED = -0.4f;

    // --- Multipliers ---
    public static final float COMBAT_MULTIPLIER = 2.0f;
    public static final float ALIGNED_CYCLE_MULTIPLIER = 0.75f;
    public static final float MISALIGNED_CYCLE_MULTIPLIER = 1.25f;

    // --- Thresholds (0-20 scale) ---
    // Energized: 0-3 fatigue (energy 17-20)
    // Alert:     4-7 fatigue (energy 13-16)
    // Tired:     8-11 fatigue (energy 9-12)
    // Drowsy:    12-15 fatigue (energy 5-8)
    // Exhausted: 16-19 fatigue (energy 1-4)
    // Collapsed: 20 fatigue (energy 0)
    // Collapse at energy 0, recover when energy reaches 2
    public static final int COLLAPSE_THRESHOLD = 20;
    public static final int RECOVERY_GATE = 18;
    public static final int TIRED_THRESHOLD = 8;
    public static final int DROWSY_THRESHOLD = 12;

    // --- Speed penalties ---
    public static final double SPEED_PENALTY_TIRED = -0.10;
    public static final double SPEED_PENALTY_DROWSY = -0.20;
    public static final double SPEED_PENALTY_EXHAUSTED = -0.30;

    // --- Hunger interaction ---
    public static final float DROWSY_HUNGER_MULTIPLIER = 1.25f;

    // --- Tick intervals ---
    public static final int ACCUMULATION_INTERVAL = 500;
    public static final int MOOD_CHECK_INTERVAL = 2400;
    // Collapse recovery uses gameTime (not dayTime) so villagers escape collapse
    // in predictable real time regardless of doDaylightCycle or time-scaling mods.
    public static final int COLLAPSED_GAMETIME_INTERVAL = 500;

    // --- NBT keys (attachment internal) ---
    private static final String KEY_FATIGUE = "fatigue";
    private static final String KEY_COLLAPSED = "fatigueCollapsed";
    private static final String KEY_GATED = "fatigueGated";
    private static final String KEY_MOOD_DRIFT = "fatigueMoodDrift";
    private static final String KEY_REST_OVERRIDE_ACTIVE = "restOverrideActive";
    private static final String KEY_REST_OVERRIDE_REASON = "restOverrideReason";
    private static final String KEY_REST_DEBUG_REASON = "restDebugReason";
    private static final String KEY_REST_DEBUG_BLOCK = "restDebugBlock";
    private static final String KEY_REST_DEBUG_TARGET = "restDebugTarget";
    private static final String KEY_EMERGENCY_BED = "emergencyBedPos";
    private static final String KEY_SAVED_HOME_POS = "savedHomePos";
    private static final String KEY_SAVED_HOME_DIM = "savedHomeDim";

    // --- NBT keys for editor sync ---
    public static final String EDITOR_KEY_FATIGUE = "townstead_fatigue";

    private FatigueData() {}

    // --- CompoundTag read/write ---

    public static int getFatigue(CompoundTag tag) {
        return tag.contains(KEY_FATIGUE) ? tag.getInt(KEY_FATIGUE) : DEFAULT_FATIGUE;
    }

    public static void setFatigue(CompoundTag tag, int value) {
        tag.putInt(KEY_FATIGUE, clamp(value, 0, MAX_FATIGUE));
    }

    public static boolean isCollapsed(CompoundTag tag) {
        return tag.getBoolean(KEY_COLLAPSED);
    }

    public static void setCollapsed(CompoundTag tag, boolean collapsed) {
        tag.putBoolean(KEY_COLLAPSED, collapsed);
    }

    public static boolean isGated(CompoundTag tag) {
        return tag.getBoolean(KEY_GATED);
    }

    public static void setGated(CompoundTag tag, boolean gated) {
        tag.putBoolean(KEY_GATED, gated);
    }

    public static float getMoodDrift(CompoundTag tag) {
        return tag.getFloat(KEY_MOOD_DRIFT);
    }

    public static void setMoodDrift(CompoundTag tag, float value) {
        tag.putFloat(KEY_MOOD_DRIFT, Math.max(-4f, Math.min(value, 4f)));
    }

    public static boolean isRestOverrideActive(CompoundTag tag) {
        return tag.getBoolean(KEY_REST_OVERRIDE_ACTIVE);
    }

    public static void setRestOverride(CompoundTag tag, boolean active, SleepReason reason) {
        tag.putBoolean(KEY_REST_OVERRIDE_ACTIVE, active);
        tag.putString(KEY_REST_OVERRIDE_REASON, active ? reason.id() : SleepReason.NONE.id());
    }

    public static SleepReason getRestOverrideReason(CompoundTag tag) {
        return SleepReason.fromId(tag.getString(KEY_REST_OVERRIDE_REASON));
    }

    public static boolean hasEmergencyBed(CompoundTag tag) {
        return tag.contains(KEY_EMERGENCY_BED);
    }

    public static BlockPos getEmergencyBed(CompoundTag tag) {
        return tag.contains(KEY_EMERGENCY_BED) ? BlockPos.of(tag.getLong(KEY_EMERGENCY_BED)) : null;
    }

    public static void setEmergencyBed(CompoundTag tag, BlockPos pos) {
        tag.putLong(KEY_EMERGENCY_BED, pos.asLong());
    }

    public static void clearEmergencyBed(CompoundTag tag) {
        tag.remove(KEY_EMERGENCY_BED);
    }

    public static boolean hasSavedHome(CompoundTag tag) {
        return tag.contains(KEY_SAVED_HOME_DIM);
    }

    public static void saveHome(CompoundTag tag, net.minecraft.core.GlobalPos home) {
        tag.putLong(KEY_SAVED_HOME_POS, home.pos().asLong());
        tag.putString(KEY_SAVED_HOME_DIM, home.dimension().location().toString());
    }

    /**
     * Mark that the villager had no HOME before the emergency bed override.
     * Uses a sentinel dimension value to distinguish from "no saved home data".
     */
    public static void saveNoHome(CompoundTag tag) {
        tag.putLong(KEY_SAVED_HOME_POS, Long.MIN_VALUE);
        tag.putString(KEY_SAVED_HOME_DIM, "");
    }

    public static net.minecraft.core.GlobalPos getSavedHome(CompoundTag tag) {
        if (!hasSavedHome(tag)) return null;
        String dim = tag.getString(KEY_SAVED_HOME_DIM);
        if (dim.isEmpty()) return null; // was homeless
        long pos = tag.getLong(KEY_SAVED_HOME_POS);
        //? if >=1.21 {
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(dim));
        //?} else {
        /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(dim));
        *///?}
        return net.minecraft.core.GlobalPos.of(key, BlockPos.of(pos));
    }

    public static boolean wasPreviouslyHomeless(CompoundTag tag) {
        return hasSavedHome(tag) && tag.getString(KEY_SAVED_HOME_DIM).isEmpty();
    }

    public static void clearSavedHome(CompoundTag tag) {
        tag.remove(KEY_SAVED_HOME_POS);
        tag.remove(KEY_SAVED_HOME_DIM);
    }

    public static void setRestDebugDecision(CompoundTag tag, SleepReason reason, SleepBlockReason blockReason, BlockPos targetBed) {
        tag.putString(KEY_REST_DEBUG_REASON, reason.id());
        tag.putString(KEY_REST_DEBUG_BLOCK, blockReason.id());
        if (targetBed != null) {
            tag.putLong(KEY_REST_DEBUG_TARGET, targetBed.asLong());
        }
    }

    public static String getRestDebugReasonId(CompoundTag tag) {
        String value = tag.getString(KEY_REST_DEBUG_REASON);
        return value.isEmpty() ? SleepReason.NONE.id() : value;
    }

    public static String getRestDebugBlockId(CompoundTag tag) {
        String value = tag.getString(KEY_REST_DEBUG_BLOCK);
        return value.isEmpty() ? SleepBlockReason.NONE.id() : value;
    }

    public static long getRestDebugTargetBed(CompoundTag tag) {
        return tag.contains(KEY_REST_DEBUG_TARGET) ? tag.getLong(KEY_REST_DEBUG_TARGET) : Long.MIN_VALUE;
    }

    /**
     * Get the FatigueState for UI display and effect calculation.
     * Fatigue is ascending: 0 = rested, 20 = exhausted.
     */
    public static FatigueState getState(int fatigue) {
        if (fatigue >= COLLAPSE_THRESHOLD) return FatigueState.EXHAUSTED;
        if (fatigue >= DROWSY_THRESHOLD) return FatigueState.DROWSY;
        if (fatigue >= TIRED_THRESHOLD) return FatigueState.TIRED;
        if (fatigue >= 4) return FatigueState.ALERT;
        return FatigueState.RESTED;
    }

    /**
     * Get mood pressure for the given fatigue state.
     */
    public static float getMoodPressure(FatigueState state) {
        return switch (state) {
            case RESTED -> 0.15f;
            case ALERT -> 0f;
            case TIRED -> -0.33f;
            case DROWSY -> -0.5f;
            case EXHAUSTED -> -0.75f;
        };
    }

    /**
     * Get the speed penalty for the current fatigue level.
     * Returns 0 if no penalty applies.
     */
    public static double getSpeedPenalty(int fatigue) {
        if (fatigue >= COLLAPSE_THRESHOLD) return SPEED_PENALTY_EXHAUSTED;
        if (fatigue >= DROWSY_THRESHOLD) return SPEED_PENALTY_DROWSY;
        if (fatigue >= TIRED_THRESHOLD) return SPEED_PENALTY_TIRED;
        return 0.0;
    }

    // --- Energy-restoring item support ---
    public static final int ENERGY_RESTORE_AMOUNT = 5;

    //? if >=1.21 {
    public static final TagKey<net.minecraft.world.item.Item> ENERGY_RESTORING_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "energy_restoring"));
    //?} else {
    /*public static final TagKey<net.minecraft.world.item.Item> ENERGY_RESTORING_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation(Townstead.MOD_ID, "energy_restoring"));
    *///?}

    // Fallback item IDs. Some modpacks ship a datapack that replaces our
    // townstead:energy_restoring tag (LSO bundles have been observed doing this),
    // leaving villagers unable to auto-drink even when they hold coffee.
    //? if >=1.21 {
    private static final Set<ResourceLocation> FALLBACK_ENERGY_RESTORING_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "coffee"),
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "milk_coffee"),
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "chocolate_coffee"),
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "honey_coffee"),
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "syrup_coffee"),
            ResourceLocation.fromNamespaceAndPath("rusticdelight", "dark_coffee")
    );
    //?} else {
    /*private static final Set<ResourceLocation> FALLBACK_ENERGY_RESTORING_IDS = Set.of(
            new ResourceLocation("rusticdelight", "coffee"),
            new ResourceLocation("rusticdelight", "milk_coffee"),
            new ResourceLocation("rusticdelight", "chocolate_coffee"),
            new ResourceLocation("rusticdelight", "honey_coffee"),
            new ResourceLocation("rusticdelight", "syrup_coffee"),
            new ResourceLocation("rusticdelight", "dark_coffee")
    );
    *///?}

    /**
     * Apply fatigue reduction when a villager consumes an energy-restoring item.
     * Items are matched via the {@code townstead:energy_restoring} item tag,
     * with a hardcoded fallback for modpacks that override the tag.
     */
    public static void applyCoffeeEffect(VillagerEntityMCA villager, ItemStack consumed) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return;
        if (!isEnergyRestoring(consumed)) return;

        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        int current = getFatigue(fatigue);
        setFatigue(fatigue, Math.max(0, current - ENERGY_RESTORE_AMOUNT));
        //? if neoforge {
        villager.setData(Townstead.FATIGUE_DATA, fatigue);
        //?} else {
        /*villager.getPersistentData().put("townstead_fatigue", fatigue);
        *///?}
    }

    /**
     * Check if an item is an energy-restoring item. Matches the
     * {@code townstead:energy_restoring} item tag, or a hardcoded fallback list
     * for modpacks that override the tag.
     */
    public static boolean isEnergyRestoring(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ENERGY_RESTORING_TAG)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return FALLBACK_ENERGY_RESTORING_IDS.contains(id);
    }

    /**
     * Try to auto-consume an energy-restoring item from the villager's inventory.
     * Used when fatigued villagers can't go through the normal eating flow.
     * Returns true if an item was consumed.
     */
    public static boolean tryAutoDrinkCoffee(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        net.minecraft.world.SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isEnergyRestoring(stack)) {
                //? if >=1.21 {
                ItemStack consumed = stack.copyWithCount(1);
                //?} else {
                /*ItemStack consumed = stack.copy(); consumed.setCount(1);
                *///?}
                stack.shrink(1);
                applyCoffeeEffect(villager, consumed);
                return true;
            }
        }
        return false;
    }

    /**
     * Convert internal fatigue (0=rested, 20=exhausted) to display energy (20=full, 0=empty).
     */
    public static int toEnergy(int fatigue) {
        return MAX_FATIGUE - fatigue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public enum FatigueState {
        RESTED("townstead.energy.energized", 0x55FF55),       // green
        ALERT("townstead.energy.alert", 0xFFFFFF),             // white
        TIRED("townstead.energy.tired", 0xFFAA00),             // gold
        DROWSY("townstead.energy.drowsy", 0xFF5555),           // red
        EXHAUSTED("townstead.energy.exhausted", 0xAA0000);     // dark red

        private final String translationKey;
        private final int color;

        FatigueState(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getTranslationKey() { return translationKey; }
        public int getColor() { return color; }
    }
}
