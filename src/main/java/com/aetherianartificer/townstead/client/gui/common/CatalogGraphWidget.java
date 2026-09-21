package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.catalog.*;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared graph for both tabs. Picking uses exactly the same camera as rendering. */
public final class CatalogGraphWidget extends AbstractWidget {
    private final Font font;
    private float uiScale = 1;
    private CatalogDataLoader.Theme theme = CatalogDataLoader.Theme.DEFAULT;
    public void theme(CatalogDataLoader.Theme value) { theme = value; }
    public void uiScale(float value) { uiScale = value; }
    private final CatalogViewport camera;
    private final Supplier<String> selection;
    private final Consumer<String> select;
    private CatalogGraphLayout.Layout layout = CatalogGraphLayout.Layout.EMPTY;
    private Map<String, CatalogEntries.Display> displays = Map.of();
    private String focusedSector = "";
    private double startX, startY;
    private boolean armed, dragging;

    public CatalogGraphWidget(Font font, int x, int y, int w, int h, CatalogViewport camera,
                              Supplier<String> selection, Consumer<String> select) {
        super(x, y, w, h, Component.translatable("townstead.catalog.graph"));
        this.font = font; this.camera = camera; this.selection = selection; this.select = select;
    }
    public void content(CatalogGraphLayout.Layout layout, Map<String, CatalogEntries.Display> displays) {
        this.layout = layout; this.displays = displays;
    }
    public boolean dragging() { return armed; }
    public void fit() { camera.fit(layout.width(), layout.height(), width, height); }
    public void revealSelected() {
        layout.nodes().stream().filter(n -> n.entry().id().equals(selection.get()))
                .sorted(Comparator.comparing(n -> !n.sector().equals(focusedSector))).findFirst()
                .ifPresent(n -> camera.reveal(n.x(), n.y(), width, height));
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        g.fill(getX(), getY(), getX() + width, getY() + height, theme.graphBackgroundColor());
        ScaledWidget.scissor(g, uiScale, getX(), getY(), getX() + width, getY() + height);
        g.pose().pushPose();
        g.pose().translate(getX(), getY(), 0);
        g.pose().scale((float) camera.zoom, (float) camera.zoom, 1);
        g.pose().translate(camera.panX, camera.panY, 0);
        CatalogGraphLayout.Node hovered = isMouseOver(mx, my) ? nodeAt(mx, my) : null;
        for (var sector : layout.sectors()) {
            int color = SpiritRegistry.get(sector.id()).map(s -> s.color()).orElse(theme.borderColor());
            g.fill(sector.x(), sector.y(), sector.x() + sector.width(), sector.y() + sector.height(), color);
            g.fill(sector.x() + 1, sector.y() + 1, sector.x() + sector.width() - 1,
                    sector.y() + sector.height() - 1, theme.graphBackgroundColor());
            g.fill(sector.x() + 1, sector.y() + 1, sector.x() + sector.width() - 1, sector.y() + 19,
                    (color & 0xFFFFFF) | 0x30000000);
            String count = Integer.toString(sector.members());
            String label = font.plainSubstrByWidth(sector.label(), sector.width() - font.width(count) - 18);
            g.drawString(font, label, sector.x() + 6, sector.y() + 5, color, false);
            g.drawString(font, count, sector.x() + sector.width() - font.width(count) - 5, sector.y() + 5, 0xAABBA9, false);
            for (var edge : sector.edges()) g.fill(edge.x1(), edge.y1(), edge.x2(), edge.y2() + 1, 0xFF879B93);
            for (var node : sector.nodes()) {
                int x = node.x(), y = node.y();
                if (camera.screenY(y + 54) < 0 || camera.screenY(y) > height
                        || camera.screenX(x + 45) < 0 || camera.screenX(x - 20) > width) continue;
                var entry = node.entry();
                boolean chosen = entry.id().equals(selection.get());
                boolean hot = node == hovered;
                int border = entry.recognized()
                        ? chosen ? theme.builtNodeSelectedBorderColor() : hot ? theme.builtNodeHoverBorderColor() : theme.builtNodeBorderColor()
                        : chosen ? theme.nodeSelectedBorderColor() : hot ? theme.nodeHoverBorderColor() : theme.nodeBorderColor();
                int fill = entry.recognized()
                        ? chosen ? theme.builtNodeSelectedFillColor() : hot ? theme.builtNodeHoverFillColor() : theme.builtNodeFillColor()
                        : chosen ? theme.nodeSelectedFillColor() : hot ? theme.nodeHoverFillColor() : theme.nodeFillColor();
                g.fill(x - 1, y - 1, x + 27, y + 27, border);
                g.fill(x, y, x + 26, y + 26, fill);
                if (chosen) {
                    g.fill(x - 3, y - 3, x + 29, y - 2, border);
                    g.fill(x - 3, y + 28, x + 29, y + 29, border);
                    g.fill(x - 3, y - 3, x - 2, y + 29, border);
                    g.fill(x + 28, y - 3, x + 29, y + 29, border);
                }
                var display = displays.get(entry.id());
                if (display != null) display.drawIcon(g, x + 5, y + 5);
                if (entry.hangout()) CatalogBadgeRenderer.hangout(g, x + 19, y - 5);
                if (entry.pinned()) g.drawString(font, "◆", x + 20, y + 20, 0xFFD778, false);
                if (entry.tier() > 0) {
                    String tier = CatalogGraphLayout.roman(entry.tier());
                    g.drawCenteredString(font, tier, x + 13, y - 12, 0xD6DFC9);
                }
                g.pose().pushPose(); g.pose().translate(x + 13, y + 32, 0); g.pose().scale(0.75f, 0.75f, 1);
                var lines = font.split(Component.literal(entry.name()), 86);
                for (int i = 0; i < Math.min(2, lines.size()); i++)
                    g.drawString(font, lines.get(i), -font.width(lines.get(i)) / 2, i * 10,
                            node.match() ? 0xEFE9D8 : 0x7F9188, false);
                g.pose().popPose();
                if (!node.match()) g.fill(x, y, x + 26, y + 26, 0xAA10221B);
            }
        }
        g.pose().popPose(); g.disableScissor();
        if (layout.matches() == 0) g.drawWordWrap(font, Component.translatable("townstead.catalog.no_matches"),
                getX() + 12, getY() + 20, width - 24, 0xC5C4B1);
        if (isFocused()) g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, theme.frameColor());
    }
    public void renderTooltip(GuiGraphics g, int mx, int my) {
        var hovered = isMouseOver(mx, my) ? nodeAt(mx, my) : null;
        if (hovered != null && !armed) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.entry().name()));
            tip.add(Component.literal(hovered.entry().groupLabel()));
            var display = displays.get(hovered.entry().id());
            if (display != null && !display.modName().isEmpty()) tip.add(Component.literal(display.modName()));
            if (hovered.entry().hangout()) tip.add(Component.translatable("townstead.catalog.hangout_hint"));
            if (hovered.entry().recognized()) tip.add(Component.translatable("townstead.catalog.recognized"));
            if (!hovered.match()) tip.add(Component.translatable("townstead.catalog.context"));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }
    private CatalogGraphLayout.Node nodeAt(double mx, double my) {
        double x = camera.worldX(mx - getX()), y = camera.worldY(my - getY());
        for (var node : layout.nodes()) if (x >= node.x() - 18 && x < node.x() + 45
                && y >= node.y() - 4 && y < node.y() + 53) return node;
        return null;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isMouseOver(mx, my)) return false;
        startX = mx; startY = my; armed = true; dragging = false; return true;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!armed || button != 0) return false;
        if (!dragging && Math.hypot(mx - startX, my - startY) >= 3) {
            dragging = true; camera.pan(mx - startX, my - startY);
        } else if (dragging) camera.pan(dx, dy);
        return true;
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (!armed || button != 0) return false;
        if (!dragging && isMouseOver(mx, my)) {
            var node = nodeAt(mx, my);
            if (node != null) choose(node);
        }
        armed = false; dragging = false; return true;
    }
    public boolean scroll(double mx, double my, double amount) {
        if (!isMouseOver(mx, my)) return false;
        camera.zoomAt(amount, mx - getX(), my - getY()); return true;
    }
    private void choose(CatalogGraphLayout.Node node) {
        focusedSector = node.sector(); select.accept(node.entry().id());
        setMessage(Component.literal(node.entry().name()));
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_HOME) { fit(); return true; }
        int direction = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1
                : key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (direction == 0 || layout.nodes().isEmpty()) return false;
        int index = -1;
        for (int i = 0; i < layout.nodes().size(); i++) if (layout.nodes().get(i).entry().id().equals(selection.get())) {
            index = i; if (layout.nodes().get(i).sector().equals(focusedSector)) break;
        }
        var node = layout.nodes().get(Math.floorMod(index + direction, layout.nodes().size()));
        choose(node); revealSelected(); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("townstead.catalog.graph_keys"));
    }
}
