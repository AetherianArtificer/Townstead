package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where everything on the career board goes.
 *
 * <p>Two ideas, kept apart on purpose. A <b>column</b> is a path: it runs the full height of the
 * board and carries its own backdrop. A <b>band</b> is a rank: it is a GATE, meaning the rank you
 * must reach before anything inside it can be taken, and it bounds a region of every column at
 * once.</p>
 *
 * <p>A lone node may sit where the constellation wants it. Several choices offered by one Path at
 * one rank are different: they form one authored question, so they share a row and retain the order
 * written in {@code path.json}. Their order never implies an ability category; three passive
 * choices, three active choices, or any mixture are equally valid.</p>
 *
 * <p>Board units, origin at the top left of the content. {@link BoardView} centres and clamps.</p>
 */
final class CareerLayout {

    static final int COL_W = 84;
    private static final int COL_GAP = 8;
    static final int COL_PITCH = COL_W + COL_GAP;

    static final int BAND_H = 88;
    /**
     * Room above the first band, which has to clear the sticky banner AND leave the first cluster
     * looking placed rather than jammed under it. The banner is only about twelve pixels tall, but
     * matching that exactly left the first rank's marks touching it.
     */
    static final int TOP_PAD = 44;
    private static final int BOTTOM_PAD = 16;
    /** Clearance inside a band so a mark never touches its divider. */
    private static final int BAND_INSET = 15;
    /** How far a mark may sit either side of its column's centre line. */
    private static final int LANE = 25;

    private static final int[] ARM_TINTS = {
            0xFFC9A05A, 0xFFC46A42, 0xFF7E9E62, 0xFF8A7EA8, 0xFF6E93A8, 0xFFB08A4E};
    /** The unpathed column keeps the board's own warm tone and takes no path colour. */
    static final int TRUNK_TINT = 0xFFC9A05A;
    /**
     * The column Combo Skills live in, and the cool tone that says it is not one of this career's
     * own paths. The leading space makes the key unforgeable: a real path id is a ResourceLocation
     * and cannot contain one.
     */
    private static final String COMBO_PATH = " combo";
    private static final int COMBO_TINT = 0xFF8A7EA8;

    /** One path (or the unpathed trunk) as a full-height alcove. */
    record Column(String pathId, String name, int tint, String backdrop) {}

    /** One rank, as a gate over every column. */
    record Band(int rank, String name) {}

    private final Font font;

    private final Map<String, int[]> positions = new LinkedHashMap<>();
    private final List<Column> columns = new ArrayList<>();
    private final List<Band> bands = new ArrayList<>();
    private final Map<String, Integer> columnOfPath = new HashMap<>();
    private String committedPath = "";
    private int careerTier;

    CareerLayout(Font font) {
        this.font = font;
    }

    Map<String, int[]> positions() { return positions; }
    List<Column> columns() { return columns; }
    List<Band> bands() { return bands; }
    String committedPath() { return committedPath; }
    int careerTier() { return careerTier; }
    int[] positionOf(String id) { return positions.get(id); }

    int columnX(int index) { return index * COL_PITCH; }
    int bandTop(int index) { return TOP_PAD + index * BAND_H; }
    int contentWidth() { return Math.max(COL_W, columns.size() * COL_PITCH - COL_GAP); }
    int contentHeight() { return TOP_PAD + bands.size() * BAND_H + BOTTOM_PAD; }

    /** Which band a screen row falls in, for the sticky rank chip. */
    int bandAt(int boardY) {
        int index = (boardY - TOP_PAD) / BAND_H;
        return Math.max(0, Math.min(bands.size() - 1, index));
    }

    int tintOf(CareerGraphS2CPayload.Node node) {
        int index = columnIndexOf(node);
        return index >= columns.size() ? TRUNK_TINT : columns.get(index).tint();
    }

    int columnIndexOf(CareerGraphS2CPayload.Node node) {
        Integer index = columnOfPath.get(
                node.kind() == CareerGraphS2CPayload.KIND_COMBO ? COMBO_PATH : node.path().id());
        return index == null ? 0 : index;
    }

