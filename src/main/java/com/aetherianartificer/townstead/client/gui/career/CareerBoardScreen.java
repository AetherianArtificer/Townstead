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
 * The career board: careers as wooden plaques hanging on a plank wall, joined by stitched
 * ropes, with skills dangling beneath as market tags and a parchment page for details.
 * Shares the Field Post / Calendar family primitives ({@link FrameRenderer}) so all
 * Townstead GUIs read as one workshop. Entirely a view over the server-rendered
 * {@link CareerGraphS2CPayload}; the only intentions sent are skill choices.
 */
public final class CareerBoardScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int FRAME_THICK = 6;
    private static final int PAGE_W = 142;
    private static final int TAB_H = 16;

    // Ink on parchment (Calendar family palette)
    private static final int INK_HEADER = 0xFF1F1305;
    private static final int INK_TEXT = 0xFF3A2410;
    private static final int INK_DIM = 0xFF6E5430;
    private static final int INK_GOOD = 0xFF2E5E1E;
    private static final int INK_BAD = 0xFF8C2E1A;
    private static final int BAR_TRACK = 0xFFB8985C;
    private static final int BAR_FILL = 0xFFFFB347;

    // Board (light text on dark planks)
    private static final int LABEL_LIGHT = 0xFFEEDDB0;
    private static final int LABEL_DIM = 0xFFA8946E;
    private static final int PLAQUE_FILL = 0xFFD8C28F;
    private static final int PLAQUE_BORDER = 0xFF8B6F47;
    private static final int PLAQUE_LOCKED = 0xFF9A8560;
    private static final int PLAQUE_HIDDEN = 0xFF3A2C1C;
    private static final int ROPE = 0xFF6E4F2A;
    private static final int GOLD = 0xFFC9A227;
    private static final int WARM_GLOW = 0xFFFFE680;
    private static final int WAX_SEAL = 0xFFA02020;
    private static final int WAX_RIM = 0xFF701010;
    private static final int NAV_BG = 0xFFD8C28F;
    private static final int NAV_ACTIVE = 0xFFFFB347;
    private static final int NAV_BORDER = 0xFF8B6F47;
    private static final int NAV_INK = 0xFF3E2510;

    private List<CareerGraphS2CPayload.Node> nodes;
    private final Map<String, List<CareerGraphS2CPayload.Node>> byRoot = new LinkedHashMap<>();
    private final Map<String, int[]> positions = new LinkedHashMap<>();
    private String activeRoot = "";
    private String selectedId = "";
    private double panX;
    private double panY;
    private boolean dragging;
    private Button equipButton;

    private CareerBoardScreen(CareerGraphS2CPayload payload) {
        super(Component.translatable("townstead.career.screen.title", payload.title()));
        apply(payload);
    }

    /** Opens the screen, or refreshes the open one after a choose round-trip. */
    public static void openOrUpdate(CareerGraphS2CPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CareerBoardScreen open) {
            open.apply(payload);
        } else {
            minecraft.setScreen(new CareerBoardScreen(payload));
        }
    }

    private void apply(CareerGraphS2CPayload payload) {
        this.nodes = payload.nodes();
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
    private int boardY() { return contentY() + TAB_H + 4; }
    private int boardW() { return contentW() - PAGE_W - FRAME_THICK; }
    private int boardH() { return contentH() - TAB_H - 4; }
    private int pageX() { return contentX() + contentW() - PAGE_W; }

    private int boardCenterX() { return boardX() + boardW() / 2; }
    private int screenX(int[] local) { return boardCenterX() + local[0] + (int) panX; }
    private int screenY(int[] local) { return boardY() + 44 + local[1] + (int) panY; }

    // ── Layout: plaques hang downward from the root ────────────────────────

    private void layoutActiveRoot() {
        positions.clear();
        List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
        CareerGraphS2CPayload.Node root = tabNodes.stream()
                .filter(node -> node.kind() == CareerGraphS2CPayload.KIND_ROOT).findFirst().orElse(null);
        if (root == null) return;
        positions.put(root.id(), new int[]{0, 0});
        placeChildren(tabNodes, root.id(), 0, 0, 90f);
    }

    private void placeChildren(List<CareerGraphS2CPayload.Node> tabNodes, String parentId,
                               int parentX, int parentY, float directionDeg) {
        List<CareerGraphS2CPayload.Node> careers = new ArrayList<>();
        List<CareerGraphS2CPayload.Node> skills = new ArrayList<>();
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            if (!node.parentId().equals(parentId)) continue;
            (node.kind() == CareerGraphS2CPayload.KIND_SKILL ? skills : careers).add(node);
        }
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
        for (int i = 0; i < skills.size(); i++) {
            float spread = skills.size() == 1 ? 0f
                    : Mth.lerp(i / (float) (skills.size() - 1), -34f, 34f);
            float angle = (float) Math.toRadians(90f + spread);
            int x = parentX + Math.round(Mth.cos(angle) * 42);
            int y = parentY + Math.round(Mth.sin(angle) * 42);
            positions.put(skills.get(i).id(), new int[]{x, y});
        }
    }

    // ── Widgets ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        equipButton = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.career.screen.equip"),
                        button -> {
                            if (!selectedId.isEmpty()) send(new CareerChooseC2SPayload(selectedId));
                        })
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 48, PAGE_W - 12, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(pageX() + 6, height - MARGIN - FRAME_THICK - 24, PAGE_W - 12, 20).build());
        refreshEquipButton();
    }

    private void refreshEquipButton() {
        if (equipButton == null) return;
        CareerGraphS2CPayload.Node selected = nodeById(selectedId);
        boolean equippable = selected != null
                && selected.kind() == CareerGraphS2CPayload.KIND_SKILL && !selected.equipped();
        equipButton.visible = equippable;
        equipButton.active = equippable;
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
        FrameRenderer.tileTexture(g, "minecraft:block/spruce_planks", boardX(), contentY(), boardW(), contentH());
        g.fill(boardX(), contentY(), boardX() + boardW(), contentY() + contentH(), 0x5C000000);
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
        drawTabs(g, mouseX, mouseY);

        List<CareerGraphS2CPayload.Node> tabNodes = byRoot.getOrDefault(activeRoot, List.of());
        g.enableScissor(boardX(), boardY(), boardX() + boardW(), boardY() + boardH());
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] childPos = positions.get(node.id());
            int[] parentPos = positions.get(node.parentId());
            if (childPos == null || parentPos == null) continue;
            boolean lit = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED || node.equipped();
            drawRope(g, screenX(parentPos), screenY(parentPos), screenX(childPos), screenY(childPos), lit);
        }
        for (CareerGraphS2CPayload.Node node : tabNodes) {
            int[] local = positions.get(node.id());
            if (local == null) continue;
            int x = screenX(local);
            int y = screenY(local);
            drawPlaque(g, node, x, y);
            String label = node.state() == CareerGraphS2CPayload.STATE_HIDDEN
                    ? Component.translatable("townstead.career.screen.unknown").getString()
                    : node.name();
            int labelY = y + halfSizeOf(node) + (node.primary() ? 7 : 4);
            g.drawString(font, label, x - font.width(label) / 2, labelY,
                    node.state() <= CareerGraphS2CPayload.STATE_LOCKED ? LABEL_DIM : LABEL_LIGHT);
        }
        g.disableScissor();

        drawDetailsPage(g);
    }

    private static final int TAB_GAP = 3;
    private static final int TAB_ARROW_W = 13;
    private int tabScroll;

    private record TabStrip(List<String> visible, List<Integer> tabX, List<Integer> tabW,
                            boolean canLeft, boolean canRight, int leftArrowX, int rightArrowX) {}

    /** Lays out the visible tab window; shared by draw and click so geometry never drifts. */
    private TabStrip layoutTabs() {
        List<String> roots = new ArrayList<>(byRoot.keySet());
        tabScroll = Mth.clamp(tabScroll, 0, Math.max(0, roots.size() - 1));
        int total = 0;
        for (String rootId : roots) total += font.width(rootLabel(rootId)) + 12 + TAB_GAP;
        boolean overflow = total > boardW();
        int left = boardX();
        int right = boardX() + boardW();
        int leftArrowX = left;
        int rightArrowX = right - TAB_ARROW_W;
        if (overflow) {
            left += TAB_ARROW_W + TAB_GAP;
            right -= TAB_ARROW_W + TAB_GAP;
        } else {
            tabScroll = 0;
        }
        List<String> visible = new ArrayList<>();
        List<Integer> xs = new ArrayList<>();
        List<Integer> ws = new ArrayList<>();
        int x = left;
        boolean truncated = false;
        for (int i = tabScroll; i < roots.size(); i++) {
            int tabWidth = font.width(rootLabel(roots.get(i))) + 12;
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
        int y = contentY();
        TabStrip strip = layoutTabs();
        for (int i = 0; i < strip.visible().size(); i++) {
            String rootId = strip.visible().get(i);
            int x = strip.tabX().get(i);
            int tabWidth = strip.tabW().get(i);
            boolean active = rootId.equals(activeRoot);
            boolean hover = mouseX >= x && mouseX < x + tabWidth && mouseY >= y && mouseY < y + TAB_H;
            g.fill(x, y, x + tabWidth, y + TAB_H, active ? NAV_ACTIVE : hover ? 0xFFEEDDA8 : NAV_BG);
            g.fill(x, y + TAB_H - 1, x + tabWidth, y + TAB_H, NAV_BORDER);
            g.fill(x, y, x + 1, y + TAB_H, NAV_BORDER);
            g.fill(x + tabWidth - 1, y, x + tabWidth, y + TAB_H, NAV_BORDER);
            g.drawString(font, rootLabel(rootId), x + 6, y + (TAB_H - 8) / 2, NAV_INK, false);
        }
        if (strip.canLeft() || strip.canRight()) {
            drawTabArrow(g, strip.leftArrowX(), y, "<", strip.canLeft(), mouseX, mouseY);
            drawTabArrow(g, strip.rightArrowX(), y, ">", strip.canRight(), mouseX, mouseY);
        }
    }

    private void drawTabArrow(GuiGraphics g, int x, int y, String glyph, boolean enabled,
                              int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + TAB_ARROW_W
                && mouseY >= y && mouseY < y + TAB_H;
        g.fill(x, y, x + TAB_ARROW_W, y + TAB_H, hover ? 0xFFEEDDA8 : NAV_BG);
        g.fill(x, y + TAB_H - 1, x + TAB_ARROW_W, y + TAB_H, NAV_BORDER);
        g.fill(x, y, x + 1, y + TAB_H, NAV_BORDER);
        g.fill(x + TAB_ARROW_W - 1, y, x + TAB_ARROW_W, y + TAB_H, NAV_BORDER);
        g.drawString(font, glyph, x + (TAB_ARROW_W - font.width(glyph)) / 2,
                y + (TAB_H - 8) / 2, enabled ? NAV_INK : LABEL_DIM, false);
    }

    private String rootLabel(String rootId) {
        CareerGraphS2CPayload.Node root = nodeById(rootId);
        return root == null || root.name().isEmpty() ? rootId : root.name();
    }

    /** Stitched rope between plaques; taut gold along acquired paths. */
    private void drawRope(GuiGraphics g, int x1, int y1, int x2, int y2, boolean lit) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = (int) Math.max(1, Math.sqrt(dx * dx + dy * dy) / 2f);
        int color = lit ? GOLD : ROPE;
        for (int i = 0; i <= steps; i++) {
            if (!lit && (i / 3) % 2 == 1) continue;
            int x = x1 + Math.round(dx * i / steps);
            int y = y1 + Math.round(dy * i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private int halfSizeOf(CareerGraphS2CPayload.Node node) {
        return switch (node.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> 15;
            case CareerGraphS2CPayload.KIND_ADVANCED -> 13;
            default -> 10;
        };
    }

    private void drawPlaque(GuiGraphics g, CareerGraphS2CPayload.Node node, int x, int y) {
        int half = halfSizeOf(node);
        boolean hidden = node.state() == CareerGraphS2CPayload.STATE_HIDDEN;
        boolean locked = node.state() == CareerGraphS2CPayload.STATE_LOCKED;
        boolean ready = node.state() == CareerGraphS2CPayload.STATE_READY;
        boolean acquired = node.state() == CareerGraphS2CPayload.STATE_ACQUIRED;
        boolean gilded = acquired || node.equipped();

        // Ready plaques gleam; static warmth under reduce motion.
        if (ready && node.kind() != CareerGraphS2CPayload.KIND_ROOT) {
            float pulse = Accessibility.isReduceMotion() ? 0.6f
                    : 0.4f + 0.4f * Mth.sin((Util.getMillis() % 60000L) / 300f);
            int alpha = (int) (pulse * 160f) << 24;
            g.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2,
                    alpha | (WARM_GLOW & 0xFFFFFF));
        }
        if (node.id().equals(selectedId)) {
            g.fill(x - half - 2, y - half - 2, x + half + 2, y + half + 2, WARM_GLOW);
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
                g.fill(x - half, y - half, x + half, y + half, 0x70281A0C);
                townstead$drawPadlock(g, x, y + half - 4);
                g.pose().popPose();
            }
        }

        if (acquired && node.kind() != CareerGraphS2CPayload.KIND_SKILL) {
            townstead$drawWaxSeal(g, x + half - 2, y + half - 2);
        }
        if (node.equipped()) {
            townstead$drawWaxSeal(g, x + half - 2, y + half - 2);
        }
        if (node.primary()) {
            g.fill(x - half, y + half + 2, x + half, y + half + 4, NAV_ACTIVE);
        }
    }

    private static void townstead$drawPadlock(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 3, cy - 2, cx + 3, cy + 3, 0xFF2A1E10);
        g.fill(cx - 2, cy - 4, cx - 1, cy - 2, 0xFF4A3A24);
        g.fill(cx + 1, cy - 4, cx + 2, cy - 2, 0xFF4A3A24);
        g.fill(cx - 2, cy - 5, cx + 2, cy - 4, 0xFF4A3A24);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFF806840);
    }

    private static void townstead$drawWaxSeal(GuiGraphics g, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 280);
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

    private void drawDetailsPage(GuiGraphics g) {
        int x = pageX() + 10;
        int y = contentY() + 10;
        g.drawString(font, title, x, y, INK_HEADER, false);
        y += 14;

        CareerGraphS2CPayload.Node selected = nodeById(selectedId);
        if (selected == null) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("townstead.career.screen.hint"), PAGE_W - 16)) {
                g.drawString(font, line, x, y, INK_DIM, false);
                y += 10;
            }
            return;
        }

        boolean maskedNode = selected.state() == CareerGraphS2CPayload.STATE_HIDDEN;
        g.drawString(font, maskedNode
                ? Component.translatable("townstead.career.screen.unknown").getString()
                : selected.name(), x, y, INK_TEXT, false);
        y += 11;
        String kindKey = switch (selected.kind()) {
            case CareerGraphS2CPayload.KIND_ROOT -> "townstead.career.screen.kind.career";
            case CareerGraphS2CPayload.KIND_ADVANCED -> "townstead.career.screen.kind.specialization";
            default -> "townstead.career.screen.kind.skill";
        };
        String stateKey = selected.equipped() ? "townstead.career.screen.state.equipped"
                : switch (selected.state()) {
            case CareerGraphS2CPayload.STATE_HIDDEN -> "townstead.career.screen.state.hidden";
            case CareerGraphS2CPayload.STATE_LOCKED -> "townstead.career.screen.state.locked";
            case CareerGraphS2CPayload.STATE_READY -> "townstead.career.screen.state.ready";
            default -> "townstead.career.screen.state.acquired";
        };
        g.drawString(font, Component.translatable(kindKey).getString() + " - "
                + Component.translatable(stateKey).getString(), x, y, INK_DIM, false);
        y += 12;
        if (selected.primary()) {
            g.drawString(font, Component.translatable("townstead.career.screen.primary"), x, y, INK_BAD, false);
            y += 12;
        }

        if (selected.kind() != CareerGraphS2CPayload.KIND_SKILL && !maskedNode
                && (selected.xp() > 0 || selected.state() == CareerGraphS2CPayload.STATE_ACQUIRED)) {
            g.drawString(font, Component.translatable("townstead.career.screen.tier", selected.tier())
                    .getString() + "  " + selected.xp() + " XP", x, y, INK_TEXT, false);
            y += 10;
            int total = selected.xp() + selected.xpToNext();
            float progress = total <= 0 ? 1f : selected.xp() / (float) total;
            drawBar(g, x, y, PAGE_W - 16, progress, BAR_FILL);
            y += 8;
        }

        if (!selected.evidence().isEmpty()) {
            g.drawString(font, Component.translatable("townstead.career.screen.evidence"), x, y, INK_DIM, false);
            y += 11;
            for (CareerGraphS2CPayload.Evidence evidence : selected.evidence()) {
                if (y > height - MARGIN - FRAME_THICK - 78) break;
                String value = evidence.target() > 0
                        ? evidence.current() + "/" + evidence.target()
                        : String.valueOf(evidence.current());
                String mark = evidence.target() > 0 ? (evidence.met() ? "✓ " : "✗ ") : "";
                g.drawString(font, mark + evidence.label() + ": " + value, x, y,
                        evidence.target() > 0 ? (evidence.met() ? INK_GOOD : INK_BAD) : INK_TEXT, false);
                y += 10;
                if (evidence.target() > 0 && !evidence.met()) {
                    drawBar(g, x, y, PAGE_W - 16,
                            Math.min(1f, evidence.current() / (float) evidence.target()), BAR_TRACK);
                    y += 7;
                }
            }
            y += 3;
        }

        if (!selected.description().isEmpty()) {
            for (FormattedCharSequence line : font.split(
                    Component.literal(selected.description()), PAGE_W - 16)) {
                if (y > height - MARGIN - FRAME_THICK - 78) break;
                g.drawString(font, line, x, y, INK_DIM, false);
                y += 10;
            }
        }
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
        if (mouseY >= contentY() && mouseY < contentY() + TAB_H) {
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
                        panX = 0;
                        panY = 0;
                        layoutActiveRoot();
                        refreshEquipButton();
                    }
                    return true;
                }
            }
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
        panY += deltaY * 14;
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        panY += delta * 14;
        return true;
    }
    *///?}

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
