package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/** TAN's datapack drink vocabulary, with its player-only container handling for villagers. */
public final class ToughAsNailsThirstBridge implements ThirstCompatBridge {
    public static final ToughAsNailsThirstBridge INSTANCE = new ToughAsNailsThirstBridge();
    private ToughAsNailsThirstBridge() {}

    public static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("toughasnails", path);
        //?} else {
        /*return new ResourceLocation("toughasnails", path);
        *///?}
    }
    private static boolean tagged(ItemStack stack, String path) {
        return stack.is(TagKey.create(Registries.ITEM, id(path)));
    }
    @Override public boolean isActive() { return ModCompat.isLoaded("toughasnails"); }
    @Override public boolean isThirstEnabled() {
        try {
            return ThirstToggle.METHOD != null && Boolean.TRUE.equals(ThirstToggle.METHOD.invoke(null));
        } catch (ReflectiveOperationException exception) {
            // TAN's synced configuration may not have loaded yet.
            return false;
        }
    }

    private static final class ThirstToggle {
        private static final java.lang.reflect.Method METHOD = resolve();
        private static java.lang.reflect.Method resolve() {
            try {
                return Class.forName("toughasnails.api.thirst.ThirstHelper").getMethod("isThirstEnabled");
            } catch (ReflectiveOperationException exception) {
                com.aetherianartificer.townstead.Townstead.LOGGER.warn("Unable to read TAN's thirst toggle", exception);
                return null;
            }
        }
    }
    @Override public boolean isDrink(ItemStack stack) { return !stack.isEmpty() && tagged(stack, "drinks"); }
    @Override public boolean itemRestoresThirst(ItemStack stack) { return isDrink(stack) && hydration(stack) > 0; }
    @Override public int hydration(ItemStack stack) {
        if (!isDrink(stack)) return 0;
        for (int i = 1; i <= 20; i++) if (tagged(stack, "thirst/" + i + "_thirst_drinks")) return i;
        return 0;
    }
    @Override public int quenched(ItemStack stack) {
        for (int i = 100; i >= 10; i -= 10)
            if (tagged(stack, "hydration/" + i + "_hydration_drinks")) return Math.round(hydration(stack) * i / 50f);
        return 0;
    }
    @Override public boolean isPurityWaterContainer(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return isDrink(stack) && (boilableBottle(stack) || path.contains("water") || canteen(stack));
    }
    @Override public int purity(ItemStack stack) {
        for (int i = 100; i >= 25; i -= 25)
            if (tagged(stack, "poison_chance/" + i + "_poison_chance_drinks")) return 0;
        return PURITY_PURIFIED;
    }
    @Override public PurityResult evaluatePurity(int purity, RandomSource random) {
        // TAN applies its Thirst effect through consumable JSON, never vanilla poison/sickness.
        return new PurityResult(true, false, false, purity);
    }
    @Override public float exhaustionBiomeModifier(Level level, BlockPos pos) { return 1; }
    @Override public boolean extraHydrationToQuenched() { return false; }
    @Override public boolean supportsPurification() { return true; }
    @Override public ResourceLocation purificationOutput() { return id("purified_water_bottle"); }
    @Override public boolean canBoil(ItemStack stack) { return boilableBottle(stack); }

    /** Ordinary water potions and dirty bottles only; canteens use TAN's native purifier. */
    public static boolean boilableBottle(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                .equals(id("dirty_water_bottle"))) return true;
        if (!stack.is(net.minecraft.world.item.Items.POTION)) return false;
        //? if >=1.21 {
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        //?} else {
        /*return net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack)
                == net.minecraft.world.item.alchemy.Potions.WATER;
        *///?}
    }
    @Override public ResourceLocation iconTexture() { return id("textures/gui/icons.png"); }
    @Override public ThirstIconInfo iconInfo(int thirst) { return new ThirstIconInfo(iconTexture(), thirst > 13 ? 36 : thirst > 6 ? 45 : 0, 32, 256, 256); }
    @Override public double playerThirst(Player player) {
        try {
            Object data = Class.forName("toughasnails.api.thirst.ThirstHelper").getMethod("getThirst", Player.class).invoke(null, player);
            return ((Number) Class.forName("toughasnails.api.thirst.IThirst").getMethod("getThirst").invoke(data)).doubleValue();
        } catch (ReflectiveOperationException e) { return Double.NaN; }
    }
    private static boolean canteen(ItemStack stack) {
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("toughasnails.item.FilledCanteenItem")) return true;
        return false;
    }
    @Override public ItemStack onDrinkConsumed(ItemStack stack) {
        if (!canteen(stack)) return ItemStack.EMPTY;
        if (stack.getDamageValue() + 1 < stack.getMaxDamage()) {
            stack.setDamageValue(stack.getDamageValue() + 1);
            return stack;
        }
        try {
            Item empty;
            try {
                empty = (Item) stack.getItem().getClass().getMethod("getEmptyCanteen").invoke(stack.getItem());
            } catch (NoSuchMethodException legacy) {
                var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
                if (!items.containsKey(id("empty_canteen"))) return ItemStack.EMPTY;
                empty = items.get(id("empty_canteen"));
            }
            ItemStack result = new ItemStack(empty);
            //? if >=1.21 {
            EnchantmentHelper.setEnchantments(result, EnchantmentHelper.getEnchantmentsForCrafting(stack));
            //?} else {
            /*EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(stack), result);
            *///?}
            return result;
        } catch (ReflectiveOperationException e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.warn("Unable to resolve the empty TAN canteen", e);
            return ItemStack.EMPTY;
        }
    }
}
