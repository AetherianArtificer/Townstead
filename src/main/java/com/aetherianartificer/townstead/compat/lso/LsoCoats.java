package com.aetherianartificer.townstead.compat.lso;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.work.order.ModifiedProducts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Legendary Survival Overhaul's coats, read and written through the mod's own API by reflection:
 * a coat is a tag on an armor piece, and a coat item names which. Registers itself as a
 * {@link ModifiedProducts.Modifier} so a coated chestplate is its own product.
 */
public final class LsoCoats {

    public static final String MOD_ID = "legendarysurvivaloverhaul";

    private static boolean initialised;
    private static Method getCoatTag;
    private static Method setCoatTag;
    private static Method removeCoatTag;
    private static final Map<String, ResourceLocation> COAT_ITEMS = new HashMap<>();
    private static boolean coatItemsScanned;

    private LsoCoats() {}

    public static void bootstrap() {
        ModifiedProducts.register(LsoCoats::coatItemOf);
    }

    public static boolean present() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /** The coat id a worn piece carries, or empty. */
    public static String coatTagOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        initIfNeeded();
        if (getCoatTag == null) return "";
        try {
            Object tag = getCoatTag.invoke(null, stack);
            return tag instanceof String s ? s : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    /** The coat item whose coat a piece carries, or null. */
    public static @Nullable ResourceLocation coatItemOf(@Nullable ItemStack stack) {
        String tag = coatTagOf(stack);
        if (tag.isEmpty()) return null;
        scanCoatItems();
        return COAT_ITEMS.get(tag);
    }

    /** The coat id a coat item applies, or null for anything that is not a coat. */
    public static @Nullable String coatIdOf(@Nullable Item item) {
        if (item == null) return null;
        try {
            Field field = item.getClass().getField("coat");
            Object coat = field.get(item);
            if (coat == null) return null;
            Object id = coat.getClass().getMethod("id").invoke(coat);
            return id instanceof String s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Sews a coat item's coat onto an armor piece. False when the mod or the coat is missing. */
    public static boolean apply(ItemStack armor, ItemStack coat) {
        if (armor == null || armor.isEmpty() || coat == null || coat.isEmpty()) return false;
        String coatId = coatIdOf(coat.getItem());
        if (coatId == null) return false;
        initIfNeeded();
        if (setCoatTag == null) return false;
        try {
            setCoatTag.invoke(null, armor, coatId);
            return coatId.equals(coatTagOf(armor));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static boolean remove(ItemStack armor) {
        if (armor == null || armor.isEmpty()) return false;
        initIfNeeded();
        if (removeCoatTag == null) return false;
        try {
            removeCoatTag.invoke(null, armor);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        if (!present()) return;
        try {
            Class<?> util = Class.forName("sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil");
            getCoatTag = util.getMethod("getArmorCoatTag", ItemStack.class);
            setCoatTag = util.getMethod("setArmorCoatTag", ItemStack.class, String.class);
            removeCoatTag = util.getMethod("removeArmorCoatTag", ItemStack.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            getCoatTag = null;
            setCoatTag = null;
            removeCoatTag = null;
        }
    }

    /** Coat items are found by what they declare, not by a list: every item with a coat field. */
    private static synchronized void scanCoatItems() {
        if (coatItemsScanned) return;
        coatItemsScanned = true;
        if (!present()) return;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !MOD_ID.equals(id.getNamespace())) continue;
            String coatId = coatIdOf(item);
            if (coatId != null) COAT_ITEMS.putIfAbsent(coatId, id);
        }
    }
}
