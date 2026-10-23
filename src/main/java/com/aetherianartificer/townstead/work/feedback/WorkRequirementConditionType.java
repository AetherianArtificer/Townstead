package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.work.job.BlockInteractionWorkTask;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/** Reads one named, data-authored requirement from a block-interaction Job. */
public final class WorkRequirementConditionType implements ConditionType {
    public static final String KEY = "townstead:work_requirement";

    @Override public String key() { return KEY; }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation job = DataPackLang.parseId(GsonHelper.getAsString(json, "job", ""));
        String requirement = GsonHelper.getAsString(json, "requirement", "");
        String state = GsonHelper.getAsString(json, "state", "unsatisfied")
                .toLowerCase(Locale.ROOT);
        if (job == null || requirement.isEmpty() || !switch (state) {
            case "satisfied", "unsatisfied", "provisionable", "missing_source", "missing_input" -> true;
            default -> false;
        }) return null;
        return context -> {
            if (!(context.entity() instanceof VillagerEntityMCA villager)
                    || !(villager.level() instanceof ServerLevel level)) return false;
            BlockInteractionWorkTask.RequirementState actual =
                    BlockInteractionWorkTask.requirementState(level, villager, job, requirement);
            return switch (state) {
                case "satisfied" -> actual == BlockInteractionWorkTask.RequirementState.SATISFIED;
                case "unsatisfied" -> actual != BlockInteractionWorkTask.RequirementState.NOT_APPLICABLE
                        && actual != BlockInteractionWorkTask.RequirementState.SATISFIED;
                case "provisionable" -> actual == BlockInteractionWorkTask.RequirementState.PROVISIONABLE;
                case "missing_source" -> actual == BlockInteractionWorkTask.RequirementState.MISSING_SOURCE;
                case "missing_input" -> actual == BlockInteractionWorkTask.RequirementState.MISSING_INPUT;
                default -> false;
            };
        };
    }
}
