package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if >=1.21 {
import net.minecraft.world.ItemInteractionResult;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}

import java.util.UUID;

/** Executes the target block's real player interaction using a transactional context item role. */
public final class UseBlockBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:use_block";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("266f5434-42bb-47d9-b014-2b42e12ca454"), "[TownsteadWorker]");

    @Override public String key() { return KEY; }

    @Override
    public BlockAction parse(JsonObject json) {
        String role = json.has("item") ? json.get("item").getAsString() : "empty";
        boolean secondaryUse = json.has("secondary_use") && json.get("secondary_use").getAsBoolean();
        return context -> {
            ItemStack supplied = context.itemRole(role).copy();
            ServerPlayer actor;
            try { actor = FakePlayerFactory.get(context.level(), PROFILE); }
            catch (Throwable failure) { context.fail(); return; }
            try {
                // Fake players are cached by profile. Every invocation is a fresh transaction:
                // stale items from an earlier block interaction must never become inputs or
                // returned products of this one.
                actor.getInventory().clearContent();
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                actor.setShiftKeyDown(secondaryUse);
                actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                if (context.cause() != null) {
                    actor.setPos(context.cause().getX(), context.cause().getY(), context.cause().getZ());
                }
                actor.setItemInHand(InteractionHand.MAIN_HAND, supplied);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(context.pos()),
                        Direction.UP, context.pos(), false);
                BlockState beforeBlock = context.level().getBlockState(context.pos());
                ItemStack beforeItem = actor.getMainHandItem().copy();
                // Use the server's complete player-interaction path. Optional mods commonly
                // implement machines through RightClickBlock listeners rather than Block methods;
                // bypassing ServerPlayerGameMode would make those machines impossible to port
                // through data alone.
                InteractionResult result = actor.gameMode.useItemOn(actor, context.level(),
                        actor.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                boolean changed = !beforeBlock.equals(context.level().getBlockState(context.pos()))
                        || !ItemStack.matches(beforeItem, actor.getMainHandItem());
                // Event-driven integrations do not always claim the vanilla interaction result,
                // even after mutating the block or item. A real transaction is still successful
                // when one of those observable values changed.
                if (!result.consumesAction() && !changed) context.fail();
                context.setItemRole(role, actor.getMainHandItem().copy());

                // The held remainder already lives in the role. Empty the selected slot before
                // collecting inventory returns, or the same remainder is handed back twice.
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
                    context.returnItem(actor.getInventory().removeItemNoUpdate(slot));
                }
            } catch (Throwable failure) {
                context.setItemRole(role, supplied);
                context.fail();
            } finally {
                actor.setShiftKeyDown(false);
                actor.getInventory().clearContent();
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        };
    }
}
