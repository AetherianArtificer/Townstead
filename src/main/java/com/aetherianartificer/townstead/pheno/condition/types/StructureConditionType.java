package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * True when the entity stands inside a generated {@code structure} (Apugli's
 * {@code structure}). Server-only; never matches on the client.
 *
 * <p>JSON: {@code { "type":"pheno:structure", "structure":"minecraft:village_plains" }}</p>
 */
public final class StructureConditionType implements ConditionType {

    public static final String KEY = "pheno:structure";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "structure", ""));
        if (id == null) return null;
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return false;
            Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
            return structure != null
                    && level.structureManager().getStructureWithPieceAt(ctx.pos(), structure).isValid();
        };
    }
}
