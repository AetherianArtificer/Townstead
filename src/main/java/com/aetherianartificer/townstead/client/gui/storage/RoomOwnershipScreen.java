package com.aetherianartificer.townstead.client.gui.storage;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.PersonPortrait;
import com.aetherianartificer.townstead.storage.OwnershipScope;
import com.aetherianartificer.townstead.storage.RoomOwner;
import com.aetherianartificer.townstead.storage.net.RoomOwnershipSetC2SPayload;
import com.aetherianartificer.townstead.storage.net.RoomOwnershipSnapshotS2CPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Townstead's deed-like editor for deciding who may use a room or building. */
public final class RoomOwnershipScreen extends Screen {
    private static final int IDEAL_W = 360;
    private static final int IDEAL_H = 260;
    private static final int MIN_W = 300;
    private static final int MIN_H = 218;
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 28;
    private static final int PAD = 9;
    private static final int ROW_H = 29;
    private static final int SECTION_H = 13;
    /** Shared body rhythm: label, control, then the roster well. */
    private static final int SCOPE_LABEL_TOP = 5;
    private static final int SCOPE_CONTROL_TOP = 18;
    private static final int ROSTER_TOP = 43;

    private final RoomOwnershipSnapshotS2CPayload snapshot;
    private final boolean preview;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private OwnershipScope scope;
    private boolean privateAccess;
    private int scroll;
    private boolean draggingScrollbar;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private boolean showDeed;
    private Controls.Rect[] scopeRects = new Controls.Rect[0];
    private Controls.Rect publicButton;
    private Controls.Rect cancelButton;
    private Controls.Rect saveButton;

