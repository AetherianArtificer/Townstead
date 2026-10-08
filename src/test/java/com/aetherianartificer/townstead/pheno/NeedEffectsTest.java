package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.needs.NeedEffectProjection;
import com.aetherianartificer.townstead.needs.ConsumableEffectsSyncPayload;
import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.types.EnergizeActionType;
import com.aetherianartificer.townstead.pheno.action.types.HydrateActionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class NeedEffectsTest {
    @BeforeAll
    static void register() {
        ActionTypes.register(new HydrateActionType());
        ActionTypes.register(new EnergizeActionType());
    }

    @Test
    void hydrateAndEnergizeAreOrdinaryPhenoActions() {
        assertNotNull(Actions.parse(JsonParser.parseString(
                "{\"type\":\"pheno:hydrate\",\"immediate\":6,\"lasting\":8}")));
        assertNotNull(Actions.parse(JsonParser.parseString(
                "{\"type\":\"pheno:energize\",\"amount\":5}")));
    }

    @Test
    void plannerProjectsOnlyUnconditionalConstantNeedEffects() {
        JsonElement effects = JsonParser.parseString("""
                [
                  {"type":"pheno:hydrate","immediate":6,"lasting":8},
                  {"type":"pheno:energize","amount":5},
                  {"type":"pheno:chance","chance":0.5,
                   "action":{"type":"pheno:hydrate","immediate":99,"lasting":99}}
                ]
                """);
        NeedEffectProjection projection = NeedEffectProjection.project(effects);
        assertEquals(6, projection.immediateHydration());
        assertEquals(8, projection.lastingHydration());
        assertEquals(5, projection.energy());
    }

    @Test
    void consumableProjectionSurvivesServerClientSync() {
        ConsumableEffectsSyncPayload original = new ConsumableEffectsSyncPayload(List.of(
                new ConsumableEffectsSyncPayload.Row(
                        ResourceLocation.tryParse("example:tea"), 6, 8, 3, true)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        original.write(buffer);

        ConsumableEffectsSyncPayload decoded = ConsumableEffectsSyncPayload.read(buffer);
        assertEquals(original, decoded);
        assertEquals(new NeedEffectProjection(6, 8, 3), decoded.rows().get(0).projection());
        assertTrue(decoded.rows().get(0).fallback());
    }
}
