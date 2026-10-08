package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.condition.ChronicleCountConditionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.BondCountConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LifeStageConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.ProfessionConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.ValueConditionType;
import com.aetherianartificer.townstead.pheno.value.ValueTypes;
import com.aetherianartificer.townstead.pheno.value.types.BondCountValueType;
import com.aetherianartificer.townstead.pheno.value.types.BondMaxValueType;

/**
 * Registers the condition types a fabricated past can actually answer, using
 * the real registry and the real types. Everything else stays unregistered
 * here: a template gated on live world state parses with a null condition, and
 * {@link SimTemplates} reports it rather than letting it run ungated.
 */
public final class SimConditions {

    private SimConditions() {}

    public static void register() {
        ValueTypes.register(new BondCountValueType());
        ValueTypes.register(new BondMaxValueType());
        ConditionTypes.register(new ValueConditionType());
        ConditionTypes.register(new LifeStageConditionType());
        ConditionTypes.register(new BondCountConditionType());
        ConditionTypes.register(new ProfessionConditionType());
        ConditionTypes.register(new ChronicleCountConditionType());
        ConditionTypes.register(new LogicConditionType("pheno:and", LogicConditionType.Mode.AND));
        ConditionTypes.register(new LogicConditionType("pheno:or", LogicConditionType.Mode.OR));
        ConditionTypes.register(new LogicConditionType("pheno:not", LogicConditionType.Mode.NOT));
    }
}
