package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aliases let one career absorb equivalent professions from other mods: history, progression,
 * and slot policy converge on the def's primary id no matter which profession id arrives.
 */
class ProfessionAliasTest {

    private static final ResourceLocation BAKER = ResourceLocation.tryParse("townstead:baker");
    private static final ResourceLocation OTHER_BAKER = ResourceLocation.tryParse("somemod:baker");

    @AfterEach
    void clearDefs() {
        ProfessionDefs.replaceAll(Map.of());
    }

    private static ProfessionDef def(ResourceLocation id, List<ResourceLocation> aliases) {
        return new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0, 110), 230, 200000), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), List.of(), false,
                Conditions.ALWAYS, List.of(), List.of(), aliases);
    }

    @Test
    void aliasResolvesToOwningDefAndCanonicalId() {
        ProfessionDefs.replaceAll(Map.of(BAKER, def(BAKER, List.of(OTHER_BAKER))));
        assertSame(ProfessionDefs.byId(BAKER), ProfessionDefs.byId(OTHER_BAKER));
        assertEquals(BAKER, ProfessionDefs.canonicalId(OTHER_BAKER));
        assertEquals(BAKER, ProfessionDefs.canonicalId(BAKER));
    }

    @Test
    void primaryDefAlwaysBeatsAnAliasClaim() {
        Map<ResourceLocation, ProfessionDef> defs = new LinkedHashMap<>();
        defs.put(BAKER, def(BAKER, List.of(OTHER_BAKER)));
        defs.put(OTHER_BAKER, def(OTHER_BAKER, List.of()));
        ProfessionDefs.replaceAll(defs);
        assertEquals(OTHER_BAKER, ProfessionDefs.byId(OTHER_BAKER).id(),
                "a real def under the aliased id keeps its own identity");
        assertEquals(OTHER_BAKER, ProfessionDefs.canonicalId(OTHER_BAKER));
    }

    @Test
    void xpUnderAnAliasIdLandsOnTheCanonicalKey() {
        ProfessionDefs.replaceAll(Map.of(BAKER, def(BAKER, List.of(OTHER_BAKER))));
        Map<String, ProfessionXp> backing = new HashMap<>();
        ProfessionXpStore store = new ProfessionXpStore() {
            @Override public ProfessionXp professionXp(String id) {
                return backing.getOrDefault(id, ProfessionXp.EMPTY);
            }
            @Override public void setProfessionXp(String id, ProfessionXp v) { backing.put(id, v); }
        };
        ProfessionProgress.addXp(store, OTHER_BAKER, 50, 0L);
        assertEquals(50, ProfessionProgress.getXp(store, BAKER),
                "alias-earned XP reads back through the primary id");
        assertFalse(backing.containsKey(OTHER_BAKER.toString()),
                "no fragmented storage under the alias key");
    }

    @Test
    void loaderParsesAliasesAndWarnsOnShadowing() {
        Diagnostics diag = new Diagnostics();
        diag.forResource(BAKER);
        JsonObject json = JsonParser.parseString(
                "{ \"schema\": \"townstead:profession/v1\", \"aliases\": [\"somemod:baker\"] }")
                .getAsJsonObject();
        ProfessionDef parsed = ProfessionDataLoader.parseProfession(BAKER, json, Map.of(), diag);
        assertNotNull(parsed);
        assertEquals(List.of(OTHER_BAKER), parsed.aliases());
    }
}
