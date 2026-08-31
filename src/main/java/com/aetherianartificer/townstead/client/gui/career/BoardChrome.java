package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything drawn ON the board: the alcoves, the rank dividers, the lit routes and the marks.
 *
 * <p>THE BOARD'S VOCABULARY. Every mark is listed here with the question a reader is actually asking
 * when they look at it. <b>If a mark is not on this list, it is a bug, including one added in good
 * faith.</b></p>
 *
 * <pre>
 *   alcove + banner       which path is this column?
 *   band divider          which rank does this stretch belong to?
 *   frame (brass)         is this skill mine?
 *   frame (deep brass)    can I buy this one now?
 *   frame (cold, dimmed)  does this exist, and is it out of reach?
 *   frame outline shape   what kind is it: plain skill, gateway, Combo Skill?
 *   wax seal              is this the one I am using?
 *   item icon             what is it?
 *   link                  what must I own before it?
 *   lit route             what would I have to walk to reach the thing I just clicked?
 *   label                 what is it called?
 *   cost numeral          what would it cost me right now?
 *   focus ring            what am I pointing at?
 * </pre>
 *
 * <p>Nothing else. State is expressed ONCE by the mark, and light means an ACTION: a route lights
 * because you selected its end, not because the board felt like glowing. Ambient is deliberately
 * nearly nothing, since a field of pulsing marks has no focal point and reads as noise.</p>
 */
final class BoardChrome {

    private final Font font;
    private final CareerLayout layout;
    private final BoardView board;
    private final int unit;

    BoardChrome(Font font, CareerLayout layout, BoardView board, int unit) {
        this.font = font;
        this.layout = layout;
        this.board = board;
        this.unit = unit;
    }

    private float zoom() { return board.zoom(); }
    private int sx(int boardX) { return board.screenX(new int[]{boardX, 0}); }
    private int sy(int boardY) { return board.screenY(new int[]{0, boardY}); }

