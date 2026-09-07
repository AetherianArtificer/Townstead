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
            if (data == null) return null;
            // LSO offsets/resistances act on ambient; Townstead's flat offset acts on body.
            return new ThermalProtection(temperatureField.getFloat(data)
                    * com.aetherianartificer.townstead.temperature.TemperatureData.AMBIENT_PULL_PER_DEGREE,
                    coldResistanceField.getFloat(data), heatResistanceField.getFloat(data),
                    thermalResistanceField.getFloat(data));
        } catch (Exception e) {
            return null;
        }
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
    }
}
