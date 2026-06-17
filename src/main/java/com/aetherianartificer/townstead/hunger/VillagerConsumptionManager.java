package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.mca.McaSicknessAdapter;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.storage.EmptyContainerDropoff;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified consumption flow for villagers. A villager "consumes" an item using the vanilla item-use
 * timing/animation (driven by the item's own UseAnim), and finalizing applies every benefit the
 * item carries: hunger, food potion effects, coffee/fatigue, thirst (with water purity), the
 * leftover container, and chorus-fruit teleport.
 *
 * <p>Whether a villager went after an item for hunger or thirst is a <em>selection</em> concern
 * handled by SeekFoodTask / SeekDrinkTask; it is not a consumption concern. So the same item yields
 * the same result regardless of which need drove it, exactly like a player.
 */
public final class VillagerConsumptionManager {

    private record Pending(ItemStack item, ItemStack previousMainHand, long finishTick, BlockPos source) {}

    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private VillagerConsumptionManager() {}

    public static boolean isConsuming(VillagerEntityMCA villager) {
        return PENDING.containsKey(villager.getId()) || villager.isUsingItem();
    }

    /** Begins consuming one unit of {@code stack} if it is edible or restores thirst. */
    public static boolean startConsuming(VillagerEntityMCA villager, ItemStack stack) {
        return startConsuming(villager, stack, null);
    }

    /**
     * As {@link #startConsuming(VillagerEntityMCA, ItemStack)}, but records the storage block the
     * item was taken from so the leftover container can be returned there when it's finished.
     */
    public static boolean startConsuming(VillagerEntityMCA villager, ItemStack stack, BlockPos source) {
        if (stack.isEmpty() || isConsuming(villager)) return false;
        if (!isConsumable(stack)) return false;

        //? if >=1.21 {
        ItemStack oneUnit = stack.copyWithCount(1);
        //?} else {
        /*ItemStack oneUnit = stack.copy(); oneUnit.setCount(1);
        *///?}
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        //? if >=1.21 {
        int useDuration = oneUnit.getUseDuration(villager);
        //?} else {
        /*int useDuration = oneUnit.getUseDuration();
        *///?}
        if (useDuration <= 0) useDuration = 32;

        PENDING.put(villager.getId(),
                new Pending(oneUnit.copy(), previousMainHand, villager.level().getGameTime() + useDuration, source));

        villager.setItemInHand(InteractionHand.MAIN_HAND, oneUnit);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean isConsumable(ItemStack stack) {
        //? if >=1.21 {
        if (stack.get(DataComponents.FOOD) != null) return true;
        //?} else {
        /*if (stack.getFoodProperties(null) != null) return true;
        *///?}
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        return bridge != null && bridge.itemRestoresThirst(stack);
    }

    /**
     * Finalizes a pending consumption when the vanilla item use ends, applying all of the item's
     * benefits. Returns true if hunger or thirst changed (so the caller can sync).
     */
    public static boolean tickAndFinalize(VillagerEntityMCA villager, TownsteadVillager.Needs needs) {
        Pending pending = PENDING.get(villager.getId());
        if (pending == null) return false;
        if (villager.isUsingItem()) return false;

        boolean completed = villager.level().getGameTime() >= pending.finishTick();
        if (!completed) {
            // Item use was interrupted early; restart it so the villager finishes consuming.
            villager.setItemInHand(InteractionHand.MAIN_HAND, pending.item().copy());
            villager.startUsingItem(InteractionHand.MAIN_HAND);
            return false;
        }

        PENDING.remove(villager.getId());
        villager.setItemInHand(InteractionHand.MAIN_HAND, pending.previousMainHand().copy());
        return applyConsumption(villager, villager, pending.item(), needs, pending.source());
    }

    /**
     * Applies an item's consumption benefits to {@code recipient} (hunger, food potion effects,
     * coffee, chorus teleport, thirst with water purity) and gives the leftover container to
     * {@code holder}. For ordinary self-consumption {@code holder == recipient}; for caregiving
     * (an adult feeding a child, or a future downed/sick villager) the food feeds the recipient
     * while the bowl/bottle stays with the holder. Returns true if the recipient's hunger or thirst
     * changed. This is the single benefit-application path so feeding never drifts from self-eating.
     */
    public static boolean applyConsumption(VillagerEntityMCA holder, VillagerEntityMCA recipient,
                                           ItemStack stack, TownsteadVillager.Needs recipientNeeds) {
        return applyConsumption(holder, recipient, stack, recipientNeeds, null);
    }

    /** As above, returning the leftover container to {@code source} first when one was recorded. */
    public static boolean applyConsumption(VillagerEntityMCA holder, VillagerEntityMCA recipient,
                                           ItemStack stack, TownsteadVillager.Needs recipientNeeds, BlockPos source) {
        returnRemainder(holder, stack, source);
        boolean changed = applyFoodBenefits(recipient, stack, recipientNeeds);
        changed |= applyThirstBenefits(recipient, stack, recipientNeeds);
        return changed;
    }

    // --- Benefit application (act on the recipient: the villager that gains the food/drink) ---

    /** Applies hunger/saturation, food potion effects, coffee benefit and chorus teleport. */
    private static boolean applyFoodBenefits(VillagerEntityMCA recipient, ItemStack stack, TownsteadVillager.Needs needs) {
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        *///?}
        if (food == null) return false;
        int before = needs.hunger();
        float foodScale = com.aetherianartificer.townstead.origin.hook.PhenoHooks.foodMultiplier(recipient);
        needs.applyFood(food, foodScale);
        needs.setLastAteTime(recipient.level().getGameTime());
        applyFoodEffects(recipient, stack);
        if (stack.is(Items.CHORUS_FRUIT) && TownsteadConfig.ENABLE_CHORUS_FRUIT_TELEPORT.get()) {
            chorusTeleport(recipient);
        }
        FatigueData.applyCoffeeEffect(recipient, stack);
        return needs.hunger() != before;
    }

    /** Applies thirst/quenched (with water purity sickness/poison) for thirst-restoring items. */
    private static boolean applyThirstBenefits(VillagerEntityMCA recipient, ItemStack stack, TownsteadVillager.Needs needs) {
        if (!TownsteadConfig.isVillagerThirstEnabled()) return false;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !bridge.itemRestoresThirst(stack)) return false;

        int beforeThirst = needs.thirst();
        int beforeQuenched = needs.quenched();

        int hydration = bridge.hydration(stack);
        int quenched = bridge.quenched(stack);
        boolean isPurityWater = bridge.isPurityWaterContainer(stack);
        ThirstCompatBridge.PurityResult purity = isPurityWater
                ? bridge.evaluatePurity(bridge.purity(stack), recipient.getRandom())
                : new ThirstCompatBridge.PurityResult(true, false, false, -1);

        if (purity.sickness()) {
            recipient.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 5, 0));
            recipient.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0));
            McaSicknessAdapter.markSick(recipient, false);
        }
        if (purity.poison()) {
            recipient.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 10, 0));
            McaSicknessAdapter.markSick(recipient, true);
        }
        if (purity.applyHydration()) {
            needs.applyDrink(hydration, quenched, bridge.extraHydrationToQuenched());
        }
        needs.setLastDrankTime(recipient.level().getGameTime());
        return needs.thirst() != beforeThirst || needs.quenched() != beforeQuenched;
    }

    /** Applies a food's built-in potion effects (golden apple, etc.) the way LivingEntity.eat does. */
    private static void applyFoodEffects(VillagerEntityMCA recipient, ItemStack stack) {
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;
        for (FoodProperties.PossibleEffect possible : food.effects()) {
            if (recipient.getRandom().nextFloat() < possible.probability()) {
                recipient.addEffect(new MobEffectInstance(possible.effect()));
            }
        }
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        if (food == null) return;
        for (com.mojang.datafixers.util.Pair<MobEffectInstance, Float> pair : food.getEffects()) {
            if (pair.getFirst() != null && recipient.getRandom().nextFloat() < pair.getSecond()) {
                recipient.addEffect(new MobEffectInstance(pair.getFirst()));
            }
        }
        *///?}
    }

    // --- Leftover containers ---

    /**
     * Returns the container an item leaves behind once consumed (bowl, glass bottle, bucket, etc.),
     * matching what a player keeps. EMPTY if nothing is left.
     */
    public static ItemStack getConsumptionRemainder(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        // Honey bottle (craftRemainder), FD bowls, and the general modding convention declare it here.
        ItemStack remainder = stack.getCraftingRemainingItem();
        if (!remainder.isEmpty()) return remainder;
        //? if >=1.21 {
        // Stews and other foods declare their bowl/container via FoodProperties.usingConvertsTo.
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && food.usingConvertsTo().isPresent() && !food.usingConvertsTo().get().isEmpty()) {
            return food.usingConvertsTo().get().copy();
        }
        //?} else {
        /*net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.BowlFoodItem
                || item instanceof net.minecraft.world.item.SuspiciousStewItem) {
            return new ItemStack(Items.BOWL);
        }
        *///?}
        // Drink containers vanilla hardcodes in finishUsingItem rather than declaring.
        if (stack.is(Items.POTION)) return new ItemStack(Items.GLASS_BOTTLE);
        if (stack.is(Items.MILK_BUCKET)) return new ItemStack(Items.BUCKET);
        return ItemStack.EMPTY;
    }

    /** Gives the consumption remainder of {@code stack} to the villager, dropping any overflow. */
    public static void returnRemainder(VillagerEntityMCA villager, ItemStack stack) {
        returnRemainder(villager, stack, null);
    }

    /**
     * Routes the leftover container: straight back into its {@code source} if the villager is right
     * there; otherwise it's carried (and the source is recorded) so the origin-return ledger can
     * hand it back the next time the villager is near that storage. Origin-less leftovers go to the
     * nearest storage, falling to the villager's inventory only if none is reachable.
     */
    public static void returnRemainder(VillagerEntityMCA villager, ItemStack stack, BlockPos source) {
        boolean debug = TownsteadConfig.DEBUG_VILLAGER_AI.get();
        ItemStack remainder = getConsumptionRemainder(stack);
        if (remainder.isEmpty()) {
            if (debug) {
                com.aetherianartificer.townstead.Townstead.LOGGER.info(
                        "[Consume] villager {} consumed {} with no container remainder",
                        villager.getUUID(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
            return;
        }
        // Item ref captured before deposit shrinks the remainder away, for the debug line.
        net.minecraft.world.item.Item container = remainder.getItem();
        String destination;
        if (villager.level() instanceof ServerLevel level) {
            if (source != null && EmptyContainerDropoff.tryReturnToSource(level, villager, remainder, source)) {
                destination = "returned to source";
            } else {
                if (source != null) {
                    // Carry it; the ledger returns it to this origin next time we're near it.
                    EmptyContainerDropoff.recordPendingReturn(villager, source);
                    destination = "held for source";
                } else {
                    // No origin: nearest storage now, else carry.
                    EmptyContainerDropoff.deposit(level, villager, remainder, null);
                    destination = remainder.isEmpty() ? "nearest storage" : "carried";
                }
                if (!remainder.isEmpty()) {
                    ItemStack overflow = villager.getInventory().addItem(remainder);
                    if (!overflow.isEmpty()) {
                        ItemEntity drop = new ItemEntity(villager.level(),
                                villager.getX(), villager.getY() + 0.25, villager.getZ(), overflow.copy());
                        drop.setPickUpDelay(0);
                        villager.level().addFreshEntity(drop);
                        destination += ", inventory full so dropped";
                    }
                }
            }
        } else {
            villager.getInventory().addItem(remainder);
            destination = "inventory";
        }
        if (debug) {
            com.aetherianartificer.townstead.Townstead.LOGGER.info(
                    "[Consume] villager {} kept container {} from {} ({})",
                    villager.getUUID(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(container),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    destination);
        }
    }

    // --- Chorus fruit ---

    /** Vanilla ChorusFruitItem teleport: up to 16 attempts at a random spot within 16 blocks. */
    private static void chorusTeleport(VillagerEntityMCA recipient) {
        if (!(recipient.level() instanceof ServerLevel level)) return;
        double srcX = recipient.getX();
        double srcY = recipient.getY();
        double srcZ = recipient.getZ();
        for (int i = 0; i < 16; i++) {
            double x = recipient.getX() + (recipient.getRandom().nextDouble() - 0.5) * 16.0;
            double y = Mth.clamp(recipient.getY() + (recipient.getRandom().nextInt(16) - 8),
                    level.getMinBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight() - 1);
            double z = recipient.getZ() + (recipient.getRandom().nextDouble() - 0.5) * 16.0;
            if (recipient.isPassenger()) recipient.stopRiding();
            if (recipient.randomTeleport(x, y, z, true)) {
                level.playSound(null, srcX, srcY, srcZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                recipient.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                recipient.resetFallDistance();
                break;
            }
        }
    }
}
