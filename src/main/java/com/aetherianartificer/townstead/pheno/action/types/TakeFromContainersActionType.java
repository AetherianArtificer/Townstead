package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/**
 * Takes up to {@code count} matching items from the containers and storage blocks within {@code radius} into the
 * entity's own inventory: a villager's, or a player's. The search centres on the action's focus block when there is one
 * (where a seek arrived), else on the entity. Items are matched by an {@code item} id, an item {@code tag}, or an
 * {@code item_condition}; at least one is required. It fails when it takes nothing.
 *
 * <p>JSON: {@code { "type":"pheno:take_from_containers", "tag":"my_mod:remedies", "count":6, "radius":8 }}</p>
 */
public final class TakeFromContainersActionType implements ActionType {

    public static final String KEY = "pheno:take_from_containers";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Predicate<ItemStack> matches = null;
        if (json.has("item")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "item", ""));
            if (id == null) return null;
            Item item = BuiltInRegistries.ITEM.get(id);
            matches = stack -> stack.is(item);
        } else if (json.has("tag")) {
            ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "tag", "").replace("#", ""));
            if (id == null) return null;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            matches = stack -> stack.is(tag);
        }
        ItemCondition condition = json.has("item_condition") ? ItemConditions.parse(json.get("item_condition")) : null;
        if (json.has("item_condition") && condition == null) return null;
        if (matches == null && condition == null) return null;
        int count = Mth.clamp(GsonHelper.getAsInt(json, "count", 1), 1, 64);
        int radius = Mth.clamp(GsonHelper.getAsInt(json, "radius", 6), 1, 12);
        Predicate<ItemStack> wanted = matches;
        return ctx -> {
            LivingEntity entity = ctx.entity();
            if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
            BlockPos center = ctx.focusBlock() != null ? ctx.focusBlock() : entity.blockPosition();
            Predicate<ItemStack> test = stack -> !stack.isEmpty() && (wanted == null || wanted.test(stack))
                    && (condition == null || condition.test(level, stack));
            if (take(level, center, radius, entity, test, count) == 0) ctx.fail();
        };
    }

    private static int take(ServerLevel level, BlockPos center, int radius, LivingEntity entity,
                            Predicate<ItemStack> test, int count) {
        int taken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (taken >= count) break;
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            //? if neoforge {
            net.neoforged.neoforge.items.IItemHandler handler =
                    level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
            //?} else if forge {
            /*net.minecraftforge.items.IItemHandler handler =
                    be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
            *///?}
            if (handler != null) {
                for (int slot = 0; slot < handler.getSlots() && taken < count; slot++) {
                    while (taken < count && test.test(handler.getStackInSlot(slot))) {
                        ItemStack one = handler.extractItem(slot, 1, true);
                        if (one.isEmpty() || !fits(entity, one)) break;
                        one = handler.extractItem(slot, 1, false);
                        if (one.isEmpty()) break;
                        store(entity, one);
                        taken++;
                    }
                }
                continue;
            }
            if (be instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize() && taken < count; slot++) {
                    while (taken < count && test.test(container.getItem(slot))) {
                        ItemStack one = container.getItem(slot).copyWithCount(1);
                        if (!fits(entity, one)) break;
                        container.removeItem(slot, 1);
                        store(entity, one);
                        taken++;
                    }
                }
                container.setChanged();
            }
        }
        return taken;
    }

    private static boolean fits(LivingEntity entity, ItemStack stack) {
        if (entity instanceof InventoryCarrier carrier) return carrier.getInventory().canAddItem(stack);
        if (entity instanceof Player player) return player.getInventory().getFreeSlot() >= 0
                || player.getInventory().getSlotWithRemainingSpace(stack) >= 0;
        return false;
    }

    private static void store(LivingEntity entity, ItemStack stack) {
        if (entity instanceof InventoryCarrier carrier) {
            ItemStack left = carrier.getInventory().addItem(stack);
            if (!left.isEmpty()) entity.spawnAtLocation(left);
        } else if (entity instanceof Player player && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
