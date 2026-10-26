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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Executes the target block's real player interaction using a transactional context item role. */
public final class UseBlockBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:use_block";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("266f5434-42bb-47d9-b014-2b42e12ca454"), "[TownsteadWorker]");

    @Override public String key() { return KEY; }

    @Override
    public BlockAction parse(JsonObject json) {
        BlockInteractionGeometry geometry = BlockInteractionGeometry.parse(json);
        if (geometry == null) return null;
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
                Vec3 fallback = context.cause() == null ? null : context.cause().position();
                Vec3 actorPosition = geometry.actorPosition(context.pos(), fallback);
                actor.setPos(actorPosition.x, actorPosition.y, actorPosition.z);
                if (geometry.actorFacing() != null) {
                    actor.setYRot(geometry.actorFacing().toYRot());
                    actor.setXRot(geometry.actorFacing() == Direction.UP ? -90.0f
                            : geometry.actorFacing() == Direction.DOWN ? 90.0f : 0.0f);
                }
                actor.setItemInHand(InteractionHand.MAIN_HAND, supplied);
                BlockHitResult hit = geometry.hit(context.pos());
                BlockState beforeBlock = context.level().getBlockState(context.pos());
                ItemStack beforeItem = actor.getMainHandItem().copy();
                // Some public block interactions return their result by spawning it beside the
                // block rather than placing it in the player's inventory (Farmer's Delight's
                // cutting board is the canonical example). Snapshot the tight interaction area
                // so those synchronous products belong to this transaction instead of racing a
                // later worksite sweep or another entity's pickup AI.
                AABB interactionArea = new AABB(context.pos()).inflate(2.0);
                Set<UUID> existingDrops = new HashSet<>();
                for (ItemEntity drop : context.level().getEntitiesOfClass(
                        ItemEntity.class, interactionArea)) {
                    existingDrops.add(drop.getUUID());
                }
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
                context.setItemRole(role, actor.getMainHandItem().copy());

                boolean spawnedProduct = false;
                for (ItemEntity drop : context.level().getEntitiesOfClass(
                        ItemEntity.class, interactionArea,
                        candidate -> !existingDrops.contains(candidate.getUUID()))) {
                    ItemStack product = drop.getItem().copy();
                    if (product.isEmpty()) continue;
                    drop.discard();
                    context.returnItem(product);
                    spawnedProduct = true;
                }
                if (!result.consumesAction() && !changed && !spawnedProduct) context.fail();

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
