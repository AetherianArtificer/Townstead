package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.visitor.Visitors;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brings a one-off visitor into the player's village: made with the given states, they walk in
 * from the edge to the player. With {@code "from": "near"} they come up from {@code distance}
 * blocks away instead, wherever the player is, village or not. With {@code thrall}, a second
 * visitor comes with them, bound as their thrall, with the role {@code <role>_thrall}. Nothing
 * happens when a visitor with the same role is already near.
 * <pre>
 * { "type": "pheno:visitor", "role": "fledgling", "states": { "townstead_state:vampire": 1 } }
 * { "type": "pheno:visitor", "role": "vampire_couple", "states": { "townstead_state:vampire": 1 },
 *   "thrall": { "states": { "townstead_state:thrall": 1 } } }
 * </pre>
 */
public final class VisitorActionType implements ActionType {
    public static final String KEY = "pheno:visitor";
    private static final double ALREADY_HERE = 96;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "role", "");
        if (role.isBlank()) return null;
        Visitors.Spec spec = spec(role, json);
        boolean near = "near".equals(GsonHelper.getAsString(json, "from", "edge"));
        int distance = Math.max(2, GsonHelper.getAsInt(json, "distance", 12));
        return ctx -> {
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            if (player == null) {
                ctx.fail();
                return;
            }
            if (Visitors.near(player, role, ALREADY_HERE) != null) return;
            if (near) {
                net.minecraft.core.BlockPos at = nearPoint(player, distance);
                if (at == null || Visitors.arrive(player, at, spec) == null) ctx.fail();
                return;
            }
            Village village = VillageManager.get(player.serverLevel())
                    .findNearestVillage(player.blockPosition(), Village.MERGE_MARGIN).orElse(null);
            if (village == null || Visitors.arrive(player, village, spec) == null) ctx.fail();
        };
    }

    /** Open standing ground about {@code distance} blocks from the player, or null. */
    private static @Nullable net.minecraft.core.BlockPos nearPoint(ServerPlayer player, int distance) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        for (int i = 0; i < 16; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            for (int dy = 4; dy >= -6; dy--) {
                net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(x, player.getBlockY() + dy, z);
                if (level.getBlockState(feet.below()).isSolidRender(level, feet.below())
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static Visitors.Spec spec(String role, JsonObject json) {
        Map<ResourceLocation, Double> states = new LinkedHashMap<>();
        JsonObject raw = GsonHelper.getAsJsonObject(json, "states", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            ResourceLocation id = DataPackLang.parseId(entry.getKey());
            if (id != null) states.put(id, entry.getValue().getAsDouble());
        }
        String gender = json.has("gender") ? GsonHelper.getAsString(json, "gender") : null;
        ResourceLocation profession = json.has("profession") ? DataPackLang.parseId(GsonHelper.getAsString(json, "profession")) : null;
        @Nullable Visitors.Spec thrall = json.has("thrall") ? spec(role + "_thrall", GsonHelper.getAsJsonObject(json, "thrall")) : null;
        return new Visitors.Spec(role, states, gender, profession, thrall);
    }
}
