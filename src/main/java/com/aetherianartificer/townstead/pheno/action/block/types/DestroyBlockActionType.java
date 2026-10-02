package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Destroys the target block (Apugli's {@code destroy}). {@code drop_item} controls
 * whether it drops as in survival breaking; the {@code cause} entity (if any) is
 * credited as the breaker. {@code collect} puts the drops straight into the cause's inventory
 * (a player's or a villager's), dropping only what does not fit, so work done on a creature's
 * behalf does not litter the ground.
 *
 * <p>JSON: {@code { "type":"pheno:destroy", "drop_item":true, "collect":true }}</p>
 */
public final class DestroyBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:destroy";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        boolean drop = GsonHelper.getAsBoolean(json, "drop_item", true);
        boolean collect = drop && GsonHelper.getAsBoolean(json, "collect", false);
        if (!collect) return ctx -> ctx.level().destroyBlock(ctx.pos(), drop, ctx.cause());
        return ctx -> {
            ServerLevel level = ctx.level();
            BlockPos pos = ctx.pos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return;
            Entity cause = ctx.cause();
            ItemStack tool = cause instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), cause, tool);
            if (!level.destroyBlock(pos, false, cause)) return;
            for (ItemStack stack : drops) {
                ItemStack rest = store(cause, stack);
                if (!rest.isEmpty()) Block.popResource(level, pos, rest);
            }
        };
    }

    /** Puts {@code stack} into the entity's inventory; returns what did not fit. */
    private static ItemStack store(Entity cause, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (cause instanceof Player player) {
            player.getInventory().add(stack);
            return stack;
        }
        if (cause instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
            return villager.getInventory().addItem(stack);
        }
        return stack;
    }
}
