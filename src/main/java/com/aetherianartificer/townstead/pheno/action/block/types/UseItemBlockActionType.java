package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}

import java.util.UUID;

/**
 * Uses the item role in the air while aimed at the target block, the way a player uses a bottle or a
 * bucket on water: the item's own use ray finds the block, so a fluid source fills the container
 * through the item's real behavior. {@code pheno:use_block} cannot do this, because a block
 * interaction never reaches an item that acts on the fluid in front of it. By default the stand-in
 * stands on the block and looks straight down at it; {@code actor_offset} and {@code hit} move it.
 */
public final class UseItemBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:use_item";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("7a3e2c1d-5b4f-4e8a-9c6d-2f1e0b3a4c5d"), "[TownsteadWorker]");
    private static final Vec3 STAND_ON_TOP = new Vec3(0, 0.5, 0);

    @Override public String key() { return KEY; }

    @Override
    public BlockAction parse(JsonObject json) {
        BlockInteractionGeometry geometry = BlockInteractionGeometry.parse(json);
        if (geometry == null) return null;
        String role = json.has("item") ? json.get("item").getAsString() : "item";
        return context -> {
            ItemStack supplied = context.itemRole(role).copy();
            if (supplied.isEmpty()) { context.fail(); return; }
            ServerPlayer actor;
            try { actor = FakePlayerFactory.get(context.level(), PROFILE); }
            catch (Throwable failure) { context.fail(); return; }
            try {
                actor.getInventory().clearContent();
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                actor.setShiftKeyDown(false);
                actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                Vec3 center = Vec3.atCenterOf(context.pos());
                Vec3 feet = geometry.actorOffset() == null ? center.add(STAND_ON_TOP)
                        : geometry.actorPosition(context.pos(), null);
                actor.setPos(feet.x, feet.y, feet.z);
                Vec3 target = geometry.hit(context.pos()).getLocation();
                Vec3 look = target.subtract(actor.getEyePosition());
                double flat = Math.sqrt(look.x * look.x + look.z * look.z);
                actor.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0));
                actor.setXRot((float) -Math.toDegrees(Math.atan2(look.y, flat)));
                actor.setYHeadRot(actor.getYRot());
                actor.setItemInHand(InteractionHand.MAIN_HAND, supplied);
                ItemStack before = actor.getMainHandItem().copy();
                com.aetherianartificer.townstead.pheno.action.block.StandIns.begin(actor, context.cause());
                InteractionResult result;
                try {
                    result = actor.gameMode.useItem(actor, context.level(), actor.getMainHandItem(), InteractionHand.MAIN_HAND);
                } finally {
                    com.aetherianartificer.townstead.pheno.action.block.StandIns.end(actor);
                }
                boolean changed = !ItemStack.matches(before, actor.getMainHandItem());
                context.setItemRole(role, actor.getMainHandItem().copy());
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                boolean returned = false;
                for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
                    ItemStack extra = actor.getInventory().removeItemNoUpdate(slot);
                    if (!extra.isEmpty()) returned = true;
                    context.returnItem(extra);
                }
                if (!result.consumesAction() && !changed && !returned) context.fail();
            } catch (Throwable failure) {
                context.setItemRole(role, supplied);
                context.fail();
            } finally {
                actor.getInventory().clearContent();
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        };
    }
}