    /**
     * Draws the board and returns the node under the cursor, or null. The caller owns the tooltip,
     * because one drawn from inside the board's scissor would be clipped by it.
     */
    CareerGraphS2CPayload.Node draw(GuiGraphics g, List<CareerGraphS2CPayload.Node> tabNodes,
                                    String hoveredId, String selectedId,
                                    Map<String, CareerGraphS2CPayload.Node> byId) {
        int viewLeft = board.viewX();
        int viewTop = board.viewY();
        int viewRight = viewLeft + board.viewW();
        int viewBottom = viewTop + board.viewH();

        g.fill(viewLeft, viewTop, viewRight, viewBottom, Palette.DESK_DEEP);

        drawAlcoves(g, viewTop, viewBottom);
        drawBandDividers(g, viewLeft, viewRight);
        drawSpines(g, tabNodes);

        Set<String> route = routeEdges(selectedId, byId);
        drawLinks(g, tabNodes, route);

        CareerGraphS2CPayload.Node hovered = null;
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] local = layout.positionOf(node.id());
            if (local == null) continue;
            int x = board.screenX(local);
            int y = board.screenY(local);
            if (x < viewLeft - 24 || x > viewRight + 24) continue;
            if (y < viewTop - 24 || y > viewBottom + 24) continue;
            boolean isHovered = node.id().equals(hoveredId);
            if (isHovered) hovered = node;
            drawNode(g, node, x, y, isHovered, node.id().equals(selectedId));
            if (!labelled(node, hoveredId, selectedId, tabNodes)) continue;
            int column = layout.columnIndexOf(node);
            int labelRoom = Math.max(18, Math.round(CareerLayout.COL_W * zoom()) - 8);
            String label = ellipsize(node.name(), labelRoom);
            int labelX = sx(layout.columnX(column) + CareerLayout.COL_W / 2);
            int labelY = y + NodeArt.markEdge(node, zoom()) + unit + 2;
            // Ink on a dark alcove needs a shadow to hold its edge, which is the one place the old
            // board's dark-on-light logic inverts.
            g.drawString(font, label, labelX - font.width(label) / 2, labelY,
                    node.state() <= CareerGraphS2CPayload.STATE_LOCKED
                            ? 0xFF9A8A70 : 0xFFF0E2C0, true);
        }

        // Item icons are 3D geometry sitting at z=150, drawn through a buffer that flushes after the
        // flat quads around them. Flushing first fixes the ORDER but not the DEPTH: a banner filled
        // at z=0 still loses the depth test to a stack rendered above it. It has to be lifted clear
        // of the item layer as well, which is the same pair of moves the career picker needed.
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        drawStickyBanners(g, viewTop);
        g.pose().popPose();
        return hovered;
    }

    // ── Alcoves ────────────────────────────────────────────────────────────

    /**
     * A path as a recessed alcove in the desk, running the board's full height with its own backdrop
     * inside it.
     *
     * <p>This replaced the section card, which fought itself: it was a pale plate on a pale board
     * with a tinted header, a tinted rail, zebra rows AND a drop shadow, all separating the same two
     * things the gutter was already separating. An alcove separates by depth and by what is painted
     * inside it, and needs no border to be read as a region.</p>
     */
    private void drawAlcoves(GuiGraphics g, int viewTop, int viewBottom) {
        List<CareerLayout.Column> columns = layout.columns();
        for (int i = 0; i < columns.size(); i++) {
            int left = sx(layout.columnX(i));
            int right = left + Math.round(CareerLayout.COL_W * zoom());
            if (right < board.viewX() || left > board.viewX() + board.viewW()) continue;
            CareerLayout.Column column = columns.get(i);

            g.fill(left, viewTop, right, viewBottom, Palette.ALCOVE);
            // The path's colour lives here and nowhere else on the board: a wash behind everything,
            // never on the marks or the rules in front of it.
            g.fill(left, viewTop, right, viewBottom, Palette.fade(column.tint(), 0.10f));
            drawBackdrop(g, column, left, viewTop, right, viewBottom);
            // Recessed: dark at the top and left, a lip at the bottom and right.
            g.fill(left, viewTop, right, viewTop + 1, Palette.DESK_EDGE);
            g.fill(left, viewTop, left + 1, viewBottom, Palette.DESK_EDGE);
            g.fill(right - 1, viewTop, right, viewBottom, Palette.DESK_LIP);
        }
    }

    /**
     * The art a path ships for its own alcove, and nothing at all when it ships none.
     *
     * <p>There was briefly a procedural weave behind columns with no authored art, on the theory
     * that the alcoves needed telling apart by surface as well as by tint. They do not: the wash
     * and the banner already do that, and a texture invented by the renderer reads as noise rather
     * than as material. An empty alcove is a clean one.</p>
     */
    private void drawBackdrop(GuiGraphics g, CareerLayout.Column column,
                              int left, int top, int right, int bottom) {
        if (column.backdrop().isEmpty()) return;
        ResourceLocation art = DataPackLang.parseId(column.backdrop());
        if (art == null) return;
        g.setColor(1f, 1f, 1f, 0.35f);
        g.blit(art, left + 1, top, right - left - 2, bottom - top, 0f, 0f, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * A rank divider crossing every column at once, because a rank is a gate over the whole board
     * and not a property of one path.
     */
    private void drawBandDividers(GuiGraphics g, int viewLeft, int viewRight) {
        List<CareerLayout.Band> bands = layout.bands();
        for (int i = 0; i < bands.size(); i++) {
            int y = sy(layout.bandTop(i)) - 6;
            if (y < board.viewY() - 2 || y > board.viewY() + board.viewH()) continue;
            boolean reached = layout.careerTier() >= bands.get(i).rank();
            // A gate needs marking, not announcing. Two solid rules across the whole board read as
            // the strongest thing on it, which put the emphasis on the furniture rather than on the
            // marks the furniture is organising.
            g.fill(viewLeft, y, viewRight, y + 1,
                    reached ? Palette.fade(Palette.BRASS_DEEP, 0.55f)
                            : Palette.fade(Palette.DESK_LIP, 0.45f));
        }
    }

    /** Unpathed skills use a shared trunk; Path skills connect only through authored requirements. */
    private void drawSpines(GuiGraphics g, List<CareerGraphS2CPayload.Node> tabNodes) {
        Set<Integer> pathColumns = new HashSet<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_SKILL && node.path().present()) {
                pathColumns.add(layout.columnIndexOf(node));
            }
        }

        // General skills and Combo Skills do not have authored lane positions. Their one shared
        // spine remains the honest statement: these marks belong to this column in rank order.
        List<CareerLayout.Column> columns = layout.columns();
        int litTo = sy(layout.bandTop(Math.max(0,
                Math.min(layout.bands().size() - 1, layout.careerTier()))) - 6);
        for (int i = 0; i < columns.size(); i++) {
            if (pathColumns.contains(i)) continue;
            int spineX = sx(layout.columnX(i) + CareerLayout.COL_W / 2);
            if (spineX < board.viewX() - 4 || spineX > board.viewX() + board.viewW() + 4) continue;
            // The spine runs between the first and last mark it actually joins. It used to run the
            // full content height, which left a stub of line above the topmost mark in every
            // column, hanging from the banner and connecting nothing.
            int[] extent = layout.columnExtent(i);
            if (extent == null) continue;
            int top = sy(extent[0]);
            int bottom = sy(extent[1]);
            if (bottom <= top) continue;
            g.fill(spineX, top, spineX + 1, bottom, Palette.fade(Palette.COLD, 0.55f));
            if (litTo > top) {
                g.fill(spineX, top, spineX + 1, Math.min(litTo, bottom),
                        Palette.fade(Palette.BRASS_DEEP, 0.75f));
            }
        }
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.path().present()) continue;
            int[] at = layout.positionOf(node.id());
            if (at == null) continue;
            int spineX = sx(layout.columnX(layout.columnIndexOf(node)) + CareerLayout.COL_W / 2);
            int x = board.screenX(at);
            int y = board.screenY(at);
            if (Math.abs(x - spineX) < 3) continue;
            boolean lit = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped();
            g.fill(Math.min(x, spineX), y, Math.max(x, spineX), y + 1,
                    lit ? Palette.fade(Palette.BRASS, 0.8f) : Palette.fade(Palette.COLD, 0.6f));
        }
    }

    /**
     * Each column's name, pinned to the top of the viewport rather than to the top of the content.
     *
     * <p>Ten ranks of scrolling used to take the column headings off screen and leave a board of
     * anonymous marks. A banner that stays put is the difference between a deep board you can read
     * and one you get lost in.</p>
     */
    private void drawStickyBanners(GuiGraphics g, int viewTop) {
        List<CareerLayout.Column> columns = layout.columns();
        int h = font.lineHeight + unit;
        for (int i = 0; i < columns.size(); i++) {
            int left = sx(layout.columnX(i)) + 3;
            int right = left + Math.round(CareerLayout.COL_W * zoom()) - 6;
            if (right < board.viewX() || left > board.viewX() + board.viewW()) continue;
            CareerLayout.Column column = columns.get(i);
            int y = viewTop + 3;
            g.fill(left + 1, y + 1, right + 1, y + h + 1, 0x80000000);
            g.fill(left, y, right, y + h, column.tint());
            g.fill(left, y, right, y + 1, 0x3AFFFFFF);
            g.fill(left, y + h - 1, right, y + h, 0x60000000);
            String name = column.name();
            int room = right - left - 4;
            boolean clip = font.width(name) > room;
            if (clip) g.enableScissor(left + 2, y, right - 2, y + h);
            g.drawString(font, name, left + (right - left - Math.min(font.width(name), room)) / 2,
                    y + (h - font.lineHeight) / 2, 0xFF22160A, false);
            if (clip) g.disableScissor();
        }
    }

    /**
     * The rank rail: number and name for every band, in a strip the board never scrolls under.
     *
     * <p>Drawn by the screen into its own reserved column rather than over the board, so it cannot
     * cover the leftmost alcove the way a floating overlay would.</p>
     */
    void drawRankGutter(GuiGraphics g, int x, int top, int width, int bottom) {
        g.fill(x, top, x + width, bottom, 0xFF1A1208);
        g.fill(x + width - 1, top, x + width, bottom, Palette.DESK_LIP);
        List<CareerLayout.Band> bands = layout.bands();
        for (int i = 0; i < bands.size(); i++) {
            int bandTop = sy(layout.bandTop(i)) - 6;
            int bandBottom = sy(layout.bandTop(i) + CareerLayout.BAND_H) - 6;
            int visibleTop = Math.max(top, bandTop);
            int visibleBottom = Math.min(bottom, bandBottom);
            if (visibleBottom <= visibleTop) continue;
            if (i % 2 == 1) g.fill(x + 1, visibleTop, x + width - 1, visibleBottom, 0xFF211710);

            String numeral = String.valueOf(bands.get(i).rank());
            String rankName = RecordArt.abbreviate(font, bands.get(i).name(), width - 6);
            boolean reached = layout.careerTier() >= bands.get(i).rank();
            // The numeral STICKS to the top of its own band's visible stretch. Drawn only at the
            // band boundary it scrolled away, which is what the floating rank chip existed to
            // paper over, and that chip landed straight on top of the first column's banner. A
            // numeral that pins itself needs no second widget.
            int numeralY = Math.max(visibleTop + 4, bandTop + 5);
            if (numeralY + font.lineHeight <= visibleBottom) {
                g.drawString(font, numeral, x + (width - font.width(numeral)) / 2, numeralY,
                        reached ? Palette.BRASS : Palette.COLD, false);
                int nameY = numeralY + font.lineHeight + 2;
                if (nameY + font.lineHeight <= visibleBottom) {
                    g.drawString(font, rankName, x + (width - font.width(rankName)) / 2, nameY,
                            reached ? 0xFFB79A6C : Palette.COLD_DEEP, false);
                }
            }
        }
    }

    // ── Links ──────────────────────────────────────────────────────────────

    /**
     * The prerequisite chain from the career down to the selected mark, as edge keys.
     *
     * <p>This is the whole of the board's lighting logic: the route you would have to walk to reach
     * what you just clicked. Nothing else lights on its own.</p>
     */
    private Set<String> routeEdges(String selectedId,
                                   Map<String, CareerGraphS2CPayload.Node> byId) {
        Set<String> edges = new HashSet<>();
        CareerGraphS2CPayload.Node node = byId.get(selectedId);
        int guard = 0;
        while (node != null && guard++ < 64) {
            List<String> parents = edgeTargets(node);
            if (parents.isEmpty()) break;
            String parentId = parents.get(0);
            edges.add(parentId + ">" + node.id());
            node = byId.get(parentId);
        }
        return edges;
    }

    private void drawLinks(GuiGraphics g, List<CareerGraphS2CPayload.Node> tabNodes,
                           Set<String> route) {
        Map<String, int[]> ends = new HashMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] at = layout.positionOf(node.id());
            if (at != null) ends.put(node.id(), at);
        }
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] childPos = ends.get(node.id());
            if (childPos == null) continue;
            boolean acquired = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED
                    || node.equipped();
            for (String targetId : edgeTargets(node)) {
                int[] targetPos = ends.get(targetId);
                if (targetPos == null) continue;
                boolean onRoute = route.contains(targetId + ">" + node.id());
                boolean walked = acquired && isAcquired(targetId, tabNodes);
                int color;
                int glow = 0;
                boolean dashed = false;
                if (walked) {
                    color = Palette.BRASS;
                    glow = Palette.fade(Palette.BRASS, 0.16f);
                } else if (onRoute) {
                    color = 0xFFA8823E;
                    glow = Palette.fade(Palette.BRASS, 0.10f);
                } else {
                    color = Palette.COLD_DEEP;
                    dashed = true;
                }
                NodeArt.drawStraightLink(g, board.screenX(targetPos), board.screenY(targetPos),
                        board.screenX(childPos), board.screenY(childPos), color, glow, dashed);
            }
        }
    }

    private static boolean isAcquired(String id, List<CareerGraphS2CPayload.Node> tabNodes) {
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.id().equals(id)) {
                return node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped();
            }
        }
        return false;
    }

    /**
     * What a node hangs from. A Combo Skill draws no link of its own: it sits between the columns it
     * joins, and a line back to one career would claim it for that career.
     */
    private static List<String> edgeTargets(CareerGraphS2CPayload.Node node) {
        if (node.kind() == CareerGraphS2CPayload.KIND_COMBO) return List.of();
        if (node.kind() == CareerGraphS2CPayload.KIND_SKILL && !node.requires().isEmpty()) {
            return node.requires();
        }
        return node.parentId().isEmpty() ? List.of() : List.of(node.parentId());
    }

    // ── Marks ──────────────────────────────────────────────────────────────

    /**
     * Which nodes say their name unprompted. The choices you made are named; the ones you passed
     * over answer on hover, where the question is actually being asked. Naming all three options at
     * a rank puts three captions in one cluster and they collide.
     */
    private boolean labelled(CareerGraphS2CPayload.Node node, String hoveredId,
                             String selectedId,
                             List<CareerGraphS2CPayload.Node> tabNodes) {
        if (node.id().equals(hoveredId) || node.id().equals(selectedId)) return true;
        int peers = 0;
        int column = layout.columnIndexOf(node);
        for (CareerGraphS2CPayload.Node other : tabNodes) {
            if (other.kind() != CareerGraphS2CPayload.KIND_SKILL
                    && other.kind() != CareerGraphS2CPayload.KIND_COMBO) continue;
            if (other.tier() == node.tier() && layout.columnIndexOf(other) == column) peers++;
        }
        return peers <= 1;
    }

    private String ellipsize(String text, int room) {
        if (font.width(text) <= room) return text;
        String suffix = "…";
        String cut = text;
        while (cut.length() > 1 && font.width(cut + suffix) > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + suffix;
    }

    private void drawNode(GuiGraphics g, CareerGraphS2CPayload.Node node, int x, int y,
                          boolean hovered, boolean selected) {
        int half = NodeArt.halfOnScreen(node, zoom());
        NodeArt.MarkStyle style = NodeArt.styleOf(node);
        boolean ready = node.state() == CareerGraphS2CPayload.STATE_READY;
        boolean acquired = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED;

        // Ambient is almost nothing. An acquired mark holds a steady bloom because it IS lit; a
        // ready one breathes on a four-second cycle, slow enough to notice only when you look for
        // it. Everything loud is reserved for what you are pointing at or have chosen.
        float alpha = 0f;
        if (acquired) {
            alpha = 0.16f;
        } else if (ready) {
            alpha = Accessibility.isReduceMotion() ? 0.10f
                    : 0.09f + 0.035f * Mth.sin((Util.getMillis() % 240000L) / 640f);
        }
        if (hovered) alpha += 0.18f;
        if (selected) alpha += 0.12f;
        if (alpha > 0.01f) {
            // Square, to match what it is behind. A round halo around a square frame leaves the
            // frame's own corners hanging outside its glow.
            int a = (int) (Math.min(0.95f, alpha) * 255f) << 24;
            int spread = half + Math.max(2, Math.round(4 * zoom()));
            g.fill(x - spread, y - spread, x + spread, y + spread, a | (Palette.BRASS & 0xFFFFFF));
        }

        // The frame draws its own shadow and its own lit bevel, so depth is one call rather than
        // three. Flat tinted sprites on a flat ground gave the board no sense that the marks were
        // sitting ON anything.
        NodeArt.drawMark(g, node, x, y, style, zoom());

        // The icon is drawn at its authored size now: the frame was built around it, so there is
        // nothing left to shrink it to fit.
        float iconScale = Math.min(zoom(), NodeArt.innerSize(node, zoom()) / 16f);
        if (NodeArt.drawIcon(g, node, x, y, iconScale)) {
            if (!style.walked() && node.state() <= CareerGraphS2CPayload.STATE_LOCKED) {
                // Out of reach: the icon is shown, not hidden, but held back from the ones you can
                // actually act on.
                g.flush();
                g.fill(x - half + 2, y - half + 2, x + half - 2, y + half - 2, 0x99241A0E);
            }
        }
        // Cost belongs in the selected record's Requirements section. Putting “[1]” inside a
        // 22-pixel item frame obscures the icon and repeats information already visible at right.
        if (node.equipped()) {
            int edge = Math.max(5, NodeArt.markEdge(node, zoom()) - 2);
            Palette.drawOutline(g, x - edge, y - edge, x + edge, y + edge,
                    Palette.BRASS_HOT);
        }
        if (selected || hovered) {
            // Selection is painted INSIDE the authored footprint. Growing the outline outside the
            // mark made one icon in a row look physically larger even though every sprite was the
            // same 16-square item.
            int edge = Math.max(4, NodeArt.markEdge(node, zoom()) - (selected ? 2 : 1));
            Palette.drawOutline(g, x - edge, y - edge, x + edge, y + edge,
                    selected ? Palette.BRASS_HOT : Palette.fade(Palette.BRASS, 0.7f));
        }
    }

}
