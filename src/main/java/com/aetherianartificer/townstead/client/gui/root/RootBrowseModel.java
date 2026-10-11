package com.aetherianartificer.townstead.client.gui.root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The root browser's filtering and grouping, kept free of rendering so it can be tested.
 *
 * <p>Filters are facets: a root passes when it matches the search, the chosen shortcut, and at
 * least one chosen value in every facet that has any chosen. Each facet value's count is what the
 * results would hold if that value were chosen with the others, so the numbers never lie about what
 * a click will show. A facet that splits nothing is left out: species or packs with only one value,
 * and traits that every root (or none) has.</p>
 */
public final class RootBrowseModel {

    /** One root as the browser sees it. */
    public record Item(String id, String name, String speciesId, String speciesName, String ancestryId,
                       String ancestryName, String pack, String packName, Set<String> traits,
                       boolean featured, String searchText) {
        public Item {
            traits = Set.copyOf(traits);
            searchText = searchText == null ? "" : searchText.toLowerCase(Locale.ROOT);
        }
    }

    public enum Facet { SPECIES, PACK, TRAIT }

    public enum Shortcut { ALL, RECENT, PINNED, IN_WORLD, FEATURED }

    public enum GroupBy { ANCESTRY, PACK }

    /** A facet value: its key, its label, how many results choosing it would give, and whether it is chosen. */
    public record Value(String key, String label, int count, boolean chosen) {}

    /** A group of results. */
    public record Section(String key, String title, List<Item> items) {}

    /** What the browser draws for one state of the filters. */
    public record View(int total, int matching, Map<Facet, List<Value>> facets, Map<Shortcut, Integer> shortcuts,
                       List<Section> sections) {}

    /** The chosen filters. */
    public static final class State {
        public String query = "";
        public Shortcut shortcut = Shortcut.ALL;
        public GroupBy groupBy = GroupBy.ANCESTRY;
        public final Map<Facet, Set<String>> chosen = new LinkedHashMap<>();

        public Set<String> chosen(Facet facet) {
            return chosen.computeIfAbsent(facet, f -> new LinkedHashSet<>());
        }

        public void toggle(Facet facet, String key) {
            Set<String> set = chosen(facet);
            if (!set.remove(key)) set.add(key);
        }

        public boolean anyChosen() {
            for (Set<String> set : chosen.values()) if (!set.isEmpty()) return true;
            return false;
        }

        public void clear() {
            chosen.clear();
            shortcut = Shortcut.ALL;
            query = "";
        }
    }

    /** What the shortcuts read: recently viewed and pinned ids, and how many of each root live in the world. */
    public record Context(List<String> recent, Set<String> pinned, Map<String, Integer> inWorld) {}

    private final List<Item> items;
    private final Map<String, String> traitLabels;

    public RootBrowseModel(Collection<Item> items, Map<String, String> traitLabels) {
        this.items = List.copyOf(items);
        this.traitLabels = Map.copyOf(traitLabels);
    }

    public List<Item> items() {
        return items;
    }

    public View view(State state, Context context) {
        String query = state.query.trim().toLowerCase(Locale.ROOT);
        Map<Shortcut, Integer> shortcuts = new LinkedHashMap<>();
        for (Shortcut shortcut : Shortcut.values()) {
            if (shortcut == Shortcut.ALL) continue;
            int n = 0;
            for (Item item : items) if (inShortcut(item, shortcut, context)) n++;
            shortcuts.put(shortcut, n);
        }

        Map<Facet, List<Value>> facets = new LinkedHashMap<>();
        for (Facet facet : Facet.values()) {
            Map<String, String> labels = valuesOf(facet);
            if (labels.isEmpty()) continue;
            List<Value> values = new ArrayList<>();
            for (Map.Entry<String, String> value : labels.entrySet()) {
                int n = 0;
                for (Item item : items) {
                    if (matchesExcept(item, state, context, query, facet) && has(item, facet, value.getKey())) n++;
                }
                values.add(new Value(value.getKey(), value.getValue(), n, state.chosen(facet).contains(value.getKey())));
            }
            if (facet != Facet.TRAIT) values.sort(Comparator.comparingInt(Value::count).reversed()
                    .thenComparing(Value::label, String.CASE_INSENSITIVE_ORDER));
            facets.put(facet, values);
        }

        List<Item> matching = new ArrayList<>();
        for (Item item : items) if (matchesExcept(item, state, context, query, null)) matching.add(item);
        return new View(items.size(), matching.size(), facets, shortcuts, sections(matching, state, context));
    }

