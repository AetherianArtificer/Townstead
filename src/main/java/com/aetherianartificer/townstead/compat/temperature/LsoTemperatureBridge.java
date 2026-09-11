package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.LSOBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.aetherianartificer.townstead.temperature.ThermalProtection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Legendary Survival Overhaul's world temperature. Its 0..40 scale is already degree-shaped with
 * NORMAL at 20, so the value passes through unchanged. The world query lives in {@link LSOBridge},
 * which the thirst bridge already resolved; item temperatures come from the mod's item data
 * manager, whose {@code temperature} field is a flat offset on the same scale.
 */
public final class LsoTemperatureBridge implements AmbientTemperatureBridge {
    public static final LsoTemperatureBridge INSTANCE = new LsoTemperatureBridge();

    private boolean itemsInitialized;
    private Method getItem;
    private Field temperatureField;
    private Field coldResistanceField;
    private Field heatResistanceField;
    private Field thermalResistanceField;
    private Method getBlock;
    private Method matchesState;
    private Field blockTemperatureField;
    private Object coatModifier;
    private Method coatAttributes;

    private LsoTemperatureBridge() {}

    @Override
    public String id() {
        return "legendary_survival_overhaul";
    }

    @Override
    public boolean isActive() {
        return ModCompat.isLoaded("legendarysurvivaloverhaul") && LSOBridge.INSTANCE.canReadWorldTemperature();
    }

    @Override
    public float ambientCelsius(ServerLevel level, BlockPos pos) {
        return LSOBridge.INSTANCE.worldTemperature(level, pos);
    }

    @Override
    public ThermalProtection itemProtection(ItemStack stack) {
        initItemsIfNeeded();
        if (getItem == null || temperatureField == null || stack == null || stack.isEmpty()) return null;
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            Object data = getItem.invoke(null, id);
            ThermalProtection base = data == null ? ThermalProtection.NONE : protection(data);
            ThermalProtection coat = ThermalProtection.NONE;
            try {
                if (coatAttributes != null) coat = protection(coatAttributes.invoke(coatModifier, stack));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A missing coat API must not discard the ordinary garment's protection.
            }
            return combineProtection(data != null, base, coat);
        } catch (Exception e) {
            return null;
        }
    }

    static ThermalProtection combineProtection(boolean knownItem, ThermalProtection base, ThermalProtection coat) {
        // Unknown, unlined equipment must still reach other backends and Townstead tags.
        return knownItem || !coat.equals(ThermalProtection.NONE) ? base.plus(coat) : null;
    }

    private ThermalProtection protection(Object data) throws IllegalAccessException {
        if (data == null) return ThermalProtection.NONE;
        return new ThermalProtection(temperatureField.getFloat(data)
                * com.aetherianartificer.townstead.temperature.TemperatureData.AMBIENT_PULL_PER_DEGREE,
                coldResistanceField.getFloat(data), heatResistanceField.getFloat(data),
                thermalResistanceField.getFloat(data));
    }

    /** LSO uses the first matching state entry, in data order. */
    @Override
    public float blockTemperatureCelsius(Level level, BlockPos pos, BlockState state) {
        initItemsIfNeeded();
        if (getBlock == null || blockTemperatureField == null) return Float.NaN;
        try {
            Object list = getBlock.invoke(null, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            if (!(list instanceof java.util.List<?> entries) || entries.isEmpty()) return Float.NaN;
            for (Object entry : entries) {
                if (entry != null && Boolean.TRUE.equals(matchesState.invoke(entry, state))) {
                    return blockTemperatureField.getFloat(entry);
                }
            }
            return 0f;
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    private synchronized void initItemsIfNeeded() {
        if (itemsInitialized) return;
        itemsInitialized = true;
        if (!ModCompat.isLoaded("legendarysurvivaloverhaul")) return;
        try {
            Class<?> manager = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.manager.TemperatureDataManager");
            getItem = manager.getMethod("getItem", ResourceLocation.class);
            Class<?> resistance = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.json.JsonTemperatureResistance");
            temperatureField = resistance.getField("temperature");
            coldResistanceField = resistance.getField("coldResistance");
            heatResistanceField = resistance.getField("heatResistance");
            thermalResistanceField = resistance.getField("thermalResistance");
            getBlock = manager.getMethod("getBlock", ResourceLocation.class);
            Class<?> blockData = Class.forName("sfiomn.legendarysurvivaloverhaul.api.data.json.JsonTemperatureBlock");
            blockTemperatureField = blockData.getField("temperature");
            matchesState = blockData.getMethod("matchesState", BlockState.class);
        } catch (Exception e) {
            getItem = null;
            temperatureField = null;
        }
        try {
            Class<?> registry = Class.forName("sfiomn.legendarysurvivaloverhaul.registry.TemperatureModifierRegistry");
            coatModifier = ((java.util.function.Supplier<?>) registry.getField("COAT_ATTRIBUTE").get(null)).get();
            coatAttributes = coatModifier.getClass().getMethod("getItemAttributes", ItemStack.class);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            coatAttributes = null;
        }
    }
}
