package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.types.NothingActionType;
import com.aetherianartificer.townstead.pheno.action.types.ReleaseActionType;
import com.aetherianartificer.townstead.pheno.action.types.ReserveActionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.types.ReservedConditionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorTypes;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.aetherianartificer.townstead.pheno.selector.types.ReservationSelectorType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReservationVocabularyTest {

    @BeforeAll
    static void registerVocabulary() {
        ActionTypes.register(new ReserveActionType());
        ActionTypes.register(new ReleaseActionType());
        ActionTypes.register(new NothingActionType());
        SelectorTypes.register(new ReservationSelectorType());
        ConditionTypes.register(new ReservedConditionType());
    }

    @Test
    void allFourNodesParseInTheirNativeDomains() {
        assertNotNull(Actions.parse(JsonParser.parseString("""
                {"type":"pheno:reserve","on":"self",
                 "action":{"type":"pheno:nothing"}}
                """)));
        assertNotNull(Actions.parse(JsonParser.parseString("""
                {"type":"pheno:release"}
                """)));
        assertNotNull(Selectors.parse(JsonParser.parseString("""
                {"type":"pheno:reservation"}
                """)));
        assertNotNull(Conditions.parse(JsonParser.parseString("""
                {"type":"pheno:reserved"}
                """)));
    }

    @Test
    void rootActiveAbilityUsesTheSameReservationAction() {
        assertNotNull(new ActiveAbilityGeneType().parse(JsonParser.parseString("""
                {"type":"pheno:active_ability","cooldown":20,
                 "action":{"type":"pheno:reserve","on":"target",
                   "action":{"type":"pheno:nothing"}}}
                """).getAsJsonObject()));
    }
}
