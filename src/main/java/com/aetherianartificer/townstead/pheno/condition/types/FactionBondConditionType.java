package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity holds an active political Bond of {@code kind} with a faction, such as an
 * office or an oath.
 * <pre>
 *   { "type": "pheno:faction_bond", "kind": "townstead:lodge_master" }
 * </pre>
 */
public final class FactionBondConditionType implements com.aetherianartificer.townstead.pheno.condition.ConditionType {
    public static final String KEY = "pheno:faction_bond";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation kind = DataPackLang.parseId(GsonHelper.getAsString(json, "kind", ""));
        if (kind == null) return null;
        return ctx -> {
            if (ctx.entity() == null || !(ctx.entity().level() instanceof ServerLevel level)) return false;
            for (BondInstance bond : PoliticalSavedData.get(level.getServer()).activeBonds(Party.person(ctx.entity().getUUID()))) {
                if (bond.kind().equals(kind)) return true;
            }
            return false;
        };
    }
}
