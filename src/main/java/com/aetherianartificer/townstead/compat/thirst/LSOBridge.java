package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LSOBridge implements ThirstCompatBridge {
    public static final LSOBridge INSTANCE = new LSOBridge();

    private static final ResourceLocation LSO_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("legendarysurvivaloverhaul", "textures/gui/overlay.png");

    private static final float LSO_NORMAL_TEMP = 20.0f;

    private boolean initialized;
    private boolean active;

    // ThirstDataManager
    private Method getConsumableMethod;
    // Fields on JsonThirstConsumable
    private Field hydrationField;
    private Field saturationField;
    // ThirstUtil
    private Method getHydrationEnumTagMethod;
    private Method setHydrationEnumTagMethod;
    // Config.Baked
    private Field thirstEnabledField;
    // TemperatureUtil
    private Method getWorldTemperatureMethod;
    // HydrationEnum constants
    private Object hydrationPurified;
    private Object hydrationPotion;
    private Object hydrationRain;
    private Object hydrationNormal;

    private LSOBridge() {}

    @Override
    public boolean isActive() {
        initIfNeeded();
        return active;
    }

    @Override
    public boolean itemRestoresThirst(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return getConsumable(stack) != null;
    }

    @Override
    public boolean isDrink(ItemStack stack) {
        return itemRestoresThirst(stack);
    }

    @Override
    public boolean isPurityWaterContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        initIfNeeded();
        if (!active) return false;
        // Canteens: tracked via HydrationEnum tag
        if (getHydrationEnumTagMethod != null) {
            try {
                if (getHydrationEnumTagMethod.invoke(null, stack) != null) return true;
            } catch (Exception ignored) {}
        }
        // Vanilla water bottles: LSO treats these as impure drinkables (no HydrationEnum tag)
        return isWaterPotion(stack);
    }

    @Override
    public int hydration(ItemStack stack) {
        Object consumable = getConsumable(stack);
        if (consumable == null) return 0;
        try {
            return hydrationField.getInt(consumable);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int quenched(ItemStack stack) {
        Object consumable = getConsumable(stack);
        if (consumable == null) return 0;
        try {
            return (int) saturationField.getFloat(consumable);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int purity(ItemStack stack) {
        initIfNeeded();
        if (!active) return 2;
        // Check HydrationEnum tag first (canteens)
        if (getHydrationEnumTagMethod != null) {
            try {
                Object enumTag = getHydrationEnumTagMethod.invoke(null, stack);
                if (enumTag != null) return mapHydrationEnum(enumTag);
            } catch (Exception ignored) {}
        }
        // Vanilla water bottles without HydrationEnum: always NORMAL (impure)
        if (isWaterPotion(stack)) return 1;
        return 2;
    }

    @Override
    public PurityResult evaluatePurity(int purity, RandomSource random) {
        // LSO handles effects via its data-driven consumable system — no sickness rolls
        return new PurityResult(true, false, false, purity);
    }

    @Override
    public float exhaustionBiomeModifier(Level level, BlockPos pos) {
        if (level == null || pos == null) return 1.0f;
        initIfNeeded();
        if (!active || getWorldTemperatureMethod == null) return 1.0f;

        if (level.dimensionType().ultraWarm()) return 3.0f;

        try {
            Object temp = getWorldTemperatureMethod.invoke(null, level, pos);
            if (temp instanceof Number n) {
                float t = n.floatValue();
                // LSO temperature: 0-40, NORMAL=20
                // Above normal: increase drain; below: decrease
                float offset = t - LSO_NORMAL_TEMP;
                if (offset > 0) {
                    // Hot: up to 2x drain at temp 40
                    return 1.0f + (offset / LSO_NORMAL_TEMP);
                } else {
                    // Cold: down to 0.5x drain at temp 0
                    return Math.max(0.5f, 1.0f + (offset / (LSO_NORMAL_TEMP * 2)));
                }
            }
        } catch (Exception ignored) {}
        return 1.0f;
    }

    @Override
    public boolean extraHydrationToQuenched() {
        return false;
    }

    @Override
    public ResourceLocation iconTexture() {
        return LSO_OVERLAY;
    }

    @Override
    public boolean supportsPurification() {
        return true;
    }

    @Override
    public void purifyResult(ItemStack input, ItemStack output) {
        if (input.isEmpty() || output.isEmpty()) return;
        initIfNeeded();
        if (!active) return;

        // For canteens: copy enchantments and capacity from input, set PURIFIED tag
        // For water bottles: recipe output is already correct, no-op
        ResourceLocation inputId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem());
        if (inputId == null) return;
        String path = inputId.getPath();
        if (!path.contains("canteen")) return;

        // Copy enchantments from input to output
        EnchantmentHelper.setEnchantments(output, EnchantmentHelper.getEnchantmentsForCrafting(input));

        // Copy NBT capacity data if present
        if (input.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.world.item.component.CustomData inputData = input.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (inputData != null) {
                output.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, inputData);
            }
        }

        // Set HydrationPurity tag to PURIFIED
        if (setHydrationEnumTagMethod != null && hydrationPurified != null) {
            try {
                setHydrationEnumTagMethod.invoke(null, output, hydrationPurified);
            } catch (Exception e) {
                Townstead.LOGGER.debug("Failed to set HydrationPurity tag on canteen output", e);
            }
        }
    }

    @Override
    public ThirstIconInfo iconInfo(int thirst) {
        // LSO overlay.png is 256x256. NONE effect row: container=U0, full=U9, half=U18, V=0
        int u;
        if (thirst > 13) u = 9;       // full droplet
        else if (thirst > 6) u = 18;  // half droplet
        else u = 0;                    // empty/container droplet
        return new ThirstIconInfo(LSO_OVERLAY, u, 0, 256, 256);
    }

    private static boolean isWaterPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    private Object getConsumable(ItemStack stack) {
        if (stack.isEmpty()) return null;
        initIfNeeded();
        if (!active || getConsumableMethod == null) return null;
        try {
            return getConsumableMethod.invoke(null, stack);
        } catch (Exception e) {
            return null;
        }
    }

    private int mapHydrationEnum(Object enumValue) {
        if (enumValue == null) return 2;
        if (enumValue.equals(hydrationPurified) || enumValue.equals(hydrationPotion)) return 3;
        if (enumValue.equals(hydrationRain)) return 2;
        if (enumValue.equals(hydrationNormal)) return 1;
        // Fallback: try name matching
        String name = enumValue.toString().toUpperCase();
        return switch (name) {
            case "PURIFIED", "POTION" -> 3;
            case "RAIN" -> 2;
            case "NORMAL" -> 1;
            default -> 2;
        };
    }

    private void initIfNeeded() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            active = false;
            return;
        }
        try {
            // ThirstDataManager.getConsumable(ItemStack)
            Class<?> thirstDataManager = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.manager.ThirstDataManager");
            getConsumableMethod = thirstDataManager.getMethod("getConsumable", ItemStack.class);

            // JsonThirstConsumable fields
            Class<?> consumableClass = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.json.JsonThirstConsumable");
            hydrationField = consumableClass.getField("hydration");
            saturationField = consumableClass.getField("saturation");

            // ThirstUtil
            Class<?> thirstUtil = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil");
            getHydrationEnumTagMethod = thirstUtil.getMethod("getHydrationEnumTag", ItemStack.class);
            try {
                setHydrationEnumTagMethod = thirstUtil.getMethod("setHydrationEnumTag", ItemStack.class,
                        Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum"));
            } catch (Exception ignored) {
                setHydrationEnumTagMethod = null;
            }

            // HydrationEnum constants
            try {
                Class<?> hydrationEnum = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum");
                hydrationPurified = Enum.valueOf(hydrationEnum.asSubclass(Enum.class), "PURIFIED");
                hydrationPotion = Enum.valueOf(hydrationEnum.asSubclass(Enum.class), "POTION");
                hydrationRain = Enum.valueOf(hydrationEnum.asSubclass(Enum.class), "RAIN");
                hydrationNormal = Enum.valueOf(hydrationEnum.asSubclass(Enum.class), "NORMAL");
            } catch (Exception ignored) {
                // Enum constants not critical — fallback to name matching
            }

            // Config.Baked.thirstEnabled
            try {
                Class<?> configBaked = Class.forName("sfiomn.legendarysurvivaloverhaul.config.Config$Baked");
                thirstEnabledField = configBaked.getField("thirstEnabled");
            } catch (Exception ignored) {
                thirstEnabledField = null;
            }

            // TemperatureUtil.getWorldTemperature(Level, BlockPos)
            try {
                Class<?> tempUtil = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil");
                getWorldTemperatureMethod = tempUtil.getMethod("getWorldTemperature", Level.class, BlockPos.class);
            } catch (Exception ignored) {
                getWorldTemperatureMethod = null;
            }

            // Check if thirst is enabled in LSO config
            if (thirstEnabledField != null) {
                try {
                    if (!thirstEnabledField.getBoolean(null)) {
                        active = false;
                        Townstead.LOGGER.info("Legendary Survival Overhaul detected but thirst is disabled in its config.");
                        return;
                    }
                } catch (Exception ignored) {
                    // Can't read config — assume enabled
                }
            }

            active = true;
            Townstead.LOGGER.info("Legendary Survival Overhaul thirst compatibility enabled.");
        } catch (Exception e) {
            active = false;
            Townstead.LOGGER.warn("Failed to initialize Legendary Survival Overhaul thirst compatibility.", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Enum<T>> T enumValueOf(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<T>) enumClass, name);
    }
}
