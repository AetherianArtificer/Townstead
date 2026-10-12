package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The actor uses an item on the target the way a player would: shears on a sheep, a bucket on a
 * cow, a bowl on a mooshroom. The target's own interaction runs, including loader events, so
 * modded animals and tools work without naming them. The item comes from the actor's inventory,
 * and what is left in hand plus anything the interaction produced goes back to the actor. Fails
 * when nothing happened.
 *
 * <p>JSON: {@code { "type":"pheno:use_item_on", "item":"#c:tools/shears" }}. Without
 * {@code item} the actor uses an empty hand.</p>
 */
public final class UseItemOnActionType implements ActionType {

    public static final String KEY = "pheno:use_item_on";
    /** The same worker identity as {@code pheno:use_block}, so mods see one Townstead worker. */
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("266f5434-42bb-47d9-b014-2b42e12ca454"), "[TownsteadWorker]");
    private static final double DROP_REACH = 2.5;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Predicate<ItemStack> item;
        if (json.has("item")) {
            item = selector(GsonHelper.getAsString(json, "item"));
            if (item == null) return null;
        } else {
            item = null;
        }
        boolean secondaryUse = GsonHelper.getAsBoolean(json, "secondary_use", false);
        return ctx -> {
            LivingEntity actor = ctx.entity();
            LivingEntity target = ctx.other();
            if (target == null || !(actor.level() instanceof ServerLevel level) || !target.isAlive()) {
                ctx.fail();
                return;
            }
            ItemStack supplied = item == null ? ItemStack.EMPTY : take(actor, item);
            if (item != null && supplied.isEmpty()) {
                ctx.fail();
                return;
            }
            ServerPlayer worker;
            try {
                worker = FakePlayerFactory.get(level, PROFILE);
            } catch (Throwable failure) {
                give(actor, supplied);
                ctx.fail();
                return;
            }
            try {
                worker.getInventory().clearContent();
                worker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                worker.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                worker.setShiftKeyDown(secondaryUse);
                worker.setPos(actor.getX(), actor.getY(), actor.getZ());
                worker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        target.getEyePosition());
                worker.setItemInHand(InteractionHand.MAIN_HAND, supplied.copy());
                ItemStack before = supplied.copy();

                // Products usually land beside the animal (wool), so snapshot what was already
                // lying there and claim only what this interaction spawned.
                AABB area = target.getBoundingBox().inflate(DROP_REACH);
                Set<UUID> existing = new HashSet<>();
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) existing.add(drop.getUUID());

                InteractionResult result = worker.interactOn(target, InteractionHand.MAIN_HAND);

                ItemStack held = worker.getMainHandItem().copy();
                boolean changed = !ItemStack.matches(before, held);
                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                give(actor, held);
                boolean produced = false;
                for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area,
                        candidate -> !existing.contains(candidate.getUUID()))) {
                    ItemStack product = drop.getItem().copy();
                    if (product.isEmpty()) continue;
                    drop.discard();
                    give(actor, product);
                    produced = true;
                }
                for (int slot = 0; slot < worker.getInventory().getContainerSize(); slot++) {
                    ItemStack returned = worker.getInventory().removeItemNoUpdate(slot);
                    if (!returned.isEmpty()) {
                        give(actor, returned);
                        produced = true;
                    }
                }
                if (!result.consumesAction() && !changed && !produced) ctx.fail();
            } catch (Throwable failure) {
                ItemStack held = worker.getMainHandItem();
                give(actor, held.isEmpty() ? supplied : held.copy());
                ctx.fail();
            } finally {
                worker.setShiftKeyDown(false);
                worker.getInventory().clearContent();
                worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                worker.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        };
    }

    private static ItemStack take(LivingEntity actor, Predicate<ItemStack> item) {
        Container inventory = inventory(actor);
        if (inventory != null) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && item.test(stack)) {
                    ItemStack taken = stack.split(1);
                    inventory.setChanged();
                    return taken;
                }
            }
            return ItemStack.EMPTY;
        }
        ItemStack hand = actor.getMainHandItem();
        return !hand.isEmpty() && item.test(hand) ? hand.split(1) : ItemStack.EMPTY;
    }

    private static void give(LivingEntity actor, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (actor instanceof Player player) {
            if (!player.addItem(stack)) player.drop(stack, false);
            return;
        }
        if (actor instanceof InventoryCarrier carrier) {
            ItemStack rest = carrier.getInventory().addItem(stack);
            if (rest.isEmpty()) return;
            stack = rest;
        }
        actor.spawnAtLocation(stack);
    }

    private static Container inventory(LivingEntity actor) {
        if (actor instanceof Player player) return player.getInventory();
        if (actor instanceof InventoryCarrier carrier) return carrier.getInventory();
        return null;
    }

    private static Predicate<ItemStack> selector(String raw) {
        boolean tag = raw.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
        if (id == null) return null;
        if (tag) {
            TagKey<Item> key = TagKey.create(Registries.ITEM, id);
            return stack -> stack.is(key);
        }
        return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }
}
