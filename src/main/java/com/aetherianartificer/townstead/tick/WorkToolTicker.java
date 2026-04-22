package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.butchery.ButcheryCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import com.aetherianartificer.townstead.hunger.FishermanSupplyManager;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * While a villager is on a WORK shift, display their profession's tool of trade
 * in their main hand. The previous main-hand item is stashed and restored when
 * the shift ends, the profession changes, or no matching tool is in inventory.
 */
public final class WorkToolTicker {
    private WorkToolTicker() {}

    private record Rule(VillagerProfession profession, Predicate<ItemStack> matcher) {}

    //? if >=1.21 {
    private static final TagKey<Item> CLEAVER_TAG_C = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("c:cleaver"));
    private static final TagKey<Item> CLEAVER_TAG_FORGE = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("forge:cleaver"));
    private static final TagKey<Item> KNIFE_TAG_C = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("c:skinning_knives"));
    private static final TagKey<Item> KNIFE_TAG_FORGE = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("forge:skinning_knives"));
    private static final TagKey<Item> HACKSAW_TAG_C = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("c:hacksaw"));
    private static final TagKey<Item> HACKSAW_TAG_FORGE = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("forge:hacksaw"));
    //?} else {
    /*private static final TagKey<Item> CLEAVER_TAG_C = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "cleaver"));
    private static final TagKey<Item> CLEAVER_TAG_FORGE = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "cleaver"));
    private static final TagKey<Item> KNIFE_TAG_C = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "skinning_knives"));
    private static final TagKey<Item> KNIFE_TAG_FORGE = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "skinning_knives"));
    private static final TagKey<Item> HACKSAW_TAG_C = TagKey.create(
            Registries.ITEM, new ResourceLocation("c", "hacksaw"));
    private static final TagKey<Item> HACKSAW_TAG_FORGE = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "hacksaw"));
    *///?}

    private static final List<Rule> RULES = List.of(
            new Rule(VillagerProfession.FARMER, stack -> stack.getItem() instanceof HoeItem),
            new Rule(VillagerProfession.FISHERMAN, FishermanSupplyManager::isFishingRod),
            new Rule(VillagerProfession.BUTCHER, WorkToolTicker::isButcherTool)
    );

    /**
     * Matches cleavers, skinning knives, and hacksaws so the per-stage tool
     * swap (cleaver for most cuts, knife for skin, hacksaw for golem
     * processing) doesn't get reverted by the ticker's next pass.
     */
    public static boolean isButcherTool(ItemStack stack) {
        if (stack.isEmpty() || !ButcheryCompat.isLoaded()) return false;
        return stack.is(CLEAVER_TAG_C) || stack.is(CLEAVER_TAG_FORGE)
                || stack.is(KNIFE_TAG_C) || stack.is(KNIFE_TAG_FORGE)
                || stack.is(HACKSAW_TAG_C) || stack.is(HACKSAW_TAG_FORGE);
    }

    public static boolean isKnife(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(KNIFE_TAG_C) || stack.is(KNIFE_TAG_FORGE);
    }

    public static boolean isCleaver(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(CLEAVER_TAG_C) || stack.is(CLEAVER_TAG_FORGE);
    }

    public static boolean isHacksaw(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(HACKSAW_TAG_C) || stack.is(HACKSAW_TAG_FORGE);
    }

    private static final Map<UUID, ItemStack> PREVIOUS_MAIN_HAND = new ConcurrentHashMap<>();

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        Rule rule = ruleFor(villager.getVillagerData().getProfession());
        if (rule == null) {
            restore(villager);
            return;
        }

        Brain<?> brain = villager.getBrain();
        long dayTime = villager.level().getDayTime() % 24000L;
        Activity current = brain.getSchedule().getActivityAt((int) dayTime);
        if (current != Activity.WORK) {
            restore(villager);
            return;
        }

        ItemStack currentMain = villager.getMainHandItem();
        UUID id = villager.getUUID();

        // Fast path: already holding a matching tool and state is tracked.
        if (rule.matcher.test(currentMain) && PREVIOUS_MAIN_HAND.containsKey(id)) {
            return;
        }

        int slot = findSlot(villager.getInventory(), rule.matcher);
        if (slot < 0) {
            restore(villager);
            return;
        }

        ItemStack tool = villager.getInventory().getItem(slot);
        if (!PREVIOUS_MAIN_HAND.containsKey(id)) {
            ItemStack stash = rule.matcher.test(currentMain) ? ItemStack.EMPTY : currentMain.copy();
            PREVIOUS_MAIN_HAND.put(id, stash);
        }
        villager.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
    }

    public static void forget(VillagerEntityMCA villager) {
        PREVIOUS_MAIN_HAND.remove(villager.getUUID());
    }

    private static Rule ruleFor(VillagerProfession profession) {
        for (Rule rule : RULES) {
            if (rule.profession == profession) return rule;
        }
        return null;
    }

    private static void restore(VillagerEntityMCA villager) {
        ItemStack prev = PREVIOUS_MAIN_HAND.remove(villager.getUUID());
        if (prev == null) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, prev);
    }

    private static int findSlot(SimpleContainer inv, Predicate<ItemStack> matcher) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return i;
        }
        return -1;
    }
}
