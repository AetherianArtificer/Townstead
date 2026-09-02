package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Today's sheet: the registry record for whatever mark is selected on the board.
 *
 * <p>Two zones. The <b>head is pinned</b> and carries identity, state, the locator that ties this
 * record to its column on the board and the cost; the <b>body scrolls</b> and
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

    /** The endorsement field, in absolute screen coordinates, and the body's jump boxes. */
    record Result(int stampX, int stampY, int stampW, int stampH, boolean canStamp,
                  List<Jump> jumps) {}

    private static final Result EMPTY = new Result(0, 0, 0, 0, false, List.of());

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
    /** How far scrolled content dissolves back into the page at each end of the body. */
    private static final int FADE = 7;
    /**
     * The endorsement field: where a mark lands, reserved on every record whether or not one can
     * be pressed. Sized so a cartouche fits at the tilt the tool allows.
     */
    private static final int FIELD_W = 72;
    private static final int FIELD_H = 32;
    /**
     * The head's height, and it does not vary. It used to return 42 when there was something to
     * stamp and 36 otherwise, so the whole body jumped six pixels as you clicked between a
     * learnable and a learned skill.
     *
     * <p>Three text rows need 38, but 38 lands the state chip's bottom edge flush on the seam with
     * nothing under it, so the chip read as falling out of the head. The extra four pixels are the
     * gutter beneath it.</p>
     */
    private static final int HEAD_H = Math.max(42, FIELD_H + 6);
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

    /** The real body viewport below the pinned head. */
    int scrollViewHeight(CareerGraphS2CPayload.Node selected, boolean inspect,
                         int top, int bottom) {
        return Math.max(0, bottom - (top + headHeight()));
    }

    Result draw(GuiGraphics g, int pageLeft, int pageWidth, int top, int bottom,
                CareerGraphS2CPayload.Node selected, List<CareerGraphS2CPayload.Node> allNodes,
                Map<String, CareerGraphS2CPayload.Node> byId, String activeRoot,
                CareerLayout layout, boolean inspect, StampTool stamp) {
        int x = pageLeft + MARGIN_X;
        int inner = pageWidth - 2 * MARGIN_X;

        CareerGraphS2CPayload.Node career = byId.get(activeRoot);
        int points = career == null ? 0 : career.points();

        Result head = drawHead(g, pageLeft, pageWidth, top, selected, career, layout, points,
                inspect, stamp);
        int bodyTop = top + headHeight();
        int bodyBottom = bottom;

        g.enableScissor(pageLeft + 2, bodyTop, pageLeft + pageWidth - 2, bodyBottom);
        int y = bodyTop + GAP - (int) scroll;
        int startY = y;
        List<Jump> jumps = new ArrayList<>();
        if (selected == null) {
            y = drawEmpty(g, x, y, inner, career);
        } else if (selected.kind() == CareerGraphS2CPayload.KIND_SKILL
                || selected.kind() == CareerGraphS2CPayload.KIND_COMBO) {
            y = drawSkill(g, x, y, inner, selected, byId, layout, points);
        } else {
            y = drawCareer(g, x, y, inner, selected, layout);
        }
        contentHeight = y - startY;
        g.disableScissor();

        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (bodyBottom - bodyTop)));
        int viewHeight = bodyBottom - bodyTop;

        // The scissor cuts whatever line happens to straddle it, so a scrolled record ended in
        // half a row of glyphs against the rail. Fading the last few pixels back into the page
        // reads as text running under the edge rather than as text sliced off at it.
        int maxScrollNow = Math.max(0, contentHeight - viewHeight);
        if (scroll > 0.5) {
            g.fillGradient(pageLeft + 2, bodyTop, pageLeft + pageWidth - 2, bodyTop + FADE,
                    RecordArt.PAGE, RecordArt.PAGE & 0x00FFFFFF);
        }
        if (scroll < maxScrollNow - 0.5) {
            g.fillGradient(pageLeft + 2, bodyBottom - FADE, pageLeft + pageWidth - 2, bodyBottom,
                    RecordArt.PAGE & 0x00FFFFFF, RecordArt.PAGE);
        }

        if (contentHeight > viewHeight) {
            int trackX = pageLeft + pageWidth - 5;
            g.fill(trackX, bodyTop, trackX + 2, bodyBottom, 0x1A000000);
            int thumbH = Math.max(14, viewHeight * viewHeight / contentHeight);
            int maxScroll = contentHeight - viewHeight;
            int thumbY = bodyTop + (int) ((viewHeight - thumbH) * (scroll / maxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x736E5430);
        }

        return new Result(head.stampX(), head.stampY(), head.stampW(), head.stampH(),
                head.canStamp(), jumps);
    }

    // ── The pinned head ────────────────────────────────────────────────────

    /**
     * Three rows of identity beside the endorsement field, at a constant height.
     *
     * <p>It used to be a fixed four rows deep whatever the record held, and the extra room went to a
     * coloured locator bar with a rank numeral under it and an Insight tally. The masthead already
     * carries the rank and the points, the board already carries the colour, and a head that repeats
     * the masthead is a head that costs the body twenty pixels to say nothing.</p>
     */
    int headHeight() {
        return HEAD_H;
    }

    private Result drawHead(GuiGraphics g, int pageLeft, int pageWidth, int top,
                            CareerGraphS2CPayload.Node selected,
                            CareerGraphS2CPayload.Node career, CareerLayout layout, int points,
                            boolean inspect, StampTool stamp) {
        int h = headHeight();
        int x = pageLeft + MARGIN_X;
        int right = pageLeft + pageWidth - MARGIN_X;
        g.fill(pageLeft, top, pageLeft + pageWidth, top + h, RecordArt.HEAD_WASH);
        g.fill(pageLeft, top + h - 1, pageLeft + pageWidth, top + h, 0xFFB39B6E);
        g.fill(pageLeft, top, pageLeft + pageWidth, top + 1, RecordArt.PAGE_HI);

        CareerGraphS2CPayload.Node subject = selected == null ? career : selected;
        if (subject == null) return EMPTY;

        boolean canStamp = selected != null && StampTool.available(selected, inspect);
        // The field is reserved on every record, so the title always ellipsizes against the same
        // edge and the head always measures the same. Only a record with no selection has none.
        int targetW = selected == null ? 0 : FIELD_W;
        int targetX = right - targetW;
        int targetY = top + (h - FIELD_H) / 2;
        int targetH = FIELD_H;

        String kicker;
        if (selected != null && selected.kind() == CareerGraphS2CPayload.KIND_SKILL) {
            String path = selected.path().present() ? selected.path().name()
                    : Component.translatable("townstead.career.screen.general").getString();
            kicker = path + " · " + selected.tier()
                    + (selected.rankName().isEmpty() ? "" : " · " + selected.rankName());
        } else {
            kicker = Component.translatable("townstead.career.screen.career_record").getString();
        }
        g.drawString(font, ellipsize(kicker, (targetW > 0 ? targetX : right) - x - 3),
                x, top + 3, RecordArt.ACCENT, false);

        int titleY = top + 15;
        NodeArt.drawIcon(g, subject, x + 6, titleY + 5, 0.72f);
        int textX = x + 14;
        int titleRight = targetW > 0 ? targetX - 4 : right;
        g.drawString(font, ellipsize(subject.name(), titleRight - textX),
                textX, titleY, RecordArt.INK, false);

        if (selected == null) {
            g.drawString(font, Component.translatable("townstead.career.screen.pick_a_mark"),
                    textX, top + 27, RecordArt.INK_DIM, false);
            return EMPTY;
        }

        boolean skill = selected.kind() == CareerGraphS2CPayload.KIND_SKILL;
        String stateKey = selected.equipped() ? "townstead.career.screen.state.equipped"
                : switch (selected.state()) {
                    case CareerGraphS2CPayload.STATE_LOCKED -> "townstead.career.screen.state.locked";
                    case CareerGraphS2CPayload.STATE_READY -> skill
                            ? "townstead.career.screen.state.available"
                            : "townstead.career.screen.state.ready";
                    default -> skill
                            ? "townstead.career.screen.state.learned"
                            : "townstead.career.screen.state.registered";
                };
        int stateColor = selected.equipped()
                || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED ? RecordArt.GOOD
                : selected.state() == CareerGraphS2CPayload.STATE_READY
                        ? RecordArt.ACCENT : RecordArt.INK_DIM;
        String stateText = Component.translatable(stateKey).getString();
        String shownState = ellipsize(stateText, Math.max(20, targetX - textX - 4));
        int chipY = top + 27;
        int chipH = font.lineHeight + 2;
        int chipW = font.width(shownState) + 8;
        g.fill(textX, chipY, textX + chipW, chipY + chipH, 0x80FFFFFF);
        g.fill(textX, chipY, textX + 2, chipY + chipH, stateColor);
        g.drawString(font, shownState, textX + 5, chipY + 2, stateColor, false);
        if (skill && selected.points() > 0 && !canStamp) {
            String cost = selected.points() + " "
                    + Component.translatable("townstead.career.screen.insight").getString();
            int costX = textX + chipW + PAD;
            if (costX + font.width(cost) < titleRight) {
                g.drawString(font, cost, costX, chipY + 2, RecordArt.INK_DIM, false);
            }
        }

        drawEndorsementField(g, targetX, targetY, targetW, targetH);
        return new Result(targetX, targetY, targetW, targetH, canStamp, List.of());
    }

    /**
     * A lighter patch of the head's own stock, ruled and ticked. An empty one carries no words:
     * blank ruled paper already says it is waiting, and a label in it read as a control.
     *
     * <p>This is the whole difference from the dashed rectangle it replaces. The rectangle was a
     * drop target, which is a thing the fiction has no word for; a blank endorsement field on a
     * certificate is a thing that exists, and it reads as reserved without demanding anything.</p>
     */
    private void drawEndorsementField(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0) return;
        g.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x1F5A452A);
        g.fill(x, y, x + w, y + h, RecordArt.CARD);
        g.fill(x, y, x + w, y + 1, 0x80FFFFFF);
        g.fill(x, y, x + 1, y + h, RecordArt.CARD_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, RecordArt.CARD_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, RecordArt.CARD_EDGE);
        for (int ruleY = y + 10; ruleY < y + h - 5; ruleY += 9) {
            g.fill(x + 5, ruleY, x + w - 5, ruleY + 1, 0x80D4C09A);
        }
        // The registrar's alignment ticks, which is what says "a mark belongs inside these".
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x + 3 : x + w - 4;
            int cy = corner < 2 ? y + 3 : y + h - 4;
            int dx = (corner & 1) == 0 ? 1 : -1;
            int dy = corner < 2 ? 1 : -1;
            g.fill(Math.min(cx, cx + dx * 3), cy, Math.max(cx, cx + dx * 3) + 1, cy + 1,
                    RecordArt.INK_FAINT);
            g.fill(cx, Math.min(cy, cy + dy * 3), cx + 1, Math.max(cy, cy + dy * 3) + 1,
                    RecordArt.INK_FAINT);
        }
    }

    private String ellipsize(String text, int room) {
        if (room <= 0) return "";
        if (font.width(text) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
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

    /** The career's own page: what the work is, then how far along you are. */
    private int drawCareer(GuiGraphics g, int x, int y, int inner,
                           CareerGraphS2CPayload.Node career,
                           CareerLayout layout) {
        y = drawAbout(g, x, y, inner, career.description());

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


    /** A skill's page: what it is, what it does, and what is standing in the way. */
    private int drawSkill(GuiGraphics g, int x, int y, int inner,
                          CareerGraphS2CPayload.Node skill,
                          Map<String, CareerGraphS2CPayload.Node> byId, CareerLayout layout,
                          int points) {
        y = drawAbout(g, x, y, inner, skill.description());
        y = drawWhatItDoes(g, x, y, inner, skill);
        y = drawNeedsBlock(g, x, y, inner, skill, byId, layout, points);
        y = drawEvidence(g, x, y, inner, skill);
        return drawChronicle(g, x, y, inner, skill);
    }

    private int drawAbout(GuiGraphics g, int x, int y, int inner, String description) {
        if (description == null || description.isEmpty()) return y;
        List<FormattedCharSequence> lines = font.split(
                Component.literal(description), inner);
        // About in the prototype is editorial copy on the record itself: a small section label,
        // then the description. It is not a card, has no coloured strip, border, or drop shadow.
        g.drawString(font, Component.translatable("townstead.career.screen.about"),
                x, y, RecordArt.ACCENT, false);
        int dy = y + font.lineHeight + ROW_GAP;
        for (FormattedCharSequence text : lines) {
            g.drawString(font, text, x, dy, RecordArt.INK, false);
            dy += line();
        }
        return dy - unit + GAP;
    }

    /** The prototype's single factual card: effects and operating details belong together. */
    private int drawWhatItDoes(GuiGraphics g, int x, int y, int inner,
                               CareerGraphS2CPayload.Node skill) {
        CareerGraphS2CPayload.Ability ability = skill.ability();
        if (skill.effects().isEmpty() && skill.replaces().isEmpty() && !ability.present()) return y;

        g.drawString(font, Component.translatable("townstead.career.screen.effect_block"),
                x, y, RecordArt.ACCENT, false);
        int cardY = y + font.lineHeight + ROW_GAP;

        List<List<FormattedCharSequence>> entries = new ArrayList<>();
        for (String effect : skill.effects()) {
            entries.add(font.split(Component.literal(effect), inner - 2 * PAD));
        }
        int abilityEntry = -1;

        if (ability.present()) {
            List<String> details = new ArrayList<>();
            details.add(Component.translatable("townstead.career.screen.ability_block").getString());
            details.add(Component.translatable(
                    "townstead.career.screen.ability_slot", ability.slot()).getString());
            net.minecraft.client.KeyMapping key =
                    com.aetherianartificer.townstead.client.TownsteadKeybinds.abilityKey(ability.slot());
            if (key != null && !key.isUnbound()) details.add(key.getTranslatedKeyMessage().getString());
            if (ability.cooldownTicks() > 0) {
                details.add(Component.translatable("townstead.career.screen.ability_seconds",
                        RecordArt.trimSeconds(ability.cooldownTicks() / 20f)).getString());
            }
            if (ability.costAmount() > 0 && !ability.costLabel().isEmpty()) {
                details.add(ability.costAmount() + " " + ability.costLabel());
            }
            abilityEntry = entries.size();
            entries.add(font.split(Component.literal(String.join(" · ", details)),
                    inner - 2 * PAD));

            boolean radial = com.aetherianartificer.townstead.compat.radial.RadialMenuCompat.anyLoaded();
            if ((key == null || key.isUnbound()) && !radial) {
                entries.add(font.split(Component.translatable(
                        "townstead.career.screen.ability_bind_hint"), inner - 2 * PAD));
            }
        }

        int rows = 0;
        for (List<FormattedCharSequence> entry : entries) rows += entry.size();
        int extra = skill.replaces().isEmpty() ? 0 : line();
        int separator = abilityEntry > 0 ? 4 : 0;
        int h = PAD + rows * line() + Math.max(0, entries.size() - 1) * 2 + separator
                + extra - unit + FOOT;
        RecordArt.plainCard(g, x, cardY, inner, h, RecordArt.ACCENT);
        int ey = cardY + PAD;
        for (int i = 0; i < entries.size(); i++) {
            if (i == abilityEntry && abilityEntry > 0) {
                g.fill(x + PAD, ey, x + inner - PAD, ey + 1, RecordArt.CARD_EDGE);
                ey += 4;
            }
            for (FormattedCharSequence text : entries.get(i)) {
                g.drawString(font, text, x + PAD, ey,
                        i >= abilityEntry && abilityEntry >= 0
                                ? RecordArt.INK_MID : RecordArt.INK, false);
                ey += line();
            }
            if (i + 1 < entries.size()) ey += 2;
        }
        if (!skill.replaces().isEmpty()) {
            g.fill(x + PAD, ey + 4, x + PAD + 5, ey + 5, RecordArt.BAD);
            g.drawString(font, Component.translatable(
                            "townstead.career.screen.replaces", skill.replaces()).getString(),
                    x + PAD + INDENT, ey, RecordArt.BAD, false);
        }
        return cardY + h + GAP;
    }

    /**
     * An active ability: the one thing on this page you have to OPERATE rather than simply own.
     *
     * <p>Its own block, above the effects, because it is a different kind of fact. The cooldown and
     * the cost used to be two more bullets in the effect list, indistinguishable from "+1 XP", and
     * the thing that actually determines whether the ability works at all was not shown anywhere:
     * ability slots default to UNBOUND, so a player could pay a point for a power and never find
     * out why nothing happened. The key row is first, and it shouts when there is no key.</p>
     */
    private int drawAbilityBlock(GuiGraphics g, int x, int y, int inner,
                                 CareerGraphS2CPayload.Node skill) {
        CareerGraphS2CPayload.Ability ability = skill.ability();
        if (!ability.present()) return y;

        net.minecraft.client.KeyMapping key =
                com.aetherianartificer.townstead.client.TownsteadKeybinds.abilityKey(ability.slot());
        boolean unbound = key == null || key.isUnbound();

        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        labels.add(Component.translatable("townstead.career.screen.ability_key").getString());
        values.add(unbound
                ? Component.translatable("townstead.career.screen.ability_unbound").getString()
                : key.getTranslatedKeyMessage().getString());
        if (ability.cooldownTicks() > 0) {
            labels.add(Component.translatable(
                    "townstead.career.screen.ability_cooldown").getString());
            values.add(Component.translatable("townstead.career.screen.ability_seconds",
                    RecordArt.trimSeconds(ability.cooldownTicks() / 20f)).getString());
        }
        if (ability.costAmount() > 0 && !ability.costLabel().isEmpty()) {
            labels.add(Component.translatable("townstead.career.screen.ability_cost").getString());
            values.add(ability.costAmount() + " " + ability.costLabel());
        }

        // A wheel fires the key without one ever being bound, so "unbound" only means "does nothing"
        // when there is no wheel. Presence of a radial menu is the whole of the check: it cannot
        // tell us whether THIS ability is on their wheel, but it is enough to stop asserting a
        // failure that may not exist.
        boolean radial = com.aetherianartificer.townstead.compat.radial.RadialMenuCompat.anyLoaded();
        List<FormattedCharSequence> hint = unbound
                ? font.split(Component.translatable(radial
                                ? "townstead.career.screen.ability_bind_hint_radial"
                                : "townstead.career.screen.ability_bind_hint"),
                        inner - 2 * PAD)
                : List.of();
        int h = RecordArt.stripHeight() + 2 + labels.size() * line()
                + (hint.isEmpty() ? 0 : 2 + hint.size() * line()) - unit + FOOT;
        RecordArt.card(g, font, x, y, inner, h,
                Component.translatable("townstead.career.screen.ability_block").getString(),
                Component.translatable("townstead.career.screen.ability_slot",
                        ability.slot()).getString(),
                unbound && !radial ? RecordArt.BAD : RecordArt.ACCENT);

        int ay = y + RecordArt.stripHeight() + 2;
        for (int i = 0; i < labels.size(); i++) {
            // Only shout when there is genuinely no way to fire it.
            boolean missing = i == 0 && unbound && !radial;
            g.drawString(font, labels.get(i), x + PAD, ay, RecordArt.INK_MID, false);
            String value = values.get(i);
            g.drawString(font, value, x + inner - PAD - font.width(value), ay,
                    missing ? RecordArt.BAD : RecordArt.INK, false);
            ay += line();
        }
        for (FormattedCharSequence text : hint) {
            g.drawString(font, text, x + PAD, ay + 2, RecordArt.INK_DIM, false);
            ay += line();
        }
        return y + h + GAP;
    }

    /** What is standing in the way, as a ticked list rather than one red sentence. */
    private int drawNeedsBlock(GuiGraphics g, int x, int y, int inner,
                               CareerGraphS2CPayload.Node skill,
                               Map<String, CareerGraphS2CPayload.Node> byId, CareerLayout layout,
                               int points) {
        List<String> labels = new ArrayList<>();
        List<Boolean> met = new ArrayList<>();
        CareerGraphS2CPayload.Node owner = byId.get(skill.parentId());
        if (owner != null) {
            labels.add(Component.translatable("townstead.career.screen.needs_registration",
                    owner.name()).getString());
            met.add(owner.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
        }
        if (skill.tier() > 0) {
            labels.add(Component.translatable("townstead.career.screen.needs_rank",
                    skill.rankName().isEmpty() ? String.valueOf(skill.tier())
                            : skill.rankName()).getString());
            met.add(layout.careerTier() >= skill.tier());
        }
        for (String required : skill.requires()) {
            CareerGraphS2CPayload.Node node = byId.get(required);
            if (node == null) continue;
            labels.add(node.name());
            met.add(node.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
        }
        if (skill.points() > 0) {
            labels.add(Component.translatable("townstead.career.screen.needs_points",
                    skill.points()).getString());
            met.add(skill.points() <= points);
        }
        if (labels.isEmpty()) return y;

        g.drawString(font, Component.translatable("townstead.career.screen.requirements"),
                x, y, RecordArt.ACCENT, false);
        int cardY = y + font.lineHeight + ROW_GAP;
        int h = PAD + labels.size() * line() - unit + FOOT;
        boolean allMet = !met.contains(false);
        RecordArt.plainCard(g, x, cardY, inner, h, allMet ? RecordArt.GOOD : RecordArt.ACCENT);
        int ny = cardY + PAD;
        for (int i = 0; i < labels.size(); i++) {
            RecordArt.tick(g, x + PAD, ny + 1, met.get(i));
            g.drawString(font, ellipsize(labels.get(i), inner - 2 * PAD - INDENT),
                    x + PAD + INDENT, ny,
                    met.get(i) ? RecordArt.INK_DIM : RecordArt.INK, false);
            ny += line();
        }
        return cardY + h + GAP;
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
                        evidence.current() / (float) evidence.target(), done, evidence.target());
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
