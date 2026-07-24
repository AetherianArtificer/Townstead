package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.gui.fieldpost.FrameRenderer;
import com.aetherianartificer.townstead.profession.career.CareerChooseC2SPayload;
import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The career screen: careers as wooden plaques hanging on a near-dark plank wall, joined by
 * stitched ropes, with skills dangling beneath as market tags and a parchment page for
 * details. Careers select via creative-inventory-style icon tabs fused to the board's top
 * edge. Shares the Field Post / Calendar family primitives ({@link FrameRenderer}) so all
 * Townstead GUIs read as one workshop. Entirely a view over the server-rendered
 * {@link CareerGraphS2CPayload}; the only intentions sent are skill and vocation choices.
 */
public final class CareerScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int FRAME_THICK = 6;
    private static final int PAGE_W = 142;
    private static final int TAB_H = 24;

    // Ink on parchment (Calendar family palette)
    private static final int INK_HEADER = 0xFF1F1305;
    private static final int INK_TEXT = 0xFF3A2410;
    private static final int INK_DIM = 0xFF6E5430;
    private static final int INK_GOOD = 0xFF2E5E1E;
    private static final int INK_BAD = 0xFF8C2E1A;
    private static final int INK_ACCENT = 0xFF8A5A12;
    private static final int BAR_TRACK = 0xFFB8985C;
    private static final int BAR_FILL = 0xFFFFB347;

    // Board (light text on near-dark planks)
    private static final int LABEL_LIGHT = 0xFFEEDDB0;
    private static final int LABEL_DIM = 0xFFA8946E;
    private static final int PLAQUE_FILL = 0xFFD8C28F;
    private static final int PLAQUE_BORDER = 0xFF8B6F47;
    private static final int PLAQUE_LOCKED = 0xFF9A8560;
    private static final int PLAQUE_HIDDEN = 0xFF3A2C1C;
    private static final int ROPE = 0xFF7E5C34;
    private static final int ROPE_LIT = 0xFFE0BC55;
    private static final int GOLD = 0xFFC9A227;
    private static final int WARM_GLOW = 0xFFFFE680;
    private static final int HOVER_OUTLINE = 0x90FFF6DC;
    private static final int SELECT_OUTLINE = 0xF0FFFFFF;
    private static final int WAX_SEAL = 0xFFA02020;
    private static final int WAX_RIM = 0xFF701010;
    private static final int TAB_ACTIVE = 0xFFEFE0B8;
    private static final int TAB_IDLE = 0xFFB59A6C;
    private static final int TAB_HOVER = 0xFFD5BC8A;
    private static final int TAB_BORDER = 0xFF5E4A30;
    private static final int NAV_INK = 0xFF3E2510;

    // Board wall: set false for the untextured near-black fallback.
    private static final boolean WALL_TEXTURED = true;

    private List<CareerGraphS2CPayload.Node> nodes;
    private final Map<String, List<CareerGraphS2CPayload.Node>> byRoot = new LinkedHashMap<>();
    private final Map<String, int[]> positions = new LinkedHashMap<>();
    private String activeRoot = "";
    private String selectedId = "";
    private String hoveredId = "";
    private String scribeName = "";
    private String skillViewCareer = "";
    private boolean inspect;
    private double panX;
    private double panY;
    private double pageScroll;
    private int pageContentHeight;
    private boolean dragging;
    private Button equipButton;
    private Button vocationButton;
    private Button trackButton;
    private Button skillsButton;

    private CareerScreen(CareerGraphS2CPayload payload) {
        super(Component.translatable("townstead.career.screen.title", payload.title()));
        apply(payload);
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

    private void apply(CareerGraphS2CPayload payload) {
        this.nodes = payload.nodes();
        this.scribeName = payload.scribeName();
        this.inspect = payload.inspect();
        CareerGraphS2CPayload.Node viewed = nodeById(skillViewCareer);
        if (viewed == null || viewed.state() != CareerGraphS2CPayload.STATE_ACQUIRED) {
            skillViewCareer = "";
        }
        byRoot.clear();
        for (CareerGraphS2CPayload.Node node : nodes) {
            byRoot.computeIfAbsent(node.rootId(), key -> new ArrayList<>()).add(node);
        }
        if (!byRoot.containsKey(activeRoot)) {
            activeRoot = byRoot.isEmpty() ? "" : byRoot.keySet().iterator().next();
        }
        layoutActiveRoot();
        refreshEquipButton();
    }

    // ── Regions ────────────────────────────────────────────────────────────

    private int contentX() { return MARGIN + FRAME_THICK; }
    private int contentY() { return MARGIN + FRAME_THICK; }
    private int contentW() { return width - 2 * (MARGIN + FRAME_THICK); }
    private int contentH() { return height - 2 * (MARGIN + FRAME_THICK); }
    private int boardX() { return contentX(); }
    private int boardY() { return contentY() + TAB_H; }
    private int boardW() { return contentW() - PAGE_W - FRAME_THICK; }
    private int boardH() { return contentH() - TAB_H; }
    private int pageX() { return contentX() + contentW() - PAGE_W; }

    private int boardCenterX() { return boardX() + boardW() / 2; }
    private int screenX(int[] local) { return boardCenterX() + local[0] + (int) panX; }
    private int screenY(int[] local) { return boardY() + 60 + local[1] + (int) panY; }

    // ── Layout: plaques hang downward from the root ────────────────────────

    private void layoutActiveRoot() {
        positions.clear();
        List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
        if (!skillViewCareer.isEmpty()) {
            layoutLedger(tabNodes);
            return;
        }
        CareerGraphS2CPayload.Node root = tabNodes.stream()
                .filter(node -> node.kind() == CareerGraphS2CPayload.KIND_ROOT
                        || node.kind() == CareerGraphS2CPayload.KIND_ADVANCED)
                .findFirst().orElse(null);
        if (root == null) return;
        positions.put(root.id(), new int[]{0, 0});
        placeChildren(tabNodes, root.id(), 0, 0, 90f);

        // Combo Skills: shared plaques in a band beneath the career, joined to this tab
        // because one of their thresholds names it.
        List<CareerGraphS2CPayload.Node> combos = new ArrayList<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_COMBO
                    && !positions.containsKey(node.id())) {
                combos.add(node);
            }
        }
        int comboSpacing = 78;
        int comboStartX = -(combos.size() - 1) * comboSpacing / 2;
        for (int i = 0; i < combos.size(); i++) {
            positions.put(combos.get(i).id(), new int[]{comboStartX + i * comboSpacing, 96});
        }
    }

    // ── Skill ledger: one shelf per level, plaques resting on their shelf ──

    private static final int LEDGER_ROW_H = 52;
    private int ledgerContentH;
    private int ledgerContentW;

    private void layoutLedger(List<CareerGraphS2CPayload.Node> tabNodes) {
        CareerGraphS2CPayload.Node career = nodeById(skillViewCareer);
        java.util.Map<Integer, List<CareerGraphS2CPayload.Node>> byLevel = new java.util.TreeMap<>();
        int maxLevel = career == null ? 1 : Math.max(1, career.maxTier());
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL
                    || !node.parentId().equals(skillViewCareer)) {
                continue;
            }
            int level = Math.max(1, node.tier());
            maxLevel = Math.max(maxLevel, level);
            byLevel.computeIfAbsent(level, key -> new ArrayList<>()).add(node);
        }
        // Shelf spacing follows the widest name on the shelf so labels never collide.
        net.minecraft.client.gui.Font measure = Minecraft.getInstance().font;
        int startX = 130 - boardW() / 2;
        int widest = 0;
        for (java.util.Map.Entry<Integer, List<CareerGraphS2CPayload.Node>> entry
                : byLevel.entrySet()) {
            List<CareerGraphS2CPayload.Node> row = entry.getValue();
            int spacing = 60;
            for (CareerGraphS2CPayload.Node node : row) {
                spacing = Math.max(spacing, measure.width(node.name()) + 14);
            }
            for (int i = 0; i < row.size(); i++) {
                positions.put(row.get(i).id(),
                        new int[]{startX + i * spacing, (entry.getKey() - 1) * LEDGER_ROW_H + 20});
            }
            widest = Math.max(widest, 130 + (row.size() - 1) * spacing + spacing / 2);
        }
        ledgerContentH = maxLevel * LEDGER_ROW_H + 40;
        ledgerContentW = widest;
    }

    /** Panning always keeps some of the content on the board, in either view. */
    private void clampBoardPan() {
        if (!skillViewCareer.isEmpty()) {
            clampLedgerPan();
            return;
        }
        if (positions.isEmpty()) return;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] local : positions.values()) {
            minX = Math.min(minX, local[0]);
            maxX = Math.max(maxX, local[0]);
            minY = Math.min(minY, local[1]);
            maxY = Math.max(maxY, local[1]);
        }
        panX = Mth.clamp(panX,
                boardX() + 50 - (boardCenterX() + maxX),
                boardX() + boardW() - 50 - (boardCenterX() + minX));
        panY = Mth.clamp(panY,
                boardY() + 80 - (boardY() + 60 + maxY),
                boardY() + boardH() - 50 - (boardY() + 60 + minY));
    }

    private void clampLedgerPan() {
        if (skillViewCareer.isEmpty()) return;
        int visibleH = boardH() - 76;
        panY = Mth.clamp(panY, Math.min(0, visibleH - ledgerContentH), 0);
        int visibleW = boardW() - 20;
        panX = Mth.clamp(panX, Math.min(0, visibleW - ledgerContentW), 0);
    }

    private int ledgerMaxLevel(CareerGraphS2CPayload.Node career,
                               List<CareerGraphS2CPayload.Node> tabNodes) {
        int max = Math.max(1, career.maxTier());
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_SKILL
                    && node.parentId().equals(skillViewCareer)) {
                max = Math.max(max, node.tier());
            }
        }
        return max;
    }

    /** A shelf's rank name: any resident skill carries it server-resolved; else a fallback. */
    private String shelfLabel(int level, List<CareerGraphS2CPayload.Node> tabNodes) {
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_SKILL
                    && node.parentId().equals(skillViewCareer)
                    && node.tier() == level && !node.rankName().isEmpty()) {
                return node.rankName();
            }
        }
        return level <= 5
                ? Component.translatable("townstead.profession.level." + level).getString()
                : Component.translatable("townstead.career.screen.level_n", level).getString();
    }

    private void placeChildren(List<CareerGraphS2CPayload.Node> tabNodes, String parentId,
                               int parentX, int parentY, float directionDeg) {
        List<CareerGraphS2CPayload.Node> careers = new ArrayList<>();
        List<CareerGraphS2CPayload.Node> skills = new ArrayList<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (!node.parentId().equals(parentId)) continue;
            (node.kind() == CareerGraphS2CPayload.KIND_SKILL ? skills : careers).add(node);
        }
        // Skills never sit on the careers board; they live in the ledger view.
        for (int i = 0; i < careers.size(); i++) {
            CareerGraphS2CPayload.Node child = careers.get(i);
            float spread = careers.size() == 1 ? 0f
                    : Mth.lerp(i / (float) (careers.size() - 1), -55f, 55f);
            float angle = (float) Math.toRadians(directionDeg + spread);
            int hash = child.id().hashCode();
            int x = parentX + Math.round(Mth.cos(angle) * 82) + ((hash >> 3 & 7) - 3);
            int y = parentY + Math.round(Mth.sin(angle) * 82) + ((hash >> 7 & 3) - 1);
            positions.put(child.id(), new int[]{x, y});
            placeChildren(tabNodes, child.id(), x, y, 90f + spread * 0.4f);
        }
    }

    // ── Widgets ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        trackButton = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.career.screen.track"),
                        button -> {
                            if (!selectedId.isEmpty()) {
                                sendTrack(new com.aetherianartificer.townstead.profession.career
                                        .CareerTrackC2SPayload(selectedId));
                            }
                        })
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 96, PAGE_W - 12, 20).build());
        vocationButton = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.career.screen.take_up"),
                        button -> {
                            if (!selectedId.isEmpty()) {
                                sendVocation(new com.aetherianartificer.townstead.profession.career
                                        .CareerVocationC2SPayload(selectedId));
                            }
                        })
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 72, PAGE_W - 12, 20).build());
        skillsButton = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.career.screen.skills"),
                        button -> {
                            if (!selectedId.isEmpty()) {
                                skillViewCareer = selectedId;
                                panX = 0;
                                panY = 0;
                                pageScroll = 0;
                                layoutActiveRoot();
                                refreshEquipButton();
                                townstead$pageTurn();
                            }
                        })
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 96, PAGE_W - 12, 20).build());
        equipButton = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.career.screen.equip"),
                        button -> {
                            if (!selectedId.isEmpty()) send(new CareerChooseC2SPayload(selectedId));
                        })
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 48, PAGE_W - 12, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 24, PAGE_W - 12, 20).build());
        layoutActiveRoot();
        refreshEquipButton();
    }

    private void refreshEquipButton() {
        if (equipButton == null) return;
        CareerGraphS2CPayload.Node selected = nodeById(selectedId);
        boolean skillSelected = selected != null
                && selected.kind() == CareerGraphS2CPayload.KIND_SKILL;
        boolean learnedSkill = skillSelected
                && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED;
        boolean learnableSkill = skillSelected
                && selected.state() == CareerGraphS2CPayload.STATE_READY;
        boolean equippable = !inspect && skillSelected && !selected.equipped()
                && (learnedSkill || learnableSkill);
        equipButton.visible = equippable;
        equipButton.active = equippable;
        if (equippable) {
            equipButton.setMessage(learnedSkill
                    ? Component.translatable("townstead.career.screen.equip")
                    : selected.points() > 0
                            ? Component.translatable("townstead.career.screen.learn", selected.points())
                            : Component.translatable("townstead.career.screen.learn_free"));
        }
        if (vocationButton != null) {
            // Server re-validates; this only mirrors the eligibility rule for visibility.
            boolean takeUp = !inspect && selected != null && !selected.primary()
                    && (selected.kind() == CareerGraphS2CPayload.KIND_ROOT
                            && selected.state() != CareerGraphS2CPayload.STATE_HIDDEN
                    || selected.kind() == CareerGraphS2CPayload.KIND_ADVANCED
                            && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED);
            vocationButton.visible = takeUp;
            vocationButton.active = takeUp;
        }
        if (skillsButton != null) {
            boolean viewable = skillViewCareer.isEmpty() && selected != null
                    && selected.kind() != CareerGraphS2CPayload.KIND_SKILL
                    && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED
                    && hasSkills(selected.id());
            skillsButton.visible = viewable;
            skillsButton.active = viewable;
        }
        if (trackButton != null) {
            boolean trackable = !inspect && selected != null
                    && selected.kind() == CareerGraphS2CPayload.KIND_ADVANCED
                    && selected.state() != CareerGraphS2CPayload.STATE_ACQUIRED
                    && selected.state() != CareerGraphS2CPayload.STATE_HIDDEN;
            trackButton.visible = trackable;
            trackButton.active = trackable;
            if (trackable) {
                trackButton.setMessage(Component.translatable(selected.tracked()
                        ? "townstead.career.screen.untrack" : "townstead.career.screen.track"));
            }
        }
        stackButtons();
    }

    private static void townstead$pageTurn() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    private boolean hasSkills(String careerId) {
        for (CareerGraphS2CPayload.Node node : nodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_SKILL
                    && node.parentId().equals(careerId)) {
                return true;
            }
        }
        return false;
    }

    /** Visible action buttons stack upward from Done; hidden ones give their room back. */
    private void stackButtons() {
        int slotY = height - MARGIN - FRAME_THICK - 48;
        if (equipButton != null && equipButton.visible) {
            equipButton.setY(slotY);
            slotY -= 24;
        }
        if (skillsButton != null && skillsButton.visible) {
            skillsButton.setY(slotY);
            slotY -= 24;
        }
        if (vocationButton != null && vocationButton.visible) {
            vocationButton.setY(slotY);
            slotY -= 24;
        }
        if (trackButton != null && trackButton.visible) {
            trackButton.setY(slotY);
        }
    }

    private static void sendTrack(
            com.aetherianartificer.townstead.profession.career.CareerTrackC2SPayload payload) {
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
        townstead$drawBackdrop(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        townstead$drawBackdrop(g);
    }
    *///?}

    private void townstead$drawBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x70000000);
        FrameRenderer.drawWoodenFrame(g, contentX(), contentY(), contentW(), contentH(), FRAME_THICK);
        int bx = boardX();
        int by = contentY();
        int bw = boardW();
        int bh = contentH();
        if (WALL_TEXTURED) {
            // Dark oak under a heavy tint: a whisper of grain on a near-black wall.
            FrameRenderer.tileTexture(g, FrameRenderer.PLANK_DARK, bx, by, bw, bh);
            g.fill(bx, by, bx + bw, by + bh, 0xB8060402);
        } else {
            g.fillGradient(bx, by, bx + bw, by + bh, 0xFF141210, 0xFF060504);
        }
        FrameRenderer.drawMapParchment(g, pageX(), contentY(), PAGE_W, contentH());
        // Divider post between board and page
        FrameRenderer.tileTexture(g, FrameRenderer.PLANK_DARK,
                pageX() - FRAME_THICK, contentY(), FRAME_THICK, contentH());
        g.fill(pageX() - FRAME_THICK, contentY(), pageX() - FRAME_THICK + 1, contentY() + contentH(),
                FrameRenderer.FRAME_HIGHLIGHT);
        g.fill(pageX() - 1, contentY(), pageX(), contentY() + contentH(), FrameRenderer.FRAME_SHADOW);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}

        List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
        hoveredId = "";
        if (mouseX >= boardX() && mouseX < boardX() + boardW()
                && mouseY >= boardY() && mouseY < boardY() + boardH()) {
            for (CareerGraphS2CPayload.Node node : tabNodes) {
                int[] local = positions.get(node.id());
                if (local == null) continue;
                if (isOver(mouseX, mouseY, screenX(local), screenY(local), halfSizeOf(node) + 2)) {
                    hoveredId = node.id();
                    break;
                }
            }
        }

        g.enableScissor(boardX(), boardY(), boardX() + boardW(), boardY() + boardH());
        if (!skillViewCareer.isEmpty()) {
            drawLedger(g, tabNodes);
        } else {
            for (CareerGraphS2CPayload.Node node : tabNodes) {
                int[] childPos = positions.get(node.id());
                int[] parentPos = positions.get(node.parentId());
                if (childPos == null || parentPos == null) continue;
                boolean lit = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped();
                drawRope(g, screenX(parentPos), screenY(parentPos),
                        screenX(childPos), screenY(childPos), lit);
            }
            for (CareerGraphS2CPayload.Node node : tabNodes) {
                int[] local = positions.get(node.id());
                if (local == null) continue;
                int x = screenX(local);
                int y = screenY(local);
                drawPlaque(g, node, x, y, node.id().equals(hoveredId));
                if (node.state() == CareerGraphS2CPayload.STATE_ACQUIRED && node.points() > 0) {
                    townstead$drawNudge(g, x + halfSizeOf(node), y - halfSizeOf(node));
                }
                maybeHoverTooltip(node);
                String label = node.state() == CareerGraphS2CPayload.STATE_HIDDEN
                        ? Component.translatable("townstead.career.screen.unknown").getString()
                        : node.name();
                int labelY = y + halfSizeOf(node) + (node.primary() ? 7 : 4);
                g.drawString(font, label, x - font.width(label) / 2, labelY,
                        node.state() <= CareerGraphS2CPayload.STATE_LOCKED ? LABEL_DIM : LABEL_LIGHT);
            }
        }
        g.disableScissor();

        drawTabs(g, mouseX, mouseY);
        drawDetailsPage(g);
    }

    private void drawLedger(GuiGraphics g, List<CareerGraphS2CPayload.Node> tabNodes) {
        CareerGraphS2CPayload.Node career = nodeById(skillViewCareer);
        if (career == null) return;
        int maxLevel = ledgerMaxLevel(career, tabNodes);
        int leftX = boardX() + 14;
        int rightX = boardX() + boardW() - 14;
        java.util.Map<Integer, Integer> countByLevel = new java.util.HashMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() == CareerGraphS2CPayload.KIND_SKILL
                    && node.parentId().equals(skillViewCareer)) {
                countByLevel.merge(Math.max(1, node.tier()), 1, Integer::sum);
            }
        }
        for (int level = 1; level <= maxLevel; level++) {
            int rowTop = boardY() + 60 + (level - 1) * LEDGER_ROW_H + (int) panY;
            boolean reached = career.tier() >= level;
            int shelfY = rowTop + 32;
            g.fill(leftX, shelfY, rightX, shelfY + 2, reached ? 0xFF3A2A18 : 0x703A2A18);
            g.fill(leftX, shelfY, rightX, shelfY + 1, reached ? 0x38FFFFFF : 0x18FFFFFF);
            String label = shelfLabel(level, tabNodes);
            g.drawString(font, label, leftX + 2, rowTop + 14,
                    level == career.tier() ? 0xFFE8A33C : reached ? LABEL_LIGHT : LABEL_DIM);
            if (level == career.tier() && career.xpToNext() > 0) {
                // How close the next shelf is, answered in place.
                drawBar(g, leftX + 2, rowTop + 24, 62,
                        career.xp() / (float) (career.xp() + career.xpToNext()), BAR_FILL);
            }
            if (countByLevel.getOrDefault(level, 0) == 0) {
                g.drawString(font,
                        Component.translatable("townstead.career.screen.empty_shelf").getString(),
                        boardX() + 130, rowTop + 14, LABEL_DIM);
            }
        }
        drawLedgerGroups(g, tabNodes);
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] local = positions.get(node.id());
            if (local == null || node.kind() != CareerGraphS2CPayload.KIND_SKILL) continue;
            int x = screenX(local);
            int y = screenY(local);
            drawPlaque(g, node, x, y, node.id().equals(hoveredId));
            String label = node.name();
            g.drawString(font, label, x - font.width(label) / 2, y + halfSizeOf(node) + 5,
                    node.state() <= CareerGraphS2CPayload.STATE_LOCKED ? LABEL_DIM : LABEL_LIGHT);
            maybeHoverTooltip(node);
        }
    }

    /** Skills sharing a slot read as one choice: a shared backing with "or" between them. */
    private void drawLedgerGroups(GuiGraphics g, List<CareerGraphS2CPayload.Node> tabNodes) {
        java.util.Map<String, List<int[]>> members = new java.util.LinkedHashMap<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (node.kind() != CareerGraphS2CPayload.KIND_SKILL || node.group().isEmpty()) continue;
            int[] local = positions.get(node.id());
            if (local == null) continue;
            members.computeIfAbsent(node.tier() + "|" + node.group(),
                    key -> new ArrayList<>()).add(local);
        }
        String or = Component.translatable("townstead.career.screen.or").getString();
        for (List<int[]> group : members.values()) {
            if (group.size() < 2) continue;
            group.sort(java.util.Comparator.comparingInt(local -> local[0]));
            int y = screenY(group.get(0));
            int minX = screenX(group.get(0));
            int maxX = screenX(group.get(group.size() - 1));
            g.fill(minX - 15, y - 14, maxX + 15, y + 13, 0x16FFD9A0);
            drawOutline(g, minX - 15, y - 14, maxX + 15, y + 13, 0x2EFFD9A0);
            for (int i = 0; i + 1 < group.size(); i++) {
                int mid = (screenX(group.get(i)) + screenX(group.get(i + 1))) / 2;
                g.drawString(font, or, mid - font.width(or) / 2, y - 4, LABEL_DIM);
            }
        }
    }

    private int backLinkWidth() {
        return font.width(Component.translatable("townstead.career.screen.back").getString()) + 4;
    }

    private boolean overBackLink(double mouseX, double mouseY) {
        return !skillViewCareer.isEmpty()
                && mouseX >= boardX() + 8 && mouseX < boardX() + 8 + backLinkWidth()
                && mouseY >= boardY() + 4 && mouseY < boardY() + 18;
    }

    // ── Tabs: creative-inventory-style icon tabs fused to the board edge ───

    private static final int TAB_W = 24;
    private static final int TAB_GAP = -1; // folder overlap: neighbors share a border
    private static final int TAB_ARROW_W = 13;
    private int tabScroll;

    private record TabStrip(List<String> visible, List<Integer> tabX, List<Integer> tabW,
                            boolean canLeft, boolean canRight, int leftArrowX, int rightArrowX) {}

    /** Lays out the visible tab window; shared by draw and click so geometry never drifts. */
    private TabStrip layoutTabs() {
        List<String> roots = new ArrayList<>(byRoot.keySet());
        tabScroll = Mth.clamp(tabScroll, 0, Math.max(0, roots.size() - 1));
        int total = roots.size() * (TAB_W + TAB_GAP);
        boolean overflow = total > boardW() - 4;
        int left = boardX() + 2;
        int right = boardX() + boardW() - 2;
        int leftArrowX = left;
        int rightArrowX = right - TAB_ARROW_W;
        if (overflow) {
            left += TAB_ARROW_W + 2;
            right -= TAB_ARROW_W + 2;
        } else {
            tabScroll = 0;
        }
        List<String> visible = new ArrayList<>();
        List<Integer> xs = new ArrayList<>();
        List<Integer> ws = new ArrayList<>();
        int x = left;
        boolean truncated = false;
        for (int i = tabScroll; i < roots.size(); i++) {
            int tabWidth = TAB_W;
            if (x + tabWidth > right) {
                truncated = true;
                break;
            }
            visible.add(roots.get(i));
            xs.add(x);
            ws.add(tabWidth);
            x += tabWidth + TAB_GAP;
        }
        return new TabStrip(visible, xs, ws, overflow && tabScroll > 0, overflow && truncated,
                leftArrowX, rightArrowX);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = contentY();
        int bottom = boardY();
        TabStrip strip = layoutTabs();
        // Shelf line the tabs sit on; only the active tab breaks through it.
        g.fill(boardX(), bottom - 1, boardX() + boardW(), bottom, FrameRenderer.FRAME_SHADOW);

        String hoveredTab = null;
        for (int i = 0; i < strip.visible().size(); i++) {
            String rootId = strip.visible().get(i);
            int x = strip.tabX().get(i);
            int tabWidth = strip.tabW().get(i);
            boolean active = rootId.equals(activeRoot);
            int top = y0 + 3;
            int tabBottom = active ? bottom + 2 : bottom;
            boolean hover = mouseX >= x && mouseX < x + tabWidth && mouseY >= top && mouseY < bottom;
            if (hover) hoveredTab = rootLabel(rootId);
            g.fill(x, top, x + tabWidth, tabBottom, active ? TAB_ACTIVE : hover ? TAB_HOVER : TAB_IDLE);
            g.fill(x, top, x + tabWidth, top + 1, TAB_BORDER);
            g.fill(x, top, x + 1, tabBottom, TAB_BORDER);
            g.fill(x + tabWidth - 1, top, x + tabWidth, tabBottom, TAB_BORDER);
            if (active) {
                g.fill(x + 1, top + 1, x + tabWidth - 1, top + 2, 0x70FFFFFF);
            }
            drawTabIcon(g, rootId, x + (tabWidth - 16) / 2, top + (bottom - top - 16) / 2);
        }
        if (strip.canLeft() || strip.canRight()) {
            drawTabArrow(g, strip.leftArrowX(), y0 + 3, bottom, "<", strip.canLeft(), mouseX, mouseY);
            drawTabArrow(g, strip.rightArrowX(), y0 + 3, bottom, ">", strip.canRight(), mouseX, mouseY);
        }
        // The active career's name lives on the board, not in the tab.
        if (skillViewCareer.isEmpty()) {
            g.drawString(font, rootLabel(activeRoot), boardX() + 8, bottom + 8, LABEL_LIGHT);
        } else {
            CareerGraphS2CPayload.Node career = nodeById(skillViewCareer);
            String back = Component.translatable("townstead.career.screen.back").getString();
            g.drawString(font, back, boardX() + 10, bottom + 8, 0xFFE8A33C);
            if (career != null) {
                g.drawString(font, career.name() + " · "
                                + Component.translatable("townstead.career.screen.skills_title")
                                        .getString(),
                        boardX() + 14 + backLinkWidth(), bottom + 8, LABEL_LIGHT);
                String points = Component.translatable("townstead.career.screen.points",
                        career.points()).getString();
                g.drawString(font, points, boardX() + boardW() - font.width(points) - 8,
                        bottom + 8, 0xFFE8A33C);
            }
        }
        if (hoveredTab != null && !hoveredTab.isEmpty()) {
            setTooltipForNextRenderPass(Component.literal(hoveredTab));
        }
    }

    private void drawTabIcon(GuiGraphics g, String rootId, int x, int y) {
        CareerGraphS2CPayload.Node root = nodeById(rootId);
        net.minecraft.world.item.ItemStack stack = root == null
                ? net.minecraft.world.item.ItemStack.EMPTY : iconStack(root);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
        } else {
            String initial = rootLabel(rootId).isEmpty() ? "?" : rootLabel(rootId).substring(0, 1);
            g.drawString(font, initial, x + (16 - font.width(initial)) / 2, y + 4, NAV_INK, false);
        }
    }

    private void drawTabArrow(GuiGraphics g, int x, int top, int bottom, String glyph,
                              boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + TAB_ARROW_W
                && mouseY >= top && mouseY < bottom;
        g.fill(x, top, x + TAB_ARROW_W, bottom, hover ? TAB_HOVER : TAB_IDLE);
        g.fill(x, top, x + TAB_ARROW_W, top + 1, TAB_BORDER);
        g.fill(x, top, x + 1, bottom, TAB_BORDER);
        g.fill(x + TAB_ARROW_W - 1, top, x + TAB_ARROW_W, bottom, TAB_BORDER);
        g.drawString(font, glyph, x + (TAB_ARROW_W - font.width(glyph)) / 2,
                top + (bottom - top - 8) / 2, enabled ? NAV_INK : LABEL_DIM, false);
    }

    private String rootLabel(String rootId) {
        CareerGraphS2CPayload.Node root = nodeById(rootId);
        return root == null || root.name().isEmpty() ? rootId : root.name();
    }

    // ── Board drawing ──────────────────────────────────────────────────────

    /** Stitched rope between plaques; taut bright gold along acquired paths. */
    private void drawRope(GuiGraphics g, int x1, int y1, int x2, int y2, boolean lit) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = (int) Math.max(1, Math.sqrt(dx * dx + dy * dy) / 2f);
        int color = lit ? ROPE_LIT : ROPE;
        for (int i = 0; i <= steps; i++) {
            if (!lit && (i / 3) % 2 == 1) continue;
            int x = x1 + Math.round(dx * i / steps);
            int y = y1 + Math.round(dy * i / steps);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    /** Hover context without a page trip: hidden nodes whisper, skills show their terms. */
    private void maybeHoverTooltip(CareerGraphS2CPayload.Node node) {
        if (!node.id().equals(hoveredId)) return;
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        if (node.state() == CareerGraphS2CPayload.STATE_HIDDEN) {
            lines.addAll(font.split(Component.translatable("townstead.career.screen.hidden_hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), 170));
        } else if (node.kind() == CareerGraphS2CPayload.KIND_COMBO) {
            lines.addAll(font.split(Component.literal(node.name()), 170));
            lines.addAll(font.split(Component.translatable("townstead.career.screen.combo")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), 170));
            for (CareerGraphS2CPayload.Evidence evidence : node.evidence()) {
                lines.addAll(font.split(Component.literal(evidence.label())
                        .withStyle(evidence.met()
                                ? net.minecraft.ChatFormatting.GREEN
                                : net.minecraft.ChatFormatting.GRAY), 170));
            }
            for (String effect : node.effects()) {
                lines.addAll(font.split(Component.literal(effect)
                        .withStyle(net.minecraft.ChatFormatting.GRAY), 170));
            }
        } else if (node.kind() == CareerGraphS2CPayload.KIND_SKILL) {
            lines.addAll(font.split(Component.literal(node.name()), 170));
            Component status = node.equipped()
                    ? Component.translatable("townstead.career.screen.state.equipped")
                    : node.state() == CareerGraphS2CPayload.STATE_ACQUIRED
                            ? Component.translatable("townstead.career.screen.state.acquired")
                    : node.points() > 0
                            ? Component.translatable("townstead.career.screen.cost", node.points())
                            : Component.translatable("townstead.career.screen.state.ready");
            lines.addAll(font.split(status.copy()
                    .withStyle(net.minecraft.ChatFormatting.GOLD), 170));
            for (String effect : node.effects()) {
                lines.addAll(font.split(Component.literal(effect)
                        .withStyle(net.minecraft.ChatFormatting.GRAY), 170));
            }
        } else {
            return;
        }
        if (!lines.isEmpty()) setTooltipForNextRenderPass(lines);
    }

    private int halfSizeOf(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 15;
            case CareerGraphS2CPayload.KIND_ADVANCED -> 13;
            default -> 10;
        };
    }

    private void drawPlaque(GuiGraphics g, CareerGraphS2CPayload.Node node, int x, int y,
                            boolean hovered) {
        int half = halfSizeOf(node);
        boolean hidden = node.state() == CareerGraphS2CPayload.STATE_HIDDEN;
        boolean locked = node.state() == CareerGraphS2CPayload.STATE_LOCKED;
        boolean ready = node.state() == CareerGraphS2CPayload.STATE_READY;
        boolean acquired = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED;
        boolean gilded = acquired || node.equipped();
        boolean selected = node.id().equals(selectedId);

        // Ready plaques gleam; static warmth under reduce motion.
        if (ready && node.kind() != CareerGraphS2CPayload.KIND_ROOT) {
            float pulse = Accessibility.isReduceMotion() ? 0.6f
                    : 0.4f + 0.4f * Mth.sin((Util.getMillis() % 60000L) / 300f);
            int alpha = (int) (pulse * 160f) << 24;
            g.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2,
                    alpha | (WARM_GLOW & 0xFFFFFF));
        }
        if (selected) {
            drawOutline(g, x - half - 3, y - half - 3, x + half + 3, y + half + 3, SELECT_OUTLINE);
        } else if (hovered) {
            drawOutline(g, x - half - 2, y - half - 2, x + half + 2, y + half + 2, HOVER_OUTLINE);
        }

        int border = gilded ? GOLD : hidden ? 0xFF241808 : locked ? 0xFF5E4A30 : PLAQUE_BORDER;
        int fill = hidden ? PLAQUE_HIDDEN : locked ? PLAQUE_LOCKED : PLAQUE_FILL;
        g.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, border);
        g.fill(x - half, y - half, x + half, y + half, fill);
        // Bevel
        g.fill(x - half, y - half, x + half, y - half + 1, 0x60FFFFFF);
        g.fill(x - half, y + half - 1, x + half, y + half, 0x50000000);

        net.minecraft.world.item.ItemStack iconStack = iconStack(node);
        if (hidden) {
            String mystery = "?";
            g.drawString(font, mystery, x - font.width(mystery) / 2, y - 4, LABEL_DIM, false);
        } else if (!iconStack.isEmpty()) {
            float scale = node.kind() == CareerGraphS2CPayload.KIND_SKILL ? 0.75f : 1.0f;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(iconStack, -8, -8);
            g.pose().popPose();
            if (locked) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 260);
                g.fill(x - half, y - half, x + half, y + half, 0x8C281A0C);
                townstead$drawPadlock(g, x + half - 4, y + half - 4);
                g.pose().popPose();
            }
        }
        if (node.kind() == CareerGraphS2CPayload.KIND_SKILL && !hidden && node.points() > 0) {
            // Cost pips: lit amber when the skill is learnable right now.
            int pips = Math.min(3, node.points());
            for (int i = 0; i < pips; i++) {
                int px = x - half + 2 + i * 4;
                int py = y - half + 2;
                g.fill(px - 1, py - 1, px + 3, py + 3, 0x90000000);
                g.fill(px, py, px + 2, py + 2, ready ? 0xFFE8A33C : 0xFF7A6242);
            }
        }

        if (node.equipped()) {
            townstead$drawWaxSeal(g, x + half - 2, y + half - 2);
        }
        if (node.primary()) {
            g.fill(x - half, y + half + 2, x + half, y + half + 4, BAR_FILL);
        }
    }

    private static void drawOutline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void townstead$drawPadlock(GuiGraphics g, int cx, int cy) {
        // Corner badge: light iron on a dark plate; the plaque icon stays visible beside it.
        g.fill(cx - 4, cy - 6, cx + 5, cy + 4, 0xE0140F08);
        g.fill(cx - 2, cy - 5, cx + 2, cy - 4, 0xFFD9C9A8);
        g.fill(cx - 2, cy - 4, cx - 1, cy - 2, 0xFFD9C9A8);
        g.fill(cx + 1, cy - 4, cx + 2, cy - 2, 0xFFD9C9A8);
        g.fill(cx - 3, cy - 2, cx + 3, cy + 3, 0xFFD9C9A8);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF140F08);
    }

    /** Unspent skill points: a small amber gem pinned to the plaque corner. */
    private static void townstead$drawNudge(GuiGraphics g, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int d = -3; d <= 3; d++) {
            int hw = 3 - Math.abs(d);
            g.fill(cx - hw, cy + d, cx + hw + 1, cy + d + 1, 0xFF2A1C0E);
        }
        for (int d = -2; d <= 2; d++) {
            int hw = 2 - Math.abs(d);
            g.fill(cx - hw, cy + d, cx + hw + 1, cy + d + 1, 0xFFE8A33C);
        }
        g.fill(cx - 1, cy - 1, cx, cy, 0xFFFFE0A8);
        g.pose().popPose();
    }

    private static void townstead$drawWaxSeal(GuiGraphics g, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 280);
        g.fill(cx - 4, cy - 3, cx + 4, cy + 3, 0xC0140F08);
        g.fill(cx - 3, cy - 4, cx + 3, cy + 4, 0xC0140F08);
        g.fill(cx - 3, cy - 2, cx + 3, cy + 2, WAX_RIM);
        g.fill(cx - 2, cy - 3, cx + 2, cy + 3, WAX_RIM);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, WAX_SEAL);
        g.fill(cx - 1, cy - 1, cx, cy, 0xFFD86060);
        g.pose().popPose();
    }

    /** Resolves the def-declared item icon, empty when absent or the item is not installed. */
    private net.minecraft.world.item.ItemStack iconStack(CareerGraphS2CPayload.Node node) {
        if (node.icon().isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(node.icon());
        if (id == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .map(net.minecraft.world.item.ItemStack::new)
                .orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    // ── Details page ───────────────────────────────────────────────────────

    private void drawDetailsPage(GuiGraphics g) {
        int x = pageX() + 10;
        int top = pageViewTop();
        int bottom = pageViewBottom();
        CareerGraphS2CPayload.Node selected = nodeById(selectedId);

        pageScroll = Mth.clamp(pageScroll, 0, Math.max(0, pageContentHeight - (bottom - top)));
        g.enableScissor(pageX() + 7, top, pageX() + PAGE_W - 7, bottom);
        int y = top + 3 - (int) pageScroll;
        int startY = y;
        y = selected == null
                ? drawIdentityPage(g, x, y)
                : drawNodePage(g, selected, x, y);
        pageContentHeight = y - startY;
        g.disableScissor();

        int viewHeight = bottom - top;
        if (pageContentHeight > viewHeight) {
            int trackX = pageX() + PAGE_W - 6;
            g.fill(trackX, top, trackX + 2, bottom, 0x28000000);
            int thumbHeight = Math.max(12, viewHeight * viewHeight / pageContentHeight);
            int maxScroll = pageContentHeight - viewHeight;
            int thumbY = top + (int) ((viewHeight - thumbHeight) * (pageScroll / maxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0x806E5430);
        }

        // Fixed furniture: the signature never scrolls.
        if (!scribeName.isEmpty()) {
            String signature = Component.translatable(
                    "townstead.career.screen.scribe_signature", scribeName).getString();
            g.drawString(font, font.split(Component.literal(signature), PAGE_W - 20).get(0),
                    x, bottom + 4, INK_DIM, false);
        }
    }

    /** Empty state: the character's own record. */
    private int drawIdentityPage(GuiGraphics g, int x, int y) {
        for (FormattedCharSequence line : font.split(title, PAGE_W - 20)) {
            g.drawString(font, line, x, y, INK_HEADER, false);
            y += 11;
        }
        y += 1;
        drawRule(g, x, y);
        y += 6;
        CareerGraphS2CPayload.Node primary = null;
        for (CareerGraphS2CPayload.Node node : nodes) {
            if (node.primary()) {
                primary = node;
                break;
            }
        }
        if (primary != null) {
            net.minecraft.world.item.ItemStack icon = iconStack(primary);
            if (!icon.isEmpty()) g.renderItem(icon, x, y - 2);
            g.drawString(font, primary.name(), x + (icon.isEmpty() ? 0 : 20), y + 2, INK_TEXT, false);
            if (!primary.rankName().isEmpty()) {
                g.drawString(font, primary.rankName(),
                        x + (icon.isEmpty() ? 0 : 20), y + 12, INK_DIM, false);
            }
            y += 26;
        } else {
            g.drawString(font, Component.translatable("townstead.career.screen.unchosen"), x, y, INK_DIM, false);
            y += 12;
        }
        y += 6;
        drawRule(g, x, y);
        y += 6;
        for (FormattedCharSequence line : font.split(
                Component.translatable("townstead.career.screen.hint"), PAGE_W - 20)) {
            g.drawString(font, line, x, y, INK_DIM, false);
            y += 10;
        }
        return y;
    }

    /** A selected plaque's registry record. */
    private int drawNodePage(GuiGraphics g, CareerGraphS2CPayload.Node selected, int x, int y) {
        boolean maskedNode = selected.state() == CareerGraphS2CPayload.STATE_HIDDEN;
        boolean isSkill = selected.kind() == CareerGraphS2CPayload.KIND_SKILL;

        net.minecraft.world.item.ItemStack icon = maskedNode
                ? net.minecraft.world.item.ItemStack.EMPTY : iconStack(selected);
        if (!icon.isEmpty()) g.renderItem(icon, x, y);
        int textX = x + (icon.isEmpty() ? 0 : 20);
        g.drawString(font, maskedNode
                ? Component.translatable("townstead.career.screen.unknown").getString()
                : selected.name(), textX, y, INK_HEADER, false);
        if (!isSkill && !maskedNode
                && (selected.xp() > 0 || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED)) {
            g.drawString(font, selected.rankName(), textX, y + 10, INK_ACCENT, false);
            y += 20;
        } else {
            y += icon.isEmpty() ? 12 : 20;
        }

        String kindKey = switch (selected.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> "townstead.career.screen.kind.career";
            case CareerGraphS2CPayload.KIND_ADVANCED -> "townstead.career.screen.kind.specialization";
            case CareerGraphS2CPayload.KIND_COMBO -> "townstead.career.screen.kind.combo";
            default -> "townstead.career.screen.kind.skill";
        };
        String stateKey = selected.equipped() ? "townstead.career.screen.state.equipped"
                : switch (selected.state()) {
            case CareerGraphS2CPayload.STATE_HIDDEN -> "townstead.career.screen.state.hidden";
            case CareerGraphS2CPayload.STATE_LOCKED -> "townstead.career.screen.state.locked";
            case CareerGraphS2CPayload.STATE_READY -> "townstead.career.screen.state.ready";
            default -> "townstead.career.screen.state.acquired";
        };
        int stateColor = selected.equipped() || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED
                ? INK_GOOD
                : selected.state() == CareerGraphS2CPayload.STATE_READY ? INK_ACCENT : INK_DIM;
        String kindPart = Component.translatable(kindKey).getString() + " · ";
        g.drawString(font, kindPart, x, y, INK_DIM, false);
        g.drawString(font, Component.translatable(stateKey).getString(),
                x + font.width(kindPart), y, stateColor, false);
        y += 11;
        if (isSkill && !maskedNode && selected.points() > 0) {
            g.drawString(font, Component.translatable("townstead.career.screen.cost",
                    selected.points()).getString(), x, y, INK_DIM, false);
            y += 10;
        }
        if ((isSkill || selected.kind() == CareerGraphS2CPayload.KIND_COMBO)
                && !maskedNode && !selected.effects().isEmpty()) {
            y += 2;
            for (String effect : selected.effects()) {
                for (FormattedCharSequence line : font.split(
                        Component.literal(effect), PAGE_W - 20)) {
                    g.drawString(font, line, x, y, INK_ACCENT, false);
                    y += 10;
                }
            }
        }
        if (isSkill && selected.state() == CareerGraphS2CPayload.STATE_LOCKED
                && !selected.rankName().isEmpty()) {
            CareerGraphS2CPayload.Node owner = nodeById(selected.parentId());
            if (owner != null && owner.tier() < selected.tier()) {
                g.drawString(font, Component.translatable("townstead.career.screen.unlocks_at",
                        selected.rankName()).getString(), x, y, INK_BAD, false);
                y += 10;
            }
        }
        if (selected.primary()) {
            g.drawString(font, Component.translatable("townstead.career.screen.primary"), x, y,
                    INK_ACCENT, false);
            y += 11;
        }

        // Tier pips and progress (combos have no track of their own; their evidence is the story)
        if (!isSkill && selected.kind() != CareerGraphS2CPayload.KIND_COMBO && !maskedNode
                && (selected.xp() > 0 || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED)) {
            y += 2;
            int pips = Math.min(selected.maxTier(), 10);
            for (int i = 0; i < pips; i++) {
                int px = x + i * 11;
                boolean lit = i < selected.tier();
                g.fill(px, y, px + 8, y + 5, lit ? BAR_FILL : 0x503A2410);
                g.fill(px, y, px + 8, y + 1, lit ? 0x80FFFFFF : 0x30FFFFFF);
            }
            y += 9;
            String xpLine = selected.xpToNext() <= 0
                    ? selected.xp() + " XP"
                    : selected.nextRankName().isEmpty()
                            ? Component.translatable("townstead.career.screen.xp_line",
                                    selected.xp(), selected.xp() + selected.xpToNext()).getString()
                            : Component.translatable("townstead.career.screen.xp_line_to",
                                    selected.xp(), selected.xp() + selected.xpToNext(),
                                    selected.nextRankName()).getString();
            g.drawString(font, xpLine, x, y, INK_TEXT, false);
            y += 10;
            int total = selected.xp() + selected.xpToNext();
            float progress = total <= 0 ? 1f : selected.xp() / (float) total;
            drawBar(g, x, y, PAGE_W - 20, progress, BAR_FILL);
            y += 8;
            if (selected.dailyCap() > 0) {
                String todayLabel = Component.translatable("townstead.career.screen.today").getString()
                        + ": " + selected.xpToday() + "/" + selected.dailyCap();
                g.drawString(font, todayLabel, x, y, INK_DIM, false);
                y += 9;
                drawBar(g, x, y, PAGE_W - 20,
                        Math.min(1f, selected.xpToday() / (float) selected.dailyCap()), BAR_TRACK);
                y += 8;
            }
        }

        if (!isSkill && selected.kind() != CareerGraphS2CPayload.KIND_COMBO && !maskedNode
                && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED) {
            g.drawString(font, Component.translatable("townstead.career.screen.points",
                    selected.points()).getString(), x, y, INK_ACCENT, false);
            y += 11;
        }

        if (!selected.evidence().isEmpty()) {
            y += 2;
            drawRule(g, x, y);
            y += 5;
            g.drawString(font, Component.translatable("townstead.career.screen.evidence"), x, y, INK_DIM, false);
            y += 11;
            for (CareerGraphS2CPayload.Evidence evidence : selected.evidence()) {
                String value = evidence.target() > 0
                        ? evidence.current() + "/" + evidence.target()
                        : String.valueOf(evidence.current());
                String mark = evidence.target() > 0 ? (evidence.met() ? "✓ " : "✗ ") : "";
                g.drawString(font, mark + evidence.label() + ": " + value, x, y,
                        evidence.target() > 0 ? (evidence.met() ? INK_GOOD : INK_BAD) : INK_TEXT, false);
                y += 10;
                if (evidence.target() > 0 && !evidence.met()) {
                    drawBar(g, x, y, PAGE_W - 20,
                            Math.min(1f, evidence.current() / (float) evidence.target()), BAR_TRACK);
                    y += 7;
                }
            }
        }

        if (!selected.routesLine().isEmpty()) {
            y += 3;
            for (FormattedCharSequence line : font.split(Component.translatable(
                    "townstead.career.screen.routes", selected.routesLine()), PAGE_W - 20)) {
                g.drawString(font, line, x, y, INK_DIM, false);
                y += 10;
            }
        }
        if (!selected.replaces().isEmpty()) {
            y += 2;
            g.drawString(font, Component.translatable(
                    "townstead.career.screen.replaces", selected.replaces()).getString(),
                    x, y, INK_ACCENT, false);
            y += 11;
        }

        if (!selected.description().isEmpty()) {
            y += 3;
            drawRule(g, x, y);
            y += 5;
            for (FormattedCharSequence line : font.split(
                    Component.literal(selected.description()), PAGE_W - 20)) {
                g.drawString(font, line, x, y, INK_DIM, false);
                y += 10;
            }
        }

        if (!selected.moments().isEmpty()) {
            y += 4;
            drawRule(g, x, y);
            y += 5;
            g.drawString(font, Component.translatable("townstead.career.screen.chronicle"),
                    x, y, INK_DIM, false);
            y += 11;
            for (String moment : selected.moments()) {
                for (FormattedCharSequence line : font.split(
                        Component.literal(moment), PAGE_W - 20)) {
                    g.drawString(font, line, x, y, INK_TEXT, false);
                    y += 10;
                }
                y += 2;
            }
        }
        // The stamp closes the record, pressed at the foot of the page.
        if (!isSkill && !maskedNode
                && selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED) {
            y += 14;
            drawRegisteredStamp(g, pageX() + PAGE_W - 62, y);
            y += 14;
        }
        return y + 4;
    }

    private void drawRule(GuiGraphics g, int x, int y) {
        g.fill(x, y, pageX() + PAGE_W - 10, y + 1, 0x506E5430);
    }

    /** The Archives' red stamp on an acquired career's record. */
    private void drawRegisteredStamp(GuiGraphics g, int cx, int cy) {
        String text = Component.translatable("townstead.career.screen.registered").getString();
        int w = font.width(text) + 8;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 200);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12f));
        g.fill(-w / 2 - 1, -7, w / 2 + 1, 7, 0x30A02020);
        drawOutline(g, -w / 2 - 1, -7, w / 2 + 1, 7, 0xB0A02020);
        drawOutline(g, -w / 2 + 1, -5, w / 2 - 1, 5, 0x60A02020);
        g.drawString(font, text, -font.width(text) / 2, -4, 0xD0B02525, false);
        g.pose().popPose();
    }

    private static void drawBar(GuiGraphics g, int x, int y, int barWidth, float progress, int color) {
        g.fill(x, y, x + barWidth, y + 3, 0x503A2410);
        g.fill(x, y, x + Math.round(barWidth * Mth.clamp(progress, 0f, 1f)), y + 3,
                0xFF000000 | (color & 0xFFFFFF));
    }

    // ── Input ──────────────────────────────────────────────────────────────

    private static boolean isOver(int mouseX, int mouseY, int x, int y, int half) {
        return Math.abs(mouseX - x) <= half && Math.abs(mouseY - y) <= half;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        // Tabs and paging arrows
        if (mouseY >= contentY() && mouseY < boardY() + 2) {
            TabStrip strip = layoutTabs();
            if (strip.canLeft() && mouseX >= strip.leftArrowX()
                    && mouseX < strip.leftArrowX() + TAB_ARROW_W) {
                tabScroll--;
                return true;
            }
            if (strip.canRight() && mouseX >= strip.rightArrowX()
                    && mouseX < strip.rightArrowX() + TAB_ARROW_W) {
                tabScroll++;
                return true;
            }
            for (int i = 0; i < strip.visible().size(); i++) {
                int x = strip.tabX().get(i);
                if (mouseX >= x && mouseX < x + strip.tabW().get(i)) {
                    String rootId = strip.visible().get(i);
                    if (!rootId.equals(activeRoot)) {
                        activeRoot = rootId;
                        selectedId = "";
                        skillViewCareer = "";
                        panX = 0;
                        panY = 0;
                        layoutActiveRoot();
                        refreshEquipButton();
                    }
                    return true;
                }
            }
        }
        if (overBackLink(mouseX, mouseY)) {
            String career = skillViewCareer;
            skillViewCareer = "";
            selectedId = career;
            panX = 0;
            panY = 0;
            pageScroll = 0;
            layoutActiveRoot();
            refreshEquipButton();
            townstead$pageTurn();
            return true;
        }
        // Plaques
        if (mouseX >= boardX() && mouseX < boardX() + boardW()
                && mouseY >= boardY() && mouseY < boardY() + boardH()) {
            List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
            for (CareerGraphS2CPayload.Node node : tabNodes) {
                int[] local = positions.get(node.id());
                if (local == null) continue;
                if (isOver((int) mouseX, (int) mouseY, screenX(local), screenY(local),
                        halfSizeOf(node) + 2)) {
                    selectedId = node.id();
                    pageScroll = 0;
                    refreshEquipButton();
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            panX += dragX;
            panY += dragY;
            clampBoardPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollAt(mouseX, deltaY);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollAt(mouseX, delta);
        return true;
    }
    *///?}

    private void scrollAt(double mouseX, double delta) {
        if (mouseX >= pageX()) {
            int viewHeight = pageViewBottom() - pageViewTop();
            int maxScroll = Math.max(0, pageContentHeight - viewHeight);
            pageScroll = Mth.clamp(pageScroll - delta * 12, 0, maxScroll);
        } else {
            panY += delta * 14;
            clampBoardPan();
        }
    }

    private int pageViewTop() { return contentY() + 7; }

    /** The page extends down to the topmost visible button, minus the signature band. */
    private int pageViewBottom() {
        int top = height - MARGIN - FRAME_THICK - 24;
        if (skillsButton != null && skillsButton.visible) top = Math.min(top, skillsButton.getY());
        if (equipButton != null && equipButton.visible) top = Math.min(top, equipButton.getY());
        if (vocationButton != null && vocationButton.visible) {
            top = Math.min(top, vocationButton.getY());
        }
        if (trackButton != null && trackButton.visible) top = Math.min(top, trackButton.getY());
        return top - (scribeName.isEmpty() ? 6 : 16);
    }

    private static void send(CareerChooseC2SPayload payload) {
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
