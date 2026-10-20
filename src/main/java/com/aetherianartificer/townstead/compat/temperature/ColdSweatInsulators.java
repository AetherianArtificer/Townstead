package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads Cold Sweat's insulator registries for a worn stack, so garments any mod registers with
 * Cold Sweat warm villagers the way they warm players.
 *
 * <p>A stack counts the way Cold Sweat counts it on a player: its worn-armor and Curios entries,
 * plus every lining sewn into it, which Cold Sweat reads from the ingredient registry.</p>
 *
 * <p>Cold Sweat counts insulation in its own units: a wool sweater is 1.5 cold, a cotton shirt
 * is 1 cold and 1 heat, a goat fur parka is 14 cold. {@link #RESISTANCE_PER_UNIT} maps a unit onto
 * the resistance scale {@link ThermalProtection} shares with LSO, where the same sweater reads 0.5.</p>
 */
public final class ColdSweatInsulators {

    public static final float RESISTANCE_PER_UNIT = 1f / 3f;

    private static boolean initialised;
    private static Method holderGet;
    private static Object armorInsulators;
    private static @Nullable Object curioInsulators;
    private static Object liningInsulators;
    private static @Nullable Method insulationCapOf;
    private static Method insulationOf;
    private static Method getCold;
    private static Method getHeat;

    private ColdSweatInsulators() {}

    public static @Nullable ThermalProtection protection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        initIfNeeded();
        if (armorInsulators == null) return null;
        try {
            double[] units = new double[2];
            boolean found = add(armorInsulators, stack, units);
            found |= add(curioInsulators, stack, units);
            for (ItemStack lining : linings(stack)) found |= add(liningInsulators, lining, units);
            // Entries with only set-bonus attributes carry no units; let other sources answer.
            return found && (units[0] != 0 || units[1] != 0) ? fromUnits(units[0], units[1]) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static ThermalProtection fromUnits(double cold, double heat) {
        return new ThermalProtection(0, (float) (cold * RESISTANCE_PER_UNIT), (float) (heat * RESISTANCE_PER_UNIT), 0);
    }

    /** Adds the stack's cold and heat units from one registry; true when the registry knows the item. */
    private static boolean add(@Nullable Object holder, ItemStack stack, double[] units) throws ReflectiveOperationException {
        if (holder == null) return false;
        Object multimap = holderGet.invoke(holder);
        if (multimap == null) return false;
        Object insulators = multimap.getClass().getMethod("get", Object.class).invoke(multimap, stack.getItem());
        if (!(insulators instanceof Collection<?> collection) || collection.isEmpty()) return false;
        for (Object insulator : collection) {
            Object list = insulationOf.invoke(insulator);
            if (!(list instanceof List<?> insulations)) continue;
            for (Object insulation : insulations) {
                units[0] += ((Number) getCold.invoke(insulation)).doubleValue();
                units[1] += ((Number) getHeat.invoke(insulation)).doubleValue();
            }
        }
        return true;
    }

    /** Items sewn into the stack at a Sewing Table. Forge returns a LazyOptional, NeoForge an Optional. */
    private static List<ItemStack> linings(ItemStack stack) throws ReflectiveOperationException {
        if (insulationCapOf == null) return List.of();
        Object cap = insulationCapOf.invoke(null, stack);
        if (cap != null && !(cap instanceof Optional<?>)) cap = cap.getClass().getMethod("resolve").invoke(cap);
        if (!(cap instanceof Optional<?> optional) || optional.isEmpty()) return List.of();
        Object pairs = optional.get().getClass().getMethod("getInsulation").invoke(optional.get());
        if (!(pairs instanceof List<?> list) || list.isEmpty()) return List.of();
        return list.stream()
                .map(pair -> pair instanceof Pair<?, ?> p ? p.getFirst() : null)
                .filter(ItemStack.class::isInstance).map(ItemStack.class::cast)
                .filter(item -> !item.isEmpty()).toList();
    }

    private static synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        try {
            Class<?> settings = Class.forName("com.momosoftworks.coldsweat.config.ConfigSettings");
            Object armor = settings.getField("INSULATING_ARMORS").get(null);
            holderGet = armor.getClass().getMethod("get");
            liningInsulators = settings.getField("INSULATION_ITEMS").get(null);
            curioInsulators = optionalField(settings, "INSULATING_CURIOS");
            Class<?> insulatorData = Class.forName("com.momosoftworks.coldsweat.data.codec.configuration.InsulatorData");
            insulationOf = insulatorData.getMethod("insulation");
            Class<?> insulation = Class.forName("com.momosoftworks.coldsweat.api.insulation.Insulation");
            getCold = insulation.getMethod("getCold");
            getHeat = insulation.getMethod("getHeat");
            try {
                insulationCapOf = Class.forName("com.momosoftworks.coldsweat.common.capability.handler.ItemInsulationManager")
                        .getMethod("getInsulationCap", ItemStack.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                insulationCapOf = null;
            }
            armorInsulators = armor;
        } catch (ReflectiveOperationException | RuntimeException e) {
            armorInsulators = null;
        }
    }

    private static @Nullable Object optionalField(Class<?> owner, String name) {
        try {
            Field field = owner.getField(name);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
