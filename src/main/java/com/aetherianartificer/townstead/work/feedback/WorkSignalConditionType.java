package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** {@code townstead:work_signal} reads a namespaced fact supplied by a work engine. */
public final class WorkSignalConditionType implements ConditionType {
    public static final String KEY = "townstead:work_signal";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation signal = DataPackLang.parseId(
                GsonHelper.getAsString(json, "signal", ""));
        if (signal == null) return null;
        return ctx -> ctx.entity() instanceof VillagerEntityMCA villager
                && WorkFeedbackSignals.test(signal, villager);
    }
}
