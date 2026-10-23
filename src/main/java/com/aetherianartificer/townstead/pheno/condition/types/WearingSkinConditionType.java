package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * True while the entity wears one of the named MCA clothing skins: villagers and players alike,
 * since a player's clothes are part of their MCA appearance. {@code skin} is an id, a pattern ending
 * in {@code *}, or a list of either. Reads the same synced value on both sides, so it can gate work
 * clothes the way a profession gates them on MCA's own villagers.
 *
 * <p>JSON: {@code { "type":"pheno:wearing_skin", "skin":"my_pack:skins/clothing/frog/gardener*" }}</p>
 */
public final class WearingSkinConditionType implements ConditionType {

    public static final String KEY = "pheno:wearing_skin";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        List<String> patterns = new ArrayList<>();
        JsonElement raw = json.get("skin");
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement element : raw.getAsJsonArray()) patterns.add(element.getAsString());
        } else if (raw != null && raw.isJsonPrimitive()) {
            patterns.add(raw.getAsString());
        }
        patterns.removeIf(String::isEmpty);
        if (patterns.isEmpty()) return null;
        return ctx -> matches(clothesOf(ctx), patterns);
    }

    private static boolean matches(@Nullable String clothes, List<String> patterns) {
        if (clothes == null || clothes.isEmpty()) return false;
        for (String pattern : patterns) {
            if (pattern.endsWith("*") ? clothes.startsWith(pattern.substring(0, pattern.length() - 1))
                    : pattern.equals(clothes)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static String clothesOf(ConditionContext ctx) {
        if (ctx.entity() instanceof VillagerLike<?> villager) return villager.getClothes();
        if (ctx.entity() instanceof net.minecraft.world.entity.player.Player player) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                return net.conczin.mca.server.world.data.PlayerSaveData.get(serverPlayer)
                        .getEntityData().getString("Clothes");
            }
            if (player.level().isClientSide()) return ClientPlayerClothes.of(player);
        }
        return null;
    }

    /** Client-only MCAClient access, isolated so dedicated servers never load it. */
    private static final class ClientPlayerClothes {
        static String of(net.minecraft.world.entity.player.Player player) {
            var data = net.conczin.mca.MCAClient.playerData.get(player.getUUID());
            return data == null ? null : data.getClothes();
        }
    }
}
