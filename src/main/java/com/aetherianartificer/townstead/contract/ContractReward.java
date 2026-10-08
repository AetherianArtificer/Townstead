package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.story.reward.Reward;
import com.aetherianartificer.townstead.story.reward.Rewards;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One reward of a contract, by {@code type}:
 * <ul>
 * <li>{@code townstead:item}: {@code item}, {@code count}.</li>
 * <li>{@code townstead:currency}: emeralds, {@code min} to {@code max}, or by {@code difficulty}
 * (the contract's own when not given): easy 2 to 4, medium 4 to 8, hard 8 to 14. Rolled once,
 * when the contract is offered.</li>
 * <li>{@code townstead:xp}: {@code amount} experience points.</li>
 * <li>{@code townstead:loot_table}: {@code loot_table}, with a {@code text} for the Ledger.</li>
 * <li>{@code townstead:action}: a Pheno {@code action} on the player, with a {@code text}.</li>
 * </ul>
 */
public final class ContractReward {
    static final Set<String> TYPES = Set.of("townstead:item", "townstead:currency", "townstead:xp",
            "townstead:loot_table", "townstead:action");

    private final @Nullable Reward reward;
    private final int xp;

    private ContractReward(@Nullable Reward reward, int xp) {
        this.reward = reward;
        this.xp = xp;
    }

    static @Nullable String check(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type", "");
        if (!TYPES.contains(type)) return "unknown reward type '" + type + "'";
        return switch (type) {
            case "townstead:item" -> json.has("item") ? null : "item needs \"item\"";
            case "townstead:xp" -> json.has("amount") ? null : "xp needs \"amount\"";
            case "townstead:loot_table" -> json.has("loot_table") && json.has("text") ? null : "loot_table needs \"loot_table\" and \"text\"";
            case "townstead:action" -> json.has("action") && json.has("text") ? null : "action needs \"action\" and \"text\"";
            default -> null;
        };
    }

    /** The emerald range for a currency reward. */
    static int[] currencyRange(JsonObject json, String contractDifficulty) {
        if (json.has("min") || json.has("max")) {
            int min = GsonHelper.getAsInt(json, "min", 1), max = GsonHelper.getAsInt(json, "max", min);
            return new int[]{Math.min(min, max), Math.max(min, max)};
        }
        return switch (GsonHelper.getAsString(json, "difficulty", contractDifficulty)) {
            case "easy" -> new int[]{2, 4};
            case "hard" -> new int[]{8, 14};
            default -> new int[]{4, 8};
        };
    }

    /** Builds a reward with its template values in; currency arrives already rolled as {@code count}. */
    static @Nullable ContractReward parse(JsonObject json, String namespace, String text) {
        String type = GsonHelper.getAsString(json, "type");
        if (type.equals("townstead:xp")) return new ContractReward(null, Math.max(0, GsonHelper.getAsInt(json, "amount")));
        JsonObject shape = new JsonObject();
        switch (type) {
            case "townstead:item", "townstead:currency" -> {
                JsonObject stack = new JsonObject();
                stack.addProperty("item", type.equals("townstead:currency") ? "minecraft:emerald" : GsonHelper.getAsString(json, "item"));
                stack.addProperty("count", GsonHelper.getAsInt(json, "count", 1));
                JsonArray items = new JsonArray();
                items.add(stack);
                shape.add("items", items);
            }
            case "townstead:loot_table" -> {
                shape.addProperty("loot_table", GsonHelper.getAsString(json, "loot_table"));
                shape.addProperty("text", text);
            }
            case "townstead:action" -> {
                shape.add("action", json.get("action"));
                shape.addProperty("text", text);
            }
            default -> {
                return null;
            }
        }
        Rewards.Parsed parsed = Rewards.resolve("r", namespace, Map.of("r", shape));
        return parsed.reward() == null ? null : new ContractReward(parsed.reward(), 0);
    }

    void give(ServerPlayer player, @Nullable LivingEntity giver) {
        if (reward != null) reward.give(player, giver);
        if (xp > 0) player.giveExperiencePoints(xp);
    }

    List<Reward.Preview> preview(String giver, String player, String xpText) {
        if (reward != null) return reward.preview(giver, player);
        return List.of(new Reward.Preview(xpText, "minecraft:experience_bottle", xp));
    }
}
