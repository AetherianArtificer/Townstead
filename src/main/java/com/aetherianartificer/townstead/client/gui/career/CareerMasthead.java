package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * The head of the record: whose it is, what rank they hold, what they are working toward, and what
 * they have left to spend.
 *
 * <p>Career switching used to be a strip of anonymous item icons with paging arrows, which broke
 * down at about ten careers and told you nothing about any of them. It is now the crest itself: the
 * medallion and the career's name form one button carrying a chevron, which is a shape people
 * already read as a menu, and the list behind it has room for names and ranks. That scales to any
 * number of careers and gives the masthead back the space the strip was eating.</p>
 *
 * <p>The progress track shows a DEED, not an experience number. "Dishes cooked 42/60" is the thing
 * the chronicle actually records, it changes as you play in a way an abstract total never does, and
 * it can never be mistaken for the green bar at the bottom of the screen.</p>
 */
final class CareerMasthead {

    static final int HEIGHT = 34;
    private static final int CREST_R = 12;
    /** Baselines for the band's two rows, so nothing is nudged into place by eye. */
    private static final int ROW_1 = 4;
    private static final int ROW_2 = 17;
    private static final int BAR_H = 7;
    private static final int TOKEN_H = 18;
    /** How long the one-time "this opens" nudge stays up, in milliseconds. */
    private static final long HINT_MS = 4200L;

    private final Font font;
    private final int unit;
    private int crestX;
    private int crestY;
    private int crestW;

    CareerMasthead(Font font, int unit) {
        this.font = font;
        this.unit = unit;
    }

    boolean overCrest(double mouseX, double mouseY) {
        return mouseX >= crestX && mouseX < crestX + crestW
                && mouseY >= crestY && mouseY < crestY + HEIGHT - 2;
    }

    void draw(GuiGraphics g, int x, int y, int w, String activeRoot,
              CareerGraphS2CPayload.Node career, List<CareerGraphS2CPayload.Node> allNodes,
              int mouseX, int mouseY, boolean pickerOpen, long hintStart) {
        g.fill(x, y, x + w, y + HEIGHT, 0xFF1D1206);
        g.fill(x, y + HEIGHT - 2, x + w, y + HEIGHT - 1, 0xFF6A4E24);
        g.fill(x, y + HEIGHT - 1, x + w, y + HEIGHT, 0xFF0D0803);

        String name = career == null || career.name().isEmpty()
                ? activeRoot : career.name();
        int nameWidth = font.width(name);
        crestX = x + 2;
        crestY = y + 2;
        crestW = 2 * CREST_R + 8 + nameWidth + 12;
        boolean hot = overCrest(mouseX, mouseY) || pickerOpen;
        if (hot) {
            g.fill(crestX, crestY, crestX + crestW, crestY + HEIGHT - 4, 0xFF2E1F0C);
            Palette.drawOutline(g, crestX, crestY, crestX + crestW, crestY + HEIGHT - 4,
                    Palette.BRASS_DEEP);
        }

        int cx = crestX + 2 + CREST_R;
        int cy = crestY + (HEIGHT - 4) / 2;
        drawCrest(g, career, cx, cy, hot);

        int textX = cx + CREST_R + 5;
        g.drawString(font, name, textX, y + ROW_1, hot ? 0xFFFFF3D6 : Palette.LABEL_LIGHT, false);
        drawChevron(g, textX + nameWidth + 4, y + ROW_1 + 4, hot);

        // Rank as pips rather than a number: marks you can count at a glance say more about where
        // you are in a career than "3" does.
        if (career != null) {
            int pips = Math.min(Math.max(1, career.maxTier()), 8);
            int pipY = y + ROW_2 + (font.lineHeight - 3) / 2;
            for (int i = 0; i < pips; i++) {
                int px = textX + i * 7;
                int fill = i < career.tier() ? Palette.BRASS : 0xFF4A3A20;
                g.fill(px + 1, pipY, px + 3, pipY + 1, fill);
                g.fill(px, pipY + 1, px + 4, pipY + 2, fill);
                g.fill(px + 1, pipY + 2, px + 3, pipY + 3, fill);
            }
            if (!career.rankName().isEmpty()) {
                g.drawString(font, career.rankName(), textX + pips * 7 + 5, y + ROW_2,
                        0xFFB79A6C, false);
            }
        }

        int tokenW = 42;
        int tokenX = x + w - tokenW - unit;
        // The left block owns whatever the crest, the name and the rank line actually need, so the
        // track starts clear of the longest of the three instead of clear of the crest alone.
        int leftBlock = crestX + crestW;
        if (career != null) {
            int pips = Math.min(Math.max(1, career.maxTier()), 8);
            leftBlock = Math.max(leftBlock,
                    textX + pips * 7 + 5 + font.width(career.rankName()));
        }
        int trackX = leftBlock + 3 * unit;
        int trackW = tokenX - 3 * unit - trackX;
        if (trackW > 50) drawDeedTrack(g, career, trackX, y, trackW);
        // The token sits ON the bar's baseline rather than centred in the band. Two boxes of
        // different heights next to each other read as level when their feet agree, not when their
        // middles do, and the bar is the thing the eye is already tracking across.
        drawPointsToken(g, career, tokenX, barY(y) + BAR_H - TOKEN_H, tokenW);

        if (hintStart > 0) {
            // The nudge hangs BELOW the masthead, over a board whose column banners are themselves
            // lifted clear of the item layer. It has to sit above both or it is a hint you cannot
            // read, which is worse than no hint.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 350);
            drawHint(g, hintStart);
            g.pose().popPose();
        }
    }

