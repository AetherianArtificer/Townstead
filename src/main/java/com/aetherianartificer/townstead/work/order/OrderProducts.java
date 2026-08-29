package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.work.recipe.PotionBrewingRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Stable identities for orderable products whose meaningful state is carried by the stack.
 *
 * <p>Ordinary products keep their item id. Potion products use a synthetic id containing both
 * the bottle form and registered potion id, so Healing, Strength and every mod-registered mix
 * remain separate lines even though all three are {@code minecraft:potion} items. Pizza Delight's
 * assembled raw pizza similarly stays distinct from its blank base even though both use the same
 * item id. The encoding is deliberately readable and registry-derived; no numeric ids or
 * implementation NBT enter saves.</p>
 */
public final class OrderProducts {
    private static final String NAMESPACE = "townstead_product";
    private static final String POTION_PREFIX = "potion/";
    private static final String ASSEMBLED_PIZZA_PATH = "pizza/assembled_raw";
    private static final String PIZZA_STATION_RECIPE =
            "townstead:protocol/townstead/pizza_station/0";

    private OrderProducts() {}

    /** Product identity for a recipe. Ordinary recipes remain keyed by their output item. */
    public static ResourceLocation key(ProducerRecipe recipe) {
        if (recipe == null) return air();
        if (recipe.id() != null && PIZZA_STATION_RECIPE.equals(recipe.id().toString())) {
            return assembledPizzaKey();
        }
        ResourceLocation potion = PotionBrewingRecipes.productKey(recipe.id());
        return potion == null ? recipe.output() : potion;
    }

    /** Product identity for a real stack. */
    public static ResourceLocation key(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return air();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (com.aetherianartificer.townstead.compat.pizzadelight.PizzaDelightCompat
                .isAssembledPizza(stack)) {
            return assembledPizzaKey();
        }
        ResourceLocation potionId = potionId(stack);
        return potionId == null ? itemId : potionKey(itemId, potionId);
    }

    public static boolean matches(ResourceLocation product, ItemStack stack) {
        return product != null && product.equals(key(stack));
    }

    /** True when the product is narrower than its physical item id. */
    public static boolean exact(ResourceLocation product, ResourceLocation item) {
        return product != null && item != null && !product.equals(item);
    }

    /** Representative stack used by the Order Sheet for its icon and vanilla hover name. */
    public static ItemStack displayStack(@Nullable ResourceLocation product,
                                         @Nullable ResourceLocation fallbackItem) {
        PotionParts potion = decodePotion(product);
        if (potion != null) {
            Item form = BuiltInRegistries.ITEM.get(potion.form());
            net.minecraft.world.item.alchemy.Potion value =
                    BuiltInRegistries.POTION.get(potion.potion());
            if (form != null && form != Items.AIR && value != null) {
                //? if >=1.21 {
                return net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        form, BuiltInRegistries.POTION.wrapAsHolder(value));
                //?} else {
                /*return net.minecraft.world.item.alchemy.PotionUtils.setPotion(
                        new ItemStack(form), value);
                *///?}
            }
        }
        Item fallback = fallbackItem == null ? Items.AIR : BuiltInRegistries.ITEM.get(fallbackItem);
        return fallback == null ? ItemStack.EMPTY : new ItemStack(fallback);
    }

    public static String label(ResourceLocation product, ResourceLocation fallbackItem) {
        if (assembledPizzaKey().equals(product)) return "Prepared Pizza";
        ItemStack stack = displayStack(product, fallbackItem);
        return stack.isEmpty() ? fallbackItem.toString() : stack.getHoverName().getString();
    }

    public static ResourceLocation potionKey(ResourceLocation form, ResourceLocation potion) {
        return id(NAMESPACE, POTION_PREFIX
                + form.getNamespace() + "/" + form.getPath() + "/"
                + potion.getNamespace() + "/" + potion.getPath());
    }

    public static ResourceLocation assembledPizzaKey() {
        return id(NAMESPACE, ASSEMBLED_PIZZA_PATH);
    }

    /** Physical item used to draw a synthetic exact-product identity. */
    public static @Nullable ResourceLocation fallbackItem(@Nullable ResourceLocation product) {
        PotionParts potion = decodePotion(product);
        if (potion != null) return potion.form();
        if (assembledPizzaKey().equals(product)) {
            return ResourceLocation.tryParse("pizzadelight:raw_pizza");
        }
        return null;
    }

    @Nullable
    public static PotionParts decodePotion(@Nullable ResourceLocation key) {
        if (key == null || !NAMESPACE.equals(key.getNamespace())
                || !key.getPath().startsWith(POTION_PREFIX)) return null;
        String[] parts = key.getPath().substring(POTION_PREFIX.length()).split("/", 4);
        if (parts.length != 4) return null;
        ResourceLocation form = id(parts[0], parts[1]);
        ResourceLocation potion = id(parts[2], parts[3]);
        return new PotionParts(form, potion);
    }

    @Nullable
    private static ResourceLocation potionId(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)
                && !stack.is(Items.LINGERING_POTION)) return null;
        //? if >=1.21 {
        net.minecraft.world.item.alchemy.PotionContents contents =
                stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return null;
        return contents.potion().get().unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElseGet(() -> BuiltInRegistries.POTION.getKey(contents.potion().get().value()));
        //?} else {
        /*net.minecraft.world.item.alchemy.Potion potion =
                net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
        return potion == null ? null : BuiltInRegistries.POTION.getKey(potion);
        *///?}
    }

    private static ResourceLocation air() {
        return BuiltInRegistries.ITEM.getKey(Items.AIR);
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    public record PotionParts(ResourceLocation form, ResourceLocation potion) {}
}
