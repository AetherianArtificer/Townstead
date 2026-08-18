package com.aetherianartificer.townstead.client.root;

import com.mojang.blaze3d.systems.RenderSystem;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.gui.ability.AbilityWheelScreen;
import com.aetherianartificer.townstead.root.ability.ResourceSyncS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Composable, configurable HUD for horizontal, vertical and squircle resource meters. */
public final class ResourceHudOverlay {

    private static final int GAP = 4;

    private ResourceHudOverlay() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long now = System.currentTimeMillis();
        boolean abilityUiOpen = mc.screen instanceof AbilityWheelScreen;
        // The ability wheel deliberately keeps resources visible. Every other screen owns its
        // complete render stack, including the resource HUD editor's isolated live preview.
        if (mc.screen != null && !abilityUiOpen) return;
        List<ResourceClientStore.Visible> visible = ResourceClientStore.visible(now,
                ResourceHudConfig.visibility(), ResourceHudConfig.holdTicks(),
                ResourceHudConfig.fadeTicks(), abilityUiOpen);
        if (visible.isEmpty()) return;

        renderAnchoredGroups(graphics, visible, graphics.guiWidth(), graphics.guiHeight(), now, true);
    }

    /** Draws representative meters in the client configuration screen. */
    public static void renderPreview(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        List<ResourceClientStore.Visible> preview = List.of(
                new ResourceClientStore.Visible(previewBar("townstead:spirit/magical", 72, "horizontal",
                        "continuous", "TOP_LEFT", 0x3FA0FF, 0xC04AC0, 0xF5C7FF, true), 1f),
                new ResourceClientStore.Visible(previewBar("townstead:spirit/nautical", 46, "squircle",
                        "continuous", "TOP_RIGHT", 0x3FA0FF, 0x4A90B8, 0xA8ECFF, false), 1f),
                new ResourceClientStore.Visible(previewBar("townstead:spirit/industrious", 61, "vertical",
                        "pips", "BOTTOM_CENTER", 0x3FA0FF, 0xBF8A3A, 0xFFD27A, false), 1f));

        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0f);
            renderAnchoredGroups(graphics, preview, width, height, System.currentTimeMillis(), false);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    private static ResourceSyncS2CPayload.Bar previewBar(String id, int value, String shape,
                                                         String fillMode, String anchor,
                                                         int barColor, int framePrimaryColor,
                                                         int frameSecondaryColor, boolean gradient) {
        return new ResourceSyncS2CPayload.Bar(id, value, 0, 100, 100,
                barColor, shape, fillMode,
                gradient ? List.of(new ResourceSyncS2CPayload.Effect(
                        "townstead:gradient", 0.3f, "crosswise", -1, -1)) : List.of(),
                "townstead:spirit_trough", id, anchor, "DOTS", 10, 0,
                0xD80E1014, framePrimaryColor, frameSecondaryColor, 2, "", -1);
    }

    private static void renderAnchoredGroups(GuiGraphics graphics, List<ResourceClientStore.Visible> visible,
                                             int viewportWidth, int viewportHeight, long now,
                                             boolean avoidVanillaHud) {
        TownsteadConfig.ResourceHudAnchor selected = ResourceHudConfig.anchor();
        if (selected != TownsteadConfig.ResourceHudAnchor.PACK_DECIDED) {
            renderGroup(graphics, visible, viewportWidth, viewportHeight, now, avoidVanillaHud, selected);
            return;
        }

        Map<TownsteadConfig.ResourceHudAnchor, List<ResourceClientStore.Visible>> groups =
                new EnumMap<>(TownsteadConfig.ResourceHudAnchor.class);
        for (ResourceClientStore.Visible item : visible) {
            groups.computeIfAbsent(packAnchor(item.bar().anchor()), ignored -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<TownsteadConfig.ResourceHudAnchor, List<ResourceClientStore.Visible>> group
                : groups.entrySet()) {
            renderGroup(graphics, group.getValue(), viewportWidth, viewportHeight, now,
                    avoidVanillaHud, group.getKey());
        }
    }

    private static void renderGroup(GuiGraphics graphics, List<ResourceClientStore.Visible> visible,
                                    int viewportWidth, int viewportHeight, long now,
                                    boolean avoidVanillaHud, TownsteadConfig.ResourceHudAnchor anchor) {
        Minecraft mc = Minecraft.getInstance();

        boolean showValues = ResourceHudConfig.showValues();
        TownsteadConfig.ResourceHudStack stack = ResourceHudConfig.stack();
        List<Size> sizes = new ArrayList<>(visible.size());
        int groupWidth = 0;
        int groupHeight = 0;
        for (ResourceClientStore.Visible item : visible) {
            Size size = sizeOf(item.bar(), showValues);
            sizes.add(size);
            if (stack == TownsteadConfig.ResourceHudStack.DOWN) {
                groupWidth = Math.max(groupWidth, size.width());
                groupHeight += size.height();
            } else {
                groupWidth += size.width();
                groupHeight = Math.max(groupHeight, size.height());
            }
        }
        if (visible.size() > 1) {
            if (stack == TownsteadConfig.ResourceHudStack.DOWN) groupHeight += GAP * (visible.size() - 1);
            else groupWidth += GAP * (visible.size() - 1);
        }

        float scale = Math.max(0.5f, ResourceHudConfig.scale());
        int screenWidth = Math.max(1, (int) (viewportWidth / scale));
        int screenHeight = Math.max(1, (int) (viewportHeight / scale));
        int anchorHeight = screenHeight;
        if (avoidVanillaHud && isBottomAnchor(anchor)) {
            int inset = vanillaHudBottomInset(mc);
            anchorHeight = Math.max(1, screenHeight - (int) Math.ceil(inset / scale));
        }
        int x = anchorX(anchor, screenWidth, groupWidth, ResourceHudConfig.offsetX());
        int y = anchorY(anchor, anchorHeight, groupHeight, ResourceHudConfig.offsetY());
        x = Math.max(0, Math.min(Math.max(0, screenWidth - groupWidth), x));
        y = Math.max(0, Math.min(Math.max(0, anchorHeight - groupHeight), y));

        List<Placed> placed = new ArrayList<>(visible.size());
        int cursorX = x;
        int cursorY = y;
        for (int i = 0; i < visible.size(); i++) {
            ResourceClientStore.Visible item = visible.get(i);
            ResourceSyncS2CPayload.Bar bar = item.bar();
            placed.add(new Placed(bar, cursorX, cursorY, item.alpha(),
                    ResourceHudMath.normalized(bar.value(), bar.min(), bar.max()),
                    withAlpha(0xFF000000 | bar.color(), item.alpha()), normalized(bar.shape())));
            if (stack == TownsteadConfig.ResourceHudStack.DOWN) cursorY += sizes.get(i).height() + GAP;
            else cursorX += sizes.get(i).width() + GAP;
        }

        graphics.pose().pushPose();
        try {
            graphics.pose().scale(scale, scale, 1f);

            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0f, 0f, 10f);
                for (Placed item : placed) renderMeterLayer(graphics, item, now);
            } finally {
                graphics.pose().popPose();
            }

            if (showValues) {
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(0f, 0f, 20f);
                    for (Placed item : placed) renderLabelLayer(graphics, mc, item);
                } finally {
                    graphics.pose().popPose();
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void renderMeterLayer(GuiGraphics graphics, Placed item, long now) {
        if ("vertical".equals(item.shape())) {
            renderVertical(graphics, item.bar(), item.x(), item.y(), item.fraction(),
                    item.fillColor(), item.alpha(), now);
        } else if (isSquircle(item.shape())) {
            renderSquircle(graphics, item.bar(), item.x(), item.y(), item.fraction(),
                    item.fillColor(), item.alpha(), now);
        } else {
            renderHorizontal(graphics, item.bar(), item.x(), item.y(), item.fraction(),
                    item.fillColor(), item.alpha(), now);
        }
    }

    private static void renderLabelLayer(GuiGraphics graphics, Minecraft mc, Placed item) {
        if ("vertical".equals(item.shape())) {
            drawValue(graphics, mc, item.bar(), item.x() + (hasSprite(item.bar()) ? 10 : 7),
                    item.y() + (hasSprite(item.bar()) ? 70 : 66), item.alpha(), true);
        } else if (isSquircle(item.shape())) {
            drawValue(graphics, mc, item.bar(), item.x() + 20, item.y() + 43, item.alpha(), true);
        } else {
            drawValue(graphics, mc, item.bar(), item.x() + (hasSprite(item.bar()) ? 104 : 86),
                    item.y() + (hasSprite(item.bar()) ? 4 : 1), item.alpha(), false);
        }
    }

    private static void renderHorizontal(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                         int x, int y, float fraction, int fillColor, float alpha, long now) {
        boolean sprite = hasSprite(bar);
        int width = sprite ? 100 : 82;
        int height = sprite ? 16 : 10;
        frame(graphics, bar, x, y, width, height, alpha);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = x + (sprite ? 10 : t);
        int innerY = y + (sprite ? 4 : t);
        int innerWidth = sprite ? 80 : Math.max(1, width - t * 2);
        int innerHeight = sprite ? 8 : Math.max(1, height - t * 2);
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, false);
    }

    private static void renderVertical(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                       int x, int y, float fraction, int fillColor, float alpha, long now) {
        boolean sprite = hasSprite(bar);
        int width = sprite ? 20 : 14;
        int height = sprite ? 68 : 64;
        frame(graphics, bar, x, y, width, height, alpha);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = x + (sprite ? 6 : t);
        int innerY = y + (sprite ? 6 : t);
        int innerWidth = sprite ? 8 : Math.max(1, width - t * 2);
        int innerHeight = sprite ? 56 : Math.max(1, height - t * 2);
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, true);
    }

    private static void renderLinear(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                     int x, int y, int width, int height, float fraction,
                                     int fillColor, boolean vertical) {
        String mode = normalized(bar.fillMode());
        int length = vertical ? height : width;
        if ("continuous".equals(mode)) {
            int filled = Math.round(length * fraction);
            if (filled <= 0) return;
            if (vertical) {
                int top = y + height - filled;
                fillBarRect(graphics, bar, x, top, x + width, y + height, fillColor, true);
            } else {
                fillBarRect(graphics, bar, x, y, x + filled, y + height, fillColor, false);
            }
            return;
        }

        int units = Math.max(2, Math.min(bar.segments(), Math.max(2, length / 3)));
        if ("pips".equals(mode)) {
            renderLinearPips(graphics, bar, x, y, width, height, fraction, fillColor, vertical, units);
            return;
        }
        int whole = -1;
        for (int i = 0; i < units; i++) {
            int start = Math.round(i * length / (float) units);
            int end = Math.round((i + 1) * length / (float) units) - 1;
            if (end <= start) continue;
            float unitFill = whole >= 0 ? (i < whole ? 1f : 0f)
                    : Math.max(0f, Math.min(1f, fraction * units - i));
            if (unitFill <= 0f) continue;
            int filled = Math.max(1, Math.round((end - start) * unitFill));
            if (vertical) {
                int bottom = y + height - start;
                fillBarRect(graphics, bar, x, bottom - filled, x + width, bottom,
                        fillColor, true);
            } else {
                fillBarRect(graphics, bar, x + start, y, x + start + filled, y + height,
                        fillColor, false);
            }
        }
    }

    private static void renderLinearPips(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                         int x, int y, int width, int height, float fraction,
                                         int fillColor, boolean vertical, int units) {
        int filled = ResourceHudMath.filledUnits(fraction, units);
        int empty = darken(bar.backgroundColor(), 0.72f);
        for (int i = 0; i < units; i++) {
            float along = (i + 0.5f) / units;
            int px = vertical ? x + width / 2 : x + Math.round(along * (width - 1));
            int py = vertical ? y + height - 1 - Math.round(along * (height - 1)) : y + height / 2;
            int color = i < filled
                    ? applyGradientEffects(bar, fillColor, 0.82f, along, 0.64f) : empty;
            int highlight = i < filled
                    ? applyGradientEffects(bar, lighten(fillColor, 0.45f), 0.08f, along, 0.92f)
                    : darken(empty, 0.72f);
            drawPip(graphics, px, py, bar.pipStyle(), color, highlight, vertical);
        }
    }

    /** Pixel-art marks centered at (x,y); pips remain marks rather than miniature bar slices. */
    private static void drawPip(GuiGraphics graphics, int x, int y, String rawStyle,
                                int color, int highlight, boolean vertical) {
        String style = normalized(rawStyle);
        switch (style) {
            case "notches", "notch", "ticks" -> {
                if (vertical) graphics.fill(x - 1, y, x + 2, y + 1, color);
                else graphics.fill(x, y - 1, x + 1, y + 2, color);
            }
            case "beads", "bead", "pearls" -> {
                graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
                graphics.fill(x, y - 1, x + 1, y, highlight);
                graphics.fill(x - 1, y, x, y + 1, highlight);
            }
            case "shards", "shard", "crystals" -> {
                if (vertical) {
                    graphics.fill(x - 1, y - 2, x + 1, y + 2, color);
                    graphics.fill(x + 1, y - 1, x + 2, y + 1, color);
                    graphics.fill(x, y - 2, x + 1, y - 1, highlight);
                } else {
                    graphics.fill(x - 2, y - 1, x + 2, y + 1, color);
                    graphics.fill(x - 1, y + 1, x + 1, y + 2, color);
                    graphics.fill(x - 2, y, x - 1, y + 1, highlight);
                }
            }
            default -> {
                graphics.fill(x, y - 1, x + 1, y + 2, color);
                graphics.fill(x - 1, y, x + 2, y + 1, color);
                graphics.fill(x, y - 1, x + 1, y, highlight);
            }
        }
    }

    private static boolean insideRoundedSquare(int x, int y, int half, int corner) {
        int ax = Math.abs(x);
        int ay = Math.abs(y);
        if (ax > half || ay > half) return false;
        int straight = half - corner;
        if (ax <= straight || ay <= straight) return true;
        int dx = ax - straight;
        int dy = ay - straight;
        return dx * dx + dy * dy <= corner * corner;
    }

    /** Clockwise square-perimeter coordinate, starting at top centre. */
    private static int[] squirclePoint(float fraction, int radius) {
        float position = (fraction - (float) Math.floor(fraction)) * 8f;
        if (position < 1f) return new int[]{Math.round(position * radius), -radius};
        if (position < 3f) return new int[]{radius, Math.round((position - 2f) * radius)};
        if (position < 5f) return new int[]{Math.round((4f - position) * radius), radius};
        if (position < 7f) return new int[]{-radius, Math.round((6f - position) * radius)};
        return new int[]{Math.round((position - 8f) * radius), -radius};
    }

    private static void renderSquircle(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                       int x, int y, float fraction, int fillColor, float alpha, long now) {
        int centerX = x + 20;
        int centerY = y + 20;
        if (hasSprite(bar) && !Accessibility.highContrast()) frame(graphics, bar, x, y, 40, 40, alpha);
        else squircleFrame(graphics, bar, centerX, centerY, alpha);
        String mode = normalized(bar.fillMode());
        int empty = withAlpha(darken(bar.backgroundColor(), 0.55f), alpha);
        int units = Math.max(2, Math.min(32, bar.segments()));

        if ("pips".equals(mode)) {
            int filled = ResourceHudMath.filledUnits(fraction, units);
            for (int i = 0; i < units; i++) {
                int[] point = squirclePoint(i / (float) units, 13);
                int px = centerX + point[0];
                int py = centerY + point[1];
                float along = (i + 0.5f) / units;
                int pipColor = applyGradientEffects(bar, fillColor, 0.82f, along, 0.64f);
                int pipHighlight = applyGradientEffects(
                        bar, lighten(fillColor, 0.45f), 0.08f, along, 0.92f);
                drawPip(graphics, px, py, bar.pipStyle(), i < filled ? pipColor : empty,
                        i < filled ? pipHighlight : darken(empty, 0.72f), false);
            }
        } else {
            float shownFraction = "segmented".equals(mode)
                    ? ResourceHudMath.filledUnits(fraction, units) / (float) units : fraction;
            for (int py = -15; py <= 15; py++) {
                for (int px = -15; px <= 15; px++) {
                    if (!insideRoundedSquare(px, py, 15, 5)
                            || insideRoundedSquare(px, py, 10, 3)) continue;
                    double angle = Math.atan2(px, -py);
                    if (angle < 0d) angle += Math.PI * 2d;
                    float around = (float) (angle / (Math.PI * 2d));
                    boolean gap = "segmented".equals(mode) && ((around * units) % 1f) < 0.10f;
                    float cross = (py + 15) / 30f;
                    float along = shownFraction <= 0f ? 0f
                            : Math.max(0f, Math.min(1f, around / shownFraction));
                    float radial = Math.max(0f, Math.min(1f,
                            (Math.max(Math.abs(px), Math.abs(py)) - 10) / 5f));
                    int color = !gap && around <= shownFraction
                            ? applyGradientEffects(bar, fillColor, cross, along, radial) : empty;
                    graphics.fill(centerX + px, centerY + py,
                            centerX + px + 1, centerY + py + 1, color);
                }
            }
        }

        if (fraction > 0f && fraction < 1f && !"pips".equals(mode)) {
            int[] inner = squirclePoint(fraction, 10);
            int[] outer = squirclePoint(fraction, 15);
            int x1 = centerX + inner[0];
            int y1 = centerY + inner[1];
            int x2 = centerX + outer[0];
            int y2 = centerY + outer[1];
            drawLine(graphics, x1, y1, x2, y2,
                    applyGradientEffects(bar, lighten(fillColor, 0.45f), 0.5f, 1f, 1f));
        }
    }

    /** Rounded-square trough modeled after the original GIF: transparent corners and centre. */
    private static void squircleFrame(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                    int centerX, int centerY, float alpha) {
        int background = withAlpha(bar.backgroundColor(), alpha);
        int light = withAlpha(Accessibility.highContrast() ? 0xFFFFFFFF : bar.frameSecondaryColor(), alpha);
        int dark = withAlpha(Accessibility.highContrast() ? 0xFF000000 : bar.framePrimaryColor(), alpha);
        for (int py = -18; py <= 18; py++) {
            for (int px = -18; px <= 18; px++) {
                if (!insideRoundedSquare(px, py, 18, 6)
                        || insideRoundedSquare(px, py, 8, 2)) continue;
                int color;
                if (!insideRoundedSquare(px, py, 16, 5)) {
                    color = px + py < 0 ? light : dark;
                } else if (insideRoundedSquare(px, py, 10, 3)) {
                    color = px + py < 0 ? dark : light;
                } else {
                    color = background;
                }
                graphics.fill(centerX + px, centerY + py,
                        centerX + px + 1, centerY + py + 1, color);
            }
        }
    }

    private static void frame(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                              int x, int y, int width, int height, float alpha) {
        if (hasSprite(bar) && !Accessibility.highContrast()) {
            ResourceLocation texture = ResourceLocation.tryParse(bar.frameTexture());
            if (texture != null) {
                int u;
                int sourceWidth;
                if (width >= 80) {
                    u = 16;
                    sourceWidth = 112;
                } else if (height >= 60) {
                    u = 128;
                    sourceWidth = 48;
                } else {
                    u = 176;
                    sourceWidth = 64;
                }
                int v = Math.max(0, Math.min(11, bar.frameSpriteRow())) * 32;
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                try {
                    graphics.blit(texture, x, y, width, height, (float) u, (float) v,
                            sourceWidth, 32, 256, 384);
                } finally {
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
                return;
            }
        }
        int thickness = Math.max(1, Math.min(4, bar.frameThickness()));
        int background = withAlpha(bar.backgroundColor(), alpha);
        int light = withAlpha(Accessibility.highContrast() ? 0xFFFFFFFF : bar.frameSecondaryColor(), alpha);
        int dark = withAlpha(Accessibility.highContrast() ? 0xFF000000 : bar.framePrimaryColor(), alpha);
        graphics.fill(x, y, x + width, y + height, background);
        for (int i = 0; i < thickness; i++) {
            graphics.fill(x + i, y + i, x + width - i, y + i + 1, light);
            graphics.fill(x + i, y + i, x + i + 1, y + height - i, light);
            graphics.fill(x + i, y + height - i - 1, x + width - i, y + height - i, dark);
            graphics.fill(x + width - i - 1, y + i, x + width - i, y + height - i, dark);
        }
    }

    private static void drawValue(GuiGraphics graphics, Minecraft mc, ResourceSyncS2CPayload.Bar bar,
                                  int x, int y, float alpha, boolean centered) {
        String text = bar.value() + "/" + bar.max();
        int color = withAlpha(0xFFFFFFFF, alpha);
        int drawX = centered ? x - mc.font.width(text) / 2 : x;
        graphics.drawString(mc.font, text, drawX, y, color, true);
    }

    private static int darken(int color, float factor) {
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        int r = Math.round(((color >> 16) & 0xFF) * factor);
        int g = Math.round(((color >> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return withAlpha(0xFF000000 | (r << 16) | (g << 8) | b, alpha);
    }

    private static void fillBarRect(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                    int x1, int y1, int x2, int y2, int color,
                                    boolean verticalBar) {
        int width = Math.max(1, x2 - x1);
        int height = Math.max(1, y2 - y1);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float cross = verticalBar ? normalizedPixel(px, width) : normalizedPixel(py, height);
                float along = verticalBar
                        ? 1f - normalizedPixel(py, height) : normalizedPixel(px, width);
                float radial = Math.abs(cross - 0.5f) * 2f;
                int effected = applyGradientEffects(bar, color, cross, along, radial);
                graphics.fill(x1 + px, y1 + py, x1 + px + 1, y1 + py + 1, effected);
            }
        }
    }

    private static float normalizedPixel(int position, int span) {
        return span <= 1 ? 0.5f : position / (float) (span - 1);
    }

    private static int applyGradientEffects(ResourceSyncS2CPayload.Bar bar, int color,
                                            float cross, float along, float radial) {
        int result = color;
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if (!"townstead:gradient".equals(effect.type()) || effect.strength() <= 0f) continue;
            float coordinate = switch (normalized(effect.gradientShape())) {
                case "along", "leading_edge" -> along;
                case "radial" -> radial;
                default -> cross;
            };
            result = gradientEffectColor(result, coordinate, effect);
        }
        return result;
    }

    private static int gradientEffectColor(int color, float coordinate,
                                           ResourceSyncS2CPayload.Effect effect) {
        float n = Math.max(0f, Math.min(1f, coordinate));
        int highlight = effect.highlightColor() < 0 ? 0xFFFFFF : effect.highlightColor();
        int shadow = effect.shadowColor() < 0 ? 0x000000 : effect.shadowColor();
        float strength = effect.strength();
        return switch (normalized(effect.gradientShape())) {
            case "centered" -> {
                float edge = Math.abs(n - 0.5f) * 2f;
                if (edge < 0.5f) yield blend(color, highlight, strength * (1f - edge * 2f));
                yield blend(color, shadow, strength * ((edge - 0.5f) * 2f));
            }
            case "leading_edge" -> blend(color, highlight, strength * n);
            case "radial" -> blend(color, n < 0.5f ? shadow : highlight,
                    strength * Math.abs(n - 0.5f) * 2f);
            default -> {
                if (n < 0.5f) yield blend(color, highlight, strength * (1f - n * 2f));
                yield blend(color, shadow, strength * ((n - 0.5f) * 2f));
            }
        };
    }

    private static int blend(int color, int target, float amount) {
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        float clamped = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(((color >> 16) & 0xFF)
                + (((target >> 16) & 0xFF) - ((color >> 16) & 0xFF)) * clamped);
        int g = Math.round(((color >> 8) & 0xFF)
                + (((target >> 8) & 0xFF) - ((color >> 8) & 0xFF)) * clamped);
        int b = Math.round((color & 0xFF) + ((target & 0xFF) - (color & 0xFF)) * clamped);
        return withAlpha(0xFF000000 | (r << 16) | (g << 8) | b, alpha);
    }

    private static int lighten(int color, float amount) {
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        int r = Math.round(((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount);
        int g = Math.round(((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount);
        int b = Math.round((color & 0xFF) + (255 - (color & 0xFF)) * amount);
        return withAlpha(0xFF000000 | (r << 16) | (g << 8) | b, alpha);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static Size sizeOf(ResourceSyncS2CPayload.Bar bar, boolean showValues) {
        boolean sprite = hasSprite(bar);
        return switch (normalized(bar.shape())) {
            case "vertical" -> new Size(showValues ? 46 : (sprite ? 20 : 14),
                    showValues ? (sprite ? 80 : 76) : (sprite ? 68 : 64));
            case "squircle", "ring" -> new Size(40, showValues ? 54 : 40);
            default -> new Size(showValues ? (sprite ? 146 : 128) : (sprite ? 100 : 82),
                    sprite ? 16 : 10);
        };
    }

    private static int anchorX(TownsteadConfig.ResourceHudAnchor anchor, int screen, int group, int offset) {
        return switch (anchor) {
            case TOP_CENTER, BOTTOM_CENTER -> (screen - group) / 2 + offset;
            case TOP_RIGHT, BOTTOM_RIGHT -> screen - group - offset;
            default -> offset;
        };
    }

    private static int anchorY(TownsteadConfig.ResourceHudAnchor anchor, int screen, int group, int offset) {
        return switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screen - group - offset;
            default -> offset;
        };
    }

    private static boolean isBottomAnchor(TownsteadConfig.ResourceHudAnchor anchor) {
        return anchor == TownsteadConfig.ResourceHudAnchor.BOTTOM_LEFT
                || anchor == TownsteadConfig.ResourceHudAnchor.BOTTOM_CENTER
                || anchor == TownsteadConfig.ResourceHudAnchor.BOTTOM_RIGHT;
    }

    private static TownsteadConfig.ResourceHudAnchor packAnchor(String raw) {
        try {
            TownsteadConfig.ResourceHudAnchor parsed = TownsteadConfig.ResourceHudAnchor.valueOf(
                    normalized(raw).toUpperCase(Locale.ROOT));
            return parsed == TownsteadConfig.ResourceHudAnchor.PACK_DECIDED
                    ? TownsteadConfig.ResourceHudAnchor.TOP_LEFT : parsed;
        } catch (IllegalArgumentException ignored) {
            return TownsteadConfig.ResourceHudAnchor.TOP_LEFT;
        }
    }

    private static int vanillaHudBottomInset(Minecraft mc) {
        if (mc.player == null) return 0;
        // Survival status rows occupy roughly the lowest 49 GUI pixels. Leave a small gap so
        // resources stack above health/food/armour like the other survival HUD meters.
        return !mc.player.isCreative() && !mc.player.isSpectator() ? 54 : 26;
    }

    private static String normalized(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSquircle(String shape) {
        String normalized = normalized(shape);
        return "squircle".equals(normalized) || "ring".equals(normalized);
    }

    private static boolean hasSprite(ResourceSyncS2CPayload.Bar bar) {
        return bar.frameSpriteRow() >= 0 && bar.frameTexture() != null && !bar.frameTexture().isBlank();
    }

    private record Size(int width, int height) {}

    private record Placed(ResourceSyncS2CPayload.Bar bar, int x, int y, float alpha,
                          float fraction, int fillColor, String shape) {}
}