    private void drawCrest(GuiGraphics g, CareerGraphS2CPayload.Node career, int cx, int cy,
                           boolean hot) {
        if (hot) {
            NodeArt.drawBead(g, cx, cy, CREST_R + 4, Palette.fade(Palette.BRASS, 0.22f));
        }
        NodeArt.drawBead(g, cx, cy, CREST_R, 0xFF0D0803);
        NodeArt.drawBeadRing(g, cx, cy, CREST_R, hot ? Palette.BRASS : 0xFF6A4E24);
        NodeArt.drawBead(g, cx, cy, CREST_R - 2, Palette.BRASS_DEEP);
        NodeArt.drawBead(g, cx, cy, CREST_R - 3, Palette.BRASS);
        ItemStack icon = career == null ? ItemStack.EMPTY : NodeArt.iconStack(career);
        if (!icon.isEmpty()) {
            g.renderItem(icon, cx - 8, cy - 8);
        } else {
            NodeArt.drawBead(g, cx, cy, CREST_R - 6, Palette.BRASS_HOT);
        }
    }

    private void drawChevron(GuiGraphics g, int x, int y, boolean hot) {
        int ink = hot ? Palette.BRASS_HOT : 0xFF9A7A48;
        g.fill(x, y, x + 5, y + 1, ink);
        g.fill(x + 1, y + 1, x + 4, y + 2, ink);
        g.fill(x + 2, y + 2, x + 3, y + 3, ink);
    }

