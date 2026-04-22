package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.butchery.GrinderStateMachine;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
//? if >=1.21 {
import net.minecraft.world.item.crafting.SingleRecipeInput;
//?}
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ButcherSupplyManager {
    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 3;
    private static final int FOOD_RESERVE_COUNT = 4;

    // Cross-loader "cooked meat" tags. Present when Butchery (and other meat
    // mods) publish them. Absent tags resolve to empty and are harmless.
    //? if >=1.21 {
    private static final TagKey<Item> COOKED_MEAT_TAG_C = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("c:cooked_meat"));
    private static final TagKey<Item> COOKED_MEAT_TAG_FORGE = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("forge:cooked_meat"));
    //?} else {
    /*private static final TagKey<Item> COOKED_MEAT_TAG_C = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "cooked_meat"));
    private static final TagKey<Item> COOKED_MEAT_TAG_FORGE = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "cooked_meat"));
    *///?}

    private ButcherSupplyManager() {}

    public static boolean hasRawInput(SimpleContainer inv, ServerLevel level) {
        return findRawInputSlot(inv, level) >= 0;
    }

    public static boolean hasFuel(SimpleContainer inv) {
        return findFuelSlot(inv) >= 0;
    }

    public static int findRawInputSlot(SimpleContainer inv, ServerLevel level) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isRawInput(stack, level)) continue;
            int score = rawInputScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static int findFuelSlot(SimpleContainer inv) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isFuel(stack)) continue;
            int score = fuelScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static boolean pullRawInput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor
    ) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> isRawInput(stack, level),
                ButcherSupplyManager::rawInputScore,
                anchor
        );
    }

    public static boolean pullFuel(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                ButcherSupplyManager::isFuel,
                ButcherSupplyManager::fuelScore,
                anchor
        );
    }

    /**
     * Pull the first cleaver found in nearby storage into the butcher's
     * inventory. Same search radius and anchor conventions as raw input /
     * fuel sourcing. Returns true if a cleaver was pulled.
     */
    public static boolean pullCleaver(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                com.aetherianartificer.townstead.tick.WorkToolTicker::isCleaver,
                ButcherSupplyManager::toolScore,
                anchor
        );
    }

    /**
     * Pull the first skinning knife found in nearby storage into the
     * butcher's inventory. Needed for the skin stage of drained carcasses.
     */
    public static boolean pullKnife(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                com.aetherianartificer.townstead.tick.WorkToolTicker::isKnife,
                ButcherSupplyManager::toolScore,
                anchor
        );
    }

    /**
     * Pull the first hacksaw found in nearby storage into the butcher's
     * inventory. Needed to process iron golem bodies (hacksaw is the only
     * tool Butchery's IronGolemCutUpProcedure accepts).
     */
    public static boolean pullHacksaw(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                com.aetherianartificer.townstead.tick.WorkToolTicker::isHacksaw,
                ButcherSupplyManager::toolScore,
                anchor
        );
    }

    /**
     * Pull a hammer from nearby storage. Used for breaking placed head /
     * skull blocks into their component material drops.
     */
    public static boolean pullHammer(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                com.aetherianartificer.townstead.tick.WorkToolTicker::isHammer,
                ButcherSupplyManager::toolScore,
                anchor
        );
    }

    /**
     * Pull a sponge or rag from nearby storage. Prefers already-wet cloths
     * so the butcher can clean right away without a detour to a cauldron
     * or water source.
     */
    public static boolean pullCloth(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                com.aetherianartificer.townstead.compat.butchery.SpongeRagHelper::isCloth,
                ButcherSupplyManager::clothScore,
                anchor
        );
    }

    /** Prefer wet cloths over dry; among equals, prefer higher wetness. */
    private static int clothScore(ItemStack stack) {
        int wetness = com.aetherianartificer.townstead.compat.butchery.SpongeRagHelper.readWetness(stack);
        return wetness * 100 + stack.getCount();
    }

    /**
     * Prefer the least-damaged tool so the villager doesn't burn through a
     * nearly-broken blade first while a fresh one sits in the next chest.
     */
    private static int toolScore(ItemStack stack) {
        if (!stack.isDamageableItem()) return 0;
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    // ── Grinder-specific pulls ──

    public static boolean pullIntestines(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                GrinderStateMachine::isIntestines,
                ItemStack::getCount,
                anchor);
    }

    public static boolean pullSausageAttachment(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                GrinderStateMachine::isSausageAttachment,
                ItemStack::getCount,
                anchor);
    }

    public static boolean pullBloodBottle(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                GrinderStateMachine::isBloodBottle,
                ItemStack::getCount,
                anchor);
    }

    public static boolean pullGrinderInput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            GrinderStateMachine.Recipe recipe
    ) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || anchor == null) return false;
        return NearbyItemSources.pullSingleToInventory(
                level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                stack -> GrinderStateMachine.isInputForRecipe(stack, recipe),
                ItemStack::getCount,
                anchor);
    }

    /** True if the villager carries everything needed to stage the recipe. */
    public static boolean hasRecipeInputs(SimpleContainer inv, GrinderStateMachine.Recipe recipe) {
        boolean hasMeat = false;
        boolean hasIntestines = !recipe.requiresCasings;
        boolean hasAttachment = !recipe.requiresCasings;
        boolean hasBlood = recipe != GrinderStateMachine.Recipe.BLOOD_SAUSAGE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!hasMeat && GrinderStateMachine.isInputForRecipe(stack, recipe)) hasMeat = true;
            if (recipe.requiresCasings) {
                if (!hasIntestines && GrinderStateMachine.isIntestines(stack)) hasIntestines = true;
                if (!hasAttachment && GrinderStateMachine.isSausageAttachment(stack)) hasAttachment = true;
            }
            if (recipe == GrinderStateMachine.Recipe.BLOOD_SAUSAGE) {
                if (!hasBlood && GrinderStateMachine.isBloodBottle(stack)) hasBlood = true;
            }
        }
        return hasMeat && hasIntestines && hasAttachment && hasBlood;
    }

    public static boolean hasStockableOutput(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isButcherOutput(inv.getItem(i))) return true;
        }
        return false;
    }

    public static boolean offloadOutput(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor
    ) {
        if (anchor == null) return false;
        boolean movedAny = false;
        SimpleContainer inv = villager.getInventory();
        int reserveFoodSlot = findBestFoodSlot(inv, true);
        int reserveFuelSlot = findFuelSlot(inv);
        int reserveInputSlot = findRawInputSlot(inv, level);

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isButcherOutput(stack)) continue;
            if (i == reserveFuelSlot || i == reserveInputSlot) continue;
            if (i == reserveFoodSlot && stack.getCount() <= FOOD_RESERVE_COUNT) continue;

            int moveCount = stack.getCount();
            if (i == reserveFoodSlot) {
                moveCount = Math.max(0, stack.getCount() - FOOD_RESERVE_COUNT);
            }
            if (moveCount <= 0) continue;

            //? if >=1.21 {
            ItemStack moving = stack.copyWithCount(moveCount);
            //?} else {
            /*ItemStack moving = stack.copy(); moving.setCount(moveCount);
            *///?}
            boolean fullyStored = NearbyItemSources.insertIntoNearbyStorage(
                    level,
                    villager,
                    moving,
                    SEARCH_RADIUS,
                    VERTICAL_RADIUS,
                    anchor
            );
            if (!fullyStored && moving.getCount() == stack.getCount()) continue;
            if (i == reserveFoodSlot) {
                stack.setCount(FOOD_RESERVE_COUNT + moving.getCount());
            } else {
                stack.setCount(moving.getCount());
            }
            movedAny = true;
        }

        return movedAny;
    }

    public static boolean isRawInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        if (isButcherOutput(stack)) return false;
        //? if >=1.21 {
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        //?} else {
        /*net.minecraft.world.SimpleContainer wrapper = new net.minecraft.world.SimpleContainer(stack);
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                wrapper,
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().assemble(wrapper, level.registryAccess());
        *///?}
        if (output.isEmpty()) return false;
        // Guard against no-op or loop recipes (input -> same input) to avoid deadlocks.
        //? if >=1.21 {
        if (ItemStack.isSameItemSameComponents(output, stack)) return false;
        //?} else {
        /*if (ItemStack.isSameItemSameTags(output, stack)) return false;
        *///?}
        return true;
    }

    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return AbstractFurnaceBlockEntity.isFuel(stack);
    }

    public static boolean isButcherOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.COOKED_BEEF)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.COOKED_RABBIT)
                || stack.is(Items.COOKED_COD)
                || stack.is(Items.COOKED_SALMON)) return true;
        // Butchery and any other mod publishing the common "cooked meat" tag.
        if (stack.is(COOKED_MEAT_TAG_C) || stack.is(COOKED_MEAT_TAG_FORGE)) return true;
        // Grinder terminal outputs: mince never gets smoked further, meat
        // scraps are a finished good, and the final-stage returned glass
        // bottle from blood sausage gets offloaded with everything else.
        // Raw sausage / raw blood sausage are intentionally NOT listed here
        // so the smoker's isRawInput still accepts them as smoker input.
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(GrinderStateMachine.RAW_LAMB_MINCE_ID)
                || id.equals(GrinderStateMachine.RAW_BEEF_MINCE_ID)
                || id.equals(GrinderStateMachine.MEAT_SCRAPS_ID)) {
            return true;
        }
        return false;
    }

    public static boolean isValidSmokerInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return true;
        return isRawInput(stack, level);
    }

    public static boolean isSmokerBlockerInput(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        ).isEmpty();
        //?} else {
        /*return level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new net.minecraft.world.SimpleContainer(stack),
                level
        ).isEmpty();
        *///?}
    }

    public static boolean hasUsableSmokingRecipe(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                new SingleRecipeInput(stack),
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().value().assemble(new SingleRecipeInput(stack), level.registryAccess());
        //?} else {
        /*net.minecraft.world.SimpleContainer wrapper2 = new net.minecraft.world.SimpleContainer(stack);
        var recipe = level.getRecipeManager().getRecipeFor(
                RecipeType.SMOKING,
                wrapper2,
                level
        );
        if (recipe.isEmpty()) return false;
        ItemStack output = recipe.get().assemble(wrapper2, level.registryAccess());
        *///?}
        if (output.isEmpty()) return false;
        //? if >=1.21 {
        return !ItemStack.isSameItemSameComponents(output, stack);
        //?} else {
        /*return !ItemStack.isSameItemSameTags(output, stack);
        *///?}
    }

    private static int rawInputScore(ItemStack stack) {
        int score = stack.getCount();
        if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON)) score += 100;
        if (stack.is(Items.CHICKEN) || stack.is(Items.RABBIT)) score += 80;
        if (stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH)) score += 60;
        return score;
    }

    private static int fuelScore(ItemStack stack) {
        int score = stack.getCount();
        if (stack.is(Items.CHARCOAL)) return score + 500;
        if (stack.is(Items.COAL)) return score + 450;
        if (stack.is(Items.BLAZE_ROD)) return score + 300;
        if (stack.is(Items.DRIED_KELP_BLOCK)) return score + 200;
        if (stack.is(Items.COAL_BLOCK)) return score + 50;
        return score;
    }

    private static int findBestFoodSlot(SimpleContainer inv, boolean excludeButcherOutput) {
        int bestSlot = -1;
        int bestNutrition = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (excludeButcherOutput && isButcherOutput(stack)) continue;
            if (!FoodSafety.isSafeNutritiousFood(stack)) continue;
            //? if >=1.21 {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
            //?} else {
            /*FoodProperties food = stack.getFoodProperties(null);
            if (food.getNutrition() > bestNutrition) {
                bestNutrition = food.getNutrition();
            *///?}
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