    private List<Section> sections(List<Item> matching, State state, Context context) {
        Map<String, List<Item>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> titles = new LinkedHashMap<>();
        boolean recent = state.shortcut == Shortcut.RECENT;
        for (Item item : matching) {
            String key = state.groupBy == GroupBy.PACK ? item.pack() : item.speciesId() + "|" + item.ancestryId();
            String title = state.groupBy == GroupBy.PACK ? item.packName()
                    : item.ancestryName().isEmpty() ? item.speciesName() : item.ancestryName();
            String sortKey = (state.groupBy == GroupBy.ANCESTRY ? item.speciesName() + "\u0000" : "") + title + "\u0000" + key;
            groups.computeIfAbsent(sortKey, k -> new ArrayList<>()).add(item);
            titles.put(sortKey, title);
        }
        List<Section> out = new ArrayList<>();
        for (Map.Entry<String, List<Item>> group : groups.entrySet()) {
            List<Item> list = group.getValue();
            if (recent) {
                list.sort(Comparator.comparingInt(i -> context.recent().indexOf(i.id())));
            } else {
                list.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER));
            }
            out.add(new Section(group.getKey(), titles.get(group.getKey()), List.copyOf(list)));
        }
        return out;
    }

    private boolean matchesExcept(Item item, State state, Context context, String query, Facet except) {
        if (!query.isEmpty() && !item.searchText().contains(query)) return false;
        if (state.shortcut != Shortcut.ALL && !inShortcut(item, state.shortcut, context)) return false;
        for (Facet facet : Facet.values()) {
            if (facet == except) continue;
            Set<String> chosen = state.chosen(facet);
            if (chosen.isEmpty()) continue;
            boolean any = false;
            for (String key : chosen) {
                if (has(item, facet, key)) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        return true;
    }

    private static boolean inShortcut(Item item, Shortcut shortcut, Context context) {
        return switch (shortcut) {
            case ALL -> true;
            case RECENT -> context.recent().contains(item.id());
            case PINNED -> context.pinned().contains(item.id());
            case IN_WORLD -> context.inWorld().getOrDefault(item.id(), 0) > 0;
            case FEATURED -> item.featured();
        };
    }

    private static boolean has(Item item, Facet facet, String key) {
        return switch (facet) {
            case SPECIES -> item.speciesId().equals(key);
            case PACK -> item.pack().equals(key);
            case TRAIT -> item.traits().contains(key);
        };
    }

    /** Every value a facet takes across all roots, key to label, in a stable order. */
    private Map<String, String> valuesOf(Facet facet) {
        Map<String, String> out = new LinkedHashMap<>();
        if (facet == Facet.TRAIT) {
            // A trait splits the roots only when some have it and some do not.
            for (Map.Entry<String, String> trait : traitLabels.entrySet()) {
                int n = 0;
                for (Item item : items) if (item.traits().contains(trait.getKey())) n++;
                if (n > 0 && n < items.size()) out.put(trait.getKey(), trait.getValue());
            }
            return out;
        }
        for (Item item : items) {
            if (facet == Facet.SPECIES) out.putIfAbsent(item.speciesId(), item.speciesName());
            else out.putIfAbsent(item.pack(), item.packName());
        }
        return out.size() < 2 ? Map.of() : distinct(out);
    }

    /** Two values that read the same are told apart by where they come from ("Insects (bugs)"). */
    static Map<String, String> distinct(Map<String, String> labels) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (String label : labels.values()) seen.merge(label.toLowerCase(Locale.ROOT), 1, Integer::sum);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> value : labels.entrySet()) {
            String label = value.getValue().isBlank() ? value.getKey() : value.getValue();
            if (seen.get(value.getValue().toLowerCase(Locale.ROOT)) > 1) {
                int colon = value.getKey().indexOf(':');
                label = label + " (" + (colon < 0 ? value.getKey() : value.getKey().substring(0, colon)) + ")";
            }
            out.put(value.getKey(), label);
        }
        return out;
    }
}
