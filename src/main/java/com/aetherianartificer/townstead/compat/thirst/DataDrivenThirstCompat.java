package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.needs.Consumables;
import com.aetherianartificer.townstead.needs.NeedEffectProjection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects Townstead's data-defined hydration into the active thirst mod's own consumable lookup.
 * This makes player consumption, held-item previews, and native tooltip rendering agree with the
 * values villagers use. The input is already expanded from tags, so no client-side datapack guess
 * is involved.
 */
public final class DataDrivenThirstCompat {
    private record PreviousDrink(boolean present, Number[] value) {}

    private static final Map<Item, Number[]> TWT_INSTALLED = new HashMap<>();
    private static final Map<Item, PreviousDrink> TWT_PREVIOUS = new HashMap<>();
    private static volatile Map<ResourceLocation, Consumables.ResolvedEffect> resolved = Map.of();

    private static Object lsoDelegate;
    private static Object lsoProxy;
    private static Class<?> lsoManagerInterface;
    private static Constructor<?> lsoConsumableConstructor;
    private static volatile Map<ResourceLocation, LsoEntry> lsoConsumables = Map.of();

    private record LsoEntry(Object consumable, boolean fallback) {}

    private DataDrivenThirstCompat() {}

    /** Installs the server's freshly reloaded definitions. */
    public static synchronized void refresh() {
        installResolved(Consumables.resolvedEffects());
    }

    /** Installs a server-authoritative item-expanded view on either logical side. */
    public static synchronized void installResolved(Map<ResourceLocation, Consumables.ResolvedEffect> effects) {
        resolved = effects == null ? Map.of() : Map.copyOf(effects);
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) {
            installLso();
        } else if (ModCompat.isLoaded("thirst")) {
            installTwt();
        }
    }

    @SuppressWarnings("unchecked")
    private static void installTwt() {
        try {
            Class<?> helper = resolveThirstHelper();
            if (helper == null) return;
            Field field = helper.getField("VALID_DRINKS");
            if (!(field.get(null) instanceof Map<?, ?> raw)) return;
            Map<Item, Number[]> drinks = (Map<Item, Number[]>) raw;

            for (Map.Entry<Item, Number[]> previous : TWT_INSTALLED.entrySet()) {
                if (!same(drinks.get(previous.getKey()), previous.getValue())) continue;
                PreviousDrink original = TWT_PREVIOUS.get(previous.getKey());
                if (original != null && original.present()) drinks.put(previous.getKey(), original.value());
                else drinks.remove(previous.getKey());
            }
            TWT_INSTALLED.clear();
            TWT_PREVIOUS.clear();

            for (Map.Entry<ResourceLocation, Consumables.ResolvedEffect> entry : resolved.entrySet()) {
                NeedEffectProjection effect = entry.getValue().projection();
                if (!effect.hydrates() || !BuiltInRegistries.ITEM.containsKey(entry.getKey())) continue;
                Item item = BuiltInRegistries.ITEM.get(entry.getKey());
                if (item == Items.AIR) continue;
                Number[] original = drinks.get(item);
                if (entry.getValue().fallback() && original != null) continue;
                TWT_PREVIOUS.put(item, new PreviousDrink(drinks.containsKey(item), original));
                Number[] values = {effect.immediateHydration(), effect.lastingHydration()};
                drinks.put(item, values);
                TWT_INSTALLED.put(item, values);
            }
            logInstalled("Thirst", TWT_INSTALLED.size());
        } catch (Exception exception) {
            Townstead.LOGGER.warn("Failed to install data-defined drinks into the thirst mod.", exception);
        }
    }

    /**
     * LSO has no public mutation API for its active consumable manager. Wrap that public manager
     * interface instead: configured items answer with normal JsonThirstConsumable instances and
     * every other lookup delegates untouched. Consequently LSO's own tooltip and preview code work.
     */
    private static void installLso() {
        try {
            Class<?> managerClass = Class.forName(
                    "sfiomn.legendarysurvivaloverhaul.api.data.manager.ThirstDataManager");
            Field managerField = managerClass.getField("internalConsumable");
            Object current = managerField.get(null);
            if (current == null) return;

            if (lsoManagerInterface == null) {
                lsoManagerInterface = Class.forName(
                        "sfiomn.legendarysurvivaloverhaul.api.data.manager.IThirstConsumableManager");
                Class<?> consumableClass = Class.forName(
                        "sfiomn.legendarysurvivaloverhaul.api.data.json.JsonThirstConsumable");
                lsoConsumableConstructor = consumableClass.getConstructor(
                        int.class, float.class, List.class, Map.class);
            }

            Map<ResourceLocation, LsoEntry> configured = new HashMap<>();
            for (Map.Entry<ResourceLocation, Consumables.ResolvedEffect> entry : resolved.entrySet()) {
                NeedEffectProjection effect = entry.getValue().projection();
                if (!effect.hydrates()) continue;
                configured.put(entry.getKey(), new LsoEntry(lsoConsumableConstructor.newInstance(
                        effect.immediateHydration(), (float) effect.lastingHydration(), List.of(), Map.of()),
                        entry.getValue().fallback()));
            }
            lsoConsumables = Map.copyOf(configured);

            if (current == lsoProxy) {
                logInstalled("Legendary Survival Overhaul", configured.size());
                return;
            }
            lsoDelegate = current;
            lsoProxy = Proxy.newProxyInstance(lsoManagerInterface.getClassLoader(),
                    new Class<?>[]{lsoManagerInterface}, (proxy, method, args) -> invokeLso(method, args));
            managerField.set(null, lsoProxy);
            logInstalled("Legendary Survival Overhaul", configured.size());
        } catch (Exception exception) {
            Townstead.LOGGER.warn("Failed to install data-defined drinks into Legendary Survival Overhaul.", exception);
        }
    }

    private static Object invokeLso(Method method, Object[] args) throws Throwable {
        if ("get".equals(method.getName()) && args != null && args.length == 1) {
            ResourceLocation itemId = null;
            if (args[0] instanceof ResourceLocation id) itemId = id;
            else if (args[0] instanceof ItemStack stack && !stack.isEmpty()) {
                itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            }
            LsoEntry configured = itemId == null ? null : lsoConsumables.get(itemId);
            if (configured != null) {
                if (configured.fallback()) {
                    Object nativeValue = invokeLsoDelegate(method, args);
                    if (hasLsoValue(nativeValue)) return nativeValue;
                }
                return args[0] instanceof ResourceLocation
                        ? List.of(configured.consumable()) : configured.consumable();
            }
        }
        return invokeLsoDelegate(method, args);
    }

    private static Object invokeLsoDelegate(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(lsoDelegate, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static boolean hasLsoValue(Object value) {
        if (value == null) return false;
        return !(value instanceof java.util.Collection<?> collection) || !collection.isEmpty();
    }

    private static void logInstalled(String backend, int count) {
        if (count > 0) Townstead.LOGGER.info("Exposed {} data-defined drink(s) through {}.", count, backend);
    }

    private static boolean same(Number[] left, Number[] right) {
        if (left == null || right == null || left.length != right.length) return false;
        for (int i = 0; i < left.length; i++) {
            if (Double.compare(left[i].doubleValue(), right[i].doubleValue()) != 0) return false;
        }
        return true;
    }

    private static Class<?> resolveThirstHelper() {
        for (String name : new String[]{"cn.mlus.thirst.api.ThirstHelper", "dev.ghen.thirst.api.ThirstHelper"}) {
            try { return Class.forName(name); }
            catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
