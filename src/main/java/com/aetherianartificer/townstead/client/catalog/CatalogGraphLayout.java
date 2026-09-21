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
    private static final int MARGIN = 8, GAP = 16;
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
        return build(entries, grouping, sort, descending, query, filter, points, label, availableWidth, availableWidth);
    }

    public static Layout build(List<Entry> entries, Grouping grouping, Sort sort, boolean descending,
                               String query, Filter filter, Map<String, Integer> points,
                               Function<String, String> label, int availableWidth, int availableHeight) {
        double aspect = Math.max(0.5, Math.min(3, availableWidth / (double) Math.max(1, availableHeight)));
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
        Set<String> resultIds = new HashSet<>();
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
            List<Node> local = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            int top = 39, right = 118;
            for (var family : rows.entrySet()) {
                List<Entry> members = family.getValue();
                // Large independent groups grow in both dimensions rather than becoming towers.
                int columns = Math.max(1, (int) Math.ceil(Math.sqrt(members.size() * aspect * 62 / 70)));
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
            if (local.size() == 1) {
                Node lone = local.get(0);
                local.set(0, new Node(lone.entry(), (right - 26) / 2, lone.y(), lone.match(), lone.sector()));
            }
            sectors.add(new Sector(bucket.id(), bucket.label(), 0, 0, right, top,
                    bucket.members().size(), (int) bucket.members().stream().filter(Entry::recognized).count(),
                    List.copyOf(local), List.copyOf(edges)));
        }
        if (sectors.isEmpty()) return Layout.EMPTY;

        // Choose a canvas that fits the viewport's proportions, independent of the zoom level.
        // Keeping each row in catalog order makes Name / Built / Entries sorting predictable.
        int minimum = sectors.stream().mapToInt(Sector::width).max().orElse(0) + MARGIN * 2;
        int maximum = sectors.stream().mapToInt(s -> s.width() + GAP).sum() - GAP + MARGIN * 2;
        Layout best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int canvas = minimum; canvas < maximum + GAP; canvas += GAP) {
            Layout candidate = pack(sectors, Math.min(canvas, maximum), resultIds.size());
            double scaleNeeded = Math.max(candidate.width() / (double) Math.max(1, availableWidth),
                    candidate.height() / (double) Math.max(1, availableHeight));
            // Prefer the largest readable overview, then the least unused canvas area.
            double score = scaleNeeded * scaleNeeded + 0.1 * candidate.width() * (double) candidate.height()
                    / (Math.max(1, availableWidth) * (double) Math.max(1, availableHeight));
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }

    private static Layout pack(List<Sector> measured, int canvasWidth, int matches) {
        List<Sector> sectors = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        int x = MARGIN, y = MARGIN, rowHeight = 0, maxRight = 0;
        for (Sector sector : measured) {
            if (x > MARGIN && x + sector.width() > canvasWidth - MARGIN) {
                x = MARGIN; y += rowHeight + GAP; rowHeight = 0;
            }
            List<Node> placed = new ArrayList<>();
            for (Node node : sector.nodes()) placed.add(new Node(node.entry(), x + node.x(), y + node.y(), node.match(), node.sector()));
            List<Edge> placedEdges = new ArrayList<>();
            for (Edge edge : sector.edges()) placedEdges.add(new Edge(x + edge.x1(), y + edge.y1(), x + edge.x2(), y + edge.y2()));
            sectors.add(new Sector(sector.id(), sector.label(), x, y, sector.width(), sector.height(),
                    sector.members(), sector.recognized(),
                    List.copyOf(placed), List.copyOf(placedEdges)));
            nodes.addAll(placed);
            maxRight = Math.max(maxRight, x + sector.width());
            x += sector.width() + GAP;
            rowHeight = Math.max(rowHeight, sector.height());
        }
        return new Layout(List.copyOf(sectors), List.copyOf(nodes), maxRight + MARGIN,
                y + rowHeight + MARGIN, matches);
    }
}
