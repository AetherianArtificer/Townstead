package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Today's sheet: the registry record for whatever mark is selected on the board.
 *
 * <p>Two zones. The <b>head is pinned</b> and carries identity, state, the locator that ties this
 * record to its column on the board, the cost, and the stamp well; the <b>body scrolls</b> and
 * carries blocks. The split is not cosmetic: with the well in the scrolling flow, a long chronicle
 * could push it off screen, or scroll the page out from under a drag already in progress.</p>
 *
 * <p>Blocks are ordered by the question a reader is asking, not by what the payload happens to
 * hold: what can I do, what is it, what stops me, what have I done, what happened. The record also
 * draws nothing the masthead already shows, which is why selecting the career opens on what you
 * could register rather than on the career's name again.</p>
 */
final class RecordPage {

    /** A registerable skill listed in the head block, and the box that jumps to it. */
    record Jump(int x, int y, int w, int h, String nodeId) {}

    /** What the caller needs back: where the stamp well landed, and which rows are jumps. */
    record Result(int wellX, int wellY, boolean wellShown, List<Jump> jumps) {}

    private static final Result EMPTY = new Result(0, 0, false, List.of());

    /** How many registerable skills the shortcut lists before deferring to the board. */
    private static final int MAX_READY_ROWS = 4;

    /**
     * THE RECORD'S GRID. Three numbers, and nothing on this page is spaced by anything else.
     *
     * <p>Rows were being placed at x+7, x+9, x+15 and x+16, blocks were separated by 2, 4 or 5
     * depending on which block, and one block reserved room for a bar it did not always draw. Seen
     * together that is not a set of decisions, it is drift, and it reads as drift.</p>
     */
    private static final int PAD = 6;      // a block's inner inset, left and right
    private static final int GAP = 6;      // between blocks
    private static final int INDENT = 9;   // room a tick, glyph or wax dot takes before its text
    /** The gutter under any row of text, meter included. */
    private static final int ROW_GAP = 4;
    /**
     * A block's bottom padding. Smaller than {@link #PAD} on purpose: a line of text carries two
     * pixels of descender room below its ink, so measuring the gap from the row BOX rather than
     * from the ink was making every card's foot read half again as deep as its head.
     */
    private static final int FOOT = 3;
    /** The record sheet's own left and right margin, inside the page. */
    private static final int MARGIN_X = 7;

    private final Font font;
    private final int unit;

    private double scroll;
    private int contentHeight;

    RecordPage(Font font, int unit) {
        this.font = font;
        this.unit = unit;
    }

    private int line() { return font.lineHeight + unit; }

    void resetScroll() { scroll = 0; }

    void scrollBy(double delta, int viewHeight) {
        int maxScroll = Math.max(0, contentHeight - viewHeight);
        scroll = Mth.clamp(scroll - delta * 12, 0, maxScroll);
    }

