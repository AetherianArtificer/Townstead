package com.aetherianartificer.townstead.farming;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A farmer's right-click on a crop, through the server's full player-interaction path, so a mod's
 * own handling runs (TFC picking peppers, TFC sticks on climbing crops). Same transaction rules as
 * {@code pheno:use_block}: the fake player starts and ends empty, and anything handed to it or
 * dropped beside the block comes back to the caller.
 */
public final class FarmerBlockUse {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("7d2c4b1e-5a0f-4f63-9b8e-3c1f6a2d9e47"), "[TownsteadFarmer]");

    /** What came of one use: whether anything happened, the held item after, and the products. */
    public record Result(boolean acted, ItemStack held, List<ItemStack> returned) {
        static Result failed(ItemStack held) {
            return new Result(false, held, List.of());
        }
    }

    private FarmerBlockUse() {}

    public static Result use(ServerLevel level, BlockPos pos, ItemStack held) {
        ItemStack supplied = held.copy();
        ServerPlayer actor;
        try {
            actor = FakePlayerFactory.get(level, PROFILE);
        } catch (Throwable failure) {
            return Result.failed(supplied);
        }
        try {
            actor.getInventory().clearContent();
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            actor.setShiftKeyDown(false);
            actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            actor.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            actor.setXRot(90.0f);
            actor.setItemInHand(InteractionHand.MAIN_HAND, supplied);

            BlockState before = level.getBlockState(pos);
            ItemStack beforeItem = supplied.copy();
            AABB area = new AABB(pos).inflate(2.0);
            Set<UUID> existing = new HashSet<>();
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) existing.add(drop.getUUID());

            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            InteractionResult result = actor.gameMode.useItemOn(actor, level, actor.getMainHandItem(),
                    InteractionHand.MAIN_HAND, hit);
            boolean changed = !before.equals(level.getBlockState(pos))
                    || !ItemStack.matches(beforeItem, actor.getMainHandItem());

            List<ItemStack> returned = new ArrayList<>();
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area,
                    candidate -> !existing.contains(candidate.getUUID()))) {
                ItemStack product = drop.getItem().copy();
                if (product.isEmpty()) continue;
                drop.discard();
                returned.add(product);
            }
            ItemStack after = actor.getMainHandItem().copy();
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
                ItemStack stack = actor.getInventory().removeItemNoUpdate(slot);
                if (!stack.isEmpty()) returned.add(stack);
            }
            boolean acted = result.consumesAction() || changed || !returned.isEmpty();
            return new Result(acted, after, returned);
        } catch (Throwable failure) {
            return Result.failed(supplied);
        } finally {
            actor.getInventory().clearContent();
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }
}
