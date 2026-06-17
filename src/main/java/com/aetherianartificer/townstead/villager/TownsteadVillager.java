package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.SleepBlockReason;
import com.aetherianartificer.townstead.fatigue.SleepReason;
import com.aetherianartificer.townstead.compat.butchery.ButcherSettings;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime Townstead state for one MCA villager.
 *
 * <p>This is the in-memory domain model. NBT conversion lives at persistence
 * boundaries and in temporary adapters for older call sites.</p>
 */
public final class TownsteadVillager {
    public static final int SCHEMA_VERSION = 4;

    private final UUID villagerId;
    private boolean dirty;
    private long lastSeenGameTime;

    private final Needs needs = new Needs();
    private final ScheduleState schedule = new ScheduleState();
    private final Life life = new Life();
    private final ProfessionMemory professionMemory = new ProfessionMemory();

    public TownsteadVillager(UUID villagerId) {
        this.villagerId = villagerId;
    }

    public UUID villagerId() {
        return villagerId;
    }

    public Needs needs() {
        return needs;
    }

    public ScheduleState schedule() {
        return schedule;
    }

    public Life life() {
        return life;
    }

    public ProfessionMemory professionMemory() {
        return professionMemory;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    public void touch(long gameTime) {
        lastSeenGameTime = gameTime;
    }

    public CompoundTag toSnapshotTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("villagerId", villagerId);
        tag.putLong("lastSeenGameTime", lastSeenGameTime);
        tag.put("needs", needs.toTag());
        tag.put("schedule", schedule.toTag());
        tag.put("life", life.toTag());
        tag.put("professionMemory", professionMemory.toTag());
        return tag;
    }

    public void loadSnapshotTag(CompoundTag tag) {
        needs.load(tag.getCompound("needs"));
        schedule.load(tag.getCompound("schedule"));
        life.load(tag.getCompound("life"));
        professionMemory.load(tag.getCompound("professionMemory"));
        lastSeenGameTime = tag.getLong("lastSeenGameTime");
        clearDirty();
    }

    public void migrateLegacyRoot(CompoundTag root) {
        needs.loadHunger(root.getCompound("hunger"));
        needs.loadThirst(root.getCompound("thirst"));
        needs.loadFatigue(root.getCompound("fatigue"));
        schedule.load(root.getCompound("shift"));
        life.load(root.getCompound("life"));
        professionMemory.loadLegacyHunger(root.getCompound("hunger"));
        markDirty();
    }

    public void upgradeFromLegacyRoot(CompoundTag root) {
        professionMemory.mergeLegacyHunger(root.getCompound("hunger"));
        markDirty();
    }

    public final class Needs {
        private int hunger = HungerData.DEFAULT_HUNGER;
        private float saturation = HungerData.DEFAULT_SATURATION;
        private float hungerExhaustion;
        private long lastAteTime;
        private boolean eatingMode;
        private float hungerMoodDrift;
        private HungerData.FarmBlockedReason farmBlockedReason = HungerData.FarmBlockedReason.NONE;
        private HungerData.ButcherBlockedReason butcherBlockedReason = HungerData.ButcherBlockedReason.NONE;
        private HungerData.FishermanBlockedReason fishermanBlockedReason = HungerData.FishermanBlockedReason.NONE;

        private int thirst = ThirstData.DEFAULT_THIRST;
        private int quenched = ThirstData.DEFAULT_QUENCHED;
        private float thirstExhaustion;
        private long lastDrankTime;
        private boolean drinkingMode;
        private float thirstMoodDrift;
        private int thirstDamageTimer;

        private int fatigue = FatigueData.DEFAULT_FATIGUE;
        private boolean collapsed;
        private boolean gated;
        private float fatigueMoodDrift;
        private boolean restOverrideActive;
        private String restOverrideReason = "none";
        private String restDebugReason = "none";
        private String restDebugBlock = "none";
        private long restDebugTargetBed = Long.MIN_VALUE;
        private long emergencyBedPos = Long.MIN_VALUE;
        private long savedHomePos = Long.MIN_VALUE;
        private String savedHomeDim = null;

        public int hunger() {
            return hunger;
        }

        public float saturation() {
            return saturation;
        }

        public float hungerExhaustion() {
            return hungerExhaustion;
        }

        public int quenched() {
            return quenched;
        }

        public int thirst() {
            return thirst;
        }

        public float thirstExhaustion() {
            return thirstExhaustion;
        }

        public int fatigue() {
            return fatigue;
        }

        public boolean collapsed() {
            return collapsed;
        }

        public boolean gated() {
            return gated;
        }

        public HungerData.FarmBlockedReason farmBlockedReason() {
            return farmBlockedReason;
        }

        public HungerData.ButcherBlockedReason butcherBlockedReason() {
            return butcherBlockedReason;
        }

        public HungerData.FishermanBlockedReason fishermanBlockedReason() {
            return fishermanBlockedReason;
        }

        public void setFarmBlockedReason(HungerData.FarmBlockedReason reason) {
            farmBlockedReason = reason == null ? HungerData.FarmBlockedReason.NONE : reason;
            markDirty();
        }

        public void setButcherBlockedReason(HungerData.ButcherBlockedReason reason) {
            butcherBlockedReason = reason == null ? HungerData.ButcherBlockedReason.NONE : reason;
            markDirty();
        }

        public void setFishermanBlockedReason(HungerData.FishermanBlockedReason reason) {
            fishermanBlockedReason = reason == null ? HungerData.FishermanBlockedReason.NONE : reason;
            markDirty();
        }

        public void setHunger(int value) {
            hunger = clamp(value, 0, HungerData.MAX_HUNGER);
            markDirty();
        }

        public void setSaturation(float value) {
            saturation = Math.max(0f, Math.min(value, HungerData.MAX_SATURATION));
            markDirty();
        }

        public void setHungerExhaustion(float value) {
            hungerExhaustion = Math.max(0f, value);
            markDirty();
        }

        public void addHungerExhaustion(float value) {
            setHungerExhaustion(hungerExhaustion + value);
        }

        public void setEatingMode(boolean value) {
            eatingMode = value;
            markDirty();
        }

        public boolean eatingMode() {
            return eatingMode;
        }

        public long lastAteTime() {
            return lastAteTime;
        }

        public void setLastAteTime(long value) {
            lastAteTime = value;
            markDirty();
        }

