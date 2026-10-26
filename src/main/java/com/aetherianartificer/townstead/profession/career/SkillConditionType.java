package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/** {@code pheno:skill} — whether the subject has learned one exact Career Skill. */
public final class SkillConditionType implements ConditionType {
    public static final String KEY = "pheno:skill";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public @Nullable Condition parse(JsonObject json) {
        String raw = GsonHelper.getAsString(json, "skill", "");
        if (raw.isBlank()) return null;
        ResourceLocation skill = ResourceLocation.tryParse(raw);
        return skill == null ? null : ctx -> LearnedSkills.has(ctx.entity(), skill);
    }
}
