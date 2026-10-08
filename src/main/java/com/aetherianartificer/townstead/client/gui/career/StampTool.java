package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.InkSeal;
import com.aetherianartificer.townstead.client.gui.common.MenuPanel;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.Signet;
import com.aetherianartificer.townstead.client.seal.ClientSeal;
import com.aetherianartificer.townstead.seal.PersonalSeal;
import net.minecraft.client.Minecraft;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A physical career stamp, the desk rail it rests on, and the case where the player keeps their
 * seal. The stamp presses the player's own seal, the same one they sign a Charter with.
 */
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
    private static final int REST_W = Signet.REST_W;
    /**
     * The case is a drawer in the desk, so it runs the sheet's own width: a strip of the sixteen
     * inks across its top, then one device per row.
     */
    private static final int ROW_H = MenuPanel.ROW_H;
    private static final int CASE_MAX_H = 106;
    private static final int SWATCH = 10;

    private final Font font;
    private final Signet signet;

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

    private boolean caseOpen;
    private int caseX;
    private int caseY;
    private int caseH;
    private int caseScroll;
    private int caseContentH;
    private final List<OptionHit> optionHits = new ArrayList<>();

    /** {@code close} is whether choosing it shuts the drawer: a device does, an ink does not. */
    private record OptionHit(int x, int y, int w, int h, Runnable choose, boolean close) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    StampTool(Font font) {
        this.font = font;
        this.signet = new Signet(font);
    }

    boolean held() { return signet.held(); }
    boolean caseOpen() { return caseOpen; }
    // The server presses the player's stored seal; the payload's old seal fields go empty.
    String selectedTextureId() { return ""; }
    String selectedSourcePack() { return ""; }
    String selectedLabel() { return ""; }

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
        signet.putDown();
    }

    void closeCase() {
        caseOpen = false;
    }

    /** Seals press flat, so the die no longer tilts in hand. */
    void rotate(double delta) {}

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
        signet.drawResting(g, dieX, padY + 2, afford ? 1f : 0.4f);

        // Status first, because the plate takes whatever room the status leaves. Measuring rather
        // than reserving a fixed width keeps this honest in any language.
        int statusLeft = x + w - RAIL_PAD;
        if (!status.isEmpty()) {
            statusLeft -= font.width(status);
            g.drawString(font, status, statusLeft,
                    y + (RAIL_H - font.lineHeight) / 2, statusColor, false);

        }

        // A plate carrying the player's seal device in its ink: which seal is loaded, at a glance.
        int textY = y + (RAIL_H - font.lineHeight) / 2;
        caseBoxX = grooveRight + RAIL_GAP;
        caseBoxY = textY - 2;
        caseBoxW = 15;
        g.fill(caseBoxX, caseBoxY, caseBoxX + caseBoxW, caseBoxY + CASE_BOX_H, Palette.DESK_EDGE);
        g.fill(caseBoxX + 1, caseBoxY + 1, caseBoxX + caseBoxW - 1, caseBoxY + CASE_BOX_H - 1,
                caseOpen ? Palette.WELL : Palette.ALCOVE);
        PersonalSeal seal = ClientSeal.get();
        InkSeal.drawDevice(g, font, seal.device(), ownerName(), caseBoxX + 2, caseBoxY + 1,
                caseOpen ? Palette.BRASS : lighten(InkSeal.ink(seal.dye())));
    }

    /** Ink reads dark on the desk's wood, so the plate shows it a shade lighter. */
    private static int lighten(int argb) {
        int r = Math.min(255, (argb >> 16 & 255) + 60), g = Math.min(255, (argb >> 8 & 255) + 60), b = Math.min(255, (argb & 255) + 60);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static String ownerName() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : player.getGameProfile().getName();
    }

    /** The die's own footprint plus a little slack, not the vanished pad's. */
    boolean overPad(double mx, double my) {
        int dieX = padX + 7;
        return mx >= dieX - 3 && mx < dieX + REST_W + 3
                && my >= padY + 2 - Signet.REST_H && my < railY + RAIL_H;
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
        if (caseOpen) signet.putDown();
    }

    boolean overCase(double mx, double my) {
        return caseOpen && mx >= caseX && mx < caseX + railW - 2 * RAIL_PAD
                && my >= caseY && my < caseY + caseH;
    }

    boolean chooseFromCase(double mx, double my) {
        if (!caseOpen) return false;
        for (OptionHit hit : optionHits) {
            if (!hit.contains(mx, my)) continue;
            hit.choose().run();
            if (hit.close()) closeCase();
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
     * The drawer: dark wood, the sheet's own width. The inks run across its top as swatches; below
     * them, one device per row, each drawn in the chosen ink.
     */
    void drawCase(GuiGraphics g, String careerName, int mouseX, int mouseY) {
        optionHits.clear();
        if (!caseOpen) return;
        PersonalSeal seal = ClientSeal.get();
        List<InkSeal.Device> devices = InkSeal.devices();
        int strip = SWATCH + 4;
        int visible = Math.min(devices.size(), MenuPanel.fit(CASE_MAX_H - strip, false));
        caseContentH = devices.size() * ROW_H;
        caseH = MenuPanel.height(visible, false) + strip;
        caseX = railX + RAIL_PAD;
        int caseW = railW - 2 * RAIL_PAD;
        // Seated on the rail: flush, and with no bottom edge, so the drawer and the desk read as
        // one object rather than a panel resting on a band.
        caseY = railY - caseH;

        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        MenuPanel.drawFrame(g, font, caseX, caseY, caseW, caseH, null, true);

        int cell = Math.max(SWATCH, (caseW - 2 * MenuPanel.ICON_X) / 16);
        int sx = caseX + MenuPanel.ICON_X, sy = caseY + 4;
        for (int dye = 0; dye < 16; dye++) {
            int value = dye;
            int cx = sx + dye * cell;
            boolean chosen = dye == seal.dye();
            boolean hover = mouseX >= cx && mouseX < cx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
            g.fill(cx, sy, cx + SWATCH, sy + SWATCH, chosen || hover ? Palette.BRASS : Palette.MENU_EDGE);
            g.fill(cx + 1, sy + 1, cx + SWATCH - 1, sy + SWATCH - 1, InkSeal.ink(dye));
            optionHits.add(new OptionHit(cx, sy, SWATCH, SWATCH, () -> ClientSeal.choose(seal.device(), value), false));
        }

        int listTop = MenuPanel.rowsTop(caseY, false) + strip;
        int listBottom = caseY + caseH - MenuPanel.ROW_INSET;
        int first = Mth.clamp(caseScroll, 0, Math.max(0, devices.size() - visible));
        caseScroll = first;
        int color = lighten(InkSeal.ink(seal.dye()));
        g.enableScissor(caseX + 1, listTop, caseX + caseW - 1, listBottom);
        for (int local = 0; local < visible; local++) {
            int i = first + local;
            if (i >= devices.size()) break;
            InkSeal.Device device = devices.get(i);
            int ry = listTop + local * ROW_H;
            boolean hover = mouseX >= caseX && mouseX < caseX + caseW
                    && mouseY >= ry && mouseY < ry + ROW_H;
            boolean chosen = device.id().equals(seal.device());
            MenuPanel.drawRow(g, caseX, ry, caseW, chosen, hover);
            InkSeal.drawDevice(g, font, device.id(), ownerName(), caseX + MenuPanel.ICON_X, ry + 1, color);
            String name = truncate(device.name().getString(), caseW - MenuPanel.LABEL_X - 8);
            g.drawString(font, name, caseX + MenuPanel.LABEL_X, ry + MenuPanel.TEXT_Y,
                    MenuPanel.labelColor(chosen), false);
            optionHits.add(new OptionHit(caseX, ry, caseW, ROW_H, () -> ClientSeal.choose(device.id(), seal.dye()), true));
        }
        g.disableScissor();
        MenuPanel.drawScrollbar(g, caseX, caseY + strip, caseW, caseH - strip, false, first, visible,
                devices.size());
        g.pose().popPose();
    }

    void pickUp(double mx, double my) {
        closeCase();
        signet.pickUp(mx, my);
    }

    void moveTo(double mx, double my) {
        signet.moveTo(mx, my);
    }

    int centreX() { return signet.centreX(); }
    int centreY() { return signet.centreY(); }
    float rotation() { return signet.rotation(); }

    void drawHeld(GuiGraphics g, boolean validDrop) {
        signet.drawHeld(g, validDrop);
    }

    /**
     * Records the impression locally and starts the animation. The stamp STAYS IN HAND: the
     * ceremony is paid once on pickup, so registering a run of skills is press, pick the next mark,
     * press again rather than a fresh drag every time.
     */
    void press(String nodeId, int markX, int markY, int panelLeft, int panelTop) {
        signet.press();
        pendingId = nodeId;
        pendingX = markX - panelLeft;
        pendingY = markY - panelTop;
    }

    /** Forgets the local impression once the server's own mark has arrived for that record. */
    void clearPending(String nodeId) {
        if (pendingId.equals(nodeId)) pendingId = "";
    }

    void drawPressAnimation(GuiGraphics g) {
        signet.drawPressAnimation(g);
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
        String authority = stamp.authority().isEmpty()
                ? Component.translatable("townstead.career.screen.field_registry").getString()
                : stamp.authority();
        if (stamp.sealed()) {
            drawSealed(g, new PersonalSeal(stamp.device(), stamp.dye()), stamp.label(), authority, stamp.date(),
                    panelLeft + stamp.x(), panelTop + stamp.y(), targetX, targetY, targetW, targetH);
            return;
        }
        String seal = stamp.label().isEmpty()
                ? Component.translatable("townstead.career.screen.registered").getString()
                : stamp.label();
        drawImpression(g, stamp.textureId(), authority, seal, stamp.date(),
                panelLeft + stamp.x(), panelTop + stamp.y(), stamp.rotation(),
                targetX, targetY, targetW, targetH);
    }

    /**
     * The impression this client has just made, drawn from the player's own seal. The authority
     * and date only exist server side, so the field registry line stands in until the echo lands.
     */
    private void drawPendingMark(GuiGraphics g, int panelLeft, int panelTop,
                                 int targetX, int targetY, int targetW, int targetH) {
        String authority = Component.translatable(
                "townstead.career.screen.field_registry").getString();
        drawSealed(g, ClientSeal.get(), ownerName(), authority, "",
                panelLeft + pendingX, panelTop + pendingY, targetX, targetY, targetW, targetH);
    }

    /**
     * A sealed mark: the ink seal where it was pressed, and beside it the authority it was
     * registered with and the day. The whole group stays inside the endorsement field.
     */
    private void drawSealed(GuiGraphics g, PersonalSeal seal, String initial, String authority, String date,
                            int centreX, int centreY, int targetX, int targetY, int targetW, int targetH) {
        int size = InkSeal.SIZE;
        int room = Math.max(0, targetW - size - 4);
        String top = truncate(authority, room), bottom = truncate(date, room);
        int groupW = size + (room == 0 ? 0 : 4 + Math.max(font.width(top), font.width(bottom)));
        int x = Mth.clamp(centreX - size / 2, targetX, targetX + Math.max(0, targetW - groupW));
        int y = Mth.clamp(centreY - size / 2, targetY, targetY + Math.max(0, targetH - size));
        g.pose().pushPose();
        g.pose().translate(0, 0, 220);
        InkSeal.draw(g, font, seal, initial, x, y);
        if (room > 0) {
            int tx = x + size + 4;
            g.drawString(font, top, tx, y + (date.isEmpty() ? 8 : 3), BookRenderer.INK, false);
            if (!date.isEmpty()) g.drawString(font, bottom, tx, y + 13, BookRenderer.FADED, false);
        }
        g.pose().popPose();
    }

    private void drawImpression(GuiGraphics g, String textureId, String authority,
                                String seal, String date,
                                int centreX, int centreY, float rotation,
                                int targetX, int targetY, int targetW, int targetH) {
        signet.drawImpression(g, textureId, authority, seal, date, centreX, centreY, rotation,
                targetX, targetY, targetW, targetH);
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