    Result draw(GuiGraphics g, int pageLeft, int pageWidth, int top, int bottom,
                CareerGraphS2CPayload.Node selected, List<CareerGraphS2CPayload.Node> allNodes,
                Map<String, CareerGraphS2CPayload.Node> byId, String activeRoot,
                CareerLayout layout, String scribeName, boolean inspect, StampTool stamp) {
        int x = pageLeft + MARGIN_X;
        int inner = pageWidth - 2 * MARGIN_X;

        CareerGraphS2CPayload.Node career = byId.get(activeRoot);
        int points = career == null ? 0 : career.points();

        Result head = drawHead(g, pageLeft, pageWidth, top, selected, career, layout, points,
                inspect, stamp);
        int bodyTop = top + headHeight(selected, inspect);

        g.enableScissor(pageLeft + 2, bodyTop, pageLeft + pageWidth - 2, bottom);
        int y = bodyTop + GAP - (int) scroll;
        int startY = y;
        List<Jump> jumps = new ArrayList<>();
        if (selected == null) {
            y = drawEmpty(g, x, y, inner, career);
        } else if (selected.kind() == CareerGraphS2CPayload.KIND_SKILL
                || selected.kind() == CareerGraphS2CPayload.KIND_COMBO) {
            y = drawSkill(g, x, y, inner, selected, byId, layout, points);
        } else {
            y = drawCareer(g, x, y, inner, selected, allNodes, activeRoot, layout, points,
                    inspect, jumps, bodyTop, bottom);
        }
        contentHeight = y - startY;
        g.disableScissor();

        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (bottom - bodyTop)));
        int viewHeight = bottom - bodyTop;
        if (contentHeight > viewHeight) {
            int trackX = pageLeft + pageWidth - 5;
            g.fill(trackX, bodyTop, trackX + 2, bottom, 0x1A000000);
            int thumbH = Math.max(14, viewHeight * viewHeight / contentHeight);
            int maxScroll = contentHeight - viewHeight;
            int thumbY = bodyTop + (int) ((viewHeight - thumbH) * (scroll / maxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x736E5430);
        }

        if (!scribeName.isEmpty()) {
            String signature = Component.translatable(
                    "townstead.career.screen.scribe_signature", scribeName).getString();
            g.drawString(font, font.split(Component.literal(signature), pageWidth - 24).get(0),
                    x, bottom + 4, RecordArt.INK_DIM, false);
        }
        return new Result(head.wellX(), head.wellY(), head.wellShown(), jumps);
    }

    // ── The pinned head ────────────────────────────────────────────────────

    /**
     * Two rows of identity, plus a third only when there is a stamp to press.
     *
     * <p>It used to be a fixed four rows deep whatever the record held, and the extra room went to a
     * coloured locator bar with a rank numeral under it and an "Unspent" tally. The masthead already
     * carries the rank and the points, the board already carries the colour, and a head that repeats
     * the masthead is a head that costs the body twenty pixels to say nothing.</p>
     */
    int headHeight(CareerGraphS2CPayload.Node selected, boolean inspect) {
        return selected != null && StampTool.available(selected, inspect) ? 56 : 34;
    }

    private Result drawHead(GuiGraphics g, int pageLeft, int pageWidth, int top,
                            CareerGraphS2CPayload.Node selected,
                            CareerGraphS2CPayload.Node career, CareerLayout layout, int points,
                            boolean inspect, StampTool stamp) {
        int h = headHeight(selected, inspect);
        int x = pageLeft + MARGIN_X;
        int right = pageLeft + pageWidth - MARGIN_X;
        g.fill(pageLeft, top, pageLeft + pageWidth, top + h, RecordArt.HEAD_WASH);
        g.fill(pageLeft, top + h - 1, pageLeft + pageWidth, top + h, 0xFFB39B6E);
        g.fill(pageLeft, top, pageLeft + pageWidth, top + 1, RecordArt.PAGE_HI);

        CareerGraphS2CPayload.Node subject = selected == null ? career : selected;
        if (subject == null) return EMPTY;

        ItemStack icon = NodeArt.iconStack(subject);
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, top + 5, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        int textX = x + 16;
        g.drawString(font, subject.name(), textX, top + 6, RecordArt.INK, false);

        if (selected == null) {
            g.drawString(font, Component.translatable("townstead.career.screen.pick_a_mark"),
                    textX, top + 18, RecordArt.INK_DIM, false);
            return EMPTY;
        }

        String stateKey = selected.equipped() ? "townstead.career.screen.state.equipped"
                : switch (selected.state()) {
                    case CareerGraphS2CPayload.STATE_LOCKED -> "townstead.career.screen.state.locked";
                    case CareerGraphS2CPayload.STATE_READY -> "townstead.career.screen.state.ready";
                    default -> "townstead.career.screen.state.acquired";
                };
        int stateColor = selected.equipped()
                || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED ? RecordArt.GOOD
                : selected.state() == CareerGraphS2CPayload.STATE_READY
                        ? RecordArt.ACCENT : RecordArt.INK_DIM;
        String stateText = Component.translatable(stateKey).getString();
        int chipY = top + 17;
        int chipH = font.lineHeight + 2;
        int chipW = font.width(stateText) + 8;
        g.fill(textX, chipY, textX + chipW, chipY + chipH, 0x80FFFFFF);
        g.fill(textX, chipY, textX + 2, chipY + chipH, stateColor);
        g.drawString(font, stateText, textX + 5, chipY + 2, stateColor, false);
        if (selected.path().present()) {
            g.drawString(font, selected.path().name(), textX + chipW + PAD, chipY + 2,
                    RecordArt.INK_DIM, false);
        }

        if (!StampTool.available(selected, inspect)) {
            // A cost belongs beside the state that explains why you cannot pay it yet, on the row
            // that state already occupies. It does not need a row of its own.
            if (selected.kind() == CareerGraphS2CPayload.KIND_SKILL
                    && selected.state() != CareerGraphS2CPayload.STATE_ACQUIRED
                    && selected.points() > 0) {
                RecordArt.tokens(g, right, chipY + 2, selected.points(),
                        selected.points() <= points);
            }
            return EMPTY;
        }

        int rowY = top + 32;
        g.drawString(font, Component.translatable("townstead.career.screen.stamp_verb"),
                x, rowY, RecordArt.ACCENT, false);
        g.drawString(font, Component.translatable("townstead.career.screen.stamp_hint"),
                x, rowY + 10, RecordArt.INK_DIM, false);
        RecordArt.tokens(g, right - StampTool.WELL_W - PAD, chipY + 2, selected.points(),
                selected.points() <= points);
        return new Result(right - StampTool.WELL_W, top + h - StampTool.WELL_H - 4, true,
                List.of());
    }

    // ── Bodies ─────────────────────────────────────────────────────────────

    private int drawEmpty(GuiGraphics g, int x, int y, int inner,
                          CareerGraphS2CPayload.Node career) {
        List<FormattedCharSequence> lines = font.split(
                Component.translatable("townstead.career.screen.empty_help"), inner - 2 * PAD);
        int h = RecordArt.stripHeight() + 2 + lines.size() * line() - unit + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.this_record").getString(), "",
                RecordArt.INK_DIM);
        int ty = y + RecordArt.stripHeight() + 2;
        for (FormattedCharSequence text : lines) {
            g.drawString(font, text, x + PAD, ty, RecordArt.INK_MID, false);
            ty += line();
        }
        return y + h + GAP;
    }

    /** The career's own page: what you could register, then how far along you are. */
    private int drawCareer(GuiGraphics g, int x, int y, int inner,
                           CareerGraphS2CPayload.Node career,
                           List<CareerGraphS2CPayload.Node> allNodes, String activeRoot,
                           CareerLayout layout, int points, boolean inspect,
                           List<Jump> jumps, int bodyTop, int bodyBottom) {
        if (!inspect) y = drawReadyBlock(g, x, y, inner, allNodes, activeRoot, layout, points,
                jumps, bodyTop, bodyBottom);

        List<CareerLayout.Band> bands = layout.bands();
        if (!bands.isEmpty()) {
            // Stacked from a cursor rather than measured back from the card's foot: the daily line
            // was placed at h-8 and is nine pixels tall, so it hung a pixel out of the bottom of
            // the card it belongs to.
            // Naming every rung under the ladder needs a column that does not exist: five ranks over
            // this measure gives each name about thirty pixels, and "Apprentice" was arriving as
            // "Appre". The rungs count the ranks, which is all a rung can say at this size, and the
            // two names that matter are spelled out in full: the one you hold, in the card's header,
            // and the one you are working toward, under the bar that measures the distance.
            boolean hasCap = career.dailyCap() > 0;
            String next = career.nextRankName();
            boolean caption = hasCap || !next.isEmpty();
            int h = RecordArt.stripHeight() + 2 + 5 + ROW_GAP + 8
                    + (caption ? ROW_GAP + font.lineHeight : 0) + FOOT;
            RecordArt.card(g, font, x, y, inner, h,
                    Component.translatable("townstead.career.screen.progress").getString(),
                    career.rankName(), RecordArt.ACCENT);
            int cy = y + RecordArt.stripHeight() + 2;
            RecordArt.ladder(g, font, x + PAD, cy, inner - 2 * PAD,
                    bands.size(), career.tier(), 0, RecordArt.ACCENT, null);
            cy += 5 + ROW_GAP;
            int total = career.xp() + career.xpToNext();
            RecordArt.meter(g, x + PAD, cy + 2, inner - 2 * PAD,
                    total <= 0 ? 1f : career.xp() / (float) total, false);
            cy += 8;
            if (caption) {
                if (hasCap) {
                    g.drawString(font, Component.translatable("townstead.career.screen.today")
                                    .getString() + " " + career.xpToday() + " / "
                                    + career.dailyCap(),
                            x + PAD, cy + ROW_GAP, RecordArt.INK_DIM, false);
                }
                if (!next.isEmpty()) {
                    // A drawn chevron rather than an arrow character: the record is pixel art, and
                    // whether U+2192 resolves depends on which font provider is answering.
                    int nameX = x + inner - PAD - font.width(next);
                    RecordArt.chevron(g, nameX - 6, cy + ROW_GAP + 2, RecordArt.ACCENT);
                    g.drawString(font, next, nameX, cy + ROW_GAP, RecordArt.ACCENT, false);
                }
            }
            y += h + GAP;
        }
        y = drawEvidence(g, x, y, inner, career);
        y = drawChronicle(g, x, y, inner, career);
        return y;
    }

    /**
     * Everything you could register right now, cheapest first, with what you are saving for below.
     *
     * <p>A single nomination quietly made the choice for you, which is the opposite of what a skill
     * tree is for. Exclusive siblings are tied together so the one irreversible decision on this
     * screen is visible in the list rather than discovered after the press.</p>
     */
    private int drawReadyBlock(GuiGraphics g, int x, int y, int inner,
                               List<CareerGraphS2CPayload.Node> allNodes, String activeRoot,
                               CareerLayout layout, int points, List<Jump> jumps,
                               int bodyTop, int bodyBottom) {
        List<CareerGraphS2CPayload.Node> ready = new ArrayList<>();
        for (CareerGraphS2CPayload.Node node : allNodes) {
            if (!node.rootId().equals(activeRoot)) continue;
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL) continue;
            if (node.state() != CareerGraphS2CPayload.STATE_READY) continue;
            ready.add(node);
        }
        if (ready.isEmpty()) {
            // Silence here is indistinguishable from a broken screen. If nothing is learnable the
            // block still draws and says so, because "there is nothing to register yet" is an
            // answer and an empty column is not.
            // Wrapped, not written straight across: at this measure the sentence ran off the card's
            // right edge and the last word arrived cut in half.
            List<FormattedCharSequence> lines = font.split(
                    Component.translatable("townstead.career.screen.nothing_ready"),
                    inner - 2 * PAD);
            int h = RecordArt.stripHeight() + 2 + lines.size() * line() - unit + FOOT;
            RecordArt.card(g, font, x, y, inner, h,
                    Component.translatable("townstead.career.screen.ready_block").getString(),
                    "0", RecordArt.INK_DIM);
            int ny = y + RecordArt.stripHeight() + 2;
            for (FormattedCharSequence text : lines) {
                g.drawString(font, text, x + PAD, ny, RecordArt.INK_DIM, false);
                ny += line();
            }
            return y + h + GAP;
        }
        // Affordable first, then by cost, then by name, so the list is stable between opens and the
        // things you can actually do are at the top of it.
        ready.sort(Comparator
                .comparingInt((CareerGraphS2CPayload.Node n) -> n.points() <= points ? 0 : 1)
                .thenComparingInt(CareerGraphS2CPayload.Node::points)
                .thenComparing(CareerGraphS2CPayload.Node::name));

        int shown = Math.min(MAX_READY_ROWS, ready.size());
        int ties = 0;
        for (int i = 1; i < shown; i++) {
            CareerGraphS2CPayload.Node a = ready.get(i - 1);
            CareerGraphS2CPayload.Node b = ready.get(i);
            if (!a.group().isEmpty() && a.group().equals(b.group())) ties++;
        }
        int overflow = ready.size() - shown;
        int rowH = font.lineHeight + ROW_GAP;
        int h = RecordArt.stripHeight() + 2 + shown * rowH - 2 + ties * 6
                + (overflow > 0 ? font.lineHeight + 2 : 0) + FOOT;

        int affordable = 0;
        for (CareerGraphS2CPayload.Node node : ready) if (node.points() <= points) affordable++;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.ready_block").getString(),
                String.valueOf(affordable),
                affordable > 0 ? RecordArt.GOOD : RecordArt.INK_DIM);

        int ry = y + RecordArt.stripHeight() + 2;
        String lastGroup = "";
        for (int i = 0; i < shown; i++) {
            CareerGraphS2CPayload.Node node = ready.get(i);
            boolean afford = node.points() <= points;
            if (!node.group().isEmpty() && node.group().equals(lastGroup)) {
                // The bracket says these two are one decision, not two offers.
                g.fill(x + 3, ry - 6, x + 4, ry + 1, 0x80A8322A);
                g.fill(x + 3, ry - 6, x + 7, ry - 5, 0x80A8322A);
                g.fill(x + 3, ry, x + 7, ry + 1, 0x80A8322A);
                g.drawString(font, Component.translatable("townstead.career.screen.or"),
                        x + PAD + 3, ry - 7, RecordArt.BAD, false);
                ry += 6;
            }
            int rowX = x + PAD;
            int rowW = inner - 2 * PAD;
            g.fill(rowX, ry, rowX + 3, ry + rowH - 2, afford ? layout.tintOf(node) : 0x738A7654);
            String name = RecordArt.abbreviate(font, node.name(), rowW - 30 - node.points() * 8);
            g.drawString(font, name, rowX + INDENT, ry + 1,
                    afford ? RecordArt.INK : RecordArt.INK_FAINT, false);
            RecordArt.tokens(g, rowX + rowW - 8, ry + 2, node.points(), afford);
            if (afford) RecordArt.chevron(g, rowX + rowW - 4, ry + 2, RecordArt.ACCENT);
            // Only rows actually inside the scrolling viewport can be clicked.
            if (ry >= bodyTop - rowH && ry <= bodyBottom) {
                jumps.add(new Jump(rowX, ry, rowW, rowH - 1, node.id()));
            }
            lastGroup = node.group();
            ry += rowH;
        }
        if (overflow > 0) {
            g.drawString(font, Component.translatable(
                            "townstead.career.screen.more_on_board", overflow).getString(),
                    x + PAD + INDENT, ry, RecordArt.INK_FAINT, false);
        }
        return y + h + GAP;
    }

    /** A skill's page: what it does, what it closes off, and what is standing in the way. */
    private int drawSkill(GuiGraphics g, int x, int y, int inner,
                          CareerGraphS2CPayload.Node skill,
                          Map<String, CareerGraphS2CPayload.Node> byId, CareerLayout layout,
                          int points) {
        if (!skill.effects().isEmpty() || !skill.replaces().isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String effect : skill.effects()) {
                lines.addAll(font.split(Component.literal(effect), inner - 24));
            }
            int extra = skill.replaces().isEmpty() ? 0 : line();
            int h = RecordArt.stripHeight() + 2 + lines.size() * line() + extra - unit + FOOT;
            RecordArt.card(g, font, x, y, inner, h,
                    Component.translatable("townstead.career.screen.effect_block").getString(), "",
                    RecordArt.ACCENT);
            int ey = y + RecordArt.stripHeight() + 2;
            for (FormattedCharSequence text : lines) {
                RecordArt.glyph(g, x + PAD, ey + 2, '+', RecordArt.ACCENT);
                g.drawString(font, text, x + PAD + INDENT, ey, RecordArt.ACCENT, false);
                ey += line();
            }
            if (!skill.replaces().isEmpty()) {
                g.fill(x + PAD, ey + 4, x + PAD + 5, ey + 5, RecordArt.BAD);
                g.drawString(font, Component.translatable(
                                "townstead.career.screen.replaces", skill.replaces()).getString(),
                        x + PAD + INDENT, ey, RecordArt.BAD, false);
            }
            y += h + GAP;
        }

        y = drawChoiceBlock(g, x, y, inner, skill, byId);
        y = drawNeedsBlock(g, x, y, inner, skill, byId, layout, points);

        if (!skill.description().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(skill.description()), inner - 2 * PAD);
            int h = RecordArt.stripHeight() + 2 + lines.size() * line() - unit + FOOT;
            RecordArt.card(g, font, x, y, inner, h,
                    Component.translatable("townstead.career.screen.about").getString(), "",
                    RecordArt.INK_DIM);
            int dy = y + RecordArt.stripHeight() + 2;
            for (FormattedCharSequence text : lines) {
                g.drawString(font, text, x + PAD, dy, RecordArt.INK_MID, false);
                dy += line();
            }
            y += h + GAP;
        }
        y = drawEvidence(g, x, y, inner, skill);
        return drawChronicle(g, x, y, inner, skill);
    }

    /** The sibling this skill rules out, shown before you spend rather than after. */
    private int drawChoiceBlock(GuiGraphics g, int x, int y, int inner,
                                CareerGraphS2CPayload.Node skill,
                                Map<String, CareerGraphS2CPayload.Node> byId) {
        if (skill.group().isEmpty()) return y;
        CareerGraphS2CPayload.Node other = null;
        for (CareerGraphS2CPayload.Node node : byId.values()) {
            if (node.id().equals(skill.id())) continue;
            if (!node.group().equals(skill.group())) continue;
            if (!node.rootId().equals(skill.rootId())) continue;
            other = node;
            break;
        }
        if (other == null) return y;
        List<FormattedCharSequence> lines = other.effects().isEmpty()
                ? List.of() : font.split(Component.literal(other.effects().get(0)), inner - 2 * PAD);
        List<FormattedCharSequence> closes = font.split(
                Component.translatable("townstead.career.screen.closes_other"), inner - 2 * PAD);
        int rows = 1 + lines.size();
        int h = RecordArt.stripHeight() + 2 + rows * line() + 3
                + closes.size() * line() - unit + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.or_choose").getString(), "",
                RecordArt.BAD);
        int cy = y + RecordArt.stripHeight() + 2;
        g.drawString(font, other.name(), x + PAD, cy, RecordArt.INK, false);
        cy += line();
        for (FormattedCharSequence text : lines) {
            g.drawString(font, text, x + PAD, cy, RecordArt.INK_DIM, false);
            cy += line();
        }
        g.fill(x + PAD, cy, x + inner - PAD, cy + 1, 0x59A8322A);
        cy += 3;
        for (FormattedCharSequence text : closes) {
            g.drawString(font, text, x + PAD, cy, RecordArt.BAD, false);
            cy += line();
        }
        return y + h + GAP;
    }

    /** What is standing in the way, as a ticked list rather than one red sentence. */
    private int drawNeedsBlock(GuiGraphics g, int x, int y, int inner,
                               CareerGraphS2CPayload.Node skill,
                               Map<String, CareerGraphS2CPayload.Node> byId, CareerLayout layout,
                               int points) {
        if (skill.state() != CareerGraphS2CPayload.STATE_LOCKED) return y;
        List<String> labels = new ArrayList<>();
        List<Boolean> met = new ArrayList<>();
        if (skill.tier() > 0 && layout.careerTier() < skill.tier()) {
            labels.add(Component.translatable("townstead.career.screen.needs_rank",
                    skill.rankName().isEmpty() ? CareerLayout.roman(skill.tier())
                            : skill.rankName()).getString());
            met.add(false);
        }
        for (String required : skill.requires()) {
            CareerGraphS2CPayload.Node node = byId.get(required);
            if (node == null) continue;
            labels.add(node.name());
            met.add(node.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
        }
        if (skill.points() > points) {
            labels.add(Component.translatable("townstead.career.screen.needs_points",
                    skill.points()).getString());
            met.add(false);
        }
        if (labels.isEmpty()) return y;

        int metCount = 0;
        for (Boolean value : met) if (value) metCount++;
        int h = RecordArt.stripHeight() + 2 + labels.size() * line() - unit + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.needs_block").getString(),
                metCount + " / " + labels.size(), RecordArt.BAD);
        int ny = y + RecordArt.stripHeight() + 2;
        for (int i = 0; i < labels.size(); i++) {
            RecordArt.tick(g, x + PAD, ny + 1, met.get(i));
            g.drawString(font, labels.get(i), x + PAD + INDENT, ny,
                    met.get(i) ? RecordArt.INK_DIM : RecordArt.INK, false);
            ny += line();
        }
        return y + h + GAP;
    }

    /**
     * Counted deeds, each row as tall as what it actually draws.
     *
     * <p>Every row used to reserve space for a meter. A deed with no target draws no meter, so it
     * left a bar-shaped hole under itself and the block's rhythm broke wherever the two kinds sat
     * next to each other, which is exactly where it is most visible.</p>
     */
    private int drawEvidence(GuiGraphics g, int x, int y, int inner,
                             CareerGraphS2CPayload.Node node) {
        if (node.evidence().isEmpty()) return y;
        int content = 0;
        int met = 0;
        for (CareerGraphS2CPayload.Evidence evidence : node.evidence()) {
            if (evidence.met()) met++;
            content += evidenceRowHeight(evidence);
        }
        int h = RecordArt.stripHeight() + 2 + content - ROW_GAP + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.evidence").getString(),
                met + " / " + node.evidence().size(),
                met == node.evidence().size() ? RecordArt.GOOD : RecordArt.ACCENT);
        int ey = y + RecordArt.stripHeight() + 2;
        for (CareerGraphS2CPayload.Evidence evidence : node.evidence()) {
            boolean done = evidence.target() > 0 && evidence.met();
            String value = evidence.target() > 0
                    ? evidence.current() + " / " + evidence.target()
                    : String.valueOf(evidence.current());
            g.drawString(font, RecordArt.abbreviate(font, evidence.label(),
                            inner - 2 * PAD - INDENT - font.width(value)), x + PAD, ey,
                    RecordArt.INK, false);
            g.drawString(font, value, x + inner - PAD - font.width(value), ey,
                    done ? RecordArt.GOOD : RecordArt.INK_MID, false);
            if (done) {
                RecordArt.tick(g, x + inner - PAD - INDENT - font.width(value), ey + 1, true);
            }
            if (evidence.target() > 0) {
                RecordArt.meter(g, x + PAD, ey + font.lineHeight + ROW_GAP, inner - 2 * PAD,
                        evidence.current() / (float) evidence.target(), done);
            }
            ey += evidenceRowHeight(evidence);
        }
        return y + h + GAP;
    }

    /** A label row, plus a meter row only when there is a target to measure against. */
    private int evidenceRowHeight(CareerGraphS2CPayload.Evidence evidence) {
        return font.lineHeight + ROW_GAP + (evidence.target() > 0 ? 10 : 0);
    }

    private int drawChronicle(GuiGraphics g, int x, int y, int inner,
                              CareerGraphS2CPayload.Node node) {
        if (node.moments().isEmpty()) return y;
        List<List<FormattedCharSequence>> entries = new ArrayList<>();
        int rows = 0;
        for (String moment : node.moments()) {
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(moment), inner - 2 * PAD - INDENT);
            entries.add(lines);
            rows += lines.size();
        }
        int h = RecordArt.stripHeight() + 2 + rows * line() + (entries.size() - 1) * 2
                - unit + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.chronicle").getString(), "",
                RecordArt.INK_DIM);
        // A spine with a wax dot per entry, so the record reads as a sequence rather than a list.
        int spineTop = y + RecordArt.stripHeight() + 4;
        g.fill(x + PAD + 2, spineTop, x + PAD + 3, y + h - FOOT, 0x738A7654);
        int my = y + RecordArt.stripHeight() + 2;
        for (List<FormattedCharSequence> lines : entries) {
            RecordArt.waxDot(g, x + PAD, my + 1);
            for (FormattedCharSequence text : lines) {
                g.drawString(font, text, x + PAD + INDENT, my, RecordArt.INK_MID, false);
                my += line();
            }
            my += 2;
        }
        return y + h + GAP;
    }
}
