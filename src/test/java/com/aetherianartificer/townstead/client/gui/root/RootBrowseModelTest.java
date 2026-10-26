package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Facet;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.GroupBy;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Item;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Section;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Shortcut;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Value;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.View;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The root browser's facets, shortcuts and grouping, without any rendering. */
class RootBrowseModelTest {

    private static final Map<String, String> TRAITS = Map.of("winged", "Winged", "lays_eggs", "Lays eggs", "ageless", "Ageless");

    private static Item root(String id, String name, String species, String ancestry, String pack, boolean featured, String... traits) {
        return new Item(id, name, species, species, ancestry, ancestry, pack, pack, Set.of(traits), featured, name);
    }

    private static final List<Item> ROOTS = List.of(
            root("runic:moon_elf", "Moon Elf", "humanoid", "Elf", "runic", true),
            root("classic:high_elf", "High Elf", "humanoid", "Elf", "classic", false),
            root("runic:fire_drake", "Fire Drake", "humanoid", "Dragonkin", "runic", true, "winged", "lays_eggs"),
            root("runic:wind_wyrm", "Wind Wyrm", "humanoid", "Dragonkin", "runic", false, "winged", "lays_eggs"),
            root("runic:faerie", "Faerie", "humanoid", "Fae", "runic", false, "winged"),
            root("folk:skeletownie", "Skeletownie", "skeleton", "Skeletownies", "folk", false, "ageless"));

    private static final RootBrowseModel.Context NONE = new RootBrowseModel.Context(List.of(), Set.of(), Map.of());

    private static View view(RootBrowseModel.State state) {
        return new RootBrowseModel(ROOTS, TRAITS).view(state, NONE);
    }

    private static Value value(View view, Facet facet, String key) {
        return view.facets().get(facet).stream().filter(v -> v.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void groupsByAncestryWithinSpecies() {
        View view = view(new RootBrowseModel.State());
        assertEquals(6, view.matching());
        assertEquals(List.of("Dragonkin", "Elf", "Fae", "Skeletownies"),
                view.sections().stream().map(Section::title).toList());
        assertEquals(List.of("High Elf", "Moon Elf"),
                view.sections().get(1).items().stream().map(Item::name).toList());
    }

    @Test
    void facetCountsShowWhatAClickWouldGive() {
        RootBrowseModel.State state = new RootBrowseModel.State();
        state.toggle(Facet.TRAIT, "winged");
        View view = view(state);
        assertEquals(3, view.matching());
        // The pack facet counts within winged roots...
        assertEquals(3, value(view, Facet.PACK, "runic").count());
        assertEquals(0, value(view, Facet.PACK, "classic").count());
        // ...while the trait facet counts as if its own choice were not made.
        assertEquals(3, value(view, Facet.TRAIT, "winged").count());
        assertTrue(value(view, Facet.TRAIT, "winged").chosen());
    }

    @Test
    void valuesWithinAFacetAreAlternatives() {
        RootBrowseModel.State state = new RootBrowseModel.State();
        state.toggle(Facet.PACK, "classic");
        state.toggle(Facet.PACK, "folk");
        assertEquals(2, view(state).matching());
    }

    @Test
    void facetsThatSplitNothingAreLeftOut() {
        List<Item> one = List.of(root("a:x", "X", "humanoid", "Elf", "a", false, "winged"),
                root("a:y", "Y", "humanoid", "Elf", "a", false, "winged"));
        View view = new RootBrowseModel(one, TRAITS).view(new RootBrowseModel.State(), NONE);
        assertTrue(view.facets().isEmpty(), "one species, one pack, and a trait everyone has");
    }

    @Test
    void searchMatchesNames() {
        RootBrowseModel.State state = new RootBrowseModel.State();
        state.query = "ELF";
        View view = view(state);
        assertEquals(2, view.matching());
        assertEquals(1, view.sections().size());
    }

    @Test
    void shortcutsFilterAndCount() {
        RootBrowseModel model = new RootBrowseModel(ROOTS, TRAITS);
        RootBrowseModel.Context context = new RootBrowseModel.Context(
                List.of("runic:faerie", "classic:high_elf"), Set.of("runic:wind_wyrm"), Map.of("runic:moon_elf", 3));
        RootBrowseModel.State state = new RootBrowseModel.State();
        View all = model.view(state, context);
        assertEquals(2, all.shortcuts().get(Shortcut.RECENT));
        assertEquals(1, all.shortcuts().get(Shortcut.PINNED));
        assertEquals(1, all.shortcuts().get(Shortcut.IN_WORLD));
        assertEquals(2, all.shortcuts().get(Shortcut.FEATURED));

        state.shortcut = Shortcut.FEATURED;
        assertEquals(2, model.view(state, context).matching());
        state.shortcut = Shortcut.RECENT;
        // Recent keeps the order the roots were looked at in, newest first.
        List<String> order = model.view(state, context).sections().stream()
                .flatMap(s -> s.items().stream()).map(Item::id).toList();
        assertEquals(List.of("classic:high_elf", "runic:faerie"), order);
    }

    @Test
    void groupsByPack() {
        RootBrowseModel.State state = new RootBrowseModel.State();
        state.groupBy = GroupBy.PACK;
        assertEquals(List.of("classic", "folk", "runic"),
                view(state).sections().stream().map(Section::title).toList());
    }

    @Test
    void lookalikeValuesAreToldApart() {
        Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("bugs:insects", "insects");
        labels.put("hive:insects", "Insects");
        labels.put("townstead_roots:humanoid", "Humanoid");
        labels.put("blank:thing", "");
        assertEquals(List.of("insects (bugs)", "Insects (hive)", "Humanoid", "blank:thing"),
                List.copyOf(RootBrowseModel.distinct(labels).values()));
    }
}
