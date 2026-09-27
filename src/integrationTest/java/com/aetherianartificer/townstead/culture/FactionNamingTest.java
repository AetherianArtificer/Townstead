package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.politics.charter.CharterIdentityService;
import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionNamingTest {
    @Test
    void culturalNamesPatternsAndAuthority() {
        var culture = id("test:culture"); var kindId = id("test:government"); var rulerId = id("test:ruler");
        var govern = id("townstead:govern_faction");
        var poolJson = JsonParser.parseString("{\"names\":[\"Thorncourt\"]}").getAsJsonObject();
        var parsed = SettlementNameJsonLoader.parse(culture, poolJson);
        FactionNaming.replace(Map.of(culture, new SettlementNamePool(culture, parsed.forms(), parsed.values())));
        Cultures.replace(Map.of(culture, new Culture(culture, Component.literal("Culture"), null, null, CultureClothing.NONE, culture)));
        var ruler = BondKind.parse(rulerId, JsonParser.parseString("""
                {"schema":"townstead:bond/v2","roles":{
                  "faction":{"party":"faction","gives":["townstead:govern_faction"]},
                  "ruler":{"party":"person"}}}
                """).getAsJsonObject(), Map.of());
        var kind = FactionKind.parse(kindId, JsonParser.parseString("""
                {"schema":"townstead:faction/v1",
                 "membership":{"bond":"townstead:citizenship","admission":"townstead:residence"},
                 "offices":[{"bond":"test:ruler","min":1,"max":1}],
                 "presentation":{"faction_name_patterns":["League of {name}"]}}
                """).getAsJsonObject(), Map.of());
        var previousBonds = BondKinds.all();
        var previousKinds = PoliticalDefinitions.snapshot().factionKinds();
        try {
            BondKinds.replaceAll(Map.of(rulerId, ruler));
            PoliticalDefinitions.replace(Map.of(kindId, kind));
            var generated = FactionNaming.generate(culture, kindId, "Reedwater");
            assertTrue(generated.display().equals("League of Thorncourt"), "Culture/kind composition failed");
            assertTrue(generated.base().equals("Thorncourt") && !generated.custom(), "Base name was not retained");
            assertTrue(FactionNaming.review(culture, kindId, generated.base(), generated.pattern(), generated.display()).equals(generated), "Reviewed origin lost");
            assertTrue(FactionNaming.review(culture, kindId, "invented", generated.pattern(), "My Faction").custom(), "Custom name misrepresented as generated");
            assertTrue(FactionNaming.generate(null, null, "Reedwater").display().equals("Reedwater"), "No-culture fallback failed");
            assertTrue(FactionNaming.generate(culture, id("unknown:government"), "Reedwater").display().equals("Thorncourt"), "Unknown kind imposed a title");
            var data = new PoliticalSavedData(); var settlement = new SettlementRef(id("minecraft:overworld"), 111);
            var faction = new Faction(id("test:faction"), kindId, "Reedwater", 0, null, 0, kindId, Faction.Status.ACTIVE, List.of(settlement), null);
            data.putFaction(faction); FactionNaming.initialize(data, faction.id(), generated);
            UUID holder = UUID.randomUUID(), visitor = UUID.randomUUID();
            var office = FactionBonds.form(data, rulerId, FactionBonds.sides(rulerId, faction.id(), holder), kindId, 1).bond();
            assertTrue(PoliticalAuthority.mayAct(data, holder, faction.id(), govern).allowed(), "Ruler denied identity authority");
            assertTrue(!PoliticalAuthority.mayAct(data, visitor, faction.id(), govern).allowed(), "Visitor granted identity authority");
            FactionBonds.end(data, office, 5, "resigned");
            assertTrue(!PoliticalAuthority.mayAct(data, holder, faction.id(), govern).allowed(), "An ended office kept its authority");
            //? if >=1.21 {
            var copy = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
            //?} else {
            /*var copy = PoliticalSavedData.load(data.save(new CompoundTag()));
            *///?}
            assertTrue(copy.factionName(faction.id()).equals(generated), "Naming provenance lost on reload");
            assertTrue(CharterIdentityService.rename(copy, faction.id(), generated.display(), "Free Thorncourt").equals("saved"), "Rename failed");
            FactionNaming.initialize(copy, faction.id(), generated);
            assertTrue(copy.faction(faction.id()).name().equals("Free Thorncourt") && copy.factionName(faction.id()).custom(), "Generation overwrote a custom name");
            for (String invalid : List.of("[]", "[\"Kingdom\"]", "[\"{other} of {name}\"]")) {
                boolean rejected = false;
                try { FactionNaming.parsePatterns(JsonParser.parseString("{\"faction_name_patterns\":" + invalid + "}").getAsJsonObject()); }
                catch (RuntimeException expected) { rejected = true; }
                assertTrue(rejected, "Invalid pattern accepted");
            }
        } finally {
            BondKinds.replaceAll(previousBonds);
            PoliticalDefinitions.replace(previousKinds);
            Cultures.replace(Map.of());
            FactionNaming.replace(Map.of());
        }
    }
    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
