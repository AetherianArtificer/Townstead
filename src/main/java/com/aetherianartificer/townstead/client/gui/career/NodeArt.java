package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.GuiGraphics;

/**
 * How a mark on the career board is drawn: its size, its state vocabulary, and the round-bead
 * primitives the rest of the family's furniture is built from.
 *
 * <p>Extracted from CareerScreen so the state table below is the only place a node's appearance is
 * decided. While this lived inline, "locked" was expressed three different ways at once and which
 * one you got depended on whether the node happened to be large enough to draw an item icon.</p>
 *
 * <p>Marks are FRAMES, not seals. Minecraft's own tree already frames a 16-square item and lets the
 * outline say what kind of thing it is, and the icons the board carries are square whatever we put
 * around them: a circle has to out-size the sprite it holds, so the old bead was a large container
 * showing a small picture. The beads live on in the masthead crest and the points token, where the
 * subject really is round, and in the wax seal, which is a seal.</p>
 */
final class NodeArt {
    private NodeArt() {}

    /**
     * The whole of the state vocabulary, as data.
     *
     * <p>{@code rim} is the frame, {@code inner} the ground the icon sits on, {@code edge} the lit
     * top bevel, and {@code walked} says whether the mark counts as yours for the links that leave
     * it. Nothing else may branch on {@code state()} to decide how a node looks.</p>
     */
    record MarkStyle(int rim, int inner, int edge, boolean walked) {}

    private static final MarkStyle STYLE_ACQUIRED =
            new MarkStyle(Palette.BRASS, 0xFF33260F, Palette.BRASS_HOT, true);
    private static final MarkStyle STYLE_READY =
            new MarkStyle(Palette.BRASS_DEEP, 0xFF2A2013, 0xFF5E5142, false);
    private static final MarkStyle STYLE_OUT_OF_REACH =
            new MarkStyle(Palette.COLD_DEEP, 0xFF241A0E, 0xFF4A4034, false);

    static MarkStyle styleOf(CareerGraphS2CPayload.Node node) {
        if (node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped()) {
            return STYLE_ACQUIRED;
        }
        return node.state() == CareerGraphS2CPayload.STATE_READY ? STYLE_READY : STYLE_OUT_OF_REACH;
    }

    // ── Sizes ──────────────────────────────────────────────────────────────
    //
    // A frame is sized to the icon it holds, not the other way round. An item sprite is 16 square,
    // and a round mark has to be about 26 across before a 16 sprite fits inside it without clipping
    // its own corners; the board's answer had been to shrink the icon to ten pixels instead, so
    // every mark was a large container holding a small unreadable picture. A 22 frame carries the
    // sprite at full size in the footprint the old 20 bead occupied.
    //
    // Sizes still carry hierarchy, but they no longer carry KIND: a gateway is told apart by the
    // shape of its outline, so size is free to mean only importance again.

    /** A gateway is the landmark where a path forks: spiked frame, one step up in size. */
    private static boolean isGateway(CareerGraphS2CPayload.Node node) {
        return node.kind() == CareerGraphS2CPayload.KIND_SKILL && node.path().gateway();
    }