    static CareerGraphS2CPayload.Node careerNode(List<CareerGraphS2CPayload.Node> tabNodes) {
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_ROOT
                    || node.kind() == CareerGraphS2CPayload.KIND_ADVANCED) {
                return node;
            }
        }
        return null;
    }

    void build(List<CareerGraphS2CPayload.Node> tabNodes) {
        positions.clear();
        columns.clear();
        bands.clear();
        columnOfPath.clear();
        committedPath = "";
        careerTier = 0;

        CareerGraphS2CPayload.Node career = careerNode(tabNodes);
        if (career == null) return;
        careerTier = career.tier();

        buildBands(career);
        buildColumns(tabNodes);
        placeSkills(tabNodes);
    }

    /**
     * One band per rank the career has. Minecraft gives a profession five, and the schema allows
     * more, so this reads the cap off the data rather than assuming.
     */
    private void buildBands(CareerGraphS2CPayload.Node career) {
        int ranks = Math.max(1, career.maxTier());
        for (int rank = 1; rank <= ranks; rank++) {
            bands.add(new Band(rank, rankName(rank)));
        }
    }

    /**
     * The rank's own name where the pack authors one, and its numeral otherwise. The payload only
     * carries the rank the subject currently holds, so the rest are resolved from the shared
     * profession level keys.
     */
    private static String rankName(int rank) {
        String key = "townstead.profession.level." + rank;
        return Language.getInstance().has(key)
                ? Component.translatable(key).getString() : roman(rank);
    }

    static String roman(int value) {
        if (value <= 0) return String.valueOf(value);
        String[] numerals = {"X", "IX", "V", "IV", "I"};
        int[] weights = {10, 9, 5, 4, 1};
        StringBuilder out = new StringBuilder();
        int left = value;
        for (int i = 0; i < weights.length && left > 0; i++) {
            while (left >= weights[i]) {
                out.append(numerals[i]);
                left -= weights[i];
            }
        }
        return out.toString();
    }

    /**
     * The unpathed column first, then the paths, with the one you have put the most into nearest the
     * start. Paths do not lock each other, so that ordering is a courtesy and not a commitment.
     */
    private void buildColumns(List<CareerGraphS2CPayload.Node> tabNodes) {
        Map<String, Integer> invested = new HashMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL || !node.path().present()) continue;
            if (node.state() != CareerGraphS2CPayload.STATE_ACQUIRED) continue;
            int count = invested.merge(node.path().id(), 1, Integer::sum);
            if (count > invested.getOrDefault(committedPath, 0)) committedPath = node.path().id();
        }

        boolean anyUnpathed = false;
        TreeMap<String, CareerGraphS2CPayload.PathTag> paths = new TreeMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL) continue;
            if (node.path().present()) {
                paths.putIfAbsent(node.path().id(), node.path());
            } else {
                anyUnpathed = true;
            }
        }

        if (anyUnpathed) {
            columns.add(new Column("", Component.translatable(
                    "townstead.career.screen.general").getString(), TRUNK_TINT, ""));
            columnOfPath.put("", 0);
        }
        List<String> order = new ArrayList<>(paths.keySet());
        order.remove(committedPath);
        if (paths.containsKey(committedPath)) order.add(0, committedPath);
        for (String pathId : order) {
            CareerGraphS2CPayload.PathTag path = paths.get(pathId);
            int tint = path.color() != 0 ? path.color() : ARM_TINTS[columns.size() % ARM_TINTS.length];
            columnOfPath.put(pathId, columns.size());
            columns.add(new Column(pathId, path.name(), tint, path.backdrop()));
        }
        if (columns.isEmpty()) {
            columns.add(new Column("", Component.translatable(
                    "townstead.career.screen.general").getString(), TRUNK_TINT, ""));
            columnOfPath.put("", 0);
        }

        // Combo Skills get a column of their own, at the end.
        //
        // The plan was to draw them in the gutter BETWEEN the two columns they join, which turns
        // dead space into where the interesting decisions live. That premise only holds for a combo
        // joining two paths of ONE career. Every real combo joins two CAREERS — Charcutier wants
        // Cook 2 and Butcher 2 — and the other career is a different board entirely, so there is no
        // "between" to sit in. It landed in whatever gutter the middle of the board happened to be,
        // which is why it read as a mark that had come loose.
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_COMBO) continue;
            columnOfPath.put(COMBO_PATH, columns.size());
            columns.add(new Column(COMBO_PATH, Component.translatable(
                    "townstead.career.screen.combo").getString(), COMBO_TINT, ""));
            break;
        }
    }

    /** Every skill into its column's slice of its rank's band. */
    private void placeSkills(List<CareerGraphS2CPayload.Node> tabNodes) {
        Map<Long, List<CareerGraphS2CPayload.Node>> cells = new TreeMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL
                    && node.kind() != CareerGraphS2CPayload.KIND_COMBO) {
                continue;
            }
            int column = columnIndexOf(node);
            int band = Math.max(0, Math.min(bands.size() - 1, Math.max(1, node.tier()) - 1));
            cells.computeIfAbsent((long) column << 32 | band, key -> new ArrayList<>()).add(node);
        }
        for (Map.Entry<Long, List<CareerGraphS2CPayload.Node>> cell : cells.entrySet()) {
            int column = (int) (cell.getKey() >> 32);
            int band = (int) (cell.getKey() & 0xFFFFFFFFL);
            List<CareerGraphS2CPayload.Node> group = cell.getValue();

            int centre = columnX(column) + COL_W / 2;
            int top = bandTop(band) + BAND_INSET;
            int inner = BAND_H - 2 * BAND_INSET;
            positions.putAll(placeCell(group, centre, top, inner));
        }
    }

    /** Pure placement for one column/rank cell, split out so ordering is regression-testable. */
    static Map<String, int[]> placeCell(List<CareerGraphS2CPayload.Node> group,
                                        int centre, int top, int inner) {
        Map<String, int[]> placed = new LinkedHashMap<>();
        int count = group.size();
        // Nodes arrive in ProfessionDef.skills order. Path documents deliberately preserve each
        // level's nested array while they are lowered into that list, so sorting here by resource
        // id silently changed an authored choice into alphabetical order.
        boolean authoredChoiceRow = count > 1 && group.get(0).path().present();
        for (int i = 0; i < count; i++) {
            CareerGraphS2CPayload.Node node = group.get(i);
            // A Path's alternatives are peers, not successive steps. Giving their list index to Y
            // produced a downward staircase and falsely described progression between them.
            // Lone/trunk clusters keep the looser constellation treatment.
            int slot = count == 1 ? inner / 2
                    : (inner * (2 * i + 1)) / (2 * count);
            int y = authoredChoiceRow ? top + inner / 2
                    : top + slot + jitter(node.id(), 11, 5);
            int offset = count == 1 ? 0
                    : Math.round((2f * i / (count - 1) - 1f) * LANE);
            int x = centre + offset
                    + (authoredChoiceRow ? 0 : jitter(node.id(), 37, 3));
            placed.put(node.id(), new int[]{x, y});
        }
        return placed;
    }

    /** The topmost and bottommost placed mark in a column, or null when it holds none. */
    int[] columnExtent(int index) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int left = columnX(index);
        int right = left + COL_W;
        for (int[] at : positions.values()) {
            if (at[0] < left - LANE || at[0] > right + LANE) continue;
            min = Math.min(min, at[1]);
            max = Math.max(max, at[1]);
        }
        return min == Integer.MAX_VALUE ? null : new int[]{min, max};
    }

    /**
     * A stable offset in {@code [-range, range]} derived from the node id.
     *
     * <p>Deliberately not random: the board must look identical every time it is opened, or a
     * relayout after learning a skill would rearrange everything the player had just learned to
     * read.</p>
     */
    private static int jitter(String id, int salt, int range) {
        if (range <= 0) return 0;
        int hash = id.hashCode() * 31 + salt;
        hash ^= (hash >>> 15);
        hash *= 0x2545F491;
        hash ^= (hash >>> 13);
        int span = 2 * range + 1;
        return Math.floorMod(hash, span) - range;
    }

    /**
     * {minX, minY, maxX, maxY} over everything drawn, in board units; null when empty.
     *
     * <p>Bounded by the columns themselves rather than by the marks inside them, so an alcove can
     * never be dragged half off the edge and clipped by the scissor.</p>
     */
    int[] contentBounds() {
        if (columns.isEmpty() || bands.isEmpty()) return null;
        int minX = 0;
        int maxX = contentWidth();
        int minY = 0;
        int maxY = contentHeight();
        // Marks near a column's edge, and the labels hanging under them, reach past the alcove.
        for (int[] local : positions.values()) {
            minX = Math.min(minX, local[0] - 18);
            maxX = Math.max(maxX, local[0] + 18);
            minY = Math.min(minY, local[1] - 18);
            maxY = Math.max(maxY, local[1] + 18 + font.lineHeight);
        }
        return new int[]{minX, minY, maxX, maxY};
    }
}
