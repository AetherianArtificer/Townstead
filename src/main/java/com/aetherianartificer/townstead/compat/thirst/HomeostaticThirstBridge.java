package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Homeostatic's water bar. It has no API, so every target is internal: {@code WaterHelper} for item
 * hydration and player drinking, the platform service for the player's water and the flask's fluid.
 * Quality lives in the fluid: plain water is dirty (its drink JSON carries a Thirst effect chance),
 * purified water is clean. Its saturation reserve caps at 5 against Townstead's 20.
 */
public final class HomeostaticThirstBridge implements ThirstCompatBridge {
    public static final HomeostaticThirstBridge INSTANCE = new HomeostaticThirstBridge();
    public static final String MOD_ID = HomeostaticEntityCompat.MOD_ID;
    private static final float RESERVE_SCALE = 4f;
    private static final int UPDATES_PER_SECOND_DIVISOR = 16;

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(MOD_ID, path);
        *///?}
    }

    private static final ResourceLocation PURIFIED_BOTTLE = id("purified_water_bottle");
    private static final ResourceLocation PURIFIED_WATER = id("purified_water");
    private static final ResourceLocation LEATHER_FLASK = id("leather_flask");
    private static final ResourceLocation ICONS = id("textures/gui/icons.png");

    private boolean initialized;
    private boolean active;
    // Method handles, not Class.getMethod: reflecting a method table resolves every signature, and
    // WaterHelper and the platform service carry HUD methods whose client types a server refuses.
    private java.lang.invoke.MethodHandle itemHydration, drinkHydration, waterCapability, fluidInfo, drainFluid;
    private Method amount, saturation, potency, duration, chance, waterLevel, infoFluid;
    private Constructor<?> hydrationRecord;
    private Object platform;

    private HomeostaticThirstBridge() {}

    @Override
    public boolean isActive() {
        init();
        return active;
    }

    private synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModCompat.isLoaded(MOD_ID)) return;
        try {
            Class<?> helper = Class.forName("homeostatic.util.WaterHelper");
            Class<?> hydration = Class.forName("homeostatic.common.Hydration");
            var lookup = java.lang.invoke.MethodHandles.publicLookup();
            itemHydration = lookup.findStatic(helper, "getItemHydration", java.lang.invoke.MethodType.methodType(hydration, ItemStack.class));
            drinkHydration = lookup.findStatic(helper, "drink", java.lang.invoke.MethodType.methodType(void.class, ServerPlayer.class, hydration, boolean.class));
            hydrationRecord = hydration.getConstructor(int.class, float.class, int.class, int.class, float.class);
            amount = hydration.getMethod("amount");
            saturation = hydration.getMethod("saturation");
            potency = hydration.getMethod("potency");
            duration = hydration.getMethod("duration");
            chance = hydration.getMethod("chance");
            platform = Class.forName("homeostatic.platform.Services").getField("PLATFORM").get(null);
            Class<?> service = Class.forName("homeostatic.platform.services.IPlatform");
            waterCapability = lookup.findVirtual(service, "getWaterCapabilty", java.lang.invoke.MethodType.methodType(Optional.class, Player.class));
            fluidInfo = lookup.findVirtual(service, "getFluidInfo", java.lang.invoke.MethodType.methodType(Optional.class, ItemStack.class));
            drainFluid = lookup.findVirtual(service, "drainFluid", java.lang.invoke.MethodType.methodType(ItemStack.class, ItemStack.class, long.class));
            infoFluid = Class.forName("homeostatic.common.fluid.FluidInfo").getMethod("fluid");
            active = true;
            Townstead.LOGGER.info("Homeostatic thirst compatibility enabled.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            itemHydration = null;
            Townstead.LOGGER.warn("Failed to initialize Homeostatic thirst compatibility. Villager thirst integration disabled.", e);
        }
    }

    private Object hydrationOf(ItemStack stack) {
        if (stack.isEmpty() || !isActive() || nonWaterPotion(stack)) return null;
        try {
            return itemHydration.invoke(stack);
        } catch (Throwable e) {
            return null;
        }
    }

    private Number field(Method accessor, Object record) {
        try {
            return record == null ? null : (Number) accessor.invoke(record);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Homeostatic reads every potion other than water as purified water; only water is water. */
    private static boolean nonWaterPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        //? if >=1.21 {
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return contents == null || !contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        //?} else {
        /*return net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack)
                != net.minecraft.world.item.alchemy.Potions.WATER;
        *///?}
    }

    /** The fluid a flask or bottle holds, or null. */
    private Fluid fluid(ItemStack stack) {
        if (stack.isEmpty() || !isActive()) return null;
        if (stack.is(Items.POTION)) return nonWaterPotion(stack) ? null : Fluids.WATER;
        if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(PURIFIED_BOTTLE)) {
            return BuiltInRegistries.FLUID.get(PURIFIED_WATER);
        }
        try {
            if (fluidInfo.invoke(platform, stack) instanceof Optional<?> info && info.isPresent()) {
                return (Fluid) infoFluid.invoke(info.get());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean flask(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(LEATHER_FLASK);
    }

    @Override
    public boolean itemRestoresThirst(ItemStack stack) {
        Number value = field(amount, hydrationOf(stack));
        return value != null && value.intValue() > 0;
    }

    @Override
    public boolean isDrink(ItemStack stack) {
        return itemRestoresThirst(stack);
    }

    @Override
    public boolean isPurityWaterContainer(ItemStack stack) {
        return purity(stack) >= 0 && itemRestoresThirst(stack);
    }

    @Override
    public int hydration(ItemStack stack) {
        Number value = field(amount, hydrationOf(stack));
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    @Override
    public int quenched(ItemStack stack) {
        Number value = field(saturation, hydrationOf(stack));
        return value == null ? 0 : Math.max(0, Math.round(value.floatValue() * RESERVE_SCALE));
    }

    /** Dirty for plain water, purified for the mod's purified water; nothing in between. */
    @Override
    public int purity(ItemStack stack) {
        Fluid fluid = fluid(stack);
        if (fluid == null || fluid == Fluids.EMPTY) return -1;
        if (fluid.isSame(Fluids.WATER)) return 0;
        return fluid.isSame(BuiltInRegistries.FLUID.get(PURIFIED_WATER)) ? PURITY_PURIFIED : -1;
    }

    @Override
    public PurityResult evaluatePurity(int purity, RandomSource random) {
        return new PurityResult(true, false, false, purity);
    }

    /**
     * The drink's own Thirst effect, at its chance, halved when the drinker is not already thirsty,
     * the way the mod rolls it for a player. The effect only drains players; villagers carry the
     * drain through {@link HomeostaticEntityCompat}.
     */
    @Override
    public boolean applyWaterSickness(LivingEntity drinker, ItemStack stack, RandomSource random) {
        Object hydration = hydrationOf(stack);
        Number ticks = field(duration, hydration);
        if (hydration == null || ticks == null) return false;
        if (ticks.intValue() <= 0) return true;
        Number odds = field(chance, hydration);
        Number level = field(potency, hydration);
        MobEffect thirst = BuiltInRegistries.MOB_EFFECT.get(id("thirst"));
        if (thirst == null || odds == null || level == null) return true;
        //? if >=1.21 {
        var holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(thirst);
        //?} else {
        /*var holder = thirst;
        *///?}
        float roll = odds.floatValue();
        if (!drinker.hasEffect(holder)) roll *= 0.5f;
        if (random.nextFloat() < roll) {
            drinker.addEffect(new MobEffectInstance(holder, ticks.intValue(), level.intValue(), false, false, false));
        }
        return true;
    }

    @Override
    public float exhaustionBiomeModifier(Level level, BlockPos pos) {
        return 1f;
    }

    /**
     * Homeostatic loses water by sweating while the skin warms: up to 0.1 exhaustion an update when
     * warm and 0.3 when hot, with an update about every 16 ticks. Villagers sweat on the same curve
     * from Townstead's body heat.
     */
    @Override
    public float thermalExhaustionPerTick(ThermalHydrationContext thermal) {
        float stress = thermal.signedBodyStress();
        if (stress <= 16f) return 0f;
        float hot = Math.max(0f, Math.min(1f, (stress - 33f) / 33f));
        float perUpdate = 0.1f + 0.2f * hot;
        return ThirstEffects.townsteadExhaustion(perUpdate / UPDATES_PER_SECOND_DIVISOR);
    }

    @Override
    public boolean extraHydrationToQuenched() {
        return false;
    }

    @Override
    public ResourceLocation iconTexture() {
        return ICONS;
    }

    @Override
    public ThirstIconInfo iconInfo(int thirst) {
        int u = thirst > 13 ? 0 : thirst > 6 ? 9 : 36;
        return new ThirstIconInfo(ICONS, u, 0, 256, 256);
    }

    @Override
    public boolean supportsPurification() {
        return true;
    }

    /** Plain water in a bottle or a flask; the mod's own campfire recipes purify both. */
    @Override
    public boolean canBoil(ItemStack stack) {
        return isPurityWaterContainer(stack) && purity(stack) == 0;
    }

    @Override
    public ResourceLocation purificationOutput() {
        return PURIFIED_BOTTLE;
    }

    /** One serving out of a Leather Flask, the share the mod drains when a player drinks. */
    @Override
    public ItemStack onDrinkConsumed(ItemStack stack) {
        if (!isActive() || !flask(stack)) return ItemStack.EMPTY;
        try {
            Method single = stack.getItem().getClass().getMethod("getSingleUse");
            Object drained = drainFluid.invoke(platform, stack, ((Number) single.invoke(stack.getItem())).longValue());
            return drained instanceof ItemStack result && !result.isEmpty() ? result : stack;
        } catch (Throwable e) {
            return ItemStack.EMPTY;
        }
    }

    private Object water(Player player) {
        try {
            return waterCapability.invoke(platform, player) instanceof Optional<?> data ? data.orElse(null) : null;
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public double playerThirst(Player player) {
        if (player == null || !isActive()) return Double.NaN;
        Object data = water(player);
        if (data == null) return Double.NaN;
        try {
            if (waterLevel == null) waterLevel = data.getClass().getMethod("getWaterLevel");
            return ((Number) waterLevel.invoke(data)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    /** The mod's own drink entry clamps both halves and syncs the bar. */
    @Override
    public boolean restorePlayerThirst(Player player, int immediate, int lasting) {
        if (!(player instanceof ServerPlayer server) || !isActive()) return false;
        if (immediate <= 0 && lasting <= 0) return false;
        try {
            Object hydration = hydrationRecord.newInstance(Math.max(0, immediate), Math.max(0, lasting) / RESERVE_SCALE, 0, 0, 0f);
            drinkHydration.invoke(server, hydration, true);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