    /** The frame's edge length. Even, so a half stays whole at every zoom step. */
    static int markSize(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 30;
            case CareerGraphS2CPayload.KIND_ADVANCED -> 26;
            case CareerGraphS2CPayload.KIND_COMBO -> 22;
            default -> isGateway(node) ? 26 : 22;
        };
    }

    static int halfSizeOf(CareerGraphS2CPayload.Node node) {
        return markSize(node) / 2;
    }

    static int halfOnScreen(CareerGraphS2CPayload.Node node, float zoom) {
        return Math.max(4, Math.round(halfSizeOf(node) * zoom));
    }

    /**
     * Where a mark's ink ends on screen. Now exactly its half-size: a square frame fills its own
     * bounding box, so the fudge factor a round sprite needed is gone, and the hit box the screen
     * tests against is finally the shape the player is looking at.
     */
    static int markEdge(CareerGraphS2CPayload.Node node, float zoom) {
        return halfOnScreen(node, zoom);
    }

    /**
     * One mark on the board: a framed slot holding an item.
     *
     * <p>Drawn rather than blitted. The frame used to come from an authored atlas, which meant every
     * size needed its own hand-rasterised circle and a zoom step that was not a power of two came
     * out mush. Four nested rectangles survive any scale, and the frame's OUTLINE is now free to say
     * what kind of mark this is: plain for a skill, spiked for a gateway, chamfered for a Combo
     * Skill that belongs to two careers at once.</p>
     */
    static void drawMark(GuiGraphics g, CareerGraphS2CPayload.Node node, int x, int y,
                         MarkStyle style, float zoom) {
        int size = Math.max(8, Math.round(markSize(node) * zoom));
        int unit = Math.max(1, Math.round(zoom));
        int half = size / 2;
        int left = x - half;
        int top = y - half;
        int right = left + size;
        int bottom = top + size;

        g.fill(left + unit, top + 2 * unit, right + unit, bottom + 2 * unit, 0x66000000);
        g.fill(left, top, right, bottom, Palette.DESK_EDGE);
        g.fill(left + unit, top + unit, right - unit, bottom - unit, style.rim());
        g.fill(left + 2 * unit, top + 2 * unit, right - 2 * unit, bottom - 2 * unit, style.inner());
        // The lit bevel, on the same side the whole board is lit from.
        g.fill(left + unit, top + unit, right - unit, top + 2 * unit, style.edge());

        int nub = 2 * unit;
        if (isGateway(node)) {
            g.fill(left - nub, top - nub, left, top, style.rim());
            g.fill(right, top - nub, right + nub, top, style.rim());
            g.fill(left - nub, bottom, left, bottom + nub, style.rim());
            g.fill(right, bottom, right + nub, bottom + nub, style.rim());
            g.fill(x - unit, top - nub, x + unit, top, style.rim());
            g.fill(x - unit, bottom, x + unit, bottom + nub, style.rim());
        } else if (node.kind() == CareerGraphS2CPayload.KIND_COMBO) {
            g.fill(left, top, left + nub, top + nub, Palette.DESK_EDGE);
            g.fill(right - nub, top, right, top + nub, Palette.DESK_EDGE);
            g.fill(left, bottom - nub, left + nub, bottom, Palette.DESK_EDGE);
            g.fill(right - nub, bottom - nub, right, bottom, Palette.DESK_EDGE);
        }
    }

    /** The room inside a frame, which is what an icon may draw into. */
    static int innerSize(CareerGraphS2CPayload.Node node, float zoom) {
        int unit = Math.max(1, Math.round(zoom));
        return Math.max(4, Math.round(markSize(node) * zoom) - 4 * unit);
    }

    // ── Beads ──────────────────────────────────────────────────────────────

    /**
     * A filled round bead, drawn as rows whose ends tuck in near the top and bottom. Cheaper and
     * crisper at 12 pixels than any real circle, and it keeps the family's pixel-art edge.
     */
    static void drawBead(GuiGraphics g, int cx, int cy, int half, int color) {
        if (half <= 2) {
            g.fill(cx - half, cy - half, cx + half, cy + half, color);
            return;
        }
        for (int dy = -half; dy < half; dy++) {
            int inset = beadInset(half, dy);
            g.fill(cx - half + inset, cy + dy, cx + half - inset, cy + dy + 1, color);
        }
    }

    /** How far a bead's row at {@code dy} tucks in from its bounding box, in pixels. */
    private static int beadInset(int half, int dy) {
        float unit = (dy + 0.5f) / half;
        return Math.round(half * (1f - (float) Math.sqrt(Math.max(0f, 1f - unit * unit))));
    }

    /**
     * A one-pixel ring around a bead. Rows carry their own left and right edge, widened to span the
     * gap wherever the inset jumps by more than a pixel, so the ring stays closed at the shoulders
     * instead of dotting.
     */
    static void drawBeadRing(GuiGraphics g, int cx, int cy, int half, int color) {
        if (half <= 2) {
            Palette.drawOutline(g, cx - half, cy - half, cx + half, cy + half, color);
            return;
        }
        int prev = Integer.MIN_VALUE;
        for (int dy = -half; dy < half; dy++) {
            int inset = beadInset(half, dy);
            if (dy == -half || dy == half - 1) {
                g.fill(cx - half + inset, cy + dy, cx + half - inset, cy + dy + 1, color);
            } else {
                int lo = Math.min(prev, inset);
                int hi = Math.max(prev, inset);
                g.fill(cx - half + lo, cy + dy, cx - half + hi + 1, cy + dy + 1, color);
                g.fill(cx + half - hi - 1, cy + dy, cx + half - lo, cy + dy + 1, color);
            }
            prev = inset;
        }
    }

    /**
     * A struck metal seal: the crest's medallion, and the family's one deliberate circle.
     *
     * <p>Built from nested FILLED discs rather than a disc with rings laid over it. A ring drawn by
     * {@link #drawBeadRing} takes its thickness from the row-to-row jump in the disc's inset, so it
     * runs three pixels wide at the poles and one at the shoulders; stacking that over a disc of the
     * same radius, with a second disc two pixels in, gave three separately rasterised edges with
     * slivers of the dark base showing between them. That is the jaggedness. Concentric fills from
     * one rasteriser cannot disagree with each other.</p>
     *
     * <p>The bevel does the rest: lit along the upper left, shadowed along the lower right, which is
     * the whole reason a struck coin reads as struck rather than as a printed circle.</p>
     */
    static void drawSeal(GuiGraphics g, int cx, int cy, int half, boolean hot) {
        drawBead(g, cx, cy, half, 0xFF0D0803);
        drawBead(g, cx, cy, half - 1, hot ? Palette.BRASS : 0xFF6A4E24);
        drawBead(g, cx, cy, half - 2, hot ? Palette.BRASS_DEEP : 0xFF5A4018);
        drawSealBevel(g, cx, cy, half - 2, true, hot ? Palette.BRASS_HOT : 0x99FFF0C8);
        drawSealBevel(g, cx, cy, half - 2, false, 0x73140F08);
        drawBead(g, cx, cy, half - 3, hot ? Palette.BRASS : 0xFF8A6428);
        drawBead(g, cx, cy, half - 4, hot ? 0xFFC08E3C : 0xFF6E4E1C);
    }

    /** One pixel down the lit or shadowed flank of a seal, following the disc's own edge. */
    private static void drawSealBevel(GuiGraphics g, int cx, int cy, int half, boolean lit,
                                      int color) {
        if (half < 3) return;
        int from = lit ? -half : 0;
        int to = lit ? 0 : half;
        for (int dy = from; dy < to; dy++) {
            int inset = beadInset(half, dy);
            if (lit) {
                g.fill(cx - half + inset, cy + dy, cx - half + inset + 1, cy + dy + 1, color);
            } else {
                g.fill(cx + half - inset - 1, cy + dy, cx + half - inset, cy + dy + 1, color);
            }
        }
        // The caps, so the arc closes over the top and under the bottom instead of stopping at the
        // equator with a visible seam.
        int capDy = lit ? -half : half - 1;
        int capInset = beadInset(half, capDy);
        g.fill(cx - half + capInset, cy + capDy, cx + half - capInset, cy + capDy + 1, color);
    }

    /**
     * The equipped mark: a pressed blob of wax, lifted above the board so nothing overdraws it.
     *
     * <p>The one round thing left on the board, and the only one that earns it. A seal IS a circle;
     * a container for a square sprite is not, which is why everything else gave its circle up.</p>
     */
    static void drawWaxSeal(GuiGraphics g, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 280);
        g.fill(cx - 4, cy - 3, cx + 4, cy + 3, 0xC0140F08);
        g.fill(cx - 3, cy - 4, cx + 3, cy + 4, 0xC0140F08);
        g.fill(cx - 3, cy - 2, cx + 3, cy + 2, Palette.WAX_RIM);
        g.fill(cx - 2, cy - 3, cx + 2, cy + 3, Palette.WAX_RIM);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, Palette.WAX_SEAL);
        g.fill(cx - 1, cy - 1, cx, cy, 0xFFD86060);
        g.pose().popPose();
    }

    // ── Links ──────────────────────────────────────────────────────────────

    /**
     * A line between two marks, plotted pixel by pixel.
     *
     * <p>The board used to route every link along the lattice, down a column and across a row, on
     * the grounds that a Minecraft GUI stair-steps any other angle. That was true and it was the
     * wrong conclusion: a constellation's lines ARE thin and angled, and the staircase reads as
     * drawn rather than as broken once the line carries a bloom under it. Elbows, meanwhile, made
     * every path look like plumbing.</p>
     *
     * @param glow when set, a soft wider pass is laid under the core so a walked route reads as lit
     *             metal rather than as a darker rule
     */
    static void drawStraightLink(GuiGraphics g, int fromX, int fromY, int toX, int toY,
                                 int color, int glow, boolean dashed) {
        int dx = Math.abs(toX - fromX);
        int dy = Math.abs(toY - fromY);
        int sx = fromX < toX ? 1 : -1;
        int sy = fromY < toY ? 1 : -1;
        int err = dx - dy;
        int x = fromX;
        int y = fromY;
        int step = 0;
        // Bounded so a corrupt position can never spin the render thread.
        for (int guard = 0; guard < 4096; guard++) {
            // An unwalked route is dashed, which reads as "not yet" AND costs a third of the quads.
            // Every pixel of every cold link being its own fill was the board's whole draw budget.
            boolean ink = !dashed || (step % 3) != 2;
            if (ink) {
                if (glow != 0) g.fill(x - 1, y - 1, x + 2, y + 2, glow);
                g.fill(x, y, x + 1, y + 1, color);
            }
            if (x == toX && y == toY) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
            step++;
        }
    }

    /**
     * Draws a node's authored icon, false when it has none.
     *
     * <p>Board icons come from the same registries the ability picker reads, so they take the same
     * two forms: an item id, or a pack's own texture.</p>
     */
    static boolean drawIcon(GuiGraphics g, CareerGraphS2CPayload.Node node, float cx, float cy,
                            float scale) {
        return node != null && com.aetherianartificer.townstead.client.gui.common.IconArt
                .drawCentred(g, node.icon(), cx, cy, scale);
    }
}
