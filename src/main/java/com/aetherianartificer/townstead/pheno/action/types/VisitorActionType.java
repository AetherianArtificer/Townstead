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
 * from the edge to the player. With {@code thrall}, a second visitor comes with them, bound as
 * their thrall. Nothing happens when a visitor with the same role is already near.
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
        return ctx -> {
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            if (player == null) {
                ctx.fail();
                return;
            }
            if (Visitors.near(player, role, ALREADY_HERE) != null) return;
            Village village = VillageManager.get(player.serverLevel())
                    .findNearestVillage(player.blockPosition(), Village.MERGE_MARGIN).orElse(null);
            if (village == null || Visitors.arrive(player, village, spec) == null) ctx.fail();
        };
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
        @Nullable Visitors.Spec thrall = json.has("thrall") ? spec(role, GsonHelper.getAsJsonObject(json, "thrall")) : null;
        return new Visitors.Spec(role, states, gender, profession, thrall);
    }
}
