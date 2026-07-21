package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:career_xp} — tests a character's Career XP:
 * {@code { "type": "pheno:career_xp", "career": "townstead:cook", "at_least": 110 }}
 * (a bare name resolves against registered defs by path).
 *
 * <p>The Careers counterpart to {@code pheno:chronicle_count}: chronicle counters are the
 * historical evidence, Career XP is the within-vocation progression. Server-only, since
 * the client holds no Career profile mirror.</p>
 */
public final class CareerXpConditionType implements ConditionType {

    public static final String KEY = "pheno:career_xp";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String career = GsonHelper.getAsString(json, "career");
        int atLeast = GsonHelper.getAsInt(json, "at_least", 1);
        int atMost = GsonHelper.getAsInt(json, "at_most", Integer.MAX_VALUE);
        return ctx -> test(ctx, career, atLeast, atMost);
    }

    private static boolean test(ConditionContext ctx, String career, int atLeast, int atMost) {
        if (ctx.level().isClientSide) return false;
        CareerProfile profile = CareerProfiles.of(ctx.entity());
        if (profile == null) return false;
        // Resolved at test time so terse pack JSON ("cook") matches the registered def's full id.
        int xp = profile.professionXp(Careers.resolve(career)).xp();
        return xp >= atLeast && xp <= atMost;
    }
}
