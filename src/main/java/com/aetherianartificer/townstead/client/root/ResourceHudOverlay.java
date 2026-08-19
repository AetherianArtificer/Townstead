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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Composable, configurable HUD for horizontal, vertical and squircle resource meters. */
public final class ResourceHudOverlay {

    private static final int GAP = 4;
    private static final Map<String, LiquidSurface> LIQUID_SURFACES = new HashMap<>();
    private static final Map<String, ViscousBoundary> VISCOUS_BOUNDARIES = new HashMap<>();

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
                        "townstead:gradient", 0.3f, 1f, 3.6f, 1f, -1,
                        "crosswise", -1, -1)) : List.of(),
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
                for (Placed item : placed) renderEscapingEmbers(graphics, item, now);
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

    /** Foreground pass for particles intentionally allowed to travel beyond the frame. */
    private static void renderEscapingEmbers(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = emberEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f
                || effect.emberEscape() <= 0f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean sprite = hasSprite(bar);
        String shape = item.shape();
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x517CC1B7;
        int particles = Math.max(1, Math.round(effect.emberCount()
                * (0.55f + effect.emberEscape() * 0.45f)));
        for (int ember = 0; ember < particles; ember++) {
            int hash = seed ^ (ember * 0x27d4eb2d);
            hash ^= hash >>> 15;
            float lane = (hash & 0xFFFF) / 65535f;
            float offset = ((hash >>> 16) & 0xFFFF) / 65535f;
            float speedVariance = 0.74f + ((hash >>> 7) & 0xFF) / 255f * 0.52f;
            float progress = now / 3100f * effect.speed() * speedVariance + offset;
            progress -= (float) Math.floor(progress);

            int sourceX;
            int sourceY;
            if ("vertical".equals(shape)) {
                int width = sprite ? 20 : 14;
                int innerX = item.x() + (sprite ? 6 : t);
                int innerWidth = sprite ? 8 : Math.max(1, width - t * 2);
                sourceX = innerX + Math.round(lane * Math.max(0, innerWidth - 1));
                sourceY = item.y() - 1;
            } else if (isSquircle(shape)) {
                sourceX = item.x() + 20 + Math.round((lane - 0.5f) * 20f);
                sourceY = item.y() + 1 + Math.round(Math.abs(lane - 0.5f) * 5f);
            } else {
                int width = sprite ? 100 : 82;
                int innerX = item.x() + (sprite ? 10 : t);
                int innerWidth = sprite ? 80 : Math.max(1, width - t * 2);
                int filledWidth = Math.max(1, Math.round(innerWidth * item.fraction()));
                sourceX = innerX + Math.round(lane * Math.max(0, filledWidth - 1));
                sourceY = item.y() - 1;
            }

            float meander = (float) Math.sin(progress * Math.PI * 3.6d + ember * 1.91d);
            float wind = (((hash >>> 12) & 3) - 1.5f) * progress;
            int px = sourceX + Math.round((meander * 4f + wind * 2f) * effect.emberDrift());
            int rise = Math.round(1f + progress * (6f + effect.emberEscape() * 10f));
            int py = sourceY - rise;
            float life = (float) Math.sin(progress * Math.PI);
            float shimmer = 0.5f + 0.5f * (float) Math.sin(
                    now / 82f * (0.84f + (hash & 7) * 0.06f) + ember * 2.17f);
            float opacity = item.alpha() * accessibility * effect.strength()
                    * life * (0.72f + effect.emberEscape() * 0.28f);
            if (opacity <= 0.025f) continue;

            int hot = effect.color() < 0 ? 0xFFD66B : effect.color();
            int cool = effect.shadowColor() < 0 ? 0xE54B1B : effect.shadowColor();
            int target = blend(cool, hot, (1f - progress) * (0.72f + shimmer * 0.28f));
            int core = withAlpha(0xFF000000 | target, opacity);
            int tail = withAlpha(0xFF000000 | cool, opacity * 0.42f);
            graphics.fill(px, py + 1, px + 1, py + 2, tail);
            graphics.fill(px, py, px + 1, py + 1, core);
            float flareThreshold = 1f - (0.06f + effect.emberFlicker() * 0.20f);
            if (shimmer > flareThreshold && progress < 0.72f) {
                int flare = withAlpha(0xFF000000 | hot, opacity * 0.52f);
                graphics.fill(px - 1, py, px, py + 1, flare);
                graphics.fill(px + 1, py, px + 2, py + 1, flare);
                graphics.fill(px, py - 1, px + 1, py, flare);
            }
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
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, false, now);
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
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, true, now);
    }

    private static void renderLinear(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                     int x, int y, int width, int height, float fraction,
                                     int fillColor, boolean vertical, long now) {
        String mode = normalized(bar.fillMode());
        int length = vertical ? height : width;
        ResourceSyncS2CPayload.Effect viscous = viscousEffect(bar);
        float effectAccessibility = Accessibility.effectIntensity();
        ViscousBoundary viscousBoundary = viscous != null && effectAccessibility > 0f
                ? viscousBoundary(bar, viscous, now) : null;
        float displayedFraction = viscousBoundary == null ? fraction
                : viscousAverage(viscousBoundary, fraction, viscous, effectAccessibility);
        if ("continuous".equals(mode)) {
            ResourceSyncS2CPayload.Effect liquid = liquidEffect(bar);
            if (liquid != null && effectAccessibility > 0f) {
                renderLiquidLinear(graphics, bar, x, y, width, height, fraction,
                        fillColor, vertical, now, liquid, viscous, viscousBoundary);
                return;
            }
            if (viscousBoundary != null) {
                renderViscousLinear(graphics, bar, x, y, width, height,
                        fillColor, vertical, now, viscous, viscousBoundary);
                return;
            }
            int filled = Math.round(length * displayedFraction);
            if (filled <= 0) return;
            if (vertical) {
                int top = y + height - filled;
                fillBarRect(graphics, bar, x, top, x + width, y + height, fillColor, true,
                        x, y, width, height, now);
            } else {
                fillBarRect(graphics, bar, x, y, x + filled, y + height, fillColor, false,
                        x, y, width, height, now);
            }
            return;
        }

        int units = Math.max(2, Math.min(bar.segments(), Math.max(2, length / 3)));
        if ("pips".equals(mode)) {
            renderLinearPips(graphics, bar, x, y, width, height, displayedFraction,
                    fillColor, vertical, units, now);
            return;
        }
        int whole = -1;
        for (int i = 0; i < units; i++) {
            int start = Math.round(i * length / (float) units);
            int end = Math.round((i + 1) * length / (float) units) - 1;
            if (end <= start) continue;
            float unitFill = whole >= 0 ? (i < whole ? 1f : 0f)
                    : Math.max(0f, Math.min(1f, displayedFraction * units - i));
            if (unitFill <= 0f) continue;
            int filled = Math.max(1, Math.round((end - start) * unitFill));
            if (vertical) {
                int bottom = y + height - start;
                fillBarRect(graphics, bar, x, bottom - filled, x + width, bottom,
                        fillColor, true, x, y, width, height, now);
            } else {
                fillBarRect(graphics, bar, x + start, y, x + start + filled, y + height,
                        fillColor, false, x, y, width, height, now);
            }
        }
    }

    private static void renderLiquidLinear(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                           int x, int y, int width, int height, float fraction,
                                           int fillColor, boolean vertical, long now,
                                           ResourceSyncS2CPayload.Effect liquid,
                                           ResourceSyncS2CPayload.Effect viscous,
                                           ViscousBoundary viscousBoundary) {
        if (fraction <= 0f && viscousBoundary == null) return;
        float accessibility = Accessibility.effectIntensity();
        LiquidSurface liquidSurface = liquidSurface(bar, liquid, now);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float cross = vertical ? normalizedPixel(px, width) : normalizedPixel(py, height);
                float pathAlong = vertical
                        ? 1f - normalizedPixel(py, height) : normalizedPixel(px, width);
                float surface;
                int crestY = -1;
                boolean filledPixel;
                if (vertical) {
                    float displacement = liquidSurface.sample(cross)
                            * liquid.strength() * accessibility * 4f;
                    float boundaryFraction = viscousBoundary == null
                            ? fraction : viscousSample(viscousBoundary, cross, fraction,
                            viscous, accessibility);
                    surface = height * (1f - boundaryFraction)
                            - displacement;
                    filledPixel = py + 0.5f >= surface;
                    crestY = Math.max(0, Math.min(height - 1,
                            (int) Math.ceil(surface - 0.5f)));
                } else {
                    float boundaryFraction = viscousBoundary == null
                            ? fraction : viscousSample(viscousBoundary, cross, fraction,
                            viscous, accessibility);
                    if (px + 0.5f > width * boundaryFraction) continue;
                    float displacement = liquidSurface.sample(pathAlong)
                            * liquid.strength() * accessibility * 4f;
                    surface = Math.max(0.25f, Math.min(height - 0.5f,
                            height * 0.18f - displacement));
                    filledPixel = py + 0.5f >= surface;
                    crestY = Math.max(0, Math.min(height - 1,
                            (int) Math.ceil(surface - 0.5f)));
                }
                if (!filledPixel) continue;

                float along = pathAlong;
                float radial = Math.abs(cross - 0.5f) * 2f;
                int screenX = x + px;
                int screenY = y + py;
                int color = applyBarEffects(bar, fillColor, cross, along, radial,
                        pathAlong, screenX, screenY, now);
                if (py == crestY) {
                    color = liquidSurfaceColor(color, liquid, accessibility, 1f);
                }
                if (viscous != null) {
                    boolean viscousEdge = vertical ? py == crestY
                            : Math.abs(px + 0.5f - width * viscousSample(
                            viscousBoundary, cross, fraction, viscous, accessibility)) < 1f;
                    if (viscousEdge) color = viscousEdgeColor(color, viscous, accessibility);
                }
                graphics.fill(screenX, screenY, screenX + 1, screenY + 1, color);
            }
        }
    }

    private static void renderViscousLinear(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                            int x, int y, int width, int height,
                                            int fillColor, boolean vertical, long now,
                                            ResourceSyncS2CPayload.Effect viscous,
                                            ViscousBoundary boundary) {
        float accessibility = Accessibility.effectIntensity();
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float cross = vertical ? normalizedPixel(px, width) : normalizedPixel(py, height);
                float pathAlong = vertical
                        ? 1f - normalizedPixel(py, height) : normalizedPixel(px, width);
                float boundaryFraction = viscousSample(boundary, cross,
                        bar.max() == bar.min() ? 0f
                                : (bar.value() - bar.min()) / (float) (bar.max() - bar.min()),
                        viscous, accessibility);
                float edge = vertical
                        ? height * (1f - boundaryFraction)
                        : width * boundaryFraction;
                boolean filled = vertical ? py + 0.5f >= edge : px + 0.5f <= edge;
                if (!filled) continue;
                int screenX = x + px;
                int screenY = y + py;
                int color = applyBarEffects(bar, fillColor, cross, pathAlong,
                        Math.abs(cross - 0.5f) * 2f, pathAlong, screenX, screenY, now);
                if (Math.abs((vertical ? py : px) + 0.5f - edge) < 1f) {
                    color = viscousEdgeColor(color, viscous, accessibility);
                }
                graphics.fill(screenX, screenY, screenX + 1, screenY + 1, color);
            }
        }
    }

    private static void renderLinearPips(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                         int x, int y, int width, int height, float fraction,
                                         int fillColor, boolean vertical, int units, long now) {
        int filled = ResourceHudMath.filledUnits(fraction, units);
        int empty = darken(bar.backgroundColor(), 0.72f);
        ResourceSyncS2CPayload.Effect liquid = liquidEffect(bar);
        float liquidAccessibility = Accessibility.effectIntensity();
        LiquidSurface liquidSurface = liquid != null && liquidAccessibility > 0f
                ? liquidSurface(bar, liquid, now) : null;
        for (int i = 0; i < units; i++) {
            float along = (i + 0.5f) / units;
            int px = vertical ? x + width / 2 : x + Math.round(along * (width - 1));
            int py = vertical ? y + height - 1 - Math.round(along * (height - 1)) : y + height / 2;
            if (liquidSurface != null) {
                int bob = Math.round(liquidSurface.sample(along)
                        * liquid.strength() * liquidAccessibility * 1.5f);
                if (vertical) px += bob;
                else py += bob;
            }
            int color = i < filled
                    ? applyBarEffects(bar, fillColor, 0.82f, along, 0.64f,
                    along, px, py, now) : empty;
            int highlight = i < filled
                    ? applyBarEffects(bar, lighten(fillColor, 0.45f), 0.08f, along, 0.92f,
                    along, px, py, now)
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
        ResourceSyncS2CPayload.Effect liquid = liquidEffect(bar);
        ResourceSyncS2CPayload.Effect viscous = viscousEffect(bar);
        float effectAccessibility = Accessibility.effectIntensity();
        LiquidSurface liquidSurface = liquid != null && effectAccessibility > 0f
                ? liquidSurface(bar, liquid, now) : null;
        ViscousBoundary viscousBoundary = viscous != null && effectAccessibility > 0f
                ? viscousBoundary(bar, viscous, now) : null;
        float displayedFraction = viscousBoundary == null ? fraction
                : viscousAverage(viscousBoundary, fraction, viscous, effectAccessibility);

        if ("pips".equals(mode)) {
            int filled = ResourceHudMath.filledUnits(displayedFraction, units);
            for (int i = 0; i < units; i++) {
                float along = (i + 0.5f) / units;
                int radius = liquidSurface != null
                        ? 13 + Math.round(liquidSurface.sample(along)
                        * liquid.strength() * effectAccessibility * 1.5f) : 13;
                int[] point = squirclePoint(i / (float) units, radius);
                int px = centerX + point[0];
                int py = centerY + point[1];
                int pipColor = applyBarEffects(bar, fillColor, 0.82f, along, 0.64f,
                        along, px, py, now);
                int pipHighlight = applyBarEffects(
                        bar, lighten(fillColor, 0.45f), 0.08f, along, 0.92f,
                        along, px, py, now);
                drawPip(graphics, px, py, bar.pipStyle(), i < filled ? pipColor : empty,
                        i < filled ? pipHighlight : darken(empty, 0.72f), false);
            }
        } else {
            float shownFraction = "segmented".equals(mode)
                    ? ResourceHudMath.filledUnits(displayedFraction, units) / (float) units
                    : displayedFraction;
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
                    float liquidFront = viscousBoundary != null && "continuous".equals(mode)
                            ? viscousSample(viscousBoundary, radial, fraction,
                            viscous, effectAccessibility) : shownFraction;
                    if (liquidSurface != null
                            && "continuous".equals(mode) && shownFraction > 0f && shownFraction < 1f) {
                        liquidFront = Math.max(0f, Math.min(1f, liquidFront
                                + liquidSurface.sample(radial) * liquid.strength()
                                * effectAccessibility * 0.045f));
                    }
                    int color = !gap && around <= liquidFront
                            ? applyBarEffects(bar, fillColor, cross, along, radial, along,
                            centerX + px, centerY + py, now) : empty;
                    if (color != empty && "continuous".equals(mode)) {
                        float distance = Math.abs(around - liquidFront);
                        if (liquid != null && effectAccessibility > 0f && distance < 0.025f) {
                            color = liquidSurfaceColor(color, liquid,
                                    effectAccessibility, 1f - distance / 0.025f);
                        }
                        if (viscous != null && effectAccessibility > 0f && distance < 0.025f) {
                            color = viscousEdgeColor(color, viscous, effectAccessibility);
                        }
                    }
                    graphics.fill(centerX + px, centerY + py,
                            centerX + px + 1, centerY + py + 1, color);
                }
            }
        }

        boolean effectOwnsEndCap = (liquid != null || viscous != null) && effectAccessibility > 0f
                && "continuous".equals(mode);
        if (displayedFraction > 0f && displayedFraction < 1f
                && !"pips".equals(mode) && !effectOwnsEndCap) {
            int[] inner = squirclePoint(displayedFraction, 10);
            int[] outer = squirclePoint(displayedFraction, 15);
            int x1 = centerX + inner[0];
            int y1 = centerY + inner[1];
            int x2 = centerX + outer[0];
            int y2 = centerY + outer[1];
            drawLine(graphics, x1, y1, x2, y2,
                    applyBarEffects(bar, lighten(fillColor, 0.45f), 0.5f, 1f, 1f,
                            1f, x2, y2, now));
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
                                    boolean verticalBar, int meterX, int meterY,
                                    int meterWidth, int meterHeight, long now) {
        int width = Math.max(1, x2 - x1);
        int height = Math.max(1, y2 - y1);
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                float cross = verticalBar ? normalizedPixel(px, width) : normalizedPixel(py, height);
                float along = verticalBar
                        ? 1f - normalizedPixel(py, height) : normalizedPixel(px, width);
                int screenX = x1 + px;
                int screenY = y1 + py;
                float pathAlong = verticalBar
                        ? 1f - normalizedPixel(screenY - meterY, meterHeight)
                        : normalizedPixel(screenX - meterX, meterWidth);
                float radial = Math.abs(cross - 0.5f) * 2f;
                int effected = applyBarEffects(bar, color, cross, along, radial,
                        pathAlong, screenX, screenY, now);
                graphics.fill(screenX, screenY, screenX + 1, screenY + 1, effected);
            }
        }
    }

    private static float normalizedPixel(int position, int span) {
        return span <= 1 ? 0.5f : position / (float) (span - 1);
    }

    private static int applyBarEffects(ResourceSyncS2CPayload.Bar bar, int color,
                                       float cross, float along, float radial, float pathAlong,
                                       int pixelX, int pixelY, long now) {
        int result = color;
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if (effect.strength() <= 0f) continue;
            if ("townstead:gradient".equals(effect.type())) {
                float coordinate = switch (normalized(effect.gradientShape())) {
                    case "along", "leading_edge" -> along;
                    case "radial" -> radial;
                    default -> cross;
                };
                result = gradientEffectColor(result, coordinate, effect);
            } else if ("townstead:shimmer".equals(effect.type())) {
                result = shimmerEffectColor(result, pathAlong, pixelX, pixelY, now,
                        bar.resourceId(), effect);
            } else if ("townstead:pulse".equals(effect.type())) {
                result = pulseEffectColor(result, now, bar.resourceId(), effect);
            } else if ("townstead:flow".equals(effect.type())) {
                result = flowEffectColor(result, cross, pathAlong, pixelX, pixelY, now,
                        bar.resourceId(), effect);
            } else if ("townstead:bubbles".equals(effect.type())) {
                result = bubbleEffectColor(result, normalized(bar.shape()), cross, pathAlong,
                        radial, now, bar.resourceId(), effect);
            } else if ("townstead:embers".equals(effect.type())) {
                result = emberEffectColor(result, normalized(bar.shape()), cross, pathAlong,
                        radial, now, bar.resourceId(), effect);
            }
        }
        return result;
    }

    /** Broad, broken pixel sheen with a deterministic rest between passes. */
    private static int shimmerEffectColor(int color, float pathAlong, int pixelX, int pixelY,
                                          long now, String resourceId,
                                          ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;
        long sweepDuration = Math.max(400L, Math.round(2400f / effect.speed()));
        long cycle = Math.max(sweepDuration + 100L, Math.round(effect.interval() * 1000f));
        int seed = resourceId == null ? 0 : resourceId.hashCode();
        long offset = Math.floorMod(seed * 31L, cycle);
        long elapsed = Math.floorMod(now + offset, cycle);
        if (elapsed > sweepDuration) return color;

        float center = -0.24f + (elapsed / (float) sweepDuration) * 1.48f;
        float envelope = 1f - Math.abs(pathAlong - center) / 0.22f;
        if (envelope <= 0f) return color;

        int hash = seed ^ (pixelX * 0x45d9f3b) ^ (pixelY * 0x119de1f3);
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        float noise = (hash & 0xFF) / 255f;
        if (noise < 0.30f) return color;

        int target = effect.color() < 0 ? lighten(color, 0.68f) : effect.color();
        float broken = 0.35f + noise * 0.65f;
        return blend(color, target, effect.strength() * accessibility * envelope * broken);
    }

    /** Whole-fill breathing tint, phase-offset so stacked meters do not pulse in lockstep. */
    private static int pulseEffectColor(int color, long now, String resourceId,
                                        ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;
        long cycle = Math.max(500L, Math.round(2400f / effect.speed()));
        int seed = resourceId == null ? 0 : resourceId.hashCode();
        long offset = Math.floorMod(seed * 47L, cycle);
        float phase = Math.floorMod(now + offset, cycle) / (float) cycle;
        float breath = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2d);
        int target = effect.color() < 0 ? lighten(color, 0.55f) : effect.color();
        return blend(color, target, effect.strength() * accessibility * breath);
    }

    /** Several offset pixel currents moving continuously in the meter's fill direction. */
    private static int flowEffectColor(int color, float cross, float pathAlong,
                                       int pixelX, int pixelY, long now, String resourceId,
                                       ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;
        int seed = resourceId == null ? 0 : resourceId.hashCode();
        int lane = Math.max(0, Math.min(3, (int) Math.floor(cross * 4f)));
        float time = (now / 2400f) * effect.speed() * (1f + lane * 0.08f);
        float phase = pathAlong * 3f - time + lane * 0.23f + (seed & 31) / 31f;
        phase -= (float) Math.floor(phase);
        if (phase >= 0.38f) return color;

        float envelope = (float) Math.sin((phase / 0.38f) * Math.PI);
        int hash = seed ^ (pixelX * 0x45d9f3b) ^ (pixelY * 0x119de1f3) ^ (lane * 0x27d4eb2d);
        hash ^= hash >>> 15;
        float texture = 0.65f + (hash & 0xFF) / 255f * 0.35f;
        int target = effect.color() < 0 ? lighten(color, 0.52f) : effect.color();
        return blend(color, target,
                effect.strength() * accessibility * envelope * texture);
    }

    /** Deterministic hollow pixel bubbles, clipped naturally by the meter's current fill. */
    private static int bubbleEffectColor(int color, String shape, float cross, float pathAlong,
                                         float radial, long now, String resourceId,
                                         ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;

        float laneCoordinate;
        float travelCoordinate;
        float lanePixels;
        float travelPixels;
        if ("vertical".equals(shape)) {
            laneCoordinate = cross;
            travelCoordinate = pathAlong;
            lanePixels = 8f;
            travelPixels = 56f;
        } else if ("squircle".equals(shape)) {
            laneCoordinate = radial;
            travelCoordinate = pathAlong;
            lanePixels = 5f;
            travelPixels = 120f;
        } else {
            laneCoordinate = pathAlong;
            travelCoordinate = 1f - cross;
            lanePixels = 82f;
            travelPixels = 8f;
        }

        int seed = resourceId == null ? 0 : resourceId.hashCode();
        for (int bubble = 0; bubble < effect.bubbleCount(); bubble++) {
            int hash = seed ^ (bubble * 0x45d9f3b);
            hash ^= hash >>> 16;
            float lane = (hash & 0xFFFF) / 65535f;
            float offset = ((hash >>> 16) & 0xFFFF) / 65535f;
            float speedVariance = 0.72f + ((hash >>> 8) & 0xFF) / 255f * 0.56f;
            float travel = now / 5200f * effect.speed() * speedVariance + offset;
            travel -= (float) Math.floor(travel);
            lane += (float) Math.sin(travel * Math.PI * 2d * 1.3d + bubble * 2.17d)
                    * effect.bubbleWobble() * 0.07f;
            lane = Math.max(0f, Math.min(1f, lane));

            boolean bubblePixel;
            if ("horizontal".equals(shape)) {
                // A short horizontal trough does not have enough vertical resolution for a
                // sampled circle. Snap each centre to the pixel grid and use deliberate
                // diamond/ring glyphs so bubbles read as bubbles instead of random glitter.
                int sampleX = Math.round(laneCoordinate * (lanePixels - 1f));
                int sampleY = Math.round(travelCoordinate * (travelPixels - 1f));
                int centerX = Math.round(lane * (lanePixels - 1f));
                int centerY = Math.round(travel * (travelPixels - 1f));
                int dx = Math.abs(sampleX - centerX);
                int dy = Math.abs(sampleY - centerY);
                bubblePixel = switch (effect.bubbleSize()) {
                    case 1 -> dx == 0 && dy == 0;
                    case 2 -> dx + dy == 1;
                    default -> (dy == 2 && dx <= 1) || (dx == 2 && dy <= 1);
                };
            } else {
                float dx = (laneCoordinate - lane) * lanePixels;
                float dy = (travelCoordinate - travel) * travelPixels;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float radius = effect.bubbleSize() == 1 ? 0.65f
                        : effect.bubbleSize() == 2 ? 1.15f : 1.70f;
                bubblePixel = effect.bubbleSize() == 1
                        ? distance <= radius
                        : distance <= radius && distance >= radius - 0.78f;
            }
            if (!bubblePixel) continue;

            int target = effect.color() < 0 ? lighten(color, 0.72f) : effect.color();
            return blend(color, target, effect.strength() * accessibility * 0.88f);
        }
        return color;
    }

    /** Rising one-pixel coals with a cooling tail and occasional cross-shaped flare. */
    private static int emberEffectColor(int color, String shape, float cross, float pathAlong,
                                        float radial, long now, String resourceId,
                                        ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;

        float laneCoordinate;
        float travelCoordinate;
        float lanePixels;
        float travelPixels;
        if ("vertical".equals(shape)) {
            laneCoordinate = cross;
            travelCoordinate = pathAlong;
            lanePixels = 8f;
            travelPixels = 56f;
        } else if ("squircle".equals(shape)) {
            laneCoordinate = radial;
            travelCoordinate = pathAlong;
            lanePixels = 5f;
            travelPixels = 120f;
        } else {
            laneCoordinate = pathAlong;
            travelCoordinate = 1f - cross;
            lanePixels = 82f;
            travelPixels = 8f;
        }

        int seed = (resourceId == null ? 0 : resourceId.hashCode()) ^ 0x6D2B79F5;
        for (int ember = 0; ember < effect.emberCount(); ember++) {
            int hash = seed ^ (ember * 0x27d4eb2d);
            hash ^= hash >>> 15;
            float lane = (hash & 0xFFFF) / 65535f;
            float offset = ((hash >>> 16) & 0xFFFF) / 65535f;
            float speedVariance = 0.70f + ((hash >>> 7) & 0xFF) / 255f * 0.62f;
            float travel = now / 4300f * effect.speed() * speedVariance + offset;
            travel -= (float) Math.floor(travel);
            lane += (float) Math.sin(travel * Math.PI * 3.4d + ember * 1.71d)
                    * effect.emberDrift() * 0.08f;
            lane = Math.max(0f, Math.min(1f, lane));

            int sampleLane = Math.round(laneCoordinate * (lanePixels - 1f));
            int sampleTravel = Math.round(travelCoordinate * (travelPixels - 1f));
            int centerLane = Math.round(lane * (lanePixels - 1f));
            int centerTravel = Math.round(travel * (travelPixels - 1f));
            int dx = sampleLane - centerLane;
            int dy = sampleTravel - centerTravel;
            float shimmer = 0.5f + 0.5f * (float) Math.sin(
                    now / 95f * (0.82f + (hash & 7) * 0.06f) + ember * 2.41f);
            float flare = shimmer * effect.emberFlicker();
            boolean core = dx == 0 && dy == 0;
            boolean tail = dx == 0 && dy == -1 && travel < 0.88f;
            float flareThreshold = 1f - (0.08f + effect.emberFlicker() * 0.22f);
            boolean flarePixel = Math.abs(dx) + Math.abs(dy) == 1 && shimmer > flareThreshold;
            if (!core && !tail && !flarePixel) continue;

            int hot = effect.color() < 0 ? 0xFFD66B : effect.color();
            int cool = effect.shadowColor() < 0 ? 0xE54B1B : effect.shadowColor();
            float heat = (1f - travel) * (0.72f + shimmer * 0.28f);
            int target = blend(cool, hot, heat);
            float opacity = core ? 0.98f : tail ? 0.62f : 0.50f;
            opacity *= 0.72f + flare * 0.28f;
            return blend(color, target, effect.strength() * accessibility * opacity);
        }
        return color;
    }

    private static ResourceSyncS2CPayload.Effect liquidEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:liquid".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect emberEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:embers".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect viscousEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:viscous".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ViscousBoundary viscousBoundary(ResourceSyncS2CPayload.Bar bar,
                                                    ResourceSyncS2CPayload.Effect effect,
                                                    long now) {
        String key = (bar.resourceId() == null ? "" : bar.resourceId())
                + '|' + normalized(bar.shape()) + '|' + normalized(bar.fillMode());
        int range = Math.max(1, bar.max() - bar.min());
        float fraction = Math.max(0f, Math.min(1f, (bar.value() - bar.min()) / (float) range));
        ViscousBoundary boundary = VISCOUS_BOUNDARIES.computeIfAbsent(key,
                ignored -> new ViscousBoundary(effect.lobeCount(), fraction, bar.value(),
                        key.hashCode(), effect.stringiness()));
        boundary.update(bar, effect, fraction, now);
        return boundary;
    }

    private static float viscousSample(ViscousBoundary boundary, float coordinate, float target,
                                       ResourceSyncS2CPayload.Effect effect, float accessibility) {
        float mixed = target + (boundary.sample(coordinate) - target)
                * effect.strength() * accessibility;
        return Math.max(0f, Math.min(1f, mixed));
    }

    private static float viscousAverage(ViscousBoundary boundary, float target,
                                        ResourceSyncS2CPayload.Effect effect, float accessibility) {
        float mixed = target + (boundary.average() - target)
                * effect.strength() * accessibility;
        return Math.max(0f, Math.min(1f, mixed));
    }

    private static int viscousEdgeColor(int color, ResourceSyncS2CPayload.Effect effect,
                                        float accessibility) {
        int target = effect.color() < 0 ? lighten(color, 0.38f) : effect.color();
        return blend(color, target, effect.strength() * accessibility * 0.72f);
    }

    private static LiquidSurface liquidSurface(ResourceSyncS2CPayload.Bar bar,
                                               ResourceSyncS2CPayload.Effect effect,
                                               long now) {
        String key = (bar.resourceId() == null ? "" : bar.resourceId())
                + '|' + normalized(bar.shape()) + '|' + normalized(bar.fillMode());
        LiquidSurface surface = LIQUID_SURFACES.computeIfAbsent(key,
                ignored -> new LiquidSurface(effect.surfacePoints(), bar.value()));
        surface.update(bar, effect, now, liquidMovement());
        return surface;
    }

    private static float liquidMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        var motion = mc.player.getDeltaMovement();
        double yaw = Math.toRadians(mc.player.getYRot());
        float localSideways = (float) (motion.x * Math.cos(yaw) + motion.z * Math.sin(yaw));
        return Math.max(-1f, Math.min(1f, localSideways * 6f));
    }

    private static int liquidSurfaceColor(int color, ResourceSyncS2CPayload.Effect effect,
                                          float accessibility, float crest) {
        int target = effect.color() < 0 ? lighten(color, 0.62f) : effect.color();
        return blend(color, target, effect.strength() * accessibility * crest * 0.85f);
    }

    /** Tiny damped heightfield: enough inertia for liquid motion without a general fluid solver. */
    private static final class LiquidSurface {
        private float[] heights;
        private float[] velocities;
        private float[] accelerations;
        private int lastValue;
        private long lastUpdateMillis;

        private LiquidSurface(int points, int initialValue) {
            reset(points, initialValue);
        }

        private void reset(int points, int value) {
            heights = new float[points];
            velocities = new float[points];
            accelerations = new float[points];
            lastValue = value;
            lastUpdateMillis = 0L;
            velocities[points / 2] = 0.12f;
        }

        private void update(ResourceSyncS2CPayload.Bar bar,
                            ResourceSyncS2CPayload.Effect effect,
                            long nowMillis, float movement) {
            if (heights.length != effect.surfacePoints()) reset(effect.surfacePoints(), bar.value());

            if (lastUpdateMillis == 0L) {
                lastUpdateMillis = nowMillis;
                lastValue = bar.value();
                return;
            }

            int range = Math.max(1, bar.max() - bar.min());
            int valueDelta = bar.value() - lastValue;
            if (valueDelta != 0) {
                float impulse = Math.max(-1f, Math.min(1f, valueDelta / (float) range))
                        * effect.splash() * 12f;
                int impact = Math.floorMod((bar.resourceId() == null ? 0 : bar.resourceId().hashCode())
                        + bar.value(), heights.length);
                velocities[impact] += impulse;
                if (impact > 0) velocities[impact - 1] += impulse * 0.45f;
                if (impact + 1 < velocities.length) velocities[impact + 1] += impulse * 0.45f;
            }
            lastValue = bar.value();

            float elapsed = Math.max(0f, Math.min(0.05f,
                    (nowMillis - lastUpdateMillis) / 1000f)) * effect.speed();
            lastUpdateMillis = nowMillis;
            if (elapsed <= 0f) return;

            int steps = Math.max(1, Math.min(12, (int) Math.ceil(elapsed / 0.016f)));
            float tickStep = elapsed * 60f / steps;
            for (int step = 0; step < steps; step++) {
                for (int i = 0; i < heights.length; i++) {
                    float left = i == 0 ? heights[i] : heights[i - 1];
                    float right = i + 1 == heights.length ? heights[i] : heights[i + 1];
                    float position = heights.length <= 1 ? 0f
                            : i / (float) (heights.length - 1) * 2f - 1f;
                    float movementTarget = movement * effect.movementInfluence() * position * 0.9f;
                    float restoring = (movementTarget - heights[i]) * 0.08f;
                    float coupling = (left + right - heights[i] * 2f) * effect.tension();
                    accelerations[i] = restoring + coupling;
                }
                float damping = (float) Math.pow(effect.damping(), tickStep);
                for (int i = 0; i < heights.length; i++) {
                    velocities[i] = (velocities[i] + accelerations[i] * tickStep) * damping;
                    heights[i] = Math.max(-1.5f, Math.min(1.5f,
                            heights[i] + velocities[i] * tickStep));
                }
            }
        }

        private float sample(float coordinate) {
            float clamped = Math.max(0f, Math.min(1f, coordinate));
            float scaled = clamped * (heights.length - 1);
            int left = Math.max(0, Math.min(heights.length - 1, (int) Math.floor(scaled)));
            int right = Math.min(heights.length - 1, left + 1);
            float blend = scaled - left;
            return heights[left] + (heights[right] - heights[left]) * blend;
        }
    }

    /** Slow viscoelastic boundary whose neighboring lobes stretch and catch up at different rates. */
    private static final class ViscousBoundary {
        private float[] positions;
        private float[] velocities;
        private int lastValue;
        private long lastUpdateMillis;
        private final int seed;

        private ViscousBoundary(int lobes, float initialFraction, int initialValue,
                                int seed, float stringiness) {
            this.seed = seed;
            reset(lobes, initialFraction, initialValue, stringiness);
        }

        private void reset(int lobes, float fraction, int value, float stringiness) {
            positions = new float[lobes];
            velocities = new float[lobes];
            for (int i = 0; i < lobes; i++) {
                positions[i] = fraction + (variation(i) - 0.5f) * stringiness * 0.04f;
            }
            lastValue = value;
            lastUpdateMillis = 0L;
        }

        private void update(ResourceSyncS2CPayload.Bar bar,
                            ResourceSyncS2CPayload.Effect effect,
                            float targetFraction, long nowMillis) {
            if (positions.length != effect.lobeCount()) {
                reset(effect.lobeCount(), targetFraction, bar.value(), effect.stringiness());
            }
            if (lastUpdateMillis == 0L) {
                lastUpdateMillis = nowMillis;
                lastValue = bar.value();
                return;
            }

            int range = Math.max(1, bar.max() - bar.min());
            int valueDelta = bar.value() - lastValue;
            if (valueDelta != 0) {
                float change = Math.max(-1f, Math.min(1f, valueDelta / (float) range));
                for (int i = 0; i < velocities.length; i++) {
                    float variation = variation(i);
                    velocities[i] += change * effect.stringiness() * (0.35f + variation * 0.35f);
                }
            }
            lastValue = bar.value();

            float elapsed = Math.max(0f, Math.min(0.05f,
                    (nowMillis - lastUpdateMillis) / 1000f)) * effect.speed();
            lastUpdateMillis = nowMillis;
            if (elapsed <= 0f) return;

            int steps = Math.max(1, Math.min(12, (int) Math.ceil(elapsed / 0.016f)));
            float tickStep = elapsed * 60f / steps;
            for (int step = 0; step < steps; step++) {
                float[] accelerations = new float[positions.length];
                for (int i = 0; i < positions.length; i++) {
                    float left = i == 0 ? positions[i] : positions[i - 1];
                    float right = i + 1 == positions.length ? positions[i] : positions[i + 1];
                    float crawl = (float) Math.sin(nowMillis * 0.00022f * effect.speed()
                            + i * 2.19f + (seed & 31) * 0.17f)
                            * effect.stringiness() * 0.012f;
                    float nodeTarget = targetFraction + crawl;
                    float response = 0.018f + (1f - effect.viscosity()) * 0.15f;
                    float spring = (nodeTarget - positions[i]) * response
                            * (0.72f + variation(i) * 0.56f);
                    float cohesion = (left + right - positions[i] * 2f)
                            * (0.08f + (1f - effect.stringiness()) * 0.12f);
                    accelerations[i] = spring + cohesion;
                }
                float damping = (float) Math.pow(0.74f + effect.viscosity() * 0.18f, tickStep);
                for (int i = 0; i < positions.length; i++) {
                    velocities[i] = (velocities[i] + accelerations[i] * tickStep) * damping;
                    positions[i] = Math.max(-0.10f, Math.min(1.10f,
                            positions[i] + velocities[i] * tickStep));
                }
            }
        }

        private float variation(int index) {
            int hash = seed ^ (index * 0x45d9f3b);
            hash ^= hash >>> 16;
            return (hash & 0xFF) / 255f;
        }

        private float sample(float coordinate) {
            float clamped = Math.max(0f, Math.min(1f, coordinate));
            float scaled = clamped * (positions.length - 1);
            int left = Math.max(0, Math.min(positions.length - 1, (int) Math.floor(scaled)));
            int right = Math.min(positions.length - 1, left + 1);
            float amount = scaled - left;
            return positions[left] + (positions[right] - positions[left]) * amount;
        }

        private float average() {
            float total = 0f;
            for (float position : positions) total += position;
            return total / positions.length;
        }
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