    /**
     * The nearest thing the chronicle is actually counting, with its own bar.
     *
     * <p>Falls back to the experience line only when a career declares no evidence at all, because
     * some progression has to be shown and a number is better than nothing.</p>
     */
    private void drawDeedTrack(GuiGraphics g, CareerGraphS2CPayload.Node career, int x, int y,
                               int w) {
        if (career == null) return;
        CareerGraphS2CPayload.Evidence deed = null;
        for (CareerGraphS2CPayload.Evidence evidence : career.evidence()) {
            if (evidence.target() <= 0) continue;
            if (!evidence.met()) {
                deed = evidence;
                break;
            }
            if (deed == null) deed = evidence;
        }

        // The NAME of the thing being counted and the COUNT are two separate strings, because only
        // one of them can afford to be shortened. They used to be concatenated and the pair was
        // scissored to fit, which cut the tally in half and left "0 / 11" reading as a wrong number
        // rather than as a clipped one.
        String heading;
        String value;
        float progress;
        if (deed != null) {
            heading = deed.label();
            value = deed.current() + " / " + deed.target();
            progress = Mth.clamp(deed.current() / (float) deed.target(), 0f, 1f);
        } else {
            int total = career.xp() + career.xpToNext();
            progress = total <= 0 ? 1f : Mth.clamp(career.xp() / (float) total, 0f, 1f);
            heading = Component.translatable("townstead.career.screen.evidence").getString();
            value = career.xp() + " / " + total;
        }

        int valueWidth = font.width(value);
        g.drawString(font, RecordArt.abbreviate(font, heading, w - valueWidth - 8), x, y + ROW_1,
                0xFF8A7048, false);
        g.drawString(font, value, x + w - valueWidth, y + ROW_1, 0xFFB79A6C, false);

        int barY = barY(y);
        g.fill(x, barY, x + w, barY + BAR_H, 0xFF0D0803);
        Palette.drawOutline(g, x, barY, x + w, barY + BAR_H, 0xFF4A3218);
        int fill = Math.round((w - 2) * progress);
        if (fill > 0) {
            g.fill(x + 1, barY + 1, x + 1 + fill, barY + BAR_H - 1, Palette.BRASS_DEEP);
            g.fill(x + 1, barY + 1, x + 1 + fill, barY + 3, Palette.BRASS);
            g.fill(x + 1, barY + 1, x + 1 + fill, barY + 2, Palette.BRASS_HOT);
        }
    }

    /** The evidence bar's top edge. The one line everything on the band's second row agrees on. */
    private int barY(int y) { return y + ROW_2 + (font.lineHeight - BAR_H) / 2; }

    /** Unspent points, glowing only while you actually have something to spend. */
    private void drawPointsToken(GuiGraphics g, CareerGraphS2CPayload.Node career, int x, int y,
                                 int w) {
        int points = career == null ? 0 : career.points();
        boolean spendable = career != null
                && career.state() == CareerGraphS2CPayload.STATE_ACQUIRED && points > 0;
        if (spendable) {
            float pulse = Accessibility.isReduceMotion() ? 0.5f
                    : 0.5f + 0.5f * Mth.sin((Util.getMillis() % 120000L) / 700f);
            NodeArt.drawBead(g, x + w / 2, y + TOKEN_H / 2, 16,
                    Palette.fade(Palette.BRASS, 0.14f + pulse * 0.12f));
        }
        g.fill(x, y, x + w, y + TOKEN_H, 0xFF2E1F0C);
        Palette.drawOutline(g, x, y, x + w, y + TOKEN_H, spendable ? 0xFF8A6A30 : 0xFF4A3218);
        // Bead and numeral share one centre line, and the numeral is centred in the room LEFT OVER
        // beside the bead rather than parked at a fixed offset from it, so a two-digit count stays
        // inside the box and a one-digit count is not hard against the bead.
        int mid = y + TOKEN_H / 2;
        NodeArt.drawBead(g, x + 9, mid, 6, spendable ? Palette.BRASS_DEEP : 0xFF3A3020);
        NodeArt.drawBead(g, x + 9, mid, 5, spendable ? Palette.BRASS : 0xFF4A4030);
        if (spendable) NodeArt.drawBead(g, x + 9, mid, 2, Palette.BRASS_HOT);
        String count = String.valueOf(points);
        int room = x + w - 3 - (x + 17);
        g.drawString(font, count, x + 17 + Math.max(0, (room - font.width(count)) / 2), mid - 4,
                spendable ? Palette.BRASS_HOT : 0xFF6E5A38, false);
    }

