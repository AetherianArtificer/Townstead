package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * How a mark on the career board is drawn: its size, its state vocabulary, and the round-bead
 * primitives the rest of the board's furniture is built from.
 *
 * <p>Extracted from CareerScreen so the state table below is the only place a node's appearance is
 * decided. While this lived inline, "locked" was expressed three different ways at once and which
 * one you got depended on whether the node happened to be large enough to draw an item icon.</p>
 */
final class NodeArt {
    private NodeArt() {}

    //? if >=1.21 {
    static final net.minecraft.resources.ResourceLocation LEDGER =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "townstead", "textures/gui/career/ledger.png");
    //?} else {
    /*static final net.minecraft.resources.ResourceLocation LEDGER =
            new net.minecraft.resources.ResourceLocation("townstead", "textures/gui/career/ledger.png");
    *///?}

    private static final int LEDGER_SIZE = 128;
    /** Atlas columns: pressed wax, struck ink, faint ink. From tools/gen_career_ledger_atlas.py. */
    private static final int MARK_SEAL = 0;
    private static final int MARK_STAMP = 1;
    private static final int MARK_FAINT = 2;

    /**
     * The whole of the state vocabulary, as data.
     *
     * <p>{@code column} is the atlas column; {@code walked} says whether the mark takes its path's
     * colour or plain ink; {@code disc} says whether a blank medallion is laid under it. Nothing
     * else may branch on {@code state()} to decide how a node looks.</p>
     */
    record MarkStyle(int column, boolean walked, boolean disc) {}

    private static final MarkStyle STYLE_ACQUIRED = new MarkStyle(MARK_SEAL, true, false);
    private static final MarkStyle STYLE_READY = new MarkStyle(MARK_STAMP, false, true);
    private static final MarkStyle STYLE_OUT_OF_REACH = new MarkStyle(MARK_FAINT, false, true);

    static MarkStyle styleOf(CareerGraphS2CPayload.Node node) {
        if (node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped()) {
            return STYLE_ACQUIRED;
        }
        return node.state() == CareerGraphS2CPayload.STATE_READY ? STYLE_READY : STYLE_OUT_OF_REACH;
    }

    // ── Sizes ──────────────────────────────────────────────────────────────
    //
    // Node sizes carry the hierarchy: the career anchors the board, a gateway is the landmark where
    // its specialization forks, and ordinary skills stay small enough that a long arm still fits.
    // All even, so a radius stays whole at every zoom step.

    /** A gateway is the landmark where a path forks, so it draws at the larger of the two sizes. */
    private static boolean isGateway(CareerGraphS2CPayload.Node node) {
        return node.kind() == CareerGraphS2CPayload.KIND_SKILL && node.path().gateway();
    }

    static int halfSizeOf(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 16;
            case CareerGraphS2CPayload.KIND_ADVANCED, CareerGraphS2CPayload.KIND_COMBO -> 12;
            default -> isGateway(node) ? 12 : 10;
        };
    }

    static int halfOnScreen(CareerGraphS2CPayload.Node node, float zoom) {
        return Math.max(4, Math.round(halfSizeOf(node) * zoom));
    }

    /** Authored sprite size, which must equal twice {@link #halfSizeOf} for the same node. */
    static int markSize(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 32;
            case CareerGraphS2CPayload.KIND_ADVANCED, CareerGraphS2CPayload.KIND_COMBO -> 24;
            default -> isGateway(node) ? 24 : 20;
        };
    }

    private static int markRow(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 0;
            case CareerGraphS2CPayload.KIND_ADVANCED, CareerGraphS2CPayload.KIND_COMBO -> 32;
            default -> isGateway(node) ? 32 : 56;
        };
    }

    /** Where a mark's ink actually ends on screen, which is inside its nominal half-size. */
    static int markEdge(CareerGraphS2CPayload.Node node, float zoom) {
        return Math.max(3, Math.round((markSize(node) / 2f - 1.2f) * zoom));
    }

    /**
     * One mark on the page. The atlas is authored at the exact pixel sizes the board uses at zoom 1
     * and the zoom steps are powers of two, so a mark is only ever drawn at its own size, half it,
     * or double it. Pixel art survives an exact halving and nothing else.
     */
    static void drawMark(GuiGraphics g, CareerGraphS2CPayload.Node node, int x, int y,
                         MarkStyle style, int tint, float zoom) {
        int authored = markSize(node);
        int v = markRow(node);
        int column = style.column();
        int drawn = Math.max(4, Math.round(authored * zoom));
        // A bare outline with an item icon floating inside it reads as art that failed to load, so
        // an unpressed mark gets a blank disc under its ring: a medallion waiting for the seal.
        if (style.disc()) {
            drawBead(g, x, y, Math.max(2, drawn / 2 - 1),
                    column == MARK_STAMP ? 0xFFF2E6C4 : 0xFFDCCAA2);
        }
        float r = ((tint >> 16) & 0xFF) / 255f;
        float gg = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        float a = ((tint >>> 24) == 0 ? 255 : (tint >>> 24)) / 255f;
        g.setColor(r, gg, b, a);
        g.blit(LEDGER, x - drawn / 2, y - drawn / 2, drawn, drawn,
                (float) (column * authored), (float) v, authored, authored,
                LEDGER_SIZE, LEDGER_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
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
     * The lit shoulder of a bead: two short arcs on the upper left, where the board's light comes
     * from.
     *
     * <p>Cheap on purpose. A real shaded sphere would need a gradient per row, and at ten pixels
     * across the only thing a reader registers is that the top edge is brighter than the bottom.</p>
     */
    static void drawGloss(GuiGraphics g, int cx, int cy, int half, int color) {
        if (half < 4) return;
        for (int dy = -half + 1; dy < -half / 3; dy++) {
            int inset = beadInset(half, dy) + 1;
            int span = Math.max(1, (half - inset) / 2);
            if (span <= 0) continue;
            g.fill(cx - half + inset, cy + dy, cx - half + inset + span, cy + dy + 1, color);
        }
    }

    /** The equipped mark: a pressed blob of wax, lifted above the board so nothing overdraws it. */
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

    /** Resolves the def-declared item icon, empty when absent or the item is not installed. */
    static ItemStack iconStack(CareerGraphS2CPayload.Node node) {
        if (node.icon().isEmpty()) return ItemStack.EMPTY;
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(node.icon());
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
