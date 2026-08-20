package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.PhenoSubject;
import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.LifeStage;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@code pheno:life_stage} — the canonical stage the subject presents as
 * ({@code stage}: one of baby/toddler/child/teen/adult/senior, or a list).
 * Reads the Root's own life cycle, so "adult" means whatever adulthood means
 * for that Root, and it is the same axis the marriage and employment gates use.
 *
 * <p>{@code adult} accepts senior too, matching
 * {@link LifeStageProgression#isPreAdult}: a senior is not a pre-adult.</p>
 *
 * <p>Subject-capable: a fabricated past asks this about someone's younger self.</p>
 */
public final class LifeStageConditionType implements ConditionType {

    public static final String KEY = "pheno:life_stage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<CanonicalStage> stages = EnumSet.noneOf(CanonicalStage.class);
        JsonElement value = json.has("stage") ? json.get("stage") : json.get("is");
        if (value == null) return null;
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) add(stages, element);
        } else {
            add(stages, value);
        }
        if (stages.isEmpty()) return null;
        boolean seniorCountsAsAdult = GsonHelper.getAsBoolean(json, "senior_counts_as_adult", true)
                && stages.contains(CanonicalStage.ADULT);
        return Condition.subjectAware(ctx -> test(ctx, stages, seniorCountsAsAdult));
    }

    private static void add(Set<CanonicalStage> stages, JsonElement element) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) return;
        CanonicalStage parsed = CanonicalStage.parse(primitive.getAsString());
        if (parsed != null) stages.add(parsed);
    }

    private static boolean test(ConditionContext ctx, Set<CanonicalStage> stages,
                                boolean seniorCountsAsAdult) {
        CanonicalStage actual = resolve(ctx);
        if (actual == null) return false;
        if (stages.contains(actual)) return true;
        return seniorCountsAsAdult && actual == CanonicalStage.SENIOR;
    }

    private static @Nullable CanonicalStage resolve(ConditionContext ctx) {
        PhenoSubject subject = ctx.subject();
        if (subject != null) return subject.lifeStage();
        LivingEntity entity = ctx.entity();
        if (entity instanceof VillagerEntityMCA villager) {
            LifeStage stage = LifeStageProgression.currentStage(villager);
            if (stage != null) return stage.presentsAs();
            return villager.isBaby() ? CanonicalStage.BABY : CanonicalStage.ADULT;
        }
        // Players and other entities read as adults, as they do everywhere else.
        return entity == null ? null : CanonicalStage.ADULT;
    }
}