        public boolean processHungerExhaustion() {
            if (hungerExhaustion < HungerData.EXHAUSTION_THRESHOLD) return false;
            boolean hungerChanged = false;
            while (hungerExhaustion >= HungerData.EXHAUSTION_THRESHOLD) {
                hungerExhaustion -= HungerData.EXHAUSTION_THRESHOLD;
                if (saturation > 0f) {
                    saturation = Math.max(0f, saturation - 1f);
                } else if (hunger > 0) {
                    hunger--;
                    hungerChanged = true;
                }
            }
            markDirty();
            return hungerChanged;
        }

        public boolean passiveHungerDrain() {
            if (saturation > 0f) {
                saturation = Math.max(0f, saturation - 1f);
                markDirty();
                return false;
            }
            if (hunger > 0) {
                hunger--;
                markDirty();
                return true;
            }
            return false;
        }

        public int applyFood(FoodProperties food) {
            return applyFood(food, 1f);
        }

        /**
         * Applies a food's hunger/saturation, scaling its nutrition by {@code nutritionMultiplier}
         * (the eater's {@code food} modifier, resolved by the caller, which holds the entity).
         */
        public int applyFood(FoodProperties food, float nutritionMultiplier) {
            //? if >=1.21 {
            int rawNutrition = food.nutrition();
            float satMod = food.saturation();
            //?} else {
            /*int rawNutrition = food.getNutrition();
            float satMod = food.getSaturationModifier();
            *///?}
            int nutrition = Math.max(0, Math.round(rawNutrition * nutritionMultiplier));
            int hungerRestored = (int)(nutrition * HungerData.FOOD_SCALE);
            hunger = Math.min(hunger + hungerRestored, HungerData.MAX_HUNGER);
            float satRestored = Math.min(nutrition * satMod * HungerData.FOOD_SCALE, hunger);
            saturation = Math.min(saturation + satRestored, HungerData.MAX_SATURATION);
            markDirty();
            return hunger;
        }

        public void resetHunger(int hungerValue, float saturationValue) {
            hunger = clamp(hungerValue, 0, HungerData.MAX_HUNGER);
            saturation = Math.max(0f, Math.min(saturationValue, HungerData.MAX_SATURATION));
            hungerExhaustion = 0f;
            eatingMode = false;
            markDirty();
        }

        public void setThirst(int value) {
            thirst = clamp(value, 0, ThirstData.MAX_THIRST);
            if (quenched > thirst) quenched = thirst;
            markDirty();
        }

        public void setQuenched(int value) {
            quenched = clamp(value, 0, Math.min(ThirstData.MAX_QUENCHED, thirst));
            markDirty();
        }

        public void setThirstExhaustion(float value) {
            thirstExhaustion = Math.max(0f, value);
            markDirty();
        }

        public void addThirstExhaustion(float value) {
            setThirstExhaustion(thirstExhaustion + value);
        }

        public void setDrinkingMode(boolean value) {
            drinkingMode = value;
            markDirty();
        }

        public boolean drinkingMode() {
            return drinkingMode;
        }

        public long lastDrankTime() {
            return lastDrankTime;
        }

        public void setLastDrankTime(long value) {
            lastDrankTime = value;
            markDirty();
        }

        public boolean processThirstExhaustion() {
            if (thirstExhaustion < ThirstData.EXHAUSTION_THRESHOLD) return false;
            boolean changed = false;
            while (thirstExhaustion >= ThirstData.EXHAUSTION_THRESHOLD) {
                thirstExhaustion -= ThirstData.EXHAUSTION_THRESHOLD;
                if (quenched > 0) {
                    quenched--;
                    changed = true;
                    continue;
                }
                if (thirst > 0) {
                    thirst--;
                    changed = true;
                }
            }
            markDirty();
            return changed;
        }

        public boolean passiveThirstDrain() {
            if (quenched > 0) {
                quenched--;
                markDirty();
                return true;
            }
            if (thirst > 0) {
                thirst--;
                markDirty();
                return true;
            }
            return false;
        }

        public void applyDrink(int hydration, int quenchedGain, boolean convertExtraHydrationToQuenched) {
            int extraQuenched = convertExtraHydrationToQuenched ? Math.max(thirst + hydration - ThirstData.MAX_THIRST, 0) : 0;
            thirst = Math.min(ThirstData.MAX_THIRST, thirst + Math.max(0, hydration));
            quenched = clamp(quenched + Math.max(0, quenchedGain) + extraQuenched, 0, Math.min(ThirstData.MAX_QUENCHED, thirst));
            markDirty();
        }

        public void setThirstDamageTimer(int value) {
            thirstDamageTimer = Math.max(0, value);
            markDirty();
        }

        public void resetThirst(int thirstValue, int quenchedValue) {
            thirst = clamp(thirstValue, 0, ThirstData.MAX_THIRST);
            quenched = clamp(quenchedValue, 0, Math.min(ThirstData.MAX_QUENCHED, thirst));
            thirstExhaustion = 0f;
            drinkingMode = false;
            thirstDamageTimer = 0;
            markDirty();
        }

        public void setFatigue(int value) {
            fatigue = clamp(value, 0, FatigueData.MAX_FATIGUE);
            markDirty();
        }

        public void addFatigue(int delta) {
            setFatigue(fatigue + delta);
        }

        public float hungerMoodDrift() {
            return hungerMoodDrift;
        }

        public void setHungerMoodDrift(float value) {
            hungerMoodDrift = Math.max(-4f, Math.min(value, 4f));
            markDirty();
        }

        public float thirstMoodDrift() {
            return thirstMoodDrift;
        }

        public void setThirstMoodDrift(float value) {
            thirstMoodDrift = Math.max(-4f, Math.min(value, 4f));
            markDirty();
        }

        public float fatigueMoodDrift() {
            return fatigueMoodDrift;
        }

        public void setFatigueMoodDrift(float value) {
            fatigueMoodDrift = Math.max(-4f, Math.min(value, 4f));
            markDirty();
        }

        public void setCollapsed(boolean value) {
            collapsed = value;
            markDirty();
        }

        public void setGated(boolean value) {
            gated = value;
            markDirty();
        }

