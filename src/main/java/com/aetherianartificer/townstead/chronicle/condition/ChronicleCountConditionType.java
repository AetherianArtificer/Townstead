package com.aetherianartificer.townstead.chronicle.condition;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:chronicle_count} — tests a chronicle counter for the entity:
 * {@code { "type": "pheno:chronicle_count", "key": "townstead:cooked", "at_least": 100 }}.
 *
 * <p>This is the Careers mastery contract ("cooked 100 meals"): a truth-side
 * read of record-time counters. Server-only — the client has no counter
 * mirror, so client evaluation is always false; do not gate client-predicted
 * visuals on it.</p>
 */
public final class ChronicleCountConditionType implements ConditionType {

    public static final String KEY = "pheno:chronicle_count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String counterKey = GsonHelper.getAsString(json, "key");
        int atLeast = GsonHelper.getAsInt(json, "at_least", 1);
        int atMost = GsonHelper.getAsInt(json, "at_most", Integer.MAX_VALUE);
        return ctx -> test(ctx, counterKey, atLeast, atMost);
    }

    private static boolean test(ConditionContext ctx, String key, int atLeast, int atMost) {
        if (ctx.level().isClientSide) return false;
        MinecraftServer server = ctx.level().getServer();
        if (server == null) return false;
        int count = Chronicles.count(server, ctx.entity().getUUID(), key);
        return count >= atLeast && count <= atMost;
    }
}
