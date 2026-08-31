package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.calendar.StampCatalog;
import com.aetherianartificer.townstead.client.gui.common.MenuPanel;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A physical career stamp, the desk rail it rests on, and its temporary seal case. */
final class StampTool {

    /** The tool rail's band across the foot of the record panel. */
    static final int RAIL_H = 20;
    /** The rail's own inset, matching the record sheet's margin. */
    private static final int RAIL_PAD = 7;
    /**
     * The groove runs a pixel past the die on each side, and everything to its right is placed
     * from its actual edge. It used to be measured off a 34 wide ink pad that no longer exists,
     * which left the plate floating eight pixels further out than anything justified.
     */
    private static final int GROOVE_OVERHANG = 1;
    /** Gap between the die's groove and the seal plate. */
    private static final int RAIL_GAP = 6;
    /**
     * The plate's height, and it is derived rather than chosen: a text row plus two pixels of
     * plate above and below it. That is what lets the plate's text share the status line's
     * baseline instead of sitting two pixels under it.
     */
    private static final int CASE_BOX_H = 13;
    private static final int DIE_W = 28;
    private static final int DIE_H = 25;
    /**
     * The die at rest is smaller than the die in hand. A 25px tool standing on a 20px rail has to
     * overhang it, and at full size that overhang reaches into the body's last rows; scaled down
     * it clears them, and a held object reading larger than a shelved one is correct anyway.
     */
    private static final int REST_W = 20;
    private static final int REST_H = 18;
    /**
     * Tilt, in radians. A mark is 62x24 in a 72x32 field, so past about seven degrees the
     * cartouche clips its own corner. The old bound was 0.55, four times what fits.
     */
    private static final float TILT_LIMIT = 0.12f;
    /**
     * The case is a drawer in the desk, so it runs the sheet's own width and stacks ROWS. A grid
     * of cells could not carry a seal's whole name, and the name is the part that distinguishes
     * an archive from a guild; a twelve pixel device cannot.
     */
    private static final int ROW_H = MenuPanel.ROW_H;
    private static final int CASE_MAX_H = 92;

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
    private int pressX;
    private int pressY;

    private int railX;
    private int railY;
    private int railW;
    private int padX;
    private int padY;
    private int caseBoxX;
    private int caseBoxY;
    private int caseBoxW;

    // The mark this client has just pressed, drawn before the server echoes it back. Without it
    // the dust settles on blank paper and the impression appears a round trip later.
    private String pendingId = "";
    private int pendingX;
    private int pendingY;
    private float pendingRot;

    private boolean caseOpen;
    private int caseX;
    private int caseY;
    private int caseH;
    private int caseScroll;
    private int caseContentH;
    private String selectedTextureId = "";
    private String selectedSourcePack = "";
    private String selectedLabel = "";
    private final List<OptionHit> optionHits = new ArrayList<>();

    private record OptionHit(int x, int y, int w, int h, CareerStampCatalog.Entry entry) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    StampTool(Font font) { this.font = font; }

    boolean held() { return held; }
    boolean caseOpen() { return caseOpen; }
    String selectedTextureId() { return selectedTextureId; }
    String selectedSourcePack() { return selectedSourcePack; }
    String selectedLabel() { return selectedLabel; }

    static boolean available(CareerGraphS2CPayload.Node node, boolean inspect) {
        if (inspect || node == null) return false;
        if (node.kind() == CareerGraphS2CPayload.KIND_SKILL) {
            return node.state() == CareerGraphS2CPayload.STATE_READY;
        }
        return takeUp(node);
    }

