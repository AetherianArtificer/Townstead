package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.OneTimeHints;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.ParchmentButton;
import com.aetherianartificer.townstead.profession.career.CareerChooseC2SPayload;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import com.aetherianartificer.townstead.profession.career.CareerTrackC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The career screen: a page from the registry the Scribe keeps at the Archives, drawn as that
 * object. The board is an old leaf of parchment; the panel beside it is today's sheet.
 *
 * <p>This class is now orchestration only. The board's contents live in {@link BoardChrome}, where
 * they go in {@link CareerLayout}, the viewport in {@link BoardView}, the right-hand record in
 * {@link RecordPage}, and the index leaves in {@link CareerTabs}. It had grown to two thousand
 * lines holding all five at once, which is why a change to any one of them risked the other four.</p>
 *
 * <p>Entirely a view over the server-rendered {@link CareerGraphS2CPayload}; the only intentions
 * sent are skill and vocation choices.</p>
 */
public final class CareerScreen extends Screen {

    private static final int MARGIN = 6;
    /** The same timber as Field Post's frame. A thinner one read as a hairline, not as furniture. */
    private static final int FRAME_THICK = 6;
    /**
     * Wide enough for a useful reading measure and for a card to hold a meter with its numerals on
     * one row, and no wider. The record is a sidebar to the board, not a peer of it: every pixel it
     * takes comes out of the columns, which are the thing the screen is actually for.
     */
    private static final int PAGE_W = 190;
    /**
     * THE GRID. One spacing unit; every offset on this screen is a multiple of it. Nothing is
     * hand-picked, because while each band had its own constant nothing shared a baseline and the
     * rhythm read as accidental, which it was.
     */
    private static final int UNIT = 4;

    /** The rank rail down the left of the board, which the content never scrolls under. */
    private static final int GUTTER_W = 16;

    private List<CareerGraphS2CPayload.Node> nodes = List.of();
    private final Map<String, List<CareerGraphS2CPayload.Node>> byRoot = new LinkedHashMap<>();
    private final Map<String, CareerGraphS2CPayload.Node> byId = new LinkedHashMap<>();
    private String activeRoot = "";
    private String selectedId = "";
    private String hoveredId = "";
    private String scribeName = "";
    private String notice = "";
    private String payloadAuthority = "";
    private String payloadDate = "";
    private boolean inspect;

    private CareerLayout layout;
    private BoardView board;
    private BoardChrome chrome;
    private RecordPage page;
    private CareerMasthead masthead;
    private StampTool stamp;
    private RecordPage.Result recordLayout;
    private boolean pickerOpen;

    /**
     * When the crest's one-time nudge started, or 0 once it has been shown.
     *
     * <p>Remembered on disk, not in this field. Static made it once per game session, so a player
     * who learned on Monday that the crest opens a list was taught it again on Tuesday.</p>
     */
    private static long hintStart = -1L;

    private Button equipButton;
    private Button trackButton;
    private Button resumeButton;

    private CareerScreen(CareerGraphS2CPayload payload) {
        super(Component.translatable("townstead.career.screen.title", payload.title()));
        store(payload);
    }

