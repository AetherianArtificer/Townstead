package com.aetherianartificer.townstead.client.catalog;

import java.util.*;
import java.util.function.Function;

/** Pure layout: sectors can move, but a family always remains an ordered chain. */
public final class CatalogGraphLayout {
    public enum Grouping { GROUP, SPIRIT, HANGOUT, STATUS }
    public enum Sort { NAME, RECOGNIZED, ENTRIES, POINTS }
    public enum Filter { ALL, HANGOUT, RECOGNIZED, MISSING, PINNED }
    public record Entry(String id, String name, String group, String groupLabel, String family, int tier,
                        Set<String> spirits, boolean recognized, boolean hangout, boolean pinned,
                        String searchText) {
        public Entry { spirits = Set.copyOf(spirits); }
    }
    public record Node(Entry entry, int x, int y, boolean match, String sector) {}
    public record Edge(int x1, int y1, int x2, int y2) {}
    public record Sector(String id, String label, int x, int y, int width, int height,
                         int members, int recognized, List<Node> nodes, List<Edge> edges) {}
    public record Layout(List<Sector> sectors, List<Node> nodes, int width, int height, int matches) {
        public static final Layout EMPTY = new Layout(List.of(), List.of(), 0, 0, 0);
    }
    private record Bucket(String id, String label, List<Entry> members) {}
    private CatalogGraphLayout() {}

    /** Datapacks may use an explicit tier prefix as well as the conventional terminal _lN. */
    public static int tierNumber(String id, String prefix) {
        String suffix = prefix != null && !prefix.isEmpty() && id.startsWith(prefix) ? id.substring(prefix.length()) : "";
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) {
            int start = id.lastIndexOf("_l");
            suffix = start < 0 ? "" : id.substring(start + 2);
        }
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return 0;
        try { return Math.max(0, Integer.parseInt(suffix)); } catch (NumberFormatException ignored) { return 0; }
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> Integer.toString(tier);
        };
    }

    public static boolean matches(Entry e, String query, Filter filter) {
        boolean eligible = switch (filter) {
            case ALL -> true;
            case HANGOUT -> e.hangout();
            case RECOGNIZED -> e.recognized();
            case MISSING -> !e.recognized();
            case PINNED -> e.pinned();
        };
        if (!eligible) return false;
        String text = (e.id() + " " + e.name() + " " + e.groupLabel() + " " + e.searchText())
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+"))
                .allMatch(text::contains);
    }

    public static Layout build(List<Entry> entries, Grouping grouping, Sort sort, boolean descending,
                               String query, Filter filter, Map<String, Integer> points,
                               Function<String, String> label, int availableWidth) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (Entry e : entries) {
            Collection<String> keys = switch (grouping) {
                case GROUP -> List.of(e.group());
                case SPIRIT -> e.spirits().isEmpty() ? List.of("~unclassified") : e.spirits();
                case HANGOUT -> List.of(e.hangout() ? "hangout" : "other");
                case STATUS -> List.of(e.recognized() ? "recognized" : "missing");
            };
            for (String key : keys) buckets.computeIfAbsent(key, k -> new Bucket(k,
                    grouping == Grouping.GROUP ? e.groupLabel() : label.apply(k), new ArrayList<>())).members().add(e);
        }
        Comparator<Bucket> names = Comparator.comparing(Bucket::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Bucket::id);
        Comparator<Bucket> ordering = switch (sort) {
            case NAME -> names;
            case RECOGNIZED -> Comparator.comparingLong(b -> b.members().stream().filter(Entry::recognized).count());
            case ENTRIES -> Comparator.comparingInt(b -> b.members().size());
            case POINTS -> Comparator.comparingInt(b -> points.getOrDefault(b.id(), 0));
        };
        if (descending) ordering = ordering.reversed();
        ordering = Comparator.<Bucket, Boolean>comparing(b -> b.id().startsWith("~"))
                .thenComparing(ordering).thenComparing(names);
        List<Sector> sectors = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        Set<String> resultIds = new HashSet<>();
        int x = 8, y = 8, rowHeight = 0, maxRight = 0;
        for (Bucket bucket : buckets.values().stream().sorted(ordering).toList()) {
            List<Entry> hits = bucket.members().stream().filter(e -> matches(e, query, filter)).toList();
            if (hits.isEmpty()) continue;
            Set<String> hitIds = new HashSet<>();
            Set<String> families = new HashSet<>();
            for (Entry hit : hits) {
                hitIds.add(hit.id()); resultIds.add(hit.id());
                if (!hit.family().isEmpty()) families.add(hit.family());
            }
            // Expand from all definitions, including stages with no contribution to this spirit.
            List<Entry> visible = entries.stream().filter(e -> hitIds.contains(e.id())
                    || (!e.family().isEmpty() && families.contains(e.family())))
                    .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
            Map<String, List<Entry>> rows = new LinkedHashMap<>();
            for (Entry e : visible) rows.computeIfAbsent(e.family().isEmpty() ? "" : e.family(),
                    ignored -> new ArrayList<>()).add(e);
            int columns = Math.max(2, Math.min(5, (availableWidth - 32) / 70));
            List<Node> local = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            int top = 39, right = 118;
            for (var family : rows.entrySet()) {
                List<Entry> members = family.getValue();
                if (!family.getKey().isEmpty()) members.sort(Comparator.comparingInt(Entry::tier).thenComparing(Entry::id));
                Node previous = null;
                for (int i = 0; i < members.size(); i++) {
                    Entry e = members.get(i);
                    boolean chain = !family.getKey().isEmpty();
                    int nx = 23 + (chain ? i : i % columns) * 70;
                    int ny = top + (chain ? 0 : i / columns * 62);
                    Node node = new Node(e, nx, ny, hitIds.contains(e.id()), bucket.id());
                    local.add(node);
                    if (chain && previous != null && e.tier() == previous.entry().tier() + 1)
                        edges.add(new Edge(previous.x() + 26, previous.y() + 13, nx, ny + 13));
                    previous = node;
                    right = Math.max(right, nx + 48);
                }
                top += family.getKey().isEmpty() ? ((members.size() + columns - 1) / columns) * 62 : 62;
            }
            if (x > 8 && x + right > availableWidth - 8) { x = 8; y += rowHeight + 10; rowHeight = 0; }
            List<Node> placed = new ArrayList<>();
            for (Node node : local) placed.add(new Node(node.entry(), x + node.x(), y + node.y(), node.match(), node.sector()));
            List<Edge> placedEdges = new ArrayList<>();
            for (Edge edge : edges) placedEdges.add(new Edge(x + edge.x1(), y + edge.y1(), x + edge.x2(), y + edge.y2()));
            sectors.add(new Sector(bucket.id(), bucket.label(), x, y, right, top,
                    bucket.members().size(), (int) bucket.members().stream().filter(Entry::recognized).count(),
                    List.copyOf(placed), List.copyOf(placedEdges)));
            nodes.addAll(placed);
            maxRight = Math.max(maxRight, x + right);
            x += right + 10;
            rowHeight = Math.max(rowHeight, top);
        }
        return new Layout(List.copyOf(sectors), List.copyOf(nodes), maxRight + 8,
                sectors.isEmpty() ? 0 : y + rowHeight + 8, resultIds.size());
    }
}
