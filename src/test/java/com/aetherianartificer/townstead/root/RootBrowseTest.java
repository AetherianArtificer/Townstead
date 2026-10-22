package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.RootBrowse.Profile;
import com.aetherianartificer.townstead.root.RootBrowseEntry.Fact;
import com.aetherianartificer.townstead.root.RootBrowseEntry.Text;
import com.aetherianartificer.townstead.root.ability.Ability;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Facts and traits read off a root's genes, and the browse entry's wire format. */
class RootBrowseTest {

    private static Profile profile(String diet, String chronotype, int days, boolean ageless, boolean eggs,
                                   int litter, boolean sterile, float scale, Set<Ability> abilities, boolean wings) {
        Text activity = chronotype == null ? null : Text.of("townstead.root.fact.active." + chronotype, chronotype);
        return new Profile(diet, activity, chronotype, days, ageless, eggs, litter, sterile, scale, abilities, wings);
    }

    private static String fact(List<Fact> facts, String kind) {
        for (Fact f : facts) if (f.kind().equals(kind)) return f.value().key() + f.value().args();
        return null;
    }

    @Test
    void aFireDrakeReadsAsItPlays() {
        Profile drake = profile("omnivore", "diurnal", 407, false, true, 2, false, 1.2f,
                EnumSet.of(Ability.ELYTRA_FLIGHT, Ability.FIRE_IMMUNITY), true);
        List<Fact> facts = RootBrowse.facts(drake);
        assertEquals(List.of("diet", "active", "lifespan", "size", "born", "moves"), facts.stream().map(Fact::kind).toList());
        assertEquals("townstead.root.fact.lifespan.ratio[4.5]", fact(facts, "lifespan"));
        assertEquals("townstead.root.fact.size.percent[120]", fact(facts, "size"));
        assertEquals("townstead.root.fact.born.clutch[2]", fact(facts, "born"));
        assertEquals("townstead.root.fact.moves.glides[]", fact(facts, "moves"));
        assertEquals(List.of("winged", "large", "lays_eggs"), RootBrowse.traits(drake));
    }

    @Test
    void aPlainHumanShowsOnlyTheBasics() {
        Profile human = profile("omnivore", "diurnal", 90, false, false, 1, false, 1f, EnumSet.noneOf(Ability.class), false);
        List<Fact> facts = RootBrowse.facts(human);
        assertEquals(List.of("diet", "active", "lifespan"), facts.stream().map(Fact::kind).toList());
        assertEquals("townstead.root.fact.lifespan.human[]", fact(facts, "lifespan"));
        assertTrue(RootBrowse.traits(human).isEmpty());
    }

    @Test
    void undeadAreAgelessAndSterile() {
        Profile undead = profile("none", "nocturnal", 0, true, false, 1, true, 1f, EnumSet.of(Ability.NIGHT_VISION), false);
        List<Fact> facts = RootBrowse.facts(undead);
        assertEquals("townstead.root.fact.diet.none[]", fact(facts, "diet"));
        assertEquals("townstead.root.fact.lifespan.ageless[]", fact(facts, "lifespan"));
        assertEquals("townstead.root.fact.born.never[]", fact(facts, "born"));
        assertEquals("townstead.root.fact.senses.darkvision[]", fact(facts, "senses"));
        assertEquals(List.of("nocturnal", "darkvision", "ageless", "sterile"), RootBrowse.traits(undead));
    }

    @Test
    void ratiosReadCleanly() {
        assertEquals("8.3", RootBrowse.ratioText(750 / 90f));
        assertEquals("4", RootBrowse.ratioText(4.0f));
        assertEquals("0.5", RootBrowse.ratioText(0.5f));
        assertEquals("13", RootBrowse.ratioText(12.6f));
    }

    @Test
    void entriesRoundTrip() {
        RootBrowseEntry entry = new RootBrowseEntry("runic_roots:fire_drake", "runic_roots:textures/root/fire_drake.png",
                "townstead_roots:humanoid", "runic_roots:dragonkin", "runic_roots:fire_drake", "runic_roots",
                Text.of("pack.runic_roots.name", "Runic Roots"), Text.literal("Runic"), true,
                List.of(new Fact("lifespan", Text.of("townstead.root.fact.lifespan.ratio", "4.5x human", "4.5"))),
                List.of("winged", "lays_eggs"),
                List.of(new RootBrowseEntry.Power("runic_roots:fire_drake/dragonfire_breath", RootBrowseEntry.Power.Kind.ACTIVE, 800),
                        new RootBrowseEntry.Power("runic_roots:fire_drake/cold_quenches_fire", RootBrowseEntry.Power.Kind.DRAWBACK, 0)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        entry.write(buf);
        assertEquals(entry, RootBrowseEntry.read(buf));
    }
}