    /** Opens the screen, or refreshes the open one after a choose round-trip. */
    public static void openOrUpdate(CareerGraphS2CPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CareerScreen open) {
            open.apply(payload);
        } else {
            minecraft.setScreen(new CareerScreen(payload));
        }
    }

    /**
     * Takes the payload without touching anything that needs a font.
     *
     * <p>Kept separate from {@link #apply} because the constructor runs before {@code font} is
     * assigned, and the layout measures path names. It survived only because no shipped def used
     * paths yet, so the measuring branch never ran.</p>
     */
    private void store(CareerGraphS2CPayload payload) {
        this.nodes = payload.nodes();
        this.scribeName = payload.scribeName();
        this.notice = payload.notice();
        this.payloadAuthority = payload.authority();
        this.payloadDate = payload.dateLine();
        this.inspect = payload.inspect();
        byId.clear();
        // Careers are ordered by their DISPLAYED name, not by whatever order the server happened to
        // walk its registry in. The tab list was reshuffling between opens, which makes the picker
        // unusable as muscle memory: the thing you clicked last time is somewhere else now.
        Map<String, List<CareerGraphS2CPayload.Node>> grouped = new LinkedHashMap<>();
        for (CareerGraphS2CPayload.Node node : nodes) {
            grouped.computeIfAbsent(node.rootId(), key -> new ArrayList<>()).add(node);
            byId.put(node.id(), node);
        }
        List<String> order = new ArrayList<>(grouped.keySet());
        order.sort(java.util.Comparator
                .comparing((String rootId) -> rootLabel(rootId).toLowerCase(java.util.Locale.ROOT))
                .thenComparing(rootId -> rootId));
        byRoot.clear();
        for (String rootId : order) byRoot.put(rootId, grouped.get(rootId));
        if (!byRoot.containsKey(activeRoot)) activeRoot = openingRoot(order);
    }

    /**
     * The career the screen opens on: the work you actually do.
     *
     * <p>Alphabetical order is right for the picker, where you are looking something up, and wrong
     * for the first frame, where the answer is almost always the career you hold. Opening on
     * whatever sorted first meant a cook opened on Blacksmith.</p>
     */
    private String openingRoot(List<String> order) {
        for (String rootId : order) {
            CareerGraphS2CPayload.Node root = byId.get(rootId);
            if (root != null && root.primary()) return rootId;
        }
        return order.isEmpty() ? "" : order.get(0);
    }

    private void apply(CareerGraphS2CPayload payload) {
        store(payload);
        if (layout == null) return;
        rebuild();
    }

    private void rebuild() {
        layout.build(byRoot.getOrDefault(activeRoot, List.of()));
        syncViewport();
        // Open on the career's own record: a skill you had selected survives a relayout instead of
        // snapping back, but a stale one falls back to the career.
        CareerGraphS2CPayload.Node career = CareerLayout.careerNode(
                byRoot.getOrDefault(activeRoot, List.of()));
        if (career != null && (selectedId.isEmpty()
                || (!selectedId.equals(career.id()) && layout.positionOf(selectedId) == null))) {
            selectedId = career.id();
        }
        if (!board.userFramed()) {
            board.frameTo(layout.contentBounds());
        } else {
            board.clamp(layout.contentBounds());
        }
        refreshButtons();
    }

    // ── Regions ────────────────────────────────────────────────────────────

    private int contentX() { return MARGIN + FRAME_THICK; }
    private int contentY() { return MARGIN + FRAME_THICK; }
    private int contentW() { return width - 2 * (MARGIN + FRAME_THICK); }
    private int contentH() { return height - 2 * (MARGIN + FRAME_THICK); }
    private int boardX() { return contentX(); }
    private int boardY() { return contentY() + CareerMasthead.HEIGHT; }
    private int boardW() { return contentW() - PAGE_W - FRAME_THICK; }
    private int boardH() { return contentH() - CareerMasthead.HEIGHT; }
    private int pageX() { return contentX() + contentW() - PAGE_W; }

    /**
     * The foot strip the board may not draw into.
     *
     * <p>The controls hint used to be written straight over the bottom of the board after the
     * scissor had been lifted, so it landed on whatever marks and column headings happened to be
     * down there. Reserving the band and shrinking the viewport means the collision cannot happen
     * rather than usually not happening.</p>
     */
    /**
     * Half size for the controls hint. Minecraft ships one bitmap font, so "smaller" can only mean
     * a scaled pose; a half is the one ratio that stays on whole pixels at even GUI scales instead
     * of resampling the glyphs into mush.
     */
    private static final float FOOT_SCALE = 0.5f;

    private int footH() { return Math.round(font.lineHeight * FOOT_SCALE) + 4; }
    private int boardViewH() { return Math.max(0, boardH() - footH()); }
    private int footY() { return boardY() + boardViewH(); }

    private void syncViewport() {
        board.setViewport(boardX() + GUTTER_W, boardY(), boardW() - GUTTER_W, boardViewH());
    }

    // ── Widgets ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (layout == null) {
            layout = new CareerLayout(font);
            board = new BoardView();
            chrome = new BoardChrome(font, layout, board, UNIT);
            page = new RecordPage(font, UNIT);
            masthead = new CareerMasthead(font, UNIT);
            stamp = new StampTool(font);
            if (hintStart < 0) {
                if (OneTimeHints.seen(OneTimeHints.CAREER_SWITCH)) {
                    hintStart = 0L;
                } else {
                    hintStart = net.minecraft.Util.getMillis() + 500L;
                    // Marked on the way in, not on the way out: whether the player read it is their
                    // business, and a hint that only counts as shown once it fully expires comes
                    // back if they close the screen early.
                    OneTimeHints.markSeen(OneTimeHints.CAREER_SWITCH);
                }
            }
        }
        int buttonX = pageX() + 14;
        int buttonW = PAGE_W - 28;
        trackButton = addRenderableWidget(new ParchmentButton(buttonX,
                height - MARGIN - FRAME_THICK - 85, buttonW, 18,
                Component.translatable("townstead.career.screen.track"), button -> {
            if (!selectedId.isEmpty()) sendTrack(new CareerTrackC2SPayload(selectedId));
        }));
        resumeButton = addRenderableWidget(new ParchmentButton(buttonX,
                height - MARGIN - FRAME_THICK - 64, buttonW, 18,
                Component.translatable("townstead.career.screen.resume_work"), button -> {
            if (!selectedId.isEmpty()) {
                sendVocation(new com.aetherianartificer.townstead.profession.career
                        .CareerVocationC2SPayload(selectedId));
            }
        }));
        equipButton = addRenderableWidget(new ParchmentButton(buttonX,
                height - MARGIN - FRAME_THICK - 43, buttonW, 18,
                Component.translatable("townstead.career.screen.equip"), button -> {
            if (!selectedId.isEmpty()) send(new CareerChooseC2SPayload(selectedId));
        }));
        addRenderableWidget(new ParchmentButton(buttonX,
                height - MARGIN - FRAME_THICK - 22, buttonW, 18,
                CommonComponents.GUI_DONE, button -> onClose()));
        rebuild();
    }

    private void refreshButtons() {
        if (equipButton == null) return;
        CareerGraphS2CPayload.Node selected = nodeById(selectedId);
        boolean skillSelected = selected != null
                && selected.kind() == CareerGraphS2CPayload.KIND_SKILL;
        boolean learnedSkill = skillSelected
                && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED;
        boolean learnableSkill = skillSelected
                && selected.state() == CareerGraphS2CPayload.STATE_READY;
        // Learning is the stamp's job now, so the button only ever says Equip. Leaving both in
        // place would have offered two ways to spend the same point, one of them ceremonial and one
        // of them a click, which makes the ceremony look optional.
        boolean equippable = !inspect && skillSelected && !selected.equipped() && learnedSkill;
        equipButton.visible = equippable;
        equipButton.active = equippable;
        if (equippable) {
            equipButton.setMessage(Component.translatable("townstead.career.screen.equip"));
        }
        // Returning to work whose record already bears your mark. The stamp handles the first
        // admission; this is the plain door back in, and it only exists once that door has been
        // opened once.
        boolean resume = !inspect && StampTool.canTakeUp(selected)
                && selected.stamp().present();
        resumeButton.visible = resume;
        resumeButton.active = resume;

        boolean trackable = !inspect && selected != null
                && selected.kind() == CareerGraphS2CPayload.KIND_ADVANCED
                && selected.state() != CareerGraphS2CPayload.STATE_ACQUIRED;
        trackButton.visible = trackable;
        trackButton.active = trackable;
        if (trackable) {
            trackButton.setMessage(Component.translatable(selected.tracked()
                    ? "townstead.career.screen.untrack" : "townstead.career.screen.track"));
        }
        stackButtons();
    }

    /** Visible action buttons stack upward from Done; hidden ones give their room back. */
    private void stackButtons() {
        int slotY = height - MARGIN - FRAME_THICK - 43;
        if (equipButton.visible) {
            equipButton.setY(slotY);
            slotY -= 21;
        }
        if (resumeButton.visible) {
            resumeButton.setY(slotY);
            slotY -= 21;
        }
        if (trackButton.visible) trackButton.setY(slotY);
    }

    private String rootLabel(String rootId) {
        CareerGraphS2CPayload.Node root = byId.get(rootId);
        return root == null || root.name().isEmpty() ? rootId : root.name();
    }

    private CareerGraphS2CPayload.Node nodeById(String id) {
        if (id.isEmpty()) return null;
        for (CareerGraphS2CPayload.Node node : nodes) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    /** The board itself is the background; no vanilla blur pass (1.21 re-blur family fix). */
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        drawBackdrop(g);
    }
    *///?}

    private void drawBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x88070402);
        FrameRenderer.drawWoodenFrame(g, contentX(), contentY(), contentW(), contentH(), FRAME_THICK);
        // One open journal rather than a black board with a second UI bolted beside it. The map
        // texture supplies paper grain across the whole surface; restrained washes establish the
        // map and the record as regions without changing material halfway across the screen.
        FrameRenderer.drawMapParchment(g, contentX(), contentY(), contentW(), contentH());
        g.fill(boardX(), contentY(), boardX() + boardW(), contentY() + contentH(), 0x18FFF2D2);
        g.fill(pageX(), contentY(), pageX() + PAGE_W, contentY() + contentH(), 0x38FFF8E5);

        // Navigation and career identity share one dark masthead, leaving the map bright enough to
        // scan.
        g.fill(boardX(), contentY(), boardX() + boardW(), boardY(), 0xFF302113);
        g.fillGradient(boardX(), contentY(), boardX() + boardW(), boardY(), 0xFF3D2A17, 0xFF24170D);

        // The seam. It was two translucent washes over parchment, which is why it read as a strip of
        // nothing in particular sitting between two things that were each clearly something. It is
        // now what it is meant to depict: the record is a sheet lying on the desk, so its left edge
        // casts a shadow onto the board and catches the light along its own fold.
        int seam = pageX() - FRAME_THICK;
        g.fill(seam, contentY(), pageX(), contentY() + contentH(), 0x73120C06);
        g.fill(seam, contentY(), seam + 1, contentY() + contentH(), 0x99000000);
        g.fill(pageX() - 1, contentY(), pageX(), contentY() + contentH(), 0xFF8A6A3C);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}
        if (layout == null) return;
        syncViewport();

        List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
        hoveredId = "";
        if (board.contains(mouseX, mouseY)) {
            for (CareerGraphS2CPayload.Node node : tabNodes) {
                int[] local = layout.positionOf(node.id());
                if (local == null) continue;
                if (isOver(mouseX, mouseY, board.screenX(local), board.screenY(local),
                        NodeArt.halfOnScreen(node, board.zoom()) + 2)) {
                    hoveredId = node.id();
                    break;
                }
            }
        }

        g.enableScissor(board.viewX(), board.viewY(),
                board.viewX() + board.viewW(), board.viewY() + board.viewH());
        CareerGraphS2CPayload.Node hovered =
                chrome.draw(g, tabNodes, hoveredId, selectedId, byId);
        board.drawScrollbars(g, layout.contentBounds());
        g.disableScissor();

        // The rank rail sits outside the board's scissor in a strip of its own, so a deep column
        // scrolling past cannot slide underneath it.
        g.enableScissor(boardX(), boardY(), boardX() + GUTTER_W, boardY() + boardViewH());
        chrome.drawRankGutter(g, boardX(), boardY(), GUTTER_W, boardY() + boardViewH());
        g.disableScissor();

        masthead.draw(g, boardX(), contentY(), boardW(), activeRoot, nodeById(activeRoot), nodes,
                mouseX, mouseY, pickerOpen, hintStart);

        drawFoot(g);
        CareerGraphS2CPayload.Node selected = nodeById(selectedId);
        recordLayout = page.draw(g, pageX(), PAGE_W, contentY(), pageViewBottom(),
                selected, nodes, byId, activeRoot, layout, scribeName, inspect, stamp);
        drawStamp(g, selected, mouseX, mouseY);

        if (pickerOpen) {
            // Items render through their own buffer, which is flushed after the flat quads drawn
            // around them, so a node's icon came out ON TOP of a panel drawn later. Flushing first
            // and then lifting the panel out of the item layer is what actually puts it in front;
            // draw order alone does not.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            masthead.drawPicker(g, boardX() + 2, contentY() + CareerMasthead.HEIGHT - 2,
                    new ArrayList<>(byRoot.keySet()), activeRoot, byId, mouseX, mouseY);
            g.pose().popPose();
        } else if (hovered != null) {
            List<FormattedCharSequence> lines = hoverTooltip(hovered);
            if (!lines.isEmpty()) setTooltipForNextRenderPass(lines);
        }
    }

    /** Where the record panel's own coordinate space begins, which marks are stored relative to. */
    private int panelLeft() { return pageX(); }
    private int panelTop() { return contentY(); }

    /**
     * Where a stamp may land: the whole sheet.
     *
     * <p>This used to stop at {@code pageViewBottom()}, which is where the SCROLLING BODY ends, not
     * where the page does. Dropping low on the sheet, which is exactly where somebody would press a
     * registration mark, fell outside and the tool flew silently back to its well.</p>
     */
    private boolean overPanel(double mouseX, double mouseY) {
        return mouseX >= pageX() + 4 && mouseX < pageX() + PAGE_W - 4
                && mouseY >= contentY() + 4 && mouseY < contentY() + contentH() - 4;
    }

    /**
     * The stamp: the mark already pressed on this record, the well it lives in, and the tool while
     * it is in hand.
     *
     * <p>Drawn after the page so a mark sits ON the writing. Free placement is the point: the record
     * reserves no corner for it, because the player chose where it went.</p>
     */
    private void drawStamp(GuiGraphics g, CareerGraphS2CPayload.Node selected, int mouseX, int mouseY) {
        if (selected == null || recordLayout == null) return;
        g.flush();
        stamp.drawMark(g, panelLeft(), panelTop(), selected.stamp(), PAGE_W - 28);
        // Unconditional: the animation is local feedback for YOUR press, and it self-expires. It
        // used to be gated on the server having echoed the mark back, so the tool vanished on
        // release and nothing moved until the round trip landed.
        stamp.drawPressAnimation(g);
        if (!recordLayout.wellShown()) return;
        // The well's position comes from the record's pinned head rather than being computed here,
        // so the tool and its slot can never drift apart when the head's height changes.
        if (!stamp.held()) {
            stamp.drawWell(g, recordLayout.wellX(), recordLayout.wellY(), stampReady(selected));
        } else {
            stamp.drawHeld(g, overPanel(mouseX, mouseY));
        }
    }

    /** Unspent points on the career this record belongs to. */
    private int availablePoints() {
        CareerGraphS2CPayload.Node career = nodeById(activeRoot);
        return career == null ? 0 : career.points();
    }

    /**
     * Whether the stamp will lift for this record.
     *
     * <p>Only a skill has a price. A career node's own {@code points()} is the pool it has LEFT to
     * spend, so comparing it against the same pool would have made taking up work conditional on a
     * number that has nothing to do with it.</p>
     */
    private boolean stampReady(CareerGraphS2CPayload.Node node) {
        if (node == null) return false;
        return node.kind() != CareerGraphS2CPayload.KIND_SKILL
                || node.points() <= availablePoints();
    }

    /**
     * The reserved foot band: why the last action was refused, or failing that the controls hint.
     *
     * <p>The server used to answer refusals in chat, which this screen's own backdrop covers, so a
     * refusal looked like a dead button.</p>
     */
    private void drawFoot(GuiGraphics g) {
        int y = footY();
        int h = footH();
        // The band is part of the desk, not a strip of parchment showing under it. It carries one
        // line and is sized to that line: it was three units taller than its own text, which made a
        // permanent hint look like a region of the screen.
        g.fill(boardX(), y, boardX() + boardW(), y + h, Palette.DESK_EDGE);
        if (!notice.isEmpty()) {
            // A refusal stays full size and grows UPWARD out of the band, over a board that is not
            // being read at the moment anyway. Shrinking the band to fit the hint must not shrink
            // the one message on this screen a player has to be able to read.
            int textWidth = font.width(notice);
            int centerX = boardX() + boardW() / 2;
            int boxH = font.lineHeight + 6;
            int top = y + h - boxH;
            g.fill(centerX - textWidth / 2 - 6, top, centerX + textWidth / 2 + 6, y + h, 0xE0140F08);
            g.fill(centerX - textWidth / 2 - 6, top, centerX + textWidth / 2 + 6, top + 1,
                    0xFFC46A4A);
            g.drawString(font, notice, centerX - textWidth / 2, top + 3, 0xFFE8C7A0, false);
            return;
        }
        String help = Component.translatable("townstead.career.screen.controls").getString();
        g.pose().pushPose();
        g.pose().translate(boardX() + UNIT, y + 2, 0);
        g.pose().scale(FOOT_SCALE, FOOT_SCALE, 1f);
        g.drawString(font, help, 0, 0, 0xFF9A7E50, false);
        g.pose().popPose();
    }

    /**
     * A compact hover cue. Full effects belong on the selected record at right; repeating them in a
     * tooltip covered the very branch a player was trying to compare.
     */
    private List<FormattedCharSequence> hoverTooltip(CareerGraphS2CPayload.Node node) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (node.kind() == CareerGraphS2CPayload.KIND_COMBO) {
            lines.addAll(font.split(Component.literal(node.name()), 140));
            lines.addAll(font.split(Component.translatable("townstead.career.screen.combo")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), 140));
        } else if (node.kind() == CareerGraphS2CPayload.KIND_SKILL) {
            lines.addAll(font.split(Component.literal(node.name()), 140));
            if (node.path().present()) {
                lines.addAll(font.split(Component.translatable(
                                "townstead.career.screen.path_member", node.path().name())
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), 140));
            }
            Component status = node.equipped()
                    ? Component.translatable("townstead.career.screen.state.equipped")
                    : node.state() == CareerGraphS2CPayload.STATE_ACQUIRED
                            ? Component.translatable("townstead.career.screen.state.acquired")
                    : node.points() > 0
                            ? Component.translatable("townstead.career.screen.cost", node.points())
                            : Component.translatable("townstead.career.screen.state.ready");
            lines.addAll(font.split(status.copy()
                    .withStyle(net.minecraft.ChatFormatting.GOLD), 140));
        } else {
            return List.of();
        }
        if (!node.id().equals(selectedId)) {
            lines.add(Component.translatable("townstead.career.screen.click_details")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        return lines;
    }

    /**
     * Selecting a mark from a record row, and bringing the board to it.
     *
     * <p>The record used to be a dead end you navigated out of by hand. A row that names something
     * you can register has to be able to take you there, or the shortcut is only a description.</p>
     */
    private boolean jumpTo(String nodeId) {
        CareerGraphS2CPayload.Node node = byId.get(nodeId);
        if (node == null) return false;
        selectedId = nodeId;
        page.resetScroll();
        int[] local = layout.positionOf(nodeId);
        if (local != null) {
            syncViewport();
            board.centreOn(local, layout.contentBounds());
        }
        refreshButtons();
        return true;
    }

    /** The page extends down to the topmost visible button, minus the signature band. */
    private int pageViewBottom() {
        int top = height - MARGIN - FRAME_THICK - 22;
        if (equipButton != null && equipButton.visible) top = Math.min(top, equipButton.getY());
        if (resumeButton != null && resumeButton.visible) {
            top = Math.min(top, resumeButton.getY());
        }
        if (trackButton != null && trackButton.visible) top = Math.min(top, trackButton.getY());
        return top - (scribeName.isEmpty() ? 6 : 16);
    }

    // ── Input ──────────────────────────────────────────────────────────────

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int half) {
        return Math.abs(mouseX - x) <= half && Math.abs(mouseY - y) <= half;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && layout != null && recordLayout != null) {
            CareerGraphS2CPayload.Node selected = nodeById(selectedId);
            if (recordLayout.wellShown() && !stamp.held()
                    && stampReady(selected)
                    && stamp.overWell(mouseX, mouseY)) {
                stamp.pickUp(mouseX, mouseY);
                return true;
            }
            for (RecordPage.Jump jump : recordLayout.jumps()) {
                if (mouseX >= jump.x() && mouseX < jump.x() + jump.w()
                        && mouseY >= jump.y() && mouseY < jump.y() + jump.h()) {
                    if (jumpTo(jump.nodeId())) return true;
                }
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || layout == null) return false;
        syncViewport();

        List<String> roots = new ArrayList<>(byRoot.keySet());
        if (pickerOpen) {
            String picked = masthead.pickerHit(mouseX, mouseY, roots,
                    boardX() + 2, contentY() + CareerMasthead.HEIGHT - 2);
            pickerOpen = false;
            if (picked != null && !picked.equals(activeRoot)) {
                activeRoot = picked;
                selectedId = "";
                board.setUserFramed(false);
                page.resetScroll();
                rebuild();
            }
            return true;
        }
        if (masthead.overCrest(mouseX, mouseY)) {
            // Opening the list is also the answer to the nudge, so it stops.
            hintStart = 0L;
            pickerOpen = roots.size() > 1;
            if (!pickerOpen) {
                CareerGraphS2CPayload.Node career = nodeById(activeRoot);
                if (career != null) {
                    selectedId = career.id();
                    page.resetScroll();
                    refreshButtons();
                }
            }
            return true;
        }
        // The rest of the masthead IS the career's row, so it selects the career: otherwise, once
        // you clicked any skill there was no way back to its own record or to the button that takes
        // up the work.
        if (mouseY >= contentY() && mouseY < boardY() && mouseX < pageX()) {
            CareerGraphS2CPayload.Node career = nodeById(activeRoot);
            if (career != null) {
                selectedId = career.id();
                page.resetScroll();
                refreshButtons();
            }
            return true;
        }
        if (board.contains(mouseX, mouseY)) {
            for (CareerGraphS2CPayload.Node node : byRoot.getOrDefault(activeRoot, List.of())) {
                int[] local = layout.positionOf(node.id());
                if (local == null) continue;
                if (isOver((int) mouseX, (int) mouseY, board.screenX(local), board.screenY(local),
                        NodeArt.halfOnScreen(node, board.zoom()) + 2)) {
                    selectedId = node.id();
                    page.resetScroll();
                    refreshButtons();
                    return true;
                }
            }
            board.setDragging(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (layout != null && stamp.held() && button == 0) {
            stamp.moveTo(mouseX, mouseY);
            return true;
        }
        if (layout != null && board.dragging() && button == 0) {
            board.panBy(dragX, dragY, layout.contentBounds());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (board != null) board.setDragging(false);
        if (layout != null && stamp.held() && button == 0) {
            CareerGraphS2CPayload.Node selected = nodeById(selectedId);
            if (overPanel(mouseX, mouseY) && StampTool.available(selected, inspect)) {
                // Position is stored relative to the panel, not the screen, so the mark survives a
                // resize or a change of GUI scale. The server re-validates the press itself.
                sendStamp(new com.aetherianartificer.townstead.profession.career
                        .CareerStampC2SPayload(selected.id(),
                        stamp.centreX() - panelLeft(), stamp.centreY() - panelTop(),
                        stamp.rotation()));
                stamp.press(stamp.centreX(), stamp.centreY());
            } else {
                stamp.reset();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollAt(mouseX, mouseY, deltaY);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollAt(mouseX, mouseY, delta);
        return true;
    }
    *///?}

    /**
     * Both halves scroll. The wheel moves the board a whole lattice row at a time so scrolling lands
     * on the grid rather than between it; hold shift to move sideways, which is what a board of
     * side-by-side columns needs. Dragging still pans freely.
     */
    private void scrollAt(double mouseX, double mouseY, double delta) {
        if (layout == null) return;
        // A stamp in hand takes the wheel: tilting it is part of pressing it, and a level mark
        // looks machine-applied when the whole point is that a hand did it.
        if (stamp.held()) {
            stamp.rotate(delta);
            return;
        }
        if (mouseX >= pageX()) {
            // Only the body scrolls; the pinned head is not part of the viewport it moves within.
            int bodyTop = contentY() + page.headHeight(nodeById(selectedId), inspect);
            page.scrollBy(delta, pageViewBottom() - bodyTop);
            return;
        }
        syncViewport();
        // A third of a band per notch: enough to make progress through a deep career, small enough
        // that a cluster never jumps past you.
        double step = delta * (CareerLayout.BAND_H / 3.0) * board.zoom();
        board.panBy(hasShiftDown() ? step : 0, hasShiftDown() ? 0 : step, layout.contentBounds());
    }

    private static void send(CareerChooseC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private static void sendStamp(
            com.aetherianartificer.townstead.profession.career.CareerStampC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private static void sendVocation(
            com.aetherianartificer.townstead.profession.career.CareerVocationC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private static void sendTrack(CareerTrackC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