        public void restoreEnergy(int amount) {
            fatigue = clamp(fatigue - Math.max(0, amount), 0, FatigueData.MAX_FATIGUE);
            if (fatigue < FatigueData.COLLAPSE_THRESHOLD) {
                collapsed = false;
            }
            if (fatigue < FatigueData.RECOVERY_GATE) {
                gated = false;
            }
            markDirty();
        }

        public void resetFatigue(int value) {
            fatigue = clamp(value, 0, FatigueData.MAX_FATIGUE);
            collapsed = false;
            gated = false;
            markDirty();
        }

        public boolean restOverrideActive() {
            return restOverrideActive;
        }

        public void setRestOverride(boolean active, SleepReason reason) {
            restOverrideActive = active;
            restOverrideReason = active && reason != null ? reason.id() : SleepReason.NONE.id();
            markDirty();
        }

        public boolean hasEmergencyBed() {
            return emergencyBedPos != Long.MIN_VALUE;
        }

        public BlockPos emergencyBed() {
            return hasEmergencyBed() ? BlockPos.of(emergencyBedPos) : null;
        }

        public void setEmergencyBed(BlockPos pos) {
            emergencyBedPos = pos == null ? Long.MIN_VALUE : pos.asLong();
            markDirty();
        }

        public void clearEmergencyBed() {
            emergencyBedPos = Long.MIN_VALUE;
            markDirty();
        }

        public boolean hasSavedHome() {
            return savedHomeDim != null;
        }

        public void saveHome(net.minecraft.core.GlobalPos home) {
            if (home == null) {
                saveNoHome();
                return;
            }
            savedHomePos = home.pos().asLong();
            savedHomeDim = home.dimension().location().toString();
            markDirty();
        }

        public void saveNoHome() {
            savedHomePos = Long.MIN_VALUE;
            savedHomeDim = "";
            markDirty();
        }

        public net.minecraft.core.GlobalPos savedHome() {
            if (!hasSavedHome() || savedHomeDim.isEmpty()) return null;
            //? if >=1.21 {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.parse(savedHomeDim));
            //?} else {
            /*net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                            new net.minecraft.resources.ResourceLocation(savedHomeDim));
            *///?}
            return net.minecraft.core.GlobalPos.of(key, BlockPos.of(savedHomePos));
        }

        public boolean wasPreviouslyHomeless() {
            return hasSavedHome() && savedHomeDim.isEmpty();
        }

        public void clearSavedHome() {
            savedHomeDim = null;
            savedHomePos = Long.MIN_VALUE;
            markDirty();
        }

        public void setRestDebugDecision(SleepReason reason, SleepBlockReason blockReason, BlockPos targetBed) {
            restDebugReason = reason == null ? SleepReason.NONE.id() : reason.id();
            restDebugBlock = blockReason == null ? SleepBlockReason.NONE.id() : blockReason.id();
            restDebugTargetBed = targetBed == null ? Long.MIN_VALUE : targetBed.asLong();
            markDirty();
        }

        public String restDebugReasonId() {
            return restDebugReason == null || restDebugReason.isEmpty() ? SleepReason.NONE.id() : restDebugReason;
        }

        public String restDebugBlockId() {
            return restDebugBlock == null || restDebugBlock.isEmpty() ? SleepBlockReason.NONE.id() : restDebugBlock;
        }

        public long restDebugTargetBed() {
            return restDebugTargetBed;
        }

        public CompoundTag hungerTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("hunger", hunger);
            tag.putFloat("saturation", saturation);
            tag.putFloat("exhaustion", hungerExhaustion);
            tag.putLong("lastAteTime", lastAteTime);
            tag.putBoolean("eatingMode", eatingMode);
            tag.putFloat("moodDrift", hungerMoodDrift);
            tag.putString("farmBlockedReason", farmBlockedReason.id());
            tag.putString("butcherBlockedReason", butcherBlockedReason.id());
            tag.putString("fishermanBlockedReason", fishermanBlockedReason.id());
            return tag;
        }

        public void loadHunger(CompoundTag tag) {
            hunger = HungerData.getHunger(tag);
            saturation = HungerData.getSaturation(tag);
            hungerExhaustion = HungerData.getExhaustion(tag);
            lastAteTime = HungerData.getLastAteTime(tag);
            eatingMode = HungerData.isEatingMode(tag);
            hungerMoodDrift = HungerData.getMoodDrift(tag);
            farmBlockedReason = HungerData.getFarmBlockedReason(tag);
            butcherBlockedReason = HungerData.getButcherBlockedReason(tag);
            fishermanBlockedReason = HungerData.getFishermanBlockedReason(tag);
            markDirty();
        }

