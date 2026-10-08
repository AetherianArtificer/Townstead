package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.persona.PersonaService;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Brings {@code count} creatures of {@code entity_type} to the player's village: each spawns out of
 * the player's sight at the village edge and, with {@code walk_in} (the default), heads for the
 * middle of the village. For a story that says trouble is coming, and means it.
 * <pre>
 * { "type": "pheno:spawn_at_edge", "entity_type": "vampirism:vampire", "count": 5 }
 * </pre>
 */
public final class SpawnAtEdgeActionType implements ActionType {
    public static final String KEY = "pheno:spawn_at_edge";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation typeId = DataPackLang.parseId(GsonHelper.getAsString(json, "entity_type", ""));
        if (typeId == null) return null;
        int count = Math.max(1, Math.min(16, GsonHelper.getAsInt(json, "count", 1)));
        boolean walkIn = GsonHelper.getAsBoolean(json, "walk_in", true);
        return ctx -> {
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
            if (player == null || type == null) {
                ctx.fail();
                return;
            }
            ServerLevel level = player.serverLevel();
            Village village = VillageManager.get(level).findNearestVillage(player.blockPosition(), Village.MERGE_MARGIN).orElse(null);
            if (village == null) {
                ctx.fail();
                return;
            }
            Vec3i center = village.getCenter();
            int spawned = 0;
            for (int i = 0; i < count; i++) {
                BlockPos at = PersonaService.arrivalPoint(level, village, player);
                Entity entity = type.create(level);
                if (entity == null) continue;
                entity.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
                if (entity instanceof Mob mob) {
                    //? if >=1.21 {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
                    //?} else {
                    /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
                    *///?}
                }
                if (!level.addFreshEntity(entity)) continue;
                spawned++;
                if (walkIn && entity instanceof Mob mob) {
                    mob.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
                }
            }
            if (spawned == 0) ctx.fail();
        };
    }
}
