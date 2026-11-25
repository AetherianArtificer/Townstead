package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.social.KeyedSocialLedger;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Set;

/**
 * Mood and heart changes held by a key, so the same cause never counts twice. The entity is the MCA villager whose mood
 * or hearts change; for hearts the other entity is the player. Kept in {@link KeyedSocialLedger}, so a restart cannot
 * reset a cap. An action that changes nothing fails, for {@code pheno:if_else} chains.
 *
 * <ul>
 *   <li>{@code { "type":"pheno:adjust_mood", "key":"my_pack:clean_home", "amount":2, "duration_ticks":24000 }}: holds
 *       {@code amount} (-15 to 15) of mood under the key, replacing what the key held. Without {@code duration_ticks}, or
 *       with 0, it lasts until cleared.</li>
 *   <li>{@code { "type":"pheno:clear_mood", "key":"my_pack:clean_home" }}: takes the key's mood back.</li>
 *   <li>{@code { "type":"pheno:adjust_hearts", "key":"my_pack:room_thanks", "amount":2, "daily_cap":2 }}: changes the
 *       villager's hearts with the player, at most {@code daily_cap} for this key a day (0 or none for no cap).</li>
 *   <li>{@code { "type":"pheno:return_hearts", "key":"my_pack:dumped_mess" }}: gives back the hearts that key took from
 *       the player, for amends.</li>
 * </ul>
 */
public final class KeyedSocialActionTypes {

    private KeyedSocialActionTypes() {}

    public static final class AdjustMood implements ActionType {
        public static final String KEY = "pheno:adjust_mood";

        @Override public String key() { return KEY; }

        @Override public Action parse(JsonObject json) {
            only(json, "type", "key", "amount", "duration_ticks");
            String key = ledgerKey(json);
            int amount = GsonHelper.getAsInt(json, "amount", 0);
            if (amount == 0 || amount < -15 || amount > 15) throw bad("amount", "must be nonzero and between -15 and 15");
            long duration = GsonHelper.getAsLong(json, "duration_ticks", 0);
            if (duration < 0) throw bad("duration_ticks", "must be >= 0");
            return ctx -> {
                if (!KeyedSocialLedger.adjustMood(ctx.entity(), key, amount, duration)) ctx.fail();
            };
        }
    }

    public static final class ClearMood implements ActionType {
        public static final String KEY = "pheno:clear_mood";

        @Override public String key() { return KEY; }

        @Override public Action parse(JsonObject json) {
            only(json, "type", "key");
            String key = ledgerKey(json);
            return ctx -> {
                if (!KeyedSocialLedger.clearMood(ctx.entity(), key)) ctx.fail();
            };
        }
    }

    public static final class AdjustHearts implements ActionType {
        public static final String KEY = "pheno:adjust_hearts";

        @Override public String key() { return KEY; }

        @Override public Action parse(JsonObject json) {
            only(json, "type", "key", "amount", "daily_cap");
            String key = ledgerKey(json);
            int amount = GsonHelper.getAsInt(json, "amount", 0);
            if (amount == 0 || amount < -100 || amount > 100) throw bad("amount", "must be nonzero and between -100 and 100");
            int cap = GsonHelper.getAsInt(json, "daily_cap", 0);
            if (cap < 0) throw bad("daily_cap", "must be >= 0");
            return ctx -> {
                if (KeyedSocialLedger.adjustHearts(ctx.entity(), ctx.other(), key, amount, cap) == 0) ctx.fail();
            };
        }
    }

    public static final class ReturnHearts implements ActionType {
        public static final String KEY = "pheno:return_hearts";

        @Override public String key() { return KEY; }

        @Override public Action parse(JsonObject json) {
            only(json, "type", "key");
            String key = ledgerKey(json);
            return ctx -> {
                if (KeyedSocialLedger.returnHearts(ctx.entity(), ctx.other(), key) == 0) ctx.fail();
            };
        }
    }

    private static String ledgerKey(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "").trim();
        if (key.isEmpty() || net.minecraft.resources.ResourceLocation.tryParse(key) == null || !key.contains(":")) {
            throw bad("key", "expected a namespaced id");
        }
        return key;
    }

    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }

    private static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
