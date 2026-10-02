package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.area.WorkAreas;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * True when a position lies inside a work area, such as the ground a Field Post plans. With
 * {@code area} it must be that kind; without it any kind counts. {@code at} picks the position:
 * {@code self} (default) is where the entity stands, {@code job_site} is its workstation, falling
 * back to where it stands when it has none.
 * <pre>{ "type": "pheno:in_work_area", "area": "townstead:field_post", "at": "job_site" }</pre>
 */
public final class InWorkAreaConditionType implements ConditionType {
    public static final String KEY = "pheno:in_work_area";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation area = json.has("area") ? DataPackLang.parseId(GsonHelper.getAsString(json, "area", "")) : null;
        if (json.has("area") && (area == null || !WorkAreas.isKnown(area))) return null;
        String at = GsonHelper.getAsString(json, "at", "self");
        if (!at.equals("self") && !at.equals("job_site")) return null;
        boolean jobSite = at.equals("job_site");
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level) || ctx.pos() == null) return false;
            BlockPos pos = ctx.pos();
            LivingEntity entity = ctx.entity();
            if (jobSite && entity != null && entity.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) {
                GlobalPos site = entity.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
                if (site != null && site.dimension() == level.dimension()) pos = site.pos();
            }
            return WorkAreas.covers(level, pos, area);
        };
    }
}
