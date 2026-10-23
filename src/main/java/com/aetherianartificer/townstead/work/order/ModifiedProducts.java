package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Identity for a workpiece something was sewn onto: an LSO coat on a chestplate, later a trim on
 * a helmet. The product names the modifier item and the base item, so a coated chestplate is a
 * different line from a plain one, and the recipe id a commission is worked under carries the
 * same two parts so the finished piece matches its line.
 *
 * <p>Which stacks are modified, and with what, is a provider's question: a compat package
 * registers a {@link Modifier} that reads its own mod's data off the stack.</p>
 */
public final class ModifiedProducts {

    /** Reads the modifier item a stack carries, or null when it carries none this provider knows. */
    public interface Modifier {
        @Nullable ResourceLocation appliedOf(ItemStack stack);
    }

    /** A decoded product: what was applied, and to which item. */
    public record Parts(ResourceLocation modifier, ResourceLocation item) {}

    private static final String NAMESPACE = "townstead_product";
    private static final String PRODUCT_PREFIX = "commission/";
    private static final String RECIPE_NAMESPACE = "townstead";
    private static final String RECIPE_PREFIX = "commission/";

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private ModifiedProducts() {}

    public static void register(Modifier modifier) {
        if (modifier != null && !MODIFIERS.contains(modifier)) MODIFIERS.add(modifier);
    }

    static void clear() {
        MODIFIERS.clear();
    }

    public static @Nullable ResourceLocation appliedOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Modifier modifier : MODIFIERS) {
            ResourceLocation applied = modifier.appliedOf(stack);
            if (applied != null) return applied;
        }
        return null;
    }

    /**
     * The product an offered modifying line carries before any piece is handed over: the key
     * with the modifier standing in for the piece, so it decodes and draws as the modifier.
     */
    public static ResourceLocation marker(ResourceLocation modifier) {
        return key(modifier, modifier);
    }

    /**
     * Whether an order's product is what a catalogue entry offers: the same id, or a commission
     * on any piece with the modifier the entry's marker names.
     */
    public static boolean sameOffer(@Nullable ResourceLocation offered, @Nullable ResourceLocation ordered) {
        if (offered == null || ordered == null) return false;
        if (offered.equals(ordered)) return true;
        Parts offer = decode(offered);
        Parts order = decode(ordered);
        return offer != null && order != null && offer.modifier().equals(order.modifier());
    }

    /** Whether a product id is a commission key or marker, for a screen that has only the id. */
    public static boolean isCommission(@Nullable ResourceLocation product) {
        return product != null && NAMESPACE.equals(product.getNamespace())
                && product.getPath().startsWith(PRODUCT_PREFIX);
    }

    public static ResourceLocation key(ResourceLocation modifier, ResourceLocation item) {
        return DataPackLang.parseId(NAMESPACE + ":" + PRODUCT_PREFIX + four(modifier, item));
    }

    public static ResourceLocation recipeId(ResourceLocation modifier, ResourceLocation item) {
        return DataPackLang.parseId(RECIPE_NAMESPACE + ":" + RECIPE_PREFIX + four(modifier, item));
    }

    /** The product a commission recipe id stands for, or null for any other recipe. */
    public static @Nullable ResourceLocation keyForRecipe(@Nullable ResourceLocation recipeId) {
        Parts parts = decodePath(recipeId, RECIPE_NAMESPACE, RECIPE_PREFIX);
        return parts == null ? null : key(parts.modifier(), parts.item());
    }

    public static @Nullable Parts decode(@Nullable ResourceLocation product) {
        return decodePath(product, NAMESPACE, PRODUCT_PREFIX);
    }

    public static @Nullable Parts decodeRecipe(@Nullable ResourceLocation recipeId) {
        return decodePath(recipeId, RECIPE_NAMESPACE, RECIPE_PREFIX);
    }

    /** "Leather Chestplate with Heating Coat I", from the two items' own names. */
    public static String label(ResourceLocation modifier, ResourceLocation item) {
        return name(item) + " with " + name(modifier);
    }

    private static String name(ResourceLocation id) {
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return String.valueOf(id);
        return new ItemStack(item).getHoverName().getString();
    }

    private static String four(ResourceLocation modifier, ResourceLocation item) {
        return modifier.getNamespace() + "/" + modifier.getPath() + "/" + item.getNamespace() + "/" + item.getPath();
    }

    private static @Nullable Parts decodePath(@Nullable ResourceLocation id, String namespace, String prefix) {
        if (id == null || !namespace.equals(id.getNamespace()) || !id.getPath().startsWith(prefix)) return null;
        String[] parts = id.getPath().substring(prefix.length()).split("/", 4);
        if (parts.length != 4) return null;
        ResourceLocation modifier = DataPackLang.parseId(parts[0] + ":" + parts[1]);
        ResourceLocation item = DataPackLang.parseId(parts[2] + ":" + parts[3]);
        return modifier == null || item == null ? null : new Parts(modifier, item);
    }
}