        public CompoundTag thirstTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("thirst", thirst);
            tag.putInt("quenched", quenched);
            tag.putFloat("thirstExhaustion", thirstExhaustion);
            tag.putLong("lastDrankTime", lastDrankTime);
            tag.putBoolean("drinkingMode", drinkingMode);
            tag.putFloat("thirstMoodDrift", thirstMoodDrift);
            tag.putInt("thirstDamageTimer", thirstDamageTimer);
            return tag;
        }

        public void loadThirst(CompoundTag tag) {
            thirst = ThirstData.getThirst(tag);
            quenched = ThirstData.getQuenched(tag);
            thirstExhaustion = ThirstData.getExhaustion(tag);
            lastDrankTime = ThirstData.getLastDrankTime(tag);
            drinkingMode = ThirstData.isDrinkingMode(tag);
            thirstMoodDrift = ThirstData.getMoodDrift(tag);
            thirstDamageTimer = ThirstData.getDamageTimer(tag);
            markDirty();
        }

        public CompoundTag fatigueTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("fatigue", fatigue);
            tag.putBoolean("fatigueCollapsed", collapsed);
            tag.putBoolean("fatigueGated", gated);
            tag.putFloat("fatigueMoodDrift", fatigueMoodDrift);
            tag.putBoolean("restOverrideActive", restOverrideActive);
            tag.putString("restOverrideReason", restOverrideReason);
            tag.putString("restDebugReason", restDebugReason);
            tag.putString("restDebugBlock", restDebugBlock);
            if (restDebugTargetBed != Long.MIN_VALUE) tag.putLong("restDebugTarget", restDebugTargetBed);
            if (emergencyBedPos != Long.MIN_VALUE) tag.putLong("emergencyBedPos", emergencyBedPos);
            if (savedHomeDim != null) {
                tag.putLong("savedHomePos", savedHomePos);
                tag.putString("savedHomeDim", savedHomeDim);
            }
            return tag;
        }

        public void loadFatigue(CompoundTag tag) {
            fatigue = FatigueData.getFatigue(tag);
            collapsed = FatigueData.isCollapsed(tag);
            gated = FatigueData.isGated(tag);
            fatigueMoodDrift = FatigueData.getMoodDrift(tag);
            restOverrideActive = FatigueData.isRestOverrideActive(tag);
            restOverrideReason = FatigueData.getRestOverrideReason(tag).id();
            restDebugReason = FatigueData.getRestDebugReasonId(tag);
            restDebugBlock = FatigueData.getRestDebugBlockId(tag);
            restDebugTargetBed = FatigueData.getRestDebugTargetBed(tag);
            emergencyBedPos = FatigueData.hasEmergencyBed(tag) ? FatigueData.getEmergencyBed(tag).asLong() : Long.MIN_VALUE;
            if (FatigueData.hasSavedHome(tag)) {
                net.minecraft.core.GlobalPos home = FatigueData.getSavedHome(tag);
                savedHomeDim = home == null ? "" : home.dimension().location().toString();
                savedHomePos = home == null ? Long.MIN_VALUE : home.pos().asLong();
            } else {
                savedHomeDim = null;
                savedHomePos = Long.MIN_VALUE;
            }
            markDirty();
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.put("hunger", hungerTag());
            tag.put("thirst", thirstTag());
            tag.put("fatigue", fatigueTag());
            return tag;
        }

        private void load(CompoundTag tag) {
            loadHunger(tag.getCompound("hunger"));
            loadThirst(tag.getCompound("thirst"));
            loadFatigue(tag.getCompound("fatigue"));
        }
    }

    public final class ScheduleState {
        private int[] shifts = ShiftData.getVanillaDefault();
        private String templateId = "";
        private String mode = ShiftData.MODE_DAILY;
        private List<String> weekDayTemplates = new ArrayList<>();
        private boolean customShifts;

        public int currentShift(int tickHour) {
            return shifts[Math.floorMod(tickHour, ShiftData.HOURS_PER_DAY)];
        }

        public int[] copyShifts() {
            return shifts.clone();
        }

        public boolean hasCustomShifts() {
            return customShifts;
        }

        public boolean hasNonDefaultCustomShifts() {
            return customShifts && !java.util.Arrays.equals(shifts, ShiftData.getVanillaDefault());
        }

        public String templateId() {
            return templateId;
        }

        public String mode() {
            return mode;
        }

        public List<String> weekDayTemplates() {
            return List.copyOf(weekDayTemplates);
        }

        public void setShifts(int[] value) {
            if (value == null || value.length != ShiftData.HOURS_PER_DAY) return;
            shifts = value.clone();
            customShifts = true;
            markDirty();
        }

        public void setTemplateId(String value) {
            templateId = value == null ? "" : value;
            markDirty();
        }

        public void setMode(String value) {
            mode = ShiftData.MODE_WEEKLY.equals(value) ? ShiftData.MODE_WEEKLY : ShiftData.MODE_DAILY;
            markDirty();
        }

        public void setWeekDayTemplates(List<String> value) {
            weekDayTemplates = value == null ? new ArrayList<>() : new ArrayList<>(value);
            markDirty();
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (customShifts) tag.putIntArray("shifts", shifts.clone());
            if (!templateId.isEmpty()) tag.putString("template_id", templateId);
            if (ShiftData.MODE_WEEKLY.equals(mode)) tag.putString("mode", mode);
            if (!weekDayTemplates.isEmpty()) {
                ListTag list = new ListTag();
                for (String id : weekDayTemplates) list.add(StringTag.valueOf(id == null ? "" : id));
                tag.put("week_days", list);
            }
            return tag;
        }

        public void load(CompoundTag tag) {
            shifts = ShiftData.getShifts(tag);
            templateId = ShiftData.getTemplateId(tag);
            mode = ShiftData.getMode(tag);
            weekDayTemplates = ShiftData.getWeekDayTemplates(tag);
            customShifts = ShiftData.hasCustomShifts(tag);
            markDirty();
        }
    }

    public final class Life {
        private long birthWorldDay = Long.MIN_VALUE;
        private boolean stamped;
        // Celebrated birthday: the month/day the villager "celebrates", decoupled from
        // birthWorldDay (the age axis). 0 = unset → display derives from birthWorldDay.
        // Editing month/day in the editor changes only this, never the age.
        private int birthMonth;
        private int birthDay;
        private String originId = "";
        private String personalityId = "";
        private int[] stageDays = EMPTY_INT_ARRAY;
        private int cycleFingerprint;
        private String currentStageId = "";
        private boolean immortal;
        // Granted agelessness (the Potion of Agelessness). Separate from the immortal flag (which the
        // immortal trait/gene keeps) and from a species' intrinsic ageless life cycle; all three pin
        // the life stage via LifeStageProgression.isAgeless.
        private boolean ageless;
        private boolean isSenior;
        private float fertility;
        // Apparent-age freeze: when "villagers do not age" is on, the day aging was frozen.
        // Display (apparent age, stage, senior progress) reads this instead of the live day,
        // so it stops advancing with the calendar. Long.MIN_VALUE = not frozen.
        private long agingFrozenDay = Long.MIN_VALUE;
        // Expressed phenotype cache: for each variant gene the villager expresses, the
        // variant id currently showing (geneId -> variantId, e.g. chronotype -> night_owl).
        // This is the dominant-resolved projection of {@link #genotype} that existing read
        // sites (sleep window, skin tint) consume; it is recomputed when the genotype changes.
        private final java.util.Map<String, String> carriedVariants = new java.util.HashMap<>();
        // Expressed allele encodings ("geneId" or "geneId#variant") for EVERY expressed locus, the same
        // list the per-entity render sync ships. Persisted so a reconstructed entity (CarryOn rebuilds
        // from NBT with a fresh untracked id, never synced) can still render its real genetics
        // (attachments, hidden features). Recomputed alongside carriedVariants whenever the genotype changes.
        private java.util.List<String> expressedAlleles = new java.util.ArrayList<>();
        // The diploid heritable truth: two alleles per discrete locus. The expressed
        // phenotype above is derived from this; inheritance draws one allele per locus
        // from each parent. Continuous body floats live on MCA's genetics, not here.
        private com.aetherianartificer.townstead.origin.gene.Genotype genotype =
                new com.aetherianartificer.townstead.origin.gene.Genotype();
        // Realized ancestry composition (the "23andMe" vector) that drives the displayed
        // race name via the HeritageRegistry; a founder is seeded from its origin, a child
        // blends its parents.
        private com.aetherianartificer.townstead.origin.Heritage heritage =
                com.aetherianartificer.townstead.origin.Heritage.EMPTY;

        public long birthWorldDay() {
            return birthWorldDay;
        }

        public boolean hasBirth() {
            return birthWorldDay != Long.MIN_VALUE;
        }

        public boolean stamped() {
            return stamped;
        }

        public void setBirth(long worldDay, boolean stamped) {
            birthWorldDay = worldDay;
            this.stamped = stamped;
            markDirty();
        }

        /** Celebrated birth month (1-based), or 0 if unset (display falls back to birthWorldDay). */
        public int birthMonth() { return birthMonth; }

        /** Celebrated birth day-of-month (1-based), or 0 if unset. */
        public int birthDay() { return birthDay; }

        public boolean hasCelebratedBirthday() { return birthMonth > 0 && birthDay > 0; }

        /** Set the celebrated month/day. Independent of age — does not touch birthWorldDay. */
        public void setCelebratedBirthday(int month, int day) {
            birthMonth = Math.max(0, month);
            birthDay = Math.max(0, day);
            markDirty();
        }

        /** The villager's origin id (e.g. {@code townstead_origins:overworlder}); empty until assigned. */
        public String originId() {
            return originId;
        }

        public boolean hasOrigin() {
            return !originId.isEmpty();
        }

        public void setOrigin(String id) {
            originId = id == null ? "" : id;
            markDirty();
        }

        /**
         * The villager's personality reference: a custom {@code PersonalityDef} id or a bare base-enum
         * name, rolled from the origin's allowlist at spawn. Empty when the origin defines no policy
         * (then MCA's own personality stands). Drives the display name and the voice tier; MCA's
         * mechanics ride the base enum this maps to (set on the brain at spawn).
         */
        public String personalityId() {
            return personalityId;
        }

        public void setPersonalityId(String id) {
            personalityId = id == null ? "" : id;
            markDirty();
        }

        /**
         * Per-stage day durations rolled at spawn, aligned to the origin's
         * {@link com.aetherianartificer.townstead.origin.LifeCycle} stage order.
         * Length 0 until the spawn handler rolls; mismatch with the current
         * cycle length means the origin was reassigned and a re-roll is due.
         */
        public int[] stageDays() {
            return stageDays.length == 0 ? EMPTY_INT_ARRAY : stageDays.clone();
        }

        public boolean hasStageDays() {
            return stageDays.length > 0;
        }

        public int stageDaysLength() {
            return stageDays.length;
        }

        public void setStageDays(int[] value) {
            stageDays = value == null || value.length == 0 ? EMPTY_INT_ARRAY : value.clone();
            markDirty();
        }

        /** Hash of the cycle shape the {@code stageDays} were rolled against; see {@link com.aetherianartificer.townstead.origin.LifeCycle#fingerprint()}. */
        public int cycleFingerprint() {
            return cycleFingerprint;
        }

        public void setCycleFingerprint(int value) {
            cycleFingerprint = value;
            markDirty();
        }

        public String currentStageId() {
            return currentStageId;
        }

        public void setCurrentStageId(String id) {
            currentStageId = id == null ? "" : id;
            markDirty();
        }

        public boolean immortal() {
            return immortal;
        }

        public void setImmortal(boolean value) {
            immortal = value;
            markDirty();
        }

        public boolean ageless() {
            return ageless;
        }

        public void setAgeless(boolean value) {
            ageless = value;
            markDirty();
        }

        public boolean isSenior() {
            return isSenior;
        }

        public void setSenior(boolean value) {
            isSenior = value;
            markDirty();
        }

        public float fertility() {
            return fertility;
        }

        public void setFertility(float value) {
            fertility = Math.max(0f, Math.min(1f, value));
            markDirty();
        }

        public boolean hasAgingFrozenDay() { return agingFrozenDay != Long.MIN_VALUE; }

        public long agingFrozenDay() { return agingFrozenDay; }

        public void setAgingFrozenDay(long worldDay) {
            agingFrozenDay = worldDay;
            markDirty();
        }

        public void clearAgingFrozenDay() {
            agingFrozenDay = Long.MIN_VALUE;
            markDirty();
        }

        /** Read-only view of the carried {@code geneId → variantId} genotype entries. */
        public java.util.Map<String, String> carriedVariants() {
            return java.util.Collections.unmodifiableMap(carriedVariants);
        }

        /** The variant id this villager carries for {@code geneId}, or empty if none. */
        public String carriedVariant(String geneId) {
            if (geneId == null) return "";
            return carriedVariants.getOrDefault(geneId, "");
        }

        public boolean hasCarriedVariant(String geneId) {
            return geneId != null && carriedVariants.containsKey(geneId);
        }

        /** Stamp (or clear, when {@code variantId} is null/empty) the carried variant for a gene. */
        public void setCarriedVariant(String geneId, String variantId) {
            if (geneId == null || geneId.isEmpty()) return;
            if (variantId == null || variantId.isEmpty()) {
                carriedVariants.remove(geneId);
            } else {
                carriedVariants.put(geneId, variantId);
            }
            markDirty();
        }

        /** Expressed allele encodings for every expressed locus (render sync + persistence). */
        public java.util.List<String> expressedAlleles() {
            return java.util.Collections.unmodifiableList(expressedAlleles);
        }

        public void setExpressedAlleles(java.util.List<String> encodings) {
            expressedAlleles = encodings == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(encodings);
            markDirty();
        }

        /** The diploid genotype (two alleles per locus); the heritable source of truth. */
        public com.aetherianartificer.townstead.origin.gene.Genotype genotype() {
            return genotype;
        }

        public boolean hasGenotype() {
            return !genotype.isEmpty();
        }

        public void setGenotype(com.aetherianartificer.townstead.origin.gene.Genotype value) {
            genotype = value == null ? new com.aetherianartificer.townstead.origin.gene.Genotype() : value;
            markDirty();
        }

        /** The villager's realized ancestry composition (drives the displayed race name). */
        public com.aetherianartificer.townstead.origin.Heritage heritage() {
            return heritage;
        }

        public boolean hasHeritage() {
            return !heritage.isEmpty();
        }

        public void setHeritage(com.aetherianartificer.townstead.origin.Heritage value) {
            heritage = value == null ? com.aetherianartificer.townstead.origin.Heritage.EMPTY : value;
            markDirty();
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (hasBirth()) {
                tag.putLong("birthWorldDay", birthWorldDay);
                tag.putBoolean("birthStamped", stamped);
            }
            if (birthMonth > 0) tag.putInt("birthMonth", birthMonth);
            if (birthDay > 0) tag.putInt("birthDay", birthDay);
            if (hasOrigin()) {
                tag.putString("originId", originId);
            }
            if (!personalityId.isEmpty()) {
                tag.putString("personalityId", personalityId);
            }
            if (stageDays.length > 0) {
                tag.putIntArray("stageDays", stageDays.clone());
            }
            if (cycleFingerprint != 0) {
                tag.putInt("cycleFingerprint", cycleFingerprint);
            }
            if (!currentStageId.isEmpty()) {
                tag.putString("currentStageId", currentStageId);
            }
            if (immortal) tag.putBoolean("immortal", true);
            if (ageless) tag.putBoolean("ageless", true);
            if (isSenior) tag.putBoolean("isSenior", true);
            if (fertility > 0f) tag.putFloat("fertility", fertility);
            if (agingFrozenDay != Long.MIN_VALUE) tag.putLong("agingFrozenDay", agingFrozenDay);
            if (!carriedVariants.isEmpty()) {
                CompoundTag cv = new CompoundTag();
                for (Map.Entry<String, String> e : carriedVariants.entrySet()) {
                    cv.putString(e.getKey(), e.getValue());
                }
                tag.put("carriedVariants", cv);
            }
            if (!expressedAlleles.isEmpty()) {
                ListTag list = new ListTag();
                for (String e : expressedAlleles) list.add(StringTag.valueOf(e));
                tag.put("expressedAlleles", list);
            }
            if (!genotype.isEmpty()) tag.put("genotype", genotype.toTag());
            if (!heritage.isEmpty()) tag.put("heritage", heritage.toTag());
            return tag;
        }

        public void load(CompoundTag tag) {
            if (tag.contains("birthWorldDay")) {
                birthWorldDay = tag.getLong("birthWorldDay");
                stamped = tag.getBoolean("birthStamped");
            } else {
                birthWorldDay = Long.MIN_VALUE;
                stamped = false;
            }
            birthMonth = tag.getInt("birthMonth");
            birthDay = tag.getInt("birthDay");
            originId = tag.getString("originId");
            personalityId = tag.getString("personalityId");
            stageDays = tag.contains("stageDays") ? tag.getIntArray("stageDays") : EMPTY_INT_ARRAY;
            cycleFingerprint = tag.getInt("cycleFingerprint");
            currentStageId = tag.getString("currentStageId");
            immortal = tag.getBoolean("immortal");
            ageless = tag.getBoolean("ageless");
            isSenior = tag.getBoolean("isSenior");
            fertility = tag.getFloat("fertility");
            agingFrozenDay = tag.contains("agingFrozenDay") ? tag.getLong("agingFrozenDay") : Long.MIN_VALUE;
            carriedVariants.clear();
            if (tag.contains("carriedVariants")) {
                CompoundTag cv = tag.getCompound("carriedVariants");
                for (String k : cv.getAllKeys()) carriedVariants.put(k, cv.getString(k));
            }
            expressedAlleles.clear();
            if (tag.contains("expressedAlleles")) {
                ListTag list = tag.getList("expressedAlleles", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) expressedAlleles.add(list.getString(i));
            }
            genotype = tag.contains("genotype")
                    ? com.aetherianartificer.townstead.origin.gene.Genotype.fromTag(tag.getCompound("genotype"))
                    : new com.aetherianartificer.townstead.origin.gene.Genotype();
            heritage = tag.contains("heritage")
                    ? com.aetherianartificer.townstead.origin.Heritage.fromTag(tag.getCompound("heritage"))
                    : com.aetherianartificer.townstead.origin.Heritage.EMPTY;
            markDirty();
        }
    }

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    public final class ProfessionMemory implements ProfessionXpStore {
        private static final String LEGACY_COOK_TRADES_LEVEL = "townsteadCookTradesLevel";
        private static final String LEGACY_BARISTA_TRADES_LEVEL = "townsteadBaristaTradesLevel";
        private String lastProfession = "";
        private ButcherSettings.SlaughterOverride slaughterOverride = ButcherSettings.SlaughterOverride.FOLLOW_CONFIG;
        private final Map<String, Progress> progressByProfession = new HashMap<>();
        private final Map<String, Integer> tradeBackfillLevels = new HashMap<>();
        private final Map<String, Long> cooldowns = new HashMap<>();
        private int lastSeenShopTier = -1;
        private final Map<String, ProfessionXp> xpByProfession = new HashMap<>();
        private final Set<ResourceLocation> learnedSkills = new LinkedHashSet<>();
        private final Map<String, Integer> skillPoints = new HashMap<>();

        public String lastProfession() {
            return lastProfession;
        }

        public void setLastProfession(String value) {
            lastProfession = value == null ? "" : value;
            markDirty();
        }

        public ButcherSettings.SlaughterOverride slaughterOverride() {
            return slaughterOverride;
        }

        public void setSlaughterOverride(ButcherSettings.SlaughterOverride value) {
            slaughterOverride = value == null ? ButcherSettings.SlaughterOverride.FOLLOW_CONFIG : value;
            markDirty();
        }

        public Progress progress(String professionId) {
            return progressByProfession.get(professionId);
        }

        public void putProgress(String professionId, int level, int xp) {
            if (professionId == null || professionId.isBlank()) return;
            progressByProfession.put(professionId, new Progress(Math.max(1, level), Math.max(0, xp)));
            markDirty();
        }

        public int tradeBackfillLevel(String key) {
            if (key == null || key.isBlank()) return 0;
            return Math.max(0, tradeBackfillLevels.getOrDefault(key, 0));
        }

        /**
         * Last gameTime a named per-villager throttle fired (complaint cooldowns,
         * the slaughter work throttle, etc.). Returns 0 if never recorded.
         */
        public long cooldown(String key) {
            if (key == null) return 0L;
            return cooldowns.getOrDefault(key, 0L);
        }

        public void setCooldown(String key, long gameTime) {
            if (key == null || key.isBlank()) return;
            cooldowns.put(key, gameTime);
            markDirty();
        }

        public int lastSeenShopTier() {
            return lastSeenShopTier;
        }

        public void setLastSeenShopTier(int tier) {
            lastSeenShopTier = tier;
            markDirty();
        }

        public ProfessionXp professionXp(String professionId) {
            if (professionId == null) return ProfessionXp.EMPTY;
            return xpByProfession.getOrDefault(professionId, ProfessionXp.EMPTY);
        }

        public void setProfessionXp(String professionId, ProfessionXp value) {
            if (professionId == null || professionId.isBlank()) return;
            xpByProfession.put(professionId, value == null ? ProfessionXp.EMPTY : value);
            markDirty();
        }

        /** Durable learned-skill set; the source of truth professions grant capabilities from. */
        public Set<ResourceLocation> learnedSkills() {
            return Collections.unmodifiableSet(learnedSkills);
        }

        public boolean hasSkill(ResourceLocation skillId) {
            return skillId != null && learnedSkills.contains(skillId);
        }

        public boolean addSkill(ResourceLocation skillId) {
            if (skillId == null || !learnedSkills.add(skillId)) return false;
            markDirty();
            return true;
        }

        public boolean removeSkill(ResourceLocation skillId) {
            if (skillId == null || !learnedSkills.remove(skillId)) return false;
            markDirty();
            return true;
        }

        /** Unspent skill points for a profession (POINTS/HYBRID unlock models). */
        public int skillPoints(String professionId) {
            if (professionId == null) return 0;
            return Math.max(0, skillPoints.getOrDefault(professionId, 0));
        }

        public void setSkillPoints(String professionId, int points) {
            if (professionId == null || professionId.isBlank()) return;
            int clamped = Math.max(0, points);
            if (clamped == 0) {
                skillPoints.remove(professionId);
            } else {
                skillPoints.put(professionId, clamped);
            }
            markDirty();
        }

        public void addSkillPoints(String professionId, int delta) {
            if (professionId == null || professionId.isBlank() || delta == 0) return;
            setSkillPoints(professionId, skillPoints(professionId) + delta);
        }

        public void setTradeBackfillLevel(String key, int level) {
            if (key == null || key.isBlank()) return;
            int clamped = Math.max(0, level);
            if (clamped == 0) {
                tradeBackfillLevels.remove(key);
            } else {
                tradeBackfillLevels.put(key, clamped);
            }
            markDirty();
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("lastProfession", lastProfession);
            if (slaughterOverride != ButcherSettings.SlaughterOverride.FOLLOW_CONFIG) {
                tag.putByte("slaughterOverride", slaughterOverride.code);
            }
            CompoundTag all = new CompoundTag();
            for (Map.Entry<String, Progress> entry : progressByProfession.entrySet()) {
                CompoundTag progress = new CompoundTag();
                progress.putInt("level", entry.getValue().level());
                progress.putInt("xp", entry.getValue().xp());
                all.put(entry.getKey(), progress);
            }
            tag.put("progress", all);
            CompoundTag backfill = new CompoundTag();
            for (Map.Entry<String, Integer> entry : tradeBackfillLevels.entrySet()) {
                int level = Math.max(0, entry.getValue());
                if (level > 0) backfill.putInt(entry.getKey(), level);
            }
            tag.put("tradeBackfillLevels", backfill);
            CompoundTag cooldownTag = new CompoundTag();
            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                cooldownTag.putLong(entry.getKey(), entry.getValue());
            }
            tag.put("cooldowns", cooldownTag);
            if (lastSeenShopTier >= 0) tag.putInt("lastSeenShopTier", lastSeenShopTier);
            CompoundTag xpAll = new CompoundTag();
            for (Map.Entry<String, ProfessionXp> entry : xpByProfession.entrySet()) {
                ProfessionXp value = entry.getValue();
                if (value == null || value.isEmpty()) continue;
                CompoundTag xp = new CompoundTag();
                xp.putInt("xp", value.xp());
                xp.putInt("tier", value.tier());
                xp.putLong("lastTierUp", value.lastTierUpTick());
                xp.putLong("xpDay", value.xpDay());
                xp.putInt("xpToday", value.xpToday());
                xpAll.put(entry.getKey(), xp);
            }
            tag.put("professionXp", xpAll);
            if (!learnedSkills.isEmpty()) {
                ListTag skills = new ListTag();
                for (ResourceLocation id : learnedSkills) skills.add(StringTag.valueOf(id.toString()));
                tag.put("learnedSkills", skills);
            }
            CompoundTag points = new CompoundTag();
            for (Map.Entry<String, Integer> entry : skillPoints.entrySet()) {
                int value = Math.max(0, entry.getValue());
                if (value > 0) points.putInt(entry.getKey(), value);
            }
            if (!points.isEmpty()) tag.put("skillPoints", points);
            return tag;
        }

        private void load(CompoundTag tag) {
            lastProfession = tag.getString("lastProfession");
            slaughterOverride = tag.contains("slaughterOverride")
                    ? ButcherSettings.SlaughterOverride.fromCode(tag.getByte("slaughterOverride"))
                    : ButcherSettings.SlaughterOverride.FOLLOW_CONFIG;
            progressByProfession.clear();
            tradeBackfillLevels.clear();
            CompoundTag all = tag.getCompound("progress");
            for (String key : all.getAllKeys()) {
                CompoundTag progress = all.getCompound(key);
                progressByProfession.put(key, new Progress(
                        Math.max(1, progress.getInt("level")),
                        Math.max(0, progress.getInt("xp"))));
            }
            CompoundTag backfill = tag.getCompound("tradeBackfillLevels");
            for (String key : backfill.getAllKeys()) {
                int level = Math.max(0, backfill.getInt(key));
                if (level > 0) tradeBackfillLevels.put(key, level);
            }
            cooldowns.clear();
            CompoundTag cooldownTag = tag.getCompound("cooldowns");
            for (String key : cooldownTag.getAllKeys()) {
                cooldowns.put(key, cooldownTag.getLong(key));
            }
            lastSeenShopTier = tag.contains("lastSeenShopTier") ? tag.getInt("lastSeenShopTier") : -1;
            xpByProfession.clear();
            CompoundTag xpAll = tag.getCompound("professionXp");
            for (String key : xpAll.getAllKeys()) {
                CompoundTag xp = xpAll.getCompound(key);
                xpByProfession.put(key, new ProfessionXp(
                        xp.getInt("xp"),
                        xp.getInt("tier"),
                        xp.getLong("lastTierUp"),
                        xp.getLong("xpDay"),
                        xp.getInt("xpToday")));
            }
            learnedSkills.clear();
            ListTag skills = tag.getList("learnedSkills", Tag.TAG_STRING);
            for (int i = 0; i < skills.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(skills.getString(i));
                if (id != null) learnedSkills.add(id);
            }
            skillPoints.clear();
            CompoundTag points = tag.getCompound("skillPoints");
            for (String key : points.getAllKeys()) {
                int value = Math.max(0, points.getInt(key));
                if (value > 0) skillPoints.put(key, value);
            }
            markDirty();
        }

        private void loadLegacyHunger(CompoundTag hunger) {
            lastProfession = hunger.getString("townsteadLastProfession");
            slaughterOverride = ButcherSettings.getSlaughterOverride(hunger);
            progressByProfession.clear();
            tradeBackfillLevels.clear();
            CompoundTag all = hunger.getCompound("townsteadProfessionProgress");
            for (String key : all.getAllKeys()) {
                CompoundTag progress = all.getCompound(key);
                progressByProfession.put(key, new Progress(
                        Math.max(1, progress.getInt("level")),
                        Math.max(0, progress.getInt("xp"))));
            }
            int cookLevel = Math.max(0, hunger.getInt(LEGACY_COOK_TRADES_LEVEL));
            if (cookLevel > 0) tradeBackfillLevels.put("cook", cookLevel);
            int baristaLevel = Math.max(0, hunger.getInt(LEGACY_BARISTA_TRADES_LEVEL));
            if (baristaLevel > 0) tradeBackfillLevels.put("barista", baristaLevel);
            // Complaint throttles, the slaughter work throttle, and last-seen shop
            // tier were all piggybacked in townstead_hunger.
            cooldowns.clear();
            long leatherworkerComplaint = hunger.getLong("townstead_lastLeatherworkerComplaint");
            if (leatherworkerComplaint != 0L) cooldowns.put("townstead_lastLeatherworkerComplaint", leatherworkerComplaint);
            long butcheryComplaint = hunger.getLong("townstead_lastButcheryComplaint");
            if (butcheryComplaint != 0L) cooldowns.put("townstead_lastButcheryComplaint", butcheryComplaint);
            long slaughterTick = hunger.getLong("townstead_lastSlaughterTick");
            if (slaughterTick != 0L) cooldowns.put("townstead_lastSlaughterTick", slaughterTick);
            lastSeenShopTier = hunger.contains("townstead_lastSeenShopTier") ? hunger.getInt("townstead_lastSeenShopTier") : -1;
            // Per-profession XP was piggybacked in townstead_hunger as flat <id>Xp/<id>Tier/... keys.
            xpByProfession.clear();
            importLegacyProfessionXp(hunger, "farmer");
            importLegacyProfessionXp(hunger, "butcher");
            importLegacyProfessionXp(hunger, "cook");
            importLegacyProfessionXp(hunger, "shepherd");
        }

        private void mergeLegacyHunger(CompoundTag hunger) {
            if (slaughterOverride == ButcherSettings.SlaughterOverride.FOLLOW_CONFIG) {
                slaughterOverride = ButcherSettings.getSlaughterOverride(hunger);
            }
            if (lastProfession.isEmpty()) {
                lastProfession = hunger.getString("townsteadLastProfession");
            }
            if (tradeBackfillLevel("cook") == 0) {
                int cookLevel = Math.max(0, hunger.getInt(LEGACY_COOK_TRADES_LEVEL));
                if (cookLevel > 0) tradeBackfillLevels.put("cook", cookLevel);
            }
            if (tradeBackfillLevel("barista") == 0) {
                int baristaLevel = Math.max(0, hunger.getInt(LEGACY_BARISTA_TRADES_LEVEL));
                if (baristaLevel > 0) tradeBackfillLevels.put("barista", baristaLevel);
            }
            mergeLegacyCooldown(hunger, "townstead_lastLeatherworkerComplaint");
            mergeLegacyCooldown(hunger, "townstead_lastButcheryComplaint");
            mergeLegacyCooldown(hunger, "townstead_lastSlaughterTick");
            if (lastSeenShopTier < 0 && hunger.contains("townstead_lastSeenShopTier")) {
                lastSeenShopTier = hunger.getInt("townstead_lastSeenShopTier");
            }
            mergeLegacyProfessionXp(hunger, "farmer");
            mergeLegacyProfessionXp(hunger, "butcher");
            mergeLegacyProfessionXp(hunger, "cook");
            mergeLegacyProfessionXp(hunger, "shepherd");
            markDirty();
        }

        private void mergeLegacyCooldown(CompoundTag hunger, String key) {
            if (cooldowns.containsKey(key)) return;
            long value = hunger.getLong(key);
            if (value != 0L) cooldowns.put(key, value);
        }

        private void mergeLegacyProfessionXp(CompoundTag hunger, String id) {
            if (!professionXp(id).isEmpty()) return;
            importLegacyProfessionXp(hunger, id);
        }

        private void importLegacyProfessionXp(CompoundTag hunger, String id) {
            if (!(hunger.contains(id + "Xp") || hunger.contains(id + "Tier")
                    || hunger.contains(id + "XpDay") || hunger.contains(id + "XpToday")
                    || hunger.contains(id + "LastTierUpTick"))) {
                return;
            }
            xpByProfession.put(id, new ProfessionXp(
                    Math.max(0, hunger.getInt(id + "Xp")),
                    hunger.getInt(id + "Tier"),
                    hunger.getLong(id + "LastTierUpTick"),
                    hunger.getLong(id + "XpDay"),
                    Math.max(0, hunger.getInt(id + "XpToday"))));
        }

        public record Progress(int level, int xp) {}
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
