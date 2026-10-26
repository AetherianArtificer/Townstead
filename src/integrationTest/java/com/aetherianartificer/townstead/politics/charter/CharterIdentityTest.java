package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CharterIdentityTest {
    @Test
    void renameKeepsIdentityAndPersists() {
        var data = new PoliticalSavedData();
        var settlement = new SettlementRef(id("minecraft:overworld"), 91);
        var original = new Faction(id("test:faction"), id("test:kind"), "Reedwater", 0xff123456, id("test:emblem"), 14,
                id("test:source"), Faction.Status.ACTIVE, List.of(settlement), null);
        data.putFaction(original);
        assertTrue(CharterIdentityService.rename(data, original.id(), "Reedwater", "  River   League  ").equals("saved"), "Valid rename rejected");
        var renamed = data.faction(original.id());
        assertTrue(renamed.name().equals("River League"), "Name was not normalized");
        assertTrue(renamed.id().equals(original.id()) && renamed.settlements().equals(original.settlements())
                && renamed.emblem().equals(original.emblem()) && renamed.kind().equals(original.kind())
                && renamed.color() == original.color() && renamed.createdAt() == original.createdAt()
                && renamed.provenance().equals(original.provenance()) && renamed.status() == original.status(), "Rename changed faction identity");
        assertTrue(CharterIdentityService.rename(data, original.id(), "Reedwater", "Stale change").equals("stale"), "Stale name overwrote newer name");
        for (String value : new String[]{"", " ", "A", "x".repeat(49), "Bad\nname", "Bad§cname"})
            assertTrue(CharterIdentityService.normalize(value) == null, "Invalid name accepted");
        assertTrue(CharterIdentityService.normalize("Étoile du Nord").equals("Étoile du Nord"), "Localized name rejected");
        PoliticalVillageBootstrap.ensure(data, settlement, "Reedwater", List.of(), 20, false, false);
        assertTrue(data.faction(original.id()).name().equals("River League"), "Settlement bootstrap reset faction name");
        //? if >=1.21 {
        var copy = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
        //?} else {
        /*var copy = PoliticalSavedData.load(data.save(new CompoundTag()));
        *///?}
        assertTrue(copy.faction(original.id()).equals(renamed), "Faction rename lost after reload");
    }
    private static ResourceLocation id(String id) { return ResourceLocation.tryParse(id); }
}
