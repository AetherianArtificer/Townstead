package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.politics.charter.CharterIdentityService;
import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionNamingTest {
    @Test
    void culturalNamesPatternsAndAuthority() throws Exception {
        var culture = id("test:culture"); var kindId = id("test:government"); var role = id("test:ruler");
        var poolJson = JsonParser.parseString("{\"names\":[\"Thorncourt\"]}").getAsJsonObject();
        var parsed = SettlementNameJsonLoader.parse(culture, poolJson);
        FactionNaming.replace(Map.of(culture, new SettlementNamePool(culture, parsed.forms(), parsed.values())));
        Cultures.replace(Map.of(culture, new Culture(culture, Component.literal("Culture"), null, null, CultureClothing.NONE, culture)));
        var patterns = FactionNaming.parsePatterns(JsonParser.parseString("{\"faction_name_patterns\":[\"League of {name}\"]}").getAsJsonObject());
        var kind = new OrganizationKindDefinition(kindId, null, Set.of(), id("test:policy"),
                List.of(new OrganizationKindDefinition.RoleBinding(role, 0, -1, false)), null,
                new OrganizationKindDefinition.Presentation(null, null, patterns));
        var field = PoliticalDefinitions.class.getDeclaredField("SNAPSHOT"); field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, new PoliticalDefinitions.Snapshot(Map.of(role, new OrganizationRoleDefinition(role, null,
                    Set.of(id("townstead:govern_polity")), OrganizationRoleDefinition.Visibility.PUBLIC)), Map.of(), Map.of(kindId, kind)));
            var generated = FactionNaming.generate(culture, kindId, "Reedwater");
            assertTrue(generated.display().equals("League of Thorncourt"), "Culture/government composition failed");
            assertTrue(generated.base().equals("Thorncourt") && !generated.custom(), "Base name was not retained");
            assertTrue(FactionNaming.review(culture, kindId, generated.base(), generated.pattern(), generated.display()).equals(generated), "Reviewed origin lost");
            assertTrue(FactionNaming.review(culture, kindId, "invented", generated.pattern(), "My Faction").custom(), "Custom name misrepresented as generated");
            assertTrue(FactionNaming.generate(null, null, "Reedwater").display().equals("Reedwater"), "No-culture fallback failed");
            assertTrue(FactionNaming.generate(culture, id("unknown:government"), "Reedwater").display().equals("Thorncourt"), "Unknown government imposed a title");
            var data = new PoliticalSavedData(); var settlement = new SettlementRef(id("minecraft:overworld"), 111);
            var org = new OrganizationInstance(id("test:council"), kindId, id("test:policy"), "Council", "Council", 0, null, 0, kindId, PoliticalStatus.Organization.ACTIVE, settlement);
            data.putOrganization(org);
            var polity = new PolityInstance(id("test:faction"), "Reedwater", 0, null, 0, kindId, PoliticalStatus.Polity.ACTIVE, List.of(settlement), org.id());
            data.putPolity(polity); FactionNaming.initialize(data, polity.id(), generated);
            var ruler = UUID.randomUUID(); var visitor = UUID.randomUUID();
            var membership = new MembershipInstance(new AffiliationInstance(id("test:membership"), ruler, org.actor(), id("townstead:membership"),
                    PoliticalStatus.Affiliation.ACTIVE, 1, AffiliationInstance.NOT_ENDED, kindId, PoliticalStatus.Visibility.PUBLIC),
                    id("test:policy"), id("test:admission"), id("test:departure"), Set.of(role));
            data.putMembership(membership);
            assertTrue(PoliticalAuthority.mayAct(data, ruler, polity.actor(), id("townstead:govern_polity")).allowed(), "Ruler denied identity authority");
            assertTrue(!PoliticalAuthority.mayAct(data, visitor, polity.actor(), id("townstead:govern_polity")).allowed(), "Visitor granted identity authority");
            data.putMembership(new MembershipInstance(membership.affiliation(), membership.membershipPolicy(), membership.admissionProcedure(), membership.departureProcedure(), Set.of()));
            assertTrue(!PoliticalAuthority.mayAct(data, ruler, polity.actor(), id("townstead:govern_polity")).allowed(), "Lost role retained authority");
            //? if >=1.21 {
            var copy = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
            //?} else {
            /*var copy = PoliticalSavedData.load(data.save(new CompoundTag()));
            *///?}
            assertTrue(copy.factionName(polity.id()).equals(generated), "Naming provenance lost on reload");
            assertTrue(CharterIdentityService.rename(copy, polity.id(), generated.display(), "Free Thorncourt").equals("saved"), "Rename failed");
            FactionNaming.initialize(copy, polity.id(), generated);
            assertTrue(copy.polity(polity.id()).name().equals("Free Thorncourt") && copy.factionName(polity.id()).custom(), "Generation overwrote a custom name");
            for (String invalid : List.of("[]", "[\"Kingdom\"]", "[\"{other} of {name}\"]")) {
                boolean rejected = false;
                try { FactionNaming.parsePatterns(JsonParser.parseString("{\"faction_name_patterns\":" + invalid + "}").getAsJsonObject()); }
                catch (RuntimeException expected) { rejected = true; }
                assertTrue(rejected, "Invalid pattern accepted");
            }
        } finally { field.set(null, previous); Cultures.replace(Map.of()); FactionNaming.replace(Map.of()); }
    }
    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
