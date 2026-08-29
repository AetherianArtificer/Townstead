package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Archives stamp: an object you pick up and press onto your own record.
 *
 * <p>Registering a skill used to be a button labelled LEARN. Spending a point is scarce and close
 * to irreversible, and a button makes that feel like every other click; reaching for the stamp makes
 * you commit with your hand, and it puts you inside the fiction rather than editing a character
 * sheet. The well's label is the verb, so everything the button used to say is still said.</p>
 *
 * <p>One stamp, not a rack. The action available is always derivable from the record's state, so
 * offering a choice of tool would be offering a choice that does not exist.</p>
 */
final class StampTool {

    static final int WELL_W = 46;
    static final int WELL_H = 36;
    private static final int DIE_W = 36;
    private static final int DIE_H = 32;

    //? if >=1.21 {
    private static final ResourceLocation TOOL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "townstead", "textures/gui/career/stamp_tool.png");
    //?} else {
    /*private static final ResourceLocation TOOL_TEXTURE = new ResourceLocation(
            "townstead", "textures/gui/career/stamp_tool.png");
    *///?}

    private static final int INK = 0xFFA8322A;
    private static final int INK_LIGHT = 0xFFC8564A;

    private final Font font;

    private boolean held;
    private double toolX;
    private double toolY;
    private float rotation = -0.09f;
    private long pressedAt = -1L;

    StampTool(Font font) {
        this.font = font;
    }

    boolean held() { return held; }

    /** Whether this record can be stamped at all: a learnable skill on your own page. */
    static boolean available(CareerGraphS2CPayload.Node node, boolean inspect) {
        if (inspect || node == null) return false;
        if (node.kind() == CareerGraphS2CPayload.KIND_SKILL) {
            return node.state() == CareerGraphS2CPayload.STATE_READY;
        }
        return takeUp(node);
    }

    /**
     * A career you could declare as your work: a root, or a specialization you already hold.
     *
     * <p>The server re-validates; this only mirrors the rule so the screen knows what to offer.</p>
     */
    static boolean canTakeUp(CareerGraphS2CPayload.Node node) {
        if (node == null || node.primary()) return false;
        return node.kind() == CareerGraphS2CPayload.KIND_ROOT
                || (node.kind() == CareerGraphS2CPayload.KIND_ADVANCED
                        && node.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
    }

    /**
     * Work you have never been admitted to, which is the only take-up worth a ceremony.
     *
     * <p>Taking up work used to be a grey button while learning a skill was a ceremony, which said
     * the smaller of the two commitments was the more serious one. But a ceremony repeated every
     * time you swap back to a career you already hold is not a ceremony, it is a toll. The record
     * itself answers which is which: once it bears your mark you have been admitted, and going back
     * to that work is resuming it, not entering it.</p>
     */
    static boolean takeUp(CareerGraphS2CPayload.Node node) {
        return canTakeUp(node) && !node.stamp().present();
    }

    void reset() {
        held = false;
        rotation = -0.09f;
    }

    void rotate(double delta) {
        if (!held) return;
        rotation = Mth.clamp(rotation + (delta > 0 ? -0.045f : 0.045f), -0.55f, 0.55f);
    }

    // ── The well ───────────────────────────────────────────────────────────

    private int wellX;
    private int wellY;

    /**
     * The well and its label, drawn in the record's footer where the Learn button used to be.
     *
     * @param cost points the press will spend, for the line under the verb
     * @param afford whether the subject can pay; an unaffordable stamp sits in its well and will not
     *               lift, which is a quieter refusal than a greyed button you can still click
     */
    void drawWell(GuiGraphics g, int x, int y, boolean afford) {
        wellX = x;
        wellY = y;
        g.fill(wellX, wellY, wellX + WELL_W, wellY + WELL_H, 0x33241708);
        Palette.drawOutline(g, wellX, wellY, wellX + WELL_W, wellY + WELL_H, 0x66705A38);
        g.fill(wellX + 4, wellY + WELL_H - 5, wellX + WELL_W - 4, wellY + WELL_H - 3, 0x44241708);
        if (held) return;
        // An unaffordable stamp sits in its well and will not lift, which is a quieter refusal than
        // a greyed button you can still click.
        drawTool(g, wellX + (WELL_W - DIE_W) / 2,
                wellY + (WELL_H - DIE_H) / 2, 0f, afford ? 1f : 0.4f);
    }

    boolean overWell(double mouseX, double mouseY) {
        return mouseX >= wellX && mouseX < wellX + WELL_W
                && mouseY >= wellY && mouseY < wellY + WELL_H;
    }

    void pickUp(double mouseX, double mouseY) {
        held = true;
        toolX = mouseX - DIE_W / 2.0;
        toolY = mouseY - DIE_H / 2.0;
    }

    void moveTo(double mouseX, double mouseY) {
        toolX = mouseX - DIE_W / 2.0;
        toolY = mouseY - DIE_H / 2.0;
    }

    /** Where the die's centre currently sits, which is what gets stored as the mark's position. */
    int centreX() { return (int) Math.round(toolX + DIE_W / 2.0); }
    int centreY() { return (int) Math.round(toolY + DIE_H / 2.0); }
    float rotation() { return rotation; }

    /** Draws the held tool and the shadow showing where it would land. */
    void drawHeld(GuiGraphics g, boolean validDrop) {
        if (!held) return;
        int x = (int) Math.round(toolX);
        int y = (int) Math.round(toolY);
        g.pose().pushPose();
        // Only the die is projected onto the page. A full tool-sized rectangle made the new,
        // taller sprite look as if it were dragging a paving slab behind it.
        g.pose().translate(x + DIE_W / 2f, y + DIE_H - 4f + 3, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        g.fill(-DIE_W / 2 + 1, -4, DIE_W / 2 - 1, 4,
                validDrop ? 0x4D000000 : 0x4D8A2018);
        g.pose().popPose();
        drawTool(g, x, y, rotation, 1f);
    }

    private int pressX;
    private int pressY;

    /**
     * The press itself: descend, contact, then lift away.
     *
     * <p>Takes the point it landed on and animates from there IMMEDIATELY, rather than waiting for
     * the server to echo the mark back. Gating the animation on the returned payload meant the tool
     * simply vanished on release and nothing happened until the round trip completed, which is
     * indistinguishable from a press that did nothing at all.</p>
     */
    void press(int markX, int markY) {
        pressedAt = Util.getMillis();
        pressX = markX;
        pressY = markY;
        held = false;
    }

    /**
     * The moment the mark lands: a squash on contact, then dust off the edges as the die lifts.
     *
     * <p>This is the one loud frame on the whole screen, and it is loud because you caused it. The
     * board's ambient light was pulled back to almost nothing so that this reads as an event.</p>
     */
    void drawPressAnimation(GuiGraphics g) {
        if (pressedAt < 0) return;
        int markX = pressX;
        int markY = pressY;
        long age = Util.getMillis() - pressedAt;
        if (age > 900) {
            pressedAt = -1L;
            return;
        }
        float t = age / 900f;
        if (age < 400) {
            int lift = Math.round(10f * (age < 150 ? 1f - age / 150f : (age - 150) / 250f));
            boolean squash = age >= 130 && age < 200;
            int jitter = squash ? 1 : 0;
            drawTool(g, markX - DIE_W / 2 + jitter, markY - DIE_H / 2 - lift + jitter,
                    rotation, 1f - t * 0.3f);
        }
        for (int i = 0; i < 14; i++) {
            double angle = (i / 14.0) * Math.PI * 2;
            double dist = 10 + t * 44;
            int px = markX + (int) Math.round(Math.cos(angle) * dist * 1.4);
            int py = markY + (int) Math.round(Math.sin(angle) * dist * 0.55);
            int alpha = (int) (Math.max(0f, 1f - t) * 130f) << 24;
            g.fill(px, py, px + 1, py + 1, alpha | 0x00CDB98E);
        }
    }

    /**
     * The wooden handle and inked die, drawn from its top-left corner.
     *
     * <p>The sprite is authored at its exact GUI size. Keeping it as one image preserves the shape
     * and material separation through every state; rebuilding it from overlapping rectangles made
     * the wood, collar, and inked die collapse into three unrelated horizontal stripes.</p>
     */
    private void drawTool(GuiGraphics g, int x, int y, float rot, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x + DIE_W / 2f, y + DIE_H / 2f, 250);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rot)));
        float opacity = Mth.clamp(alpha, 0f, 1f);
        g.setColor(1f, 1f, 1f, opacity);
        g.blit(TOOL_TEXTURE, -DIE_W / 2, -DIE_H / 2, DIE_W, DIE_H,
                0f, 0f, DIE_W, DIE_H, DIE_W, DIE_H);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    // ── The mark it leaves ─────────────────────────────────────────────────

    /**
     * A pressed mark on the page: the village that took the evidence, and the day it was entered.
     *
     * <p>Drawn in PANEL space at the position the subject chose. The record reserves nothing for it,
     * because the player put it there: a mark the game drops onto running text is a layout bug,
     * while one you pressed over your own reading is an annotation.</p>
     */
    void drawMark(GuiGraphics g, int panelLeft, int panelTop, CareerGraphS2CPayload.Stamp stamp,
                  int maxWidth) {
        if (!stamp.present()) return;
        String place = (stamp.authority().isEmpty()
                ? Component.translatable("townstead.career.screen.field_registry").getString()
                : stamp.authority()).toUpperCase(java.util.Locale.ROOT);
        String office = Component.translatable("townstead.career.screen.registered").getString();
        String sub = stamp.date().isEmpty() ? office : office + "  " + stamp.date();

        int inner = Math.max(font.width(place), font.width(sub));
        int w = Math.min(maxWidth, inner + 16);
        if (font.width(place) > w - 16) place = truncate(place, w - 16);
        if (font.width(sub) > w - 16) sub = truncate(sub, w - 16);
        int h = 2 * font.lineHeight + 15;

        g.pose().pushPose();
        g.pose().translate(panelLeft + stamp.x(), panelTop + stamp.y(), 220);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                (float) Math.toDegrees(stamp.rotation())));
        int left = -w / 2;
        int top = -h / 2;
        g.fill(left + 2, top, left + w - 2, top + 2, INK);
        g.fill(left + 2, top + h - 2, left + w - 2, top + h, INK);
        g.fill(left, top + 2, left + 2, top + h - 2, INK);
        g.fill(left + w - 2, top + 2, left + w, top + h - 2, INK);
        g.fill(left + 4, top + 3, left + w - 4, top + 4, INK_LIGHT);
        g.fill(left + 4, top + h - 4, left + w - 4, top + h - 3, INK_LIGHT);
        g.drawString(font, place, left + (w - font.width(place)) / 2, top + 5, INK, false);
        int ruleY = top + 6 + font.lineHeight;
        g.fill(left + 6, ruleY, left + w - 6, ruleY + 1, INK_LIGHT);
        g.drawString(font, sub, left + (w - font.width(sub)) / 2, ruleY + 3, INK, false);
        g.pose().popPose();
    }

    private String truncate(String text, int room) {
        String cut = text;
        while (cut.length() > 3 && font.width(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
