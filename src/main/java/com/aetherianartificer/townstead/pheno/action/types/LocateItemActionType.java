package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the nearest container within {@code radius} blocks (default 16, at most 64) that holds the
 * item in the actor's main hand, and draws a line of particles from the actor to it. With
 * {@code take}, one stack of that item moves from the container to the actor's pack; only a player
 * can take. Containers whose loot is not generated yet are not read. Fails when the hand is empty
 * or no container holds the item.
 *
 * <p>JSON: {@code { "type":"pheno:locate_item", "radius":16 }}</p>
 */
public final class LocateItemActionType implements ActionType {

    public static final String KEY = "pheno:locate_item";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int radius = Mth.clamp(GsonHelper.getAsInt(json, "radius", 16), 1, 64);
        boolean take = GsonHelper.getAsBoolean(json, "take", false);
        return ctx -> {
            LivingEntity actor = ctx.entity();
            ItemStack wanted = actor.getMainHandItem();
            if (wanted.isEmpty() || !(actor.level() instanceof ServerLevel level) || (take && !(actor instanceof Player))) {
                ctx.fail();
                return;
            }
            BlockPos center = actor.blockPosition();
            Container found = null;
            BlockPos foundAt = null;
            double best = (double) radius * radius;
            for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
                for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof Container container) || unopened(be)) continue;
                        double distance = be.getBlockPos().distSqr(center);
                        if (distance > best) continue;
                        if (!container.hasAnyMatching(stack -> ItemStack.isSameItem(stack, wanted))) continue;
                        best = distance;
                        found = container;
                        foundAt = be.getBlockPos();
                    }
                }
            }
            if (found == null) {
                ctx.fail();
                return;
            }
            Vec3 from = actor.getEyePosition();
            Vec3 to = Vec3.atCenterOf(foundAt);
            double total = from.distanceTo(to);
            for (double d = 0.5; d < total; d += 0.5) {
                double t = d / total;
                level.sendParticles(ParticleTypes.END_ROD,
                        Mth.lerp(t, from.x, to.x), Mth.lerp(t, from.y, to.y), Mth.lerp(t, from.z, to.z), 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.WAX_ON, to.x, to.y + 0.6, to.z, 8, 0.4, 0.3, 0.4, 0);
            if (!take) return;
            Player player = (Player) actor;
            for (int slot = 0; slot < found.getContainerSize(); slot++) {
                if (!ItemStack.isSameItem(found.getItem(slot), wanted)) continue;
                ItemStack stack = found.removeItemNoUpdate(slot);
                found.setChanged();
                if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
                return;
            }
        };
    }

    /** A chest that still waits for its loot: reading it would generate the loot. */
    private static boolean unopened(BlockEntity be) {
        if (!(be instanceof RandomizableContainerBlockEntity chest)) return false;
        //? if neoforge {
        return chest.getLootTable() != null;
        //?} else {
        /*return chest.saveWithoutMetadata().contains("LootTable");
        *///?}
    }
}