    /**
     * The one-time nudge that the crest opens something.
     *
     * <p>A medallion was never going to advertise itself. The chevron does most of the work; this
     * covers the rest, once, and then never appears again.</p>
     */
    private void drawHint(GuiGraphics g, long hintStart) {
        long age = Util.getMillis() - hintStart;
        if (age < 0 || age > HINT_MS) return;
        float fade = age > HINT_MS - 800 ? (HINT_MS - age) / 800f : 1f;
        float pulse = Accessibility.isReduceMotion() ? 1f
                : 0.5f + 0.5f * Mth.sin(age / 240f);
        int ringAlpha = (int) (fade * (0.35f + pulse * 0.35f) * 255f) << 24;
        // Same centre the crest is drawn from. Deriving it a second time by eye is what put the
        // ring two pixels off the thing it was pointing at.
        NodeArt.drawBeadRing(g, crestX + 2 + CREST_R, crestY + (HEIGHT - 4) / 2,
                CREST_R + 3 + Math.round(pulse * 2), ringAlpha | (Palette.BRASS_HOT & 0xFFFFFF));

        String message = Component.translatable("townstead.career.screen.switch_hint").getString();
        int w = font.width(message) + 10;
        int x = crestX + 6;
        int y = crestY + HEIGHT - 2;
        int alpha = (int) (fade * 255f) << 24;
        g.fill(x + 1, y + 1, x + w + 1, y + font.lineHeight + 7, alpha & 0x80000000);
        g.fill(x, y, x + w, y + font.lineHeight + 6, (alpha & 0xFF000000) | 0x2E1F0C);
        Palette.drawOutline(g, x, y, x + w, y + font.lineHeight + 6,
                (alpha & 0xFF000000) | (Palette.BRASS_DEEP & 0xFFFFFF));
        g.drawString(font, message, x + 5, y + 3,
                (alpha & 0xFF000000) | (Palette.BRASS_HOT & 0xFFFFFF), false);
    }

    // ── The picker ─────────────────────────────────────────────────────────

    private static final int ROW_H = 13;

    int pickerWidth() { return Math.max(120, crestW + 24); }

    int pickerHeight(int careers) { return 13 + careers * ROW_H; }

    /** @return the root id under the cursor, or null. */
    String pickerHit(double mouseX, double mouseY, List<String> roots, int x, int y) {
        int w = pickerWidth();
        if (mouseX < x || mouseX >= x + w) return null;
        int row = (int) ((mouseY - (y + 13)) / ROW_H);
        if (row < 0 || row >= roots.size()) return null;
        return roots.get(row);
    }

    void drawPicker(GuiGraphics g, int x, int y, List<String> roots, String activeRoot,
                    Map<String, CareerGraphS2CPayload.Node> byId, double mouseX, double mouseY) {
        int w = pickerWidth();
        int h = pickerHeight(roots.size());
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x8C000000);
        g.fill(x, y, x + w, y + h, 0xFF241708);
        Palette.drawOutline(g, x, y, x + w, y + h, 0xFF6A4E24);
        String heading = Component.translatable("townstead.career.screen.switch").getString();
        g.drawString(font, heading, x + 5, y + 3, 0xFF8A7048, false);
        g.fill(x + 4, y + 11, x + w - 4, y + 12, 0xFF4A3218);

        for (int i = 0; i < roots.size(); i++) {
            String rootId = roots.get(i);
            int rowY = y + 13 + i * ROW_H;
            boolean active = rootId.equals(activeRoot);
            boolean hover = mouseX >= x && mouseX < x + w
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (active || hover) {
                g.fill(x + 2, rowY, x + w - 2, rowY + ROW_H - 1, active ? 0xFF3A2611 : 0xFF2E1F0C);
            }
            if (active) g.fill(x + 2, rowY, x + 4, rowY + ROW_H - 1, Palette.BRASS);
            CareerGraphS2CPayload.Node root = byId.get(rootId);
            ItemStack icon = root == null ? ItemStack.EMPTY : NodeArt.iconStack(root);
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + 7, rowY + 1, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }
            String label = root == null || root.name().isEmpty() ? rootId : root.name();
            g.drawString(font, label, x + 19, rowY + 2,
                    active ? 0xFFF0DDB0 : 0xFFC0AC85, false);
            String rank = root == null ? "" : root.rankName();
            if (!rank.isEmpty()) {
                int rankW = font.width(rank);
                if (x + 19 + font.width(label) + 6 < x + w - 5 - rankW) {
                    g.drawString(font, rank, x + w - 5 - rankW, rowY + 2, 0xFF8A7048, false);
                }
            }
        }
    }
}