    static boolean canTakeUp(CareerGraphS2CPayload.Node node) {
        if (node == null || node.primary()) return false;
        return node.kind() == CareerGraphS2CPayload.KIND_ROOT
                || (node.kind() == CareerGraphS2CPayload.KIND_ADVANCED
                && node.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
    }

    static boolean takeUp(CareerGraphS2CPayload.Node node) {
        return canTakeUp(node) && !node.stamp().present();
    }

    void reset() {
        held = false;
        rotation = -0.09f;
    }

    void closeCase() {
        caseOpen = false;
    }

    void rotate(double delta) {
        if (held) rotation = Mth.clamp(rotation + (delta > 0 ? -0.03f : 0.03f),
                -TILT_LIMIT, TILT_LIMIT);
    }

    /**
     * The tool rail: a band of desk under the record sheet carrying the ink pad, the die, the seal
     * case, and whatever the record has to say about registering.
     *
     * <p>It draws on every record, stampable or not. That is the whole point of it: the head can
     * stop changing height, and a refusal has somewhere to be said instead of the tool simply not
     * being there.</p>
     */
    void drawRail(GuiGraphics g, int x, int y, int w, boolean afford,
                  String status, int statusColor) {
        railX = x;
        railY = y;
        railW = w;
        g.fill(x, y, x + w, y + RAIL_H, Palette.DESK);
        g.fill(x, y, x + w, y + 1, Palette.DESK_LIP);
        g.fill(x, y + 1, x + w, y + 2, 0xFF4A3620);
        g.fill(x, y + RAIL_H - 2, x + w, y + RAIL_H, Palette.DESK_DEEP);

        // One score in the desk, a shade wider than the die, and nothing else. It does two jobs
        // that drawing nothing at all does not: the die's foot lands ON something instead of
        // hanging four pixels above the rail's bottom edge, and while the stamp is in hand the
        // groove is still there saying where it goes back.
        padX = x + RAIL_PAD;
        padY = y + RAIL_H - 8;
        int dieX = padX + 7;
        int grooveRight = dieX + REST_W + GROOVE_OVERHANG;
        g.fill(dieX - GROOVE_OVERHANG, padY + 2, grooveRight, padY + 3, Palette.DESK_EDGE);
        if (!held && pressedAt < 0) {
            drawToolScaled(g, dieX, padY + 2 - REST_H, REST_W, REST_H, afford ? 1f : 0.4f);
        }

        // Status first, because the plate takes whatever room the status leaves. Measuring rather
        // than reserving a fixed width keeps this honest in any language.
        int statusLeft = x + w - RAIL_PAD;
        if (!status.isEmpty()) {
            statusLeft -= font.width(status);
            g.drawString(font, status, statusLeft,
                    y + (RAIL_H - font.lineHeight) / 2, statusColor, false);

        }

        // A plate carrying the armed seal's own device, and nothing else. A proper noun sitting
        // on a toolbar reads as a riddle, and the drawer already carries every seal's full name;
        // this only has to say WHICH one is loaded, which one device can do.
        int textY = y + (RAIL_H - font.lineHeight) / 2;
        caseBoxX = grooveRight + RAIL_GAP;
        caseBoxY = textY - 2;
        caseBoxW = 15;
        g.fill(caseBoxX, caseBoxY, caseBoxX + caseBoxW, caseBoxY + CASE_BOX_H, Palette.DESK_EDGE);
        g.fill(caseBoxX + 1, caseBoxY + 1, caseBoxX + caseBoxW - 1, caseBoxY + CASE_BOX_H - 1,
                caseOpen ? Palette.WELL : Palette.ALCOVE);
        drawArmedFace(g, caseBoxX + 2, caseBoxY + 2, caseBoxW - 4, CASE_BOX_H - 4,
                caseOpen ? Palette.BRASS : INK_LIGHT);
    }

    /**
     * The loaded seal's own face, on the rail plate. Art seals blit their texture; the built-ins
     * fall through to the same frame-and-device family the drawer rows use, so the plate and the
     * row a player picked it from show the same thing.
     */
    private void drawArmedFace(GuiGraphics g, int x, int y, int w, int h, int colour) {
        if (!selectedTextureId.isEmpty() && StampCatalog.hasTexture(selectedTextureId)) {
            //? if >=1.21 {
            ResourceLocation texture = ResourceLocation.parse(selectedTextureId);
            //?} else {
            /*ResourceLocation texture = new ResourceLocation(selectedTextureId);
            *///?}
            int size = Math.min(w, h);
            g.setColor(0.85f, 0.35f, 0.28f, 0.95f);
            g.blit(texture, x + (w - size) / 2, y, size, size, 0f, 0f, size, size, size, size);
            g.setColor(1f, 1f, 1f, 1f);
            return;
        }
        drawSealFace(g, x, y, w, h, !selectedLabel.isEmpty(), colour);
    }

    /** The die's own footprint plus a little slack, not the vanished pad's. */
    boolean overPad(double mx, double my) {
        int dieX = padX + 7;
        return mx >= dieX - 3 && mx < dieX + REST_W + 3
                && my >= padY + 2 - REST_H && my < railY + RAIL_H;
    }

    /** Anywhere on the desk band. Releasing the stamp here puts it back rather than pressing it. */
    boolean overRail(double mx, double my) {
        return mx >= railX && mx < railX + railW && my >= railY && my < railY + RAIL_H;
    }

    boolean overCaseBox(double mx, double my) {
        return mx >= caseBoxX && mx < caseBoxX + caseBoxW
                && my >= caseBoxY && my < caseBoxY + CASE_BOX_H;
    }

    void toggleCase() {
        caseOpen = !caseOpen;
        if (caseOpen) held = false;
    }

    boolean overCase(double mx, double my) {
        return caseOpen && mx >= caseX && mx < caseX + railW - 2 * RAIL_PAD
                && my >= caseY && my < caseY + caseH;
    }

    boolean chooseFromCase(double mx, double my) {
        if (!caseOpen) return false;
        for (OptionHit hit : optionHits) {
            if (!hit.contains(mx, my)) continue;
            CareerStampCatalog.Entry entry = hit.entry();
            selectedTextureId = entry.textureId();
            selectedSourcePack = entry.sourcePack();
            selectedLabel = entry.markLabel();
            closeCase();
            return true;
        }
        return false;
    }

    void scrollCase(double delta) {
        int visible = MenuPanel.fit(CASE_MAX_H, false);
        int max = Math.max(0, caseContentH / ROW_H - visible);
        caseScroll = Mth.clamp(caseScroll - (int) Math.signum(delta), 0, max);
    }

    /**
     * The drawer: dark wood, the sheet's own width, one seal per row.
     *
     * <p>It belongs to the desk, not the record. Drawn in parchment it read as a piece of the
     * sheet that had come loose, and it left the seal faces with no value to be red against. It
     * also sizes to its contents: a fixed panel to choose between the two seals that ship was
     * wrong before any of its pixels were.</p>
     */
    void drawCase(GuiGraphics g, String careerName, int mouseX, int mouseY) {
        optionHits.clear();
        if (!caseOpen) return;
        List<CareerStampCatalog.Entry> entries = CareerStampCatalog.list(careerName);
        int visible = Math.min(entries.size(), MenuPanel.fit(CASE_MAX_H, false));
        caseContentH = entries.size() * ROW_H;
        caseH = MenuPanel.height(visible, false);
        caseX = railX + RAIL_PAD;
        int caseW = railW - 2 * RAIL_PAD;
        // Seated on the rail: flush, and with no bottom edge, so the drawer and the desk read as
        // one object rather than a panel resting on a band.
        caseY = railY - caseH;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        MenuPanel.drawFrame(g, font, caseX, caseY, caseW, caseH, null, true);

        int listTop = MenuPanel.rowsTop(caseY, false);
        int listBottom = caseY + caseH - MenuPanel.ROW_INSET;
        int first = Mth.clamp(caseScroll, 0, Math.max(0, entries.size() - visible));
        caseScroll = first;
        g.enableScissor(caseX + 1, listTop, caseX + caseW - 1, listBottom);
        for (int local = 0; local < visible; local++) {
            int i = first + local;
            if (i >= entries.size()) break;
            CareerStampCatalog.Entry entry = entries.get(i);
            int ry = listTop + local * ROW_H;
            boolean hover = mouseX >= caseX && mouseX < caseX + caseW
                    && mouseY >= ry && mouseY < ry + ROW_H;
            boolean chosen = selectedTextureId.equals(entry.textureId())
                    && selectedLabel.equals(entry.markLabel());
            MenuPanel.drawRow(g, caseX, ry, caseW, chosen, hover);
            drawCaseFace(g, entry, caseX + MenuPanel.ICON_X, ry + 2,
                    MenuPanel.ICON_W, ROW_H - 5);
            String name = truncate(entry.name(), caseW - MenuPanel.LABEL_X - 8);
            g.drawString(font, name, caseX + MenuPanel.LABEL_X, ry + MenuPanel.TEXT_Y,
                    MenuPanel.labelColor(chosen), false);
            optionHits.add(new OptionHit(caseX, ry, caseW, ROW_H, entry));
        }
        g.disableScissor();
        MenuPanel.drawScrollbar(g, caseX, caseY, caseW, caseH, false, first, visible,
                entries.size());
        g.pose().popPose();
    }

    /**
     * One seal face, art or device.
     *
     * <p>The two built-ins used to be drawn as different SHAPES: a filled bordered rectangle and
     * an outlined lozenge with a cross. Different shapes never read as a set, which is most of why
     * the case looked like a bag of clip art. One frame, different device inside, is what makes a
     * family. Devices are drawn to the same small box so any of them fits any row.</p>
     */
    /**
     * One seal device, at a FIXED footprint centred in whatever box it is handed.
     *
     * <p>It used to be drawn at offsets from the centre of the caller's box, so the rail's 11x9
     * plate and the drawer's 14x10 row produced visibly different marks from the same code. A
     * seal is one shape; where it is shown must not change what it looks like.</p>
     *
     * <p>There is no frame any more either. A border around a 14x10 box left about 8x6 for the
     * device, which is not enough for a building, and the result read as a squiggle in a box. The
     * row already supplies the structure a frame was pretending to give.</p>
     */
    private static final int DEVICE_W = 11;
    private static final int DEVICE_H = 9;

    private void drawSealFace(GuiGraphics g, int x, int y, int w, int h,
                              boolean guild, int colour) {
        int ox = x + (w - DEVICE_W) / 2;
        int oy = y + (h - DEVICE_H) / 2;
        if (guild) {
            // A shield. Solid, because an outline plus a charge cannot both survive at nine rows.
            // Five straight rows before the taper. Four made it read as a heart.
            int[][] shield = {{1, 10}, {1, 10}, {1, 10}, {1, 10}, {1, 10},
                              {2, 9}, {3, 8}, {4, 7}, {5, 6}};
            for (int r = 0; r < shield.length; r++) {
                g.fill(ox + shield[r][0], oy + r, ox + shield[r][1], oy + r + 1, colour);
            }
            return;
        }
        // An archive: pediment, colonnade, stylobate.
        g.fill(ox + 3, oy, ox + 8, oy + 1, colour);
        g.fill(ox + 2, oy + 1, ox + 9, oy + 2, colour);
        g.fill(ox + 1, oy + 2, ox + 10, oy + 3, colour);
        for (int col : new int[] {2, 4, 6, 8}) {
            g.fill(ox + col, oy + 3, ox + col + 1, oy + 7, colour);
        }
        g.fill(ox + 1, oy + 7, ox + 10, oy + 8, colour);
        g.fill(ox, oy + 8, ox + 11, oy + 9, colour);
    }

    private void drawCaseFace(GuiGraphics g, CareerStampCatalog.Entry entry,
                              int x, int y, int w, int h) {
        if (entry.hasArt()) {
            int size = Math.min(w, h);
            g.setColor(0.85f, 0.35f, 0.28f, 0.95f);
            g.blit(entry.texture(), x + (w - size) / 2, y, size, size,
                    0f, 0f, size, size, size, size);
            g.setColor(1f, 1f, 1f, 1f);
            return;
        }
        drawSealFace(g, x, y, w, h, !entry.markLabel().isEmpty(), INK_LIGHT);
    }

    void pickUp(double mx, double my) {
        closeCase();
        held = true;
        toolX = mx - DIE_W / 2.0;
        toolY = my - DIE_H / 2.0;
    }

    void moveTo(double mx, double my) {
        toolX = mx - DIE_W / 2.0;
        toolY = my - DIE_H / 2.0;
    }

    int centreX() { return (int) Math.round(toolX + DIE_W / 2.0); }
    int centreY() { return (int) Math.round(toolY + DIE_H / 2.0); }
    float rotation() { return rotation; }

    void drawHeld(GuiGraphics g, boolean validDrop) {
        if (!held) return;
        int x = (int) Math.round(toolX);
        int y = (int) Math.round(toolY);
        g.pose().pushPose();
        g.pose().translate(x + DIE_W / 2f, y + DIE_H - 1f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        g.fill(-DIE_W / 2 + 1, -4, DIE_W / 2 - 1, 4,
                validDrop ? 0x4D000000 : 0x4D8A2018);
        g.pose().popPose();
        drawTool(g, x, y, rotation, 1f);
    }

    /**
     * Records the impression locally and starts the animation. The stamp STAYS IN HAND: the
     * ceremony is paid once on pickup, so registering a run of skills is press, pick the next mark,
     * press again rather than a fresh drag every time.
     */
    void press(String nodeId, int markX, int markY, int panelLeft, int panelTop) {
        pressedAt = Util.getMillis();
        pressX = markX;
        pressY = markY;
        pendingId = nodeId;
        pendingX = markX - panelLeft;
        pendingY = markY - panelTop;
        pendingRot = rotation;
    }

    /** Forgets the local impression once the server's own mark has arrived for that record. */
    void clearPending(String nodeId) {
        if (pendingId.equals(nodeId)) pendingId = "";
    }

    void drawPressAnimation(GuiGraphics g) {
        if (pressedAt < 0) return;
        long age = Util.getMillis() - pressedAt;
        if (age > 900) { pressedAt = -1L; return; }
        float t = age / 900f;
        if (age < 400) {
            int lift = Math.round(10f * (age < 150 ? 1f - age / 150f : (age - 150) / 250f));
            boolean squash = age >= 130 && age < 200;
            drawTool(g, pressX - DIE_W / 2 + (squash ? 1 : 0),
                    pressY - DIE_H / 2 - lift + (squash ? 1 : 0), rotation, 1f - t * 0.3f);
        }
        for (int i = 0; i < 14; i++) {
            double angle = (i / 14.0) * Math.PI * 2;
            double dist = 10 + t * 44;
            int px = pressX + (int) Math.round(Math.cos(angle) * dist * 1.4);
            int py = pressY + (int) Math.round(Math.sin(angle) * dist * 0.55);
            int alpha = (int) (Math.max(0f, 1f - t) * 130f) << 24;
            g.fill(px, py, px + 1, py + 1, alpha | 0x00CDB98E);
        }
    }

    private void drawTool(GuiGraphics g, int x, int y, float rot, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x + DIE_W / 2f, y + DIE_H / 2f, 550);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rot)));
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(TOOL_TEXTURE, -DIE_W / 2, -DIE_H / 2, DIE_W, DIE_H,
                0f, 0f, DIE_W, DIE_H, DIE_W, DIE_H);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private void drawToolScaled(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(TOOL_TEXTURE, x, y, w, h, 0f, 0f, DIE_W, DIE_H, DIE_W, DIE_H);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * The mark on this record, whether the server has confirmed it or this client has only just
     * pressed it. The record's own endorsement field carries the reservation, so nothing is drawn
     * here to advertise where a stamp may go.
     */
    void drawMark(GuiGraphics g, int panelLeft, int panelTop, String nodeId,
                  CareerGraphS2CPayload.Stamp stamp,
                  int targetX, int targetY, int targetW, int targetH) {
        if (stamp.present()) {
            clearPending(nodeId);
        } else if (pendingId.equals(nodeId)) {
            drawPendingMark(g, panelLeft, panelTop, targetX, targetY, targetW, targetH);
            return;
        } else {
            return;
        }
        String place = stamp.authority().isEmpty()
                ? Component.translatable("townstead.career.screen.field_registry").getString()
                : stamp.label().isEmpty() ? stamp.authority() : stamp.label();
        String office = Component.translatable("townstead.career.screen.registered").getString();
        drawImpression(g, stamp.textureId(), place,
                stamp.date().isEmpty() ? office : office + "  " + stamp.date(),
                panelLeft + stamp.x(), panelTop + stamp.y(), stamp.rotation(),
                targetX, targetY, targetW, targetH);
    }

    /**
     * The impression this client has just made, drawn from the armed seal rather than from a
     * payload. The authority and date only exist server side, so an unlabelled seal shows the
     * field registry line until the echo lands; every other field is already correct locally.
     */
    private void drawPendingMark(GuiGraphics g, int panelLeft, int panelTop,
                                 int targetX, int targetY, int targetW, int targetH) {
        String place = selectedLabel.isEmpty()
                ? Component.translatable("townstead.career.screen.field_registry").getString()
                : selectedLabel;
        drawImpression(g, selectedTextureId, place,
                Component.translatable("townstead.career.screen.registered").getString(),
                panelLeft + pendingX, panelTop + pendingY, pendingRot,
                targetX, targetY, targetW, targetH);
    }

    /** One mark, art or cartouche, clamped so it cannot hang out of the endorsement field. */
    private void drawImpression(GuiGraphics g, String textureId, String place, String sub,
                                int centreX, int centreY, float rotation,
                                int targetX, int targetY, int targetW, int targetH) {
        if (!textureId.isEmpty() && StampCatalog.hasTexture(textureId)) {
            int size = Math.min(28, Math.min(targetW, targetH));
            //? if >=1.21 {
            ResourceLocation texture = ResourceLocation.parse(textureId);
            //?} else {
            /*ResourceLocation texture = new ResourceLocation(textureId);
            *///?}
            g.pose().pushPose();
            g.pose().translate(
                    Mth.clamp(centreX, targetX + size / 2, targetX + targetW - size / 2),
                    Mth.clamp(centreY, targetY + size / 2, targetY + targetH - size / 2), 220);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                    (float) Math.toDegrees(rotation)));
            g.setColor(0.78f, 0.18f, 0.14f, 0.92f);
            g.blit(texture, -size / 2, -size / 2, size, size, 0f, 0f, size, size, size, size);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
            return;
        }
        String top1 = place.toUpperCase(Locale.ROOT);
        // Drop the date before cutting into it. "ARCHIVES 14 SPRI…" reads as a rendering fault;
        // the office on its own reads as a shorter stamp, which is a thing stamps are.
        int room = targetW - 16;
        if (font.width(sub) > room) {
            sub = Component.translatable("townstead.career.screen.registered").getString();
        }
        // Shrink the face before truncating the name. A name cut at full size is a name the
        // stamp could have carried one step down, and the name is the part worth reading.
        float face = faceScale(Math.max(font.width(top1), font.width(sub)), room);
        if (scaled(font.width(top1), face) > room) top1 = truncate(top1, Math.round(room / face));
        int w = Math.min(targetW,
                Math.max(scaled(font.width(top1), face), scaled(font.width(sub), face)) + 16);
        int h = 2 * font.lineHeight + 15;
        g.pose().pushPose();
        g.pose().translate(Mth.clamp(centreX, targetX + w / 2, targetX + targetW - w / 2),
                Mth.clamp(centreY, targetY + h / 2, targetY + targetH - h / 2), 220);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        int left = -w / 2;
        int top = -h / 2;
        g.fill(left + 2, top, left + w - 2, top + 2, INK);
        g.fill(left + 2, top + h - 2, left + w - 2, top + h, INK);
        g.fill(left, top + 2, left + 2, top + h - 2, INK);
        g.fill(left + w - 2, top + 2, left + w, top + h - 2, INK);
        g.fill(left + 4, top + 3, left + w - 4, top + 4, INK_LIGHT);
        g.fill(left + 4, top + h - 4, left + w - 4, top + h - 3, INK_LIGHT);
        drawFace(g, top1, left, w, top + 5, face);
        int ruleY = top + 6 + font.lineHeight;
        g.fill(left + 6, ruleY, left + w - 6, ruleY + 1, INK_LIGHT);
        drawFace(g, sub, left, w, ruleY + 3, face);
        g.pose().popPose();
    }

    /**
     * The face ladder from the redesign: 8px, then 7, then 6, and 6 is the floor because below it
     * Minecraft's font stops being readable. Only past 6 does anything get cut.
     */
    private float faceScale(int widest, int room) {
        if (widest <= room) return 1f;
        for (float step : new float[] {0.875f, 0.75f}) {
            if (scaled(widest, step) <= room) return step;
        }
        return 0.75f;
    }

    private int scaled(int width, float face) {
        return Math.round(width * face);
    }

    /** One centred line of the stamp's face, at whatever size the ladder settled on. */
    private void drawFace(GuiGraphics g, String text, int left, int w, int y, float face) {
        if (face >= 0.999f) {
            g.drawString(font, text, left + (w - font.width(text)) / 2, y, INK, false);
            return;
        }
        int drawn = scaled(font.width(text), face);
        g.pose().pushPose();
        g.pose().translate(left + (w - drawn) / 2f, y, 0);
        g.pose().scale(face, face, 1f);
        g.drawString(font, text, 0, 0, INK, false);
        g.pose().popPose();
    }

    private String truncate(String text, int room) {
        if (font.width(text) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