    public RoomOwnershipScreen(RoomOwnershipSnapshotS2CPayload snapshot) {
        super(Component.translatable("townstead.room_ownership.title"));
        this.snapshot = snapshot;
        this.preview = snapshot.worksiteId() < 0;
        this.scope = snapshot.scope();
        this.privateAccess = snapshot.privateAccess();
        for (RoomOwnershipSnapshotS2CPayload.Person person : snapshot.people()) {
            if (person.selected()) selected.add(person.uuid());
        }
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(IDEAL_W, width - 24));
        panelH = Math.max(MIN_H, Math.min(IDEAL_H, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        showDeed = panelW >= 330;

        int bodyY = panelY + HEADER_H;
        listX = panelX + PAD;
        listY = bodyY + ROSTER_TOP;
        listW = showDeed ? 190 : panelW - PAD * 2;
        listH = Math.max(ROW_H, panelY + panelH - FOOTER_H - 4 - listY);
        clampScroll();

        String[] scopeLabels = {
                Component.translatable("townstead.room_ownership.scope_room").getString(),
                Component.translatable("townstead.room_ownership.scope_building").getString()
        };
        scopeRects = Controls.segmentLayout(font, listX, bodyY + SCOPE_CONTROL_TOP, 18,
                scopeLabels);

        int buttonY = panelY + panelH - FOOTER_H + 6;
        publicButton = new Controls.Rect(panelX + PAD, buttonY, 72, 18);
        saveButton = new Controls.Rect(panelX + panelW - PAD - 52, buttonY, 52, 18);
        cancelButton = new Controls.Rect(saveButton.x() - 58, buttonY, 52, 18);
    }

    /** The wooden panel is the whole background; do not blur over it after drawing. */
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0070402);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0070402);
    }
    *///?}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        *///?}

        FrameRenderer.drawInnerPanel(graphics, panelX, panelY, panelW, panelH);
        FrameRenderer.drawWoodenFrame(graphics, panelX, panelY, panelW, panelH, 5);
        drawHeader(graphics);
        drawRoster(graphics, mouseX, mouseY);
        if (showDeed) drawDeed(graphics);
        drawFooter(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, title, panelX + PAD, panelY + 7, Palette.LABEL_LIGHT, false);
        String place = snapshot.placeName().isBlank()
                ? Component.translatable(scope == OwnershipScope.BUILDING
                        ? "townstead.room_ownership.building"
                        : "townstead.room_ownership.room").getString()
                : snapshot.placeName();
        place = font.plainSubstrByWidth(place, Math.max(60, panelW - 160));
        graphics.drawString(font, place, panelX + PAD, panelY + 19, Palette.LABEL_WARM, false);

        String status = isPrivate()
                ? Component.translatable("townstead.room_ownership.private_count", allowedCount()).getString()
                : Component.translatable("townstead.room_ownership.public_state").getString();
        int chipX = panelX + panelW - PAD - Controls.chipWidth(font, status);
        Controls.drawChip(graphics, font,
                isPrivate() ? Controls.Chip.WAITING : Controls.Chip.SATISFIED,
                status, chipX, panelY + 10);
        graphics.fill(panelX + PAD, panelY + HEADER_H - 2,
                panelX + panelW - PAD, panelY + HEADER_H - 1, Palette.DESK_LIP);
    }

    private void drawRoster(GuiGraphics graphics, int mouseX, int mouseY) {
        int bodyY = panelY + HEADER_H;
        Controls.fieldLabel(graphics, font,
                Component.translatable("townstead.room_ownership.scope").getString(),
                listX, bodyY + SCOPE_LABEL_TOP);
        String room = Component.translatable("townstead.room_ownership.scope_room").getString();
        String building = Component.translatable("townstead.room_ownership.scope_building").getString();
        int hovered = Controls.segmentAt(scopeRects, mouseX, mouseY);
        Controls.drawSegments(graphics, font, scopeRects, new String[]{room, building},
                scope == OwnershipScope.ROOM ? 0 : 1, hovered,
                new boolean[]{true, snapshot.wholeBuildingAvailable()});

        FrameRenderer.drawWell(graphics, listX, listY, listW, listH);
        if (snapshot.people().isEmpty()) {
            drawWrapped(graphics, Component.translatable("townstead.room_ownership.no_people"),
                    listX + 8, listY + 8, listW - 16, Palette.LABEL_WARM, 2);
            return;
        }

        List<RoomOwnershipSnapshotS2CPayload.Person> household = people(true);
        List<RoomOwnershipSnapshotS2CPayload.Person> others = people(false);
        int contentHeight = rosterContentHeight(household, others);
        int viewportHeight = listH - 6;
        int y = listY + 3 - scroll;
        graphics.enableScissor(listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);
        if (!household.isEmpty()) {
            drawSection(graphics, Component.translatable("townstead.room_ownership.household").getString(), y);
            y += SECTION_H;
            for (RoomOwnershipSnapshotS2CPayload.Person person : household) {
                drawPerson(graphics, person, y, true, contentHeight > viewportHeight, mouseX, mouseY);
                y += ROW_H;
            }
        }
        if (!others.isEmpty()) {
            drawSection(graphics, Component.translatable("townstead.room_ownership.other_people").getString(), y);
            y += SECTION_H;
            for (RoomOwnershipSnapshotS2CPayload.Person person : others) {
                drawPerson(graphics, person, y, false, contentHeight > viewportHeight, mouseX, mouseY);
                y += ROW_H;
            }
        }
        graphics.disableScissor();
        Controls.drawScrollbar(graphics, listX + listW - Controls.SCROLLBAR_W - 2,
                listY + 3, viewportHeight, scroll, viewportHeight, contentHeight);
    }

    private void drawPerson(GuiGraphics graphics, RoomOwnershipSnapshotS2CPayload.Person person,
                            int y, boolean automatic, boolean hasScrollbar,
                            int mouseX, int mouseY) {
        int x = listX + 3;
        int w = listW - 6 - (hasScrollbar ? Controls.SCROLLBAR_W + 2 : 0);
        int h = ROW_H - 2;
        boolean chosen = selected.contains(person.uuid());
        boolean hover = !automatic && mouseX >= x && mouseX < x + w
                && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h,
                !automatic && chosen ? 0xFF49321B : hover ? 0xFF3E2C19 : Palette.ROW);
        if (!automatic && chosen) graphics.fill(x, y, x + 2, y + h, Palette.BRASS);

        drawIdentityIcon(graphics, person, x + 5, y + 4);
        String name = font.plainSubstrByWidth(person.name(), Math.max(28, w - 78));
        graphics.drawString(font, name, x + 32, y + 4, Palette.LABEL_LIGHT, false);
        String kind = Component.translatable(person.kind() == RoomOwner.Kind.PLAYER
                ? "townstead.room_ownership.player" : "townstead.room_ownership.villager").getString();
        graphics.drawString(font, kind, x + 32, y + 15, Palette.LABEL_DIM, false);
        if (automatic) {
            String resident = Component.translatable("townstead.room_ownership.resident").getString();
            graphics.drawString(font, resident, x + w - font.width(resident) - 5, y + 10,
                    Controls.Chip.SATISFIED.ink, false);
        } else {
            drawChoiceMark(graphics, x + w - 12, y + 8, chosen);
        }
    }

    private void drawIdentityIcon(GuiGraphics graphics,
                                  RoomOwnershipSnapshotS2CPayload.Person person, int x, int y) {
        Controls.drawSlot(graphics, x, y);
        if (person.kind() == RoomOwner.Kind.PLAYER) {
            PersonPortrait.drawPlayer(graphics, person.uuid(), x + 1, y + 1, 16);
        } else {
            PersonPortrait.drawVillager(graphics, person.uuid(), x + 1, y + 1, 16);
        }
    }

    private void drawChoiceMark(GuiGraphics graphics, int x, int y, boolean chosen) {
        graphics.drawString(font, chosen ? "✓" : "+", x, y,
                chosen ? Palette.BRASS_HOT : Palette.LABEL_WARM, false);
    }

    private void drawSection(GuiGraphics graphics, String label, int y) {
        String shown = label.toUpperCase(java.util.Locale.ROOT);
        graphics.drawString(font, shown, listX + 8, y + 2, Palette.LABEL_DIM, false);
        int lineX = listX + 12 + font.width(shown);
        graphics.fill(lineX, y + 6, listX + listW - 11, y + 7, Palette.WELL_EDGE);
    }

    private void drawDeed(GuiGraphics graphics) {
        int x = listX + listW + 8;
        int y = panelY + HEADER_H + 1;
        int w = panelX + panelW - PAD - x;
        int h = panelH - HEADER_H - FOOTER_H - 3;
        FrameRenderer.drawMapParchment(graphics, x, y, w, h);
        int inkX = x + 12;
        int inkW = w - 24;
        String heading = Component.translatable(isPrivate()
                ? "townstead.room_ownership.deed_private"
                : "townstead.room_ownership.deed_public",
                Component.translatable(scope == OwnershipScope.BUILDING
                        ? "townstead.room_ownership.building"
                        : "townstead.room_ownership.room").getString()).getString();
        graphics.drawString(font, heading, inkX, y + 10, Palette.CARD_INK, false);
        String place = font.plainSubstrByWidth(snapshot.placeName(), Math.max(20, inkW - 18));
        graphics.drawString(font, place, inkX, y + 21, Palette.CARD_INK_DIM, false);
        graphics.fill(inkX, y + 33, x + w - 10, y + 34, 0x554A3216);

        int cursor = y + 41;
        if (!isPrivate()) {
            cursor = drawWrapped(graphics,
                    Component.translatable("townstead.room_ownership.deed_public_copy"),
                    inkX, cursor, inkW, Palette.CARD_INK, 4);
        }
        if (isPrivate()) {
            Controls.drawWaxDiamond(graphics, inkX, cursor + 2);
            String residents = Component.translatable(
                    "townstead.room_ownership.household").getString();
            graphics.drawString(font, residents, inkX + 10, cursor, Palette.CARD_INK, false);
            graphics.fill(inkX, cursor + 11, x + w - 10, cursor + 12, 0x334A3216);
            cursor += 16;
            for (RoomOwnershipSnapshotS2CPayload.Person person : people(false)) {
                if (!selected.contains(person.uuid())) continue;
                Controls.drawWaxDiamond(graphics, inkX, cursor + 2);
                String kind = Component.translatable(person.kind() == RoomOwner.Kind.PLAYER
                        ? "townstead.room_ownership.player"
                        : "townstead.room_ownership.villager").getString();
                int kindW = font.width(kind);
                String name = font.plainSubstrByWidth(person.name(), Math.max(20, inkW - kindW - 16));
                graphics.drawString(font, name, inkX + 10, cursor, Palette.CARD_INK, false);
                graphics.drawString(font, kind, x + w - 10 - kindW, cursor,
                        Palette.CARD_INK_DIM, false);
                graphics.fill(inkX, cursor + 11, x + w - 10, cursor + 12, 0x334A3216);
                cursor += 16;
                if (cursor > y + h - 15) break;
            }
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = panelY + panelH - FOOTER_H;
        graphics.fill(panelX + 1, top, panelX + panelW - 1, top + 1, Palette.DESK_LIP);
        Controls.drawButton(graphics, font, publicButton,
                Component.translatable(isPrivate()
                        ? "townstead.room_ownership.public"
                        : "townstead.room_ownership.make_private").getString(),
                false, publicButton.contains(mouseX, mouseY), true);
        Controls.drawButton(graphics, font, cancelButton,
                Component.translatable("gui.cancel").getString(), false,
                cancelButton.contains(mouseX, mouseY), true);
        Controls.drawButton(graphics, font, saveButton,
                Component.translatable("townstead.room_ownership.assign").getString(), false,
                saveButton.contains(mouseX, mouseY), true);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth,
                            int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
        return y + count * 10;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int segment = Controls.segmentAt(scopeRects, mouseX, mouseY);
        if (segment == 0 || segment == 1 && snapshot.wholeBuildingAvailable()) {
            scope = segment == 0 ? OwnershipScope.ROOM : OwnershipScope.BUILDING;
            clampScroll();
            return true;
        }
        if (publicButton.contains(mouseX, mouseY)) {
            privateAccess = !privateAccess;
            if (!privateAccess) selected.clear();
            return true;
        }
        if (cancelButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (saveButton.contains(mouseX, mouseY)) {
            save();
            return true;
        }

        int contentHeight = rosterContentHeight(people(true), people(false));
        int viewportHeight = listH - 6;
        int scrollbarX = listX + listW - Controls.SCROLLBAR_W - 2;
        int picked = Controls.scrollbarPick(scrollbarX, listY + 3, viewportHeight,
                mouseX, mouseY, viewportHeight, contentHeight);
        if (picked >= 0) {
            scroll = picked;
            draggingScrollbar = true;
            return true;
        }
        RoomOwnershipSnapshotS2CPayload.Person person = personAt(mouseX, mouseY);
        if (person != null) {
            if (homeForScope(person)) return true;
            if (!selected.remove(person.uuid())) selected.add(person.uuid());
            privateAccess = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            int contentHeight = rosterContentHeight(people(true), people(false));
            int viewportHeight = listH - 6;
            scroll = Controls.scrollbarDrag(listY + 3, viewportHeight, mouseY,
                    viewportHeight, contentHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    //? if neoforge {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroll(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scroll(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    private boolean scroll(double amount) {
        int max = maxScroll();
        if (max == 0) return false;
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount) * ROW_H));
        return true;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private List<RoomOwnershipSnapshotS2CPayload.Person> people(boolean household) {
        List<RoomOwnershipSnapshotS2CPayload.Person> out = new ArrayList<>();
        for (RoomOwnershipSnapshotS2CPayload.Person person : snapshot.people()) {
            if (homeForScope(person) == household) out.add(person);
        }
        return out;
    }

    private int rosterContentHeight(List<RoomOwnershipSnapshotS2CPayload.Person> household,
                                    List<RoomOwnershipSnapshotS2CPayload.Person> others) {
        return (!household.isEmpty() ? SECTION_H + household.size() * ROW_H : 0)
                + (!others.isEmpty() ? SECTION_H + others.size() * ROW_H : 0);
    }

    private int maxScroll() {
        return Math.max(0,
                rosterContentHeight(people(true), people(false)) - (listH - 6));
    }

    private RoomOwnershipSnapshotS2CPayload.Person personAt(double mouseX, double mouseY) {
        int scrollbarX = listX + listW - Controls.SCROLLBAR_W - 2;
        if (mouseX < listX + 3 || mouseX >= scrollbarX
                || mouseY < listY + 2 || mouseY >= listY + listH - 2) return null;
        int contentY = (int) mouseY - (listY + 3) + scroll;
        List<RoomOwnershipSnapshotS2CPayload.Person> household = people(true);
        List<RoomOwnershipSnapshotS2CPayload.Person> others = people(false);
        if (!household.isEmpty()) {
            if (contentY < SECTION_H) return null;
            contentY -= SECTION_H;
            int householdHeight = household.size() * ROW_H;
            if (contentY < householdHeight) return household.get(contentY / ROW_H);
            contentY -= householdHeight;
        }
        if (!others.isEmpty()) {
            if (contentY < SECTION_H) return null;
            contentY -= SECTION_H;
            if (contentY < others.size() * ROW_H) return others.get(contentY / ROW_H);
        }
        return null;
    }

    private boolean homeForScope(RoomOwnershipSnapshotS2CPayload.Person person) {
        return scope == OwnershipScope.BUILDING ? person.homeInBuilding() : person.homeInRoom();
    }

    private int namedCount() {
        int count = 0;
        for (RoomOwnershipSnapshotS2CPayload.Person person : snapshot.people()) {
            if (selected.contains(person.uuid()) && !homeForScope(person)) count++;
        }
        return count;
    }

    private int allowedCount() {
        int count = 0;
        for (RoomOwnershipSnapshotS2CPayload.Person person : snapshot.people()) {
            if (homeForScope(person) || selected.contains(person.uuid())) count++;
        }
        return count;
    }

    private boolean isPrivate() {
        return privateAccess;
    }

    private void save() {
        if (preview) {
            onClose();
            return;
        }
        List<UUID> named = new ArrayList<>();
        for (RoomOwnershipSnapshotS2CPayload.Person person : snapshot.people()) {
            if (selected.contains(person.uuid()) && !homeForScope(person)) named.add(person.uuid());
        }
        RoomOwnershipSetC2SPayload payload = new RoomOwnershipSetC2SPayload(
                snapshot.tagPos(), snapshot.worksiteId(), scope, privateAccess, named);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
