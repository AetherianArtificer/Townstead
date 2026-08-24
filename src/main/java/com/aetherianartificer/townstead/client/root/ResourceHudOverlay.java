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
    /** Keeps an empty procedural meter readable without turning dark frame palettes into slabs. */
    private static final float TROUGH_OPACITY = 0.52f;
    private static final String[] BUILTIN_RUNES = {
            "111101111101101", "010111010111010", "101010111010101", "110010111010110",
            "111001010100111", "101111101111101", "010101111101010", "111100110001111"
    };
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
        TownsteadConfig.ResourceHudExitStyle exitStyle = effectiveExitStyle();
        int transitionTicks = exitStyle == TownsteadConfig.ResourceHudExitStyle.INSTANT
                ? 0 : ResourceHudConfig.fadeTicks();
        List<ResourceClientStore.Visible> visible = ResourceClientStore.visible(now,
                ResourceHudConfig.visibility(), ResourceHudConfig.holdTicks(),
                transitionTicks, abilityUiOpen);
        if (visible.isEmpty()) return;

        renderAnchoredGroups(graphics, visible, graphics.guiWidth(), graphics.guiHeight(), now, true);
    }

    /** Draws representative meters in the client configuration screen. */
    public static void renderPreview(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        long now = System.currentTimeMillis();
        float transitionAlpha = previewTransitionAlpha(now);
        List<ResourceClientStore.Visible> preview = List.of(
                new ResourceClientStore.Visible(previewBar("townstead:spirit/magical", 72, "horizontal",
                        "continuous", "TOP_LEFT", 0x3FA0FF, 0xC04AC0, 0xF5C7FF, true), transitionAlpha),
                new ResourceClientStore.Visible(previewBar("townstead:spirit/nautical", 46, "squircle",
                        "continuous", "TOP_RIGHT", 0x3FA0FF, 0x4A90B8, 0xA8ECFF, false), transitionAlpha),
                new ResourceClientStore.Visible(previewBar("townstead:spirit/industrious", 61, "vertical",
                        "pips", "BOTTOM_CENTER", 0x3FA0FF, 0xBF8A3A, 0xFFD27A, false), transitionAlpha));

        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0f);
            renderAnchoredGroups(graphics, preview, width, height, now, false);
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
                List.of(), false, 0,
                "townstead:spirit_trough", id, anchor, "DOTS", 10, 0,
                0xD80E1014, framePrimaryColor, frameSecondaryColor, 2, "", -1,
                "", "", "");
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
        TownsteadConfig.ResourceHudExitStyle exitStyle = effectiveExitStyle();
        int cursorX = x;
        int cursorY = y;
        for (int i = 0; i < visible.size(); i++) {
            ResourceClientStore.Visible item = visible.get(i);
            ResourceSyncS2CPayload.Bar bar = item.bar();
            int seed = bar.resourceId() == null ? 0 : bar.resourceId().hashCode();
            float renderedAlpha = ResourceHudMath.exitAlpha(item.alpha(), exitStyle, now, seed);
            int slide = ResourceHudMath.exitSlide(item.alpha(), exitStyle, 10);
            int renderedY = isBottomAnchor(anchor) ? cursorY + slide : cursorY - slide;
            placed.add(new Placed(bar, cursorX, renderedY, renderedAlpha,
                    ResourceHudMath.normalized(bar.value(), bar.min(), bar.max()),
                    withAlpha(0xFF000000 | bar.color(), renderedAlpha), normalized(bar.shape()),
                    item.reactions()));
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
                for (Placed item : placed) renderRunes(graphics, item, now);
                for (Placed item : placed) renderCrystalline(graphics, item, now);
                for (Placed item : placed) renderCorruption(graphics, item, now);
                for (Placed item : placed) renderFallingMotes(graphics, item, now);
                for (Placed item : placed) renderFlyingRunes(graphics, item, now);
                for (Placed item : placed) renderSpores(graphics, item, now);
                for (Placed item : placed) renderEscapingEmbers(graphics, item, now);
                for (Placed item : placed) renderFlames(graphics, item, now);
                for (Placed item : placed) renderSteam(graphics, item, now);
                for (Placed item : placed) renderElectric(graphics, item, now);
                for (Placed item : placed) renderWisps(graphics, item, now);
                for (Placed item : placed) renderSparkles(graphics, item, now);
                for (Placed item : placed) renderReactions(graphics, item, now);
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

    /** Connected pixel tongues rooted at the meter base or the currently exposed fill edge. */
    private static void renderFlames(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = flameEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean sprite = hasSprite(bar);
        String shape = item.shape();
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x2C1B3C6D;
        int hotRgb = effect.color() < 0 ? 0xFFF0A0 : effect.color();
        int coolRgb = effect.shadowColor() < 0 ? 0xFF5A1F : effect.shadowColor();
        float opacity = item.alpha() * accessibility * effect.strength();
        int hot = withAlpha(0xFF000000 | hotRgb, opacity);
        int cool = withAlpha(0xFF000000 | coolRgb, opacity * 0.92f);
        int soft = withAlpha(0xFF000000 | coolRgb, opacity * 0.62f);
        boolean basePlacement = "base".equals(normalized(effect.flamePlacement()));

        if (isSquircle(shape)) {
            int centerX = item.x() + 20;
            int centerY = item.y() + 20;
            if (basePlacement) {
                int span = 23;
                int rootX = centerX - span / 2;
                int rootY = centerY + 15;
                int bedPhase = Math.floorMod((int) (now / Math.max(70f, 150f / effect.speed())), 4);
                for (int offset = 0; offset < span; offset++) {
                    int color = ((offset + bedPhase) % 4 == 0) ? hot : soft;
                    graphics.fill(rootX + offset, rootY, rootX + offset + 1, rootY + 1, color);
                }
                int tongues = Math.min(effect.flameCount(), 5);
                for (int tongue = 0; tongue < tongues; tongue++) {
                    int hash = seed ^ (tongue * 0x45d9f3b);
                    hash ^= hash >>> 16;
                    float lane = (tongue + 0.5f) / tongues;
                    lane += ((((hash >>> 5) & 0xFF) / 255f) - 0.5f) * 0.55f / tongues;
                    int tongueX = rootX + Math.round(lane * (span - 1));
                    int height = animatedFlameHeight(effect, now, tongue, hash);
                    int sway = animatedFlameSway(effect, now, tongue, hash);
                    for (int level = 0; level < height; level++) {
                        float rise = level / (float) Math.max(1, height - 1);
                        int px = tongueX + Math.round(sway * rise);
                        int py = rootY - level;
                        int halfWidth = flameHalfWidth(level, height);
                        graphics.fill(px - halfWidth, py, px + halfWidth + 1, py + 1, cool);
                        if (level < Math.max(1, Math.round(height * 0.62f))) {
                            int innerHalf = Math.max(0, halfWidth - 1);
                            graphics.fill(px - innerHalf, py, px + innerHalf + 1, py + 1, hot);
                        }
                    }
                }
                return;
            }
            int bedSamples = Math.max(6, Math.round(44f * item.fraction()));
            int bedPhase = Math.floorMod((int) (now / Math.max(70f, 150f / effect.speed())), 4);
            for (int sample = 0; sample <= bedSamples; sample++) {
                float around = item.fraction() * sample / (float) bedSamples;
                int[] point = squirclePoint(around, 16);
                int color = ((sample + bedPhase) % 4 == 0) ? hot : soft;
                graphics.fill(centerX + point[0], centerY + point[1],
                        centerX + point[0] + 1, centerY + point[1] + 1, color);
            }
            for (int tongue = 0; tongue < effect.flameCount(); tongue++) {
                int hash = seed ^ (tongue * 0x45d9f3b);
                hash ^= hash >>> 16;
                float lane = (tongue + 0.5f) / effect.flameCount();
                lane += ((((hash >>> 5) & 0xFF) / 255f) - 0.5f)
                        * 0.45f / effect.flameCount();
                float around = Math.max(0f, Math.min(1f, lane * item.fraction()));
                int height = animatedFlameHeight(effect, now, tongue, hash);
                int sway = animatedFlameSway(effect, now, tongue, hash);
                for (int level = 0; level < height; level++) {
                    float bend = around + sway * (level / (float) Math.max(1, height - 1)) * 0.006f;
                    int halfWidth = flameHalfWidth(level, height);
                    for (int offset = -halfWidth; offset <= halfWidth; offset++) {
                        int[] point = squirclePoint(bend + offset * 0.005f, 15 + level);
                        graphics.fill(centerX + point[0], centerY + point[1],
                                centerX + point[0] + 1, centerY + point[1] + 1, cool);
                    }
                    if (level < Math.max(1, Math.round(height * 0.62f))) {
                        int innerHalf = Math.max(0, halfWidth - 1);
                        for (int offset = -innerHalf; offset <= innerHalf; offset++) {
                            int[] point = squirclePoint(bend + offset * 0.005f, 15 + level);
                            graphics.fill(centerX + point[0], centerY + point[1],
                                    centerX + point[0] + 1, centerY + point[1] + 1, hot);
                        }
                    }
                }
            }
            return;
        }

        int width = "vertical".equals(shape) ? (sprite ? 20 : 14) : (sprite ? 100 : 82);
        int height = "vertical".equals(shape) ? (sprite ? 68 : 64) : (sprite ? 16 : 10);
        int innerX = item.x() + (sprite ? ("vertical".equals(shape) ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? ("vertical".equals(shape) ? 6 : 4) : t);
        int innerWidth = sprite ? ("vertical".equals(shape) ? 8 : 80)
                : Math.max(1, width - t * 2);
        int innerHeight = sprite ? ("vertical".equals(shape) ? 56 : 8)
                : Math.max(1, height - t * 2);
        int filledWidth = Math.max(1, Math.round(innerWidth * item.fraction()));
        int span = "vertical".equals(shape) ? innerWidth : filledWidth;
        int tongues = Math.min(effect.flameCount(), Math.max(1, span / 4));
        int rootY = basePlacement
                ? innerY + innerHeight - 1
                : "vertical".equals(shape)
                ? innerY + innerHeight - Math.round(innerHeight * item.fraction()) : innerY;
        int bedPhase = Math.floorMod((int) (now / Math.max(70f, 150f / effect.speed())), 4);
        for (int offset = 0; offset < span; offset++) {
            int color = ((offset + bedPhase) % 4 == 0) ? hot : soft;
            graphics.fill(innerX + offset, rootY, innerX + offset + 1, rootY + 1, color);
        }
        for (int tongue = 0; tongue < tongues; tongue++) {
            int hash = seed ^ (tongue * 0x45d9f3b);
            hash ^= hash >>> 16;
            float lane = (tongue + 0.5f) / tongues;
            lane += ((((hash >>> 5) & 0xFF) / 255f) - 0.5f) * 0.55f / tongues;
            int rootX = innerX + Math.round(lane * Math.max(0, span - 1));
            int tongueHeight = animatedFlameHeight(effect, now, tongue, hash);
            int sway = animatedFlameSway(effect, now, tongue, hash);
            for (int level = 0; level < tongueHeight; level++) {
                float rise = level / (float) Math.max(1, tongueHeight - 1);
                int px = rootX + Math.round(sway * rise);
                int py = rootY - level;
                int halfWidth = flameHalfWidth(level, tongueHeight);
                graphics.fill(px - halfWidth, py, px + halfWidth + 1, py + 1, cool);
                if (level < Math.max(1, Math.round(tongueHeight * 0.62f))) {
                    int innerHalf = Math.max(0, halfWidth - 1);
                    graphics.fill(px - innerHalf, py, px + innerHalf + 1, py + 1, hot);
                }
            }
        }
    }

    private static int flameHalfWidth(int level, int height) {
        if (height <= 2 || level >= height - 1) return 0;
        float remaining = 1f - level / (float) Math.max(1, height - 1);
        return remaining > 0.62f ? 2 : 1;
    }

    private static int animatedFlameHeight(ResourceSyncS2CPayload.Effect effect,
                                           long now, int tongue, int hash) {
        float variance = 0.62f + ((hash >>> 9) & 0xFF) / 255f * 0.48f;
        float wave = 0.5f + 0.5f * (float) Math.sin(
                now / 115f * effect.speed() * (0.84f + (hash & 7) * 0.05f)
                        + tongue * 1.73f);
        float flicker = 1f - effect.flameFlicker() * 0.25f + wave * effect.flameFlicker() * 0.38f;
        return Math.max(1, Math.round(effect.flameHeight() * variance * flicker));
    }

    private static int animatedFlameSway(ResourceSyncS2CPayload.Effect effect,
                                         long now, int tongue, int hash) {
        float sway = (float) Math.sin(now / 145f * effect.speed()
                + tongue * 2.11f + (hash & 31) * 0.17f);
        return Math.round(sway * (0.5f + effect.flameFlicker() * 1.5f));
    }

    /** Short-lived condensed-vapor billows that gather near the resource surface. */
    private static void renderSteam(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = steamEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean sprite = hasSprite(bar);
        String shape = item.shape();
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x6D2B79F5;
        int baseRgb = effect.color() < 0 ? 0xDCE6E8 : effect.color();
        int highlightRgb = blend(baseRgb, 0xFFFFFF, 0.20f);

        int billowCount = effect.steamCount() * 2;
        for (int puff = 0; puff < billowCount; puff++) {
            int hash = seed ^ (puff * 0x27D4EB2D);
            hash ^= hash >>> 15;
            float lane = (hash & 0xFFFF) / 65535f;
            float offset = ((hash >>> 16) & 0xFFFF) / 65535f;
            float speedVariance = 0.76f + ((hash >>> 8) & 0xFF) / 255f * 0.48f;
            float progress = now / 2500f * effect.speed() * speedVariance + offset;
            progress -= (float) Math.floor(progress);

            int sourceX;
            int sourceY;
            if ("vertical".equals(shape)) {
                int width = sprite ? 20 : 14;
                int height = sprite ? 68 : 64;
                int innerX = item.x() + (sprite ? 6 : t);
                int innerY = item.y() + (sprite ? 6 : t);
                int innerWidth = sprite ? 8 : Math.max(1, width - t * 2);
                int innerHeight = sprite ? 56 : Math.max(1, height - t * 2);
                sourceX = innerX + Math.round(lane * Math.max(0, innerWidth - 1));
                sourceY = innerY + innerHeight - Math.round(innerHeight * item.fraction()) - 1;
            } else if (isSquircle(shape)) {
                sourceX = item.x() + 20 + Math.round((lane - 0.5f) * 23f);
                sourceY = item.y() + 4 + Math.round(Math.abs(lane - 0.5f) * 7f);
            } else {
                int width = sprite ? 100 : 82;
                int innerX = item.x() + (sprite ? 10 : t);
                int innerY = item.y() + (sprite ? 4 : t);
                int innerWidth = sprite ? 80 : Math.max(1, width - t * 2);
                int filledWidth = Math.max(1, Math.round(innerWidth * item.fraction()));
                sourceX = innerX + Math.round(lane * Math.max(0, filledWidth - 1));
                sourceY = innerY - 1;
            }

            float meander = (float) Math.sin(progress * Math.PI * 2.2d + puff * 1.73d);
            float bias = (((hash >>> 11) & 7) / 7f - 0.5f) * progress * 2f;
            int px = sourceX + Math.round((meander * 2.5f + bias) * effect.steamDrift());
            int py = sourceY - Math.round(progress * (5f + effect.steamSize() * 2.5f));
            int puffSize = Math.min(effect.steamSize(),
                    1 + (int) (progress * effect.steamSize() * 1.6f));
            float birth = Math.min(1f, progress * 8f);
            float decay = (float) Math.pow(1f - progress, 1.35d);
            float opacity = item.alpha() * accessibility * effect.strength()
                    * birth * decay * 0.72f;
            if (opacity <= 0.025f) continue;
            drawSteamPuff(graphics, px, py, puffSize, baseRgb, highlightRgb, opacity, hash);
        }
    }

    private static void drawSteamPuff(GuiGraphics graphics, int x, int y, int size,
                                      int baseRgb, int highlightRgb, float opacity, int hash) {
        int haze = withAlpha(0xFF000000 | baseRgb, opacity * 0.22f);
        int soft = withAlpha(0xFF000000 | baseRgb, opacity * 0.34f);
        int body = withAlpha(0xFF000000 | baseRgb, opacity * 0.44f);
        int glint = withAlpha(0xFF000000 | highlightRgb, opacity * 0.28f);
        int direction = (hash & 1) == 0 ? -1 : 1;
        int lowerLeft = x - size;
        int lowerRight = x + size + 1;
        int notch = x + direction * Math.max(1, size / 2);

        // Broad, broken horizontal steps overlap into a surface mist without becoming snowflakes.
        graphics.fill(lowerLeft, y, notch, y + 1, haze);
        graphics.fill(notch + 1, y, lowerRight, y + 1, haze);
        graphics.fill(x - size / 2 - (direction < 0 ? 1 : 0), y - 1,
                x + (size + 1) / 2 + (direction > 0 ? 1 : 0), y, body);
        graphics.fill(x - direction, y - 1, x - direction + Math.max(1, size / 2), y, glint);
        if (size >= 2) {
            int upperWidth = size + 1;
            int upperX = x + direction - upperWidth / 2;
            graphics.fill(upperX, y - 2, upperX + upperWidth, y - 1, soft);
        }
        if (size >= 4) {
            graphics.fill(x - direction, y - 3, x - direction + 2, y - 2, haze);
        }
    }

    /** Coherent strike events with a travelling head, anchored forks, and a brief afterglow. */
    private static void renderElectric(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = electricEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        int period = Math.max(220, Math.round((760f - effect.electricCount() * 28f) / effect.speed()));
        int epoch = (int) (now / period);
        float phase = (now % period) / (float) period;
        if (phase > 0.82f) return;
        float baseOpacity = item.alpha() * accessibility * effect.strength();
        int baseRgb = effect.color() < 0 ? 0xA8F4FF : effect.color();
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x51ED270B;
        int strikeCount = 1 + (effect.electricCount() >= 6 ? 1 : 0)
                + (effect.electricCount() >= 10 ? 1 : 0);

        if (isSquircle(item.shape())) {
            renderElectricSquircle(graphics, item, effect, seed, epoch, phase,
                    baseOpacity, baseRgb, strikeCount);
            return;
        }

        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        int filledLength = Math.max(1, Math.round((vertical ? innerHeight : innerWidth) * item.fraction()));
        int fillStart = vertical ? innerY + innerHeight - filledLength : innerX;
        int fillEnd = fillStart + filledLength - 1;

        for (int arc = 0; arc < strikeCount; arc++) {
            float delay = arc * 0.10f;
            float reveal = Math.max(0f, Math.min(1f, (phase - delay) / (0.30f + arc * 0.03f)));
            float fadeStart = 0.48f + delay;
            float fade = phase <= fadeStart ? 1f
                    : Math.max(0f, 1f - (phase - fadeStart) / 0.28f);
            if (reveal <= 0f || fade <= 0f) continue;
            float arcOpacity = baseOpacity * fade * (arc == 0 ? 1f : 0.42f);
            int trailColor = withAlpha(0xFF000000 | baseRgb, arcOpacity * 0.28f);
            int arcColor = withAlpha(0xFF000000 | baseRgb, arcOpacity * 0.70f);
            int coreColor = withAlpha(lighten(0xFF000000 | baseRgb, 0.72f), arcOpacity);
            int hash = electricHash(seed, epoch, arc);
            int direction = (hash & 1) == 0 ? 1 : -1;
            float reachScale = arc == 0 ? effect.electricReach() : effect.electricReach() * 0.58f;
            int reach = Math.max(4, Math.round(filledLength * (0.28f + reachScale * 0.67f)));
            reach = Math.min(filledLength - 1, reach);
            int slack = Math.max(1, filledLength - reach);
            int lane = ((hash >>> 1) & 0x7FFF) % slack;
            int origin = direction > 0 ? fillStart + lane : fillEnd - lane;
            int destination = origin + direction * reach;
            int distance = Math.max(1, Math.abs(destination - origin));
            int points = Math.max(4, Math.min(18, distance / 4 + 3));
            int visiblePoints = Math.max(1, Math.min(points, (int) Math.ceil(points * reveal)));
            int previousAxis = origin;
            int previousCross = vertical ? innerX + innerWidth / 2 : innerY + innerHeight / 2;
            int branchAxis = previousAxis;
            int branchCross = previousCross;
            int branchPoint = Math.max(2, points / 2);

            for (int point = 1; point <= visiblePoints; point++) {
                int pointHash = electricHash(hash, point, epoch + arc);
                float along = point / (float) points;
                int axis = Math.round(origin + (destination - origin) * along);
                int jitter = ((pointHash >>> 5) & 7) - 3;
                int cross = vertical
                        ? Math.max(innerX, Math.min(innerX + innerWidth - 1,
                        innerX + innerWidth / 2 + jitter))
                        : Math.max(innerY, Math.min(innerY + innerHeight - 1,
                        innerY + innerHeight / 2 + jitter));
                int x0 = vertical ? previousCross : previousAxis;
                int y0 = vertical ? previousAxis : previousCross;
                int x1 = vertical ? cross : axis;
                int y1 = vertical ? axis : cross;
                drawLine(graphics, x0, y0, x1, y1, trailColor);
                if (point >= visiblePoints - 2) {
                    drawLine(graphics, x0, y0, x1, y1, arcColor);
                }
                if (point == visiblePoints) {
                    drawLine(graphics, x0, y0, x1, y1, coreColor);
                }
                previousAxis = axis;
                previousCross = cross;
                if (point == branchPoint) {
                    branchAxis = axis;
                    branchCross = cross;
                }
            }

            float branchRoll = ((hash >>> 17) & 0xFF) / 255f;
            if (visiblePoints >= branchPoint && branchRoll < effect.electricBranching()) {
                int outward = ((hash >>> 25) & 1) == 0 ? -1 : 1;
                int branchLength = 3 + Math.round(effect.electricReach() * 8f);
                int bend = ((hash >>> 20) & 3) - 1;
                int midAxis = branchAxis + bend;
                int midCross = branchCross + outward * Math.max(1, branchLength / 2);
                int endAxis = branchAxis - bend;
                int endCross = branchCross + outward * branchLength;
                if (vertical) {
                    drawLine(graphics, branchCross, branchAxis, midCross, midAxis, coreColor);
                    drawLine(graphics, midCross, midAxis, endCross, endAxis, arcColor);
                } else {
                    drawLine(graphics, branchAxis, branchCross, midAxis, midCross, coreColor);
                    drawLine(graphics, midAxis, midCross, endAxis, endCross, arcColor);
                }
            }
        }
    }

    private static void renderElectricSquircle(GuiGraphics graphics, Placed item,
                                                ResourceSyncS2CPayload.Effect effect,
                                                int seed, int epoch, float phase,
                                                float baseOpacity, int baseRgb, int strikeCount) {
        float filled = Math.max(0.01f, item.fraction());
        int centerX = item.x() + 20;
        int centerY = item.y() + 20;
        for (int arc = 0; arc < strikeCount; arc++) {
            float delay = arc * 0.10f;
            float reveal = Math.max(0f, Math.min(1f, (phase - delay) / (0.30f + arc * 0.03f)));
            float fadeStart = 0.48f + delay;
            float fade = phase <= fadeStart ? 1f
                    : Math.max(0f, 1f - (phase - fadeStart) / 0.28f);
            if (reveal <= 0f || fade <= 0f) continue;
            float arcOpacity = baseOpacity * fade * (arc == 0 ? 1f : 0.42f);
            int trailColor = withAlpha(0xFF000000 | baseRgb, arcOpacity * 0.28f);
            int arcColor = withAlpha(0xFF000000 | baseRgb, arcOpacity * 0.70f);
            int coreColor = withAlpha(lighten(0xFF000000 | baseRgb, 0.72f), arcOpacity);
            int hash = electricHash(seed, epoch, arc);
            float lane = ((hash >>> 1) & 0xFFFF) / 65535f;
            int direction = (hash & 1) == 0 ? 1 : -1;
            float reachScale = arc == 0 ? effect.electricReach() : effect.electricReach() * 0.58f;
            float span = Math.min(filled, 0.08f + reachScale * 0.30f);
            float slack = Math.max(0f, filled - span);
            float start = direction > 0 ? lane * slack : span + lane * slack;
            float end = start + direction * span;
            int points = 8 + Math.round(reachScale * 8f);
            int visiblePoints = Math.max(1, Math.min(points, (int) Math.ceil(points * reveal)));
            int[] previous = squirclePoint(start, 13);
            float branchAround = start;
            int branchPoint = Math.max(2, points / 2);
            for (int point = 1; point <= visiblePoints; point++) {
                int pointHash = electricHash(hash, point, epoch + arc);
                float along = point / (float) points;
                float around = start + (end - start) * along;
                int radius = 12 + ((pointHash >>> 6) & 3);
                int[] current = squirclePoint(around, radius);
                drawLine(graphics, centerX + previous[0], centerY + previous[1],
                        centerX + current[0], centerY + current[1], trailColor);
                if (point >= visiblePoints - 2) {
                    drawLine(graphics, centerX + previous[0], centerY + previous[1],
                            centerX + current[0], centerY + current[1], arcColor);
                }
                if (point == visiblePoints) {
                    drawLine(graphics, centerX + previous[0], centerY + previous[1],
                            centerX + current[0], centerY + current[1], coreColor);
                }
                previous = current;
                if (point == branchPoint) branchAround = around;
            }
            if (visiblePoints >= branchPoint
                    && ((hash >>> 17) & 0xFF) / 255f < effect.electricBranching()) {
                int branchLength = 3 + Math.round(effect.electricReach() * 8f);
                int[] root = squirclePoint(branchAround, 14);
                int[] bend = squirclePoint(branchAround + (((hash >>> 24) & 1) == 0 ? -0.008f : 0.008f),
                        15 + branchLength / 2);
                int[] tip = squirclePoint(branchAround, 15 + branchLength);
                drawLine(graphics, centerX + root[0], centerY + root[1],
                        centerX + bend[0], centerY + bend[1], coreColor);
                drawLine(graphics, centerX + bend[0], centerY + bend[1],
                        centerX + tip[0], centerY + tip[1], arcColor);
            }
        }
    }

    private static int electricHash(int seed, int epoch, int index) {
        int hash = seed ^ epoch * 0x45D9F3B ^ index * 0x27D4EB2D;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        return hash;
    }

    /** Spectral motes that loop around the filled meter with curved, fading pixel tails. */
    private static void renderWisps(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = wispEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x3C6EF372;
        int baseRgb = effect.color() < 0 ? 0xB8F5E8 : effect.color();
        int coreRgb = blend(baseRgb, 0xFFFFFF, 0.68f);
        float baseOpacity = item.alpha() * accessibility * effect.strength();

        for (int wisp = 0; wisp < effect.wispCount(); wisp++) {
            int hash = electricHash(seed, wisp, 0x57A5);
            float offset = ((hash >>> 8) & 0xFFFF) / 65535f;
            float variance = 0.76f + ((hash >>> 24) & 0xFF) / 255f * 0.48f;
            float progress = now / 3200f * effect.speed() * variance + offset;
            progress -= (float) Math.floor(progress);
            float life = wispLife(progress);
            float twinkle = 0.58f + 0.42f * (0.5f + 0.5f * (float) Math.sin(
                    now / (135f + (hash & 31)) + wisp * 1.91f));
            float opacity = baseOpacity * life * twinkle;
            if (opacity <= 0.02f) continue;

            for (int trail = effect.wispTrail(); trail >= 1; trail--) {
                float trailProgress = progress - trail * 0.026f;
                if (trailProgress <= 0f) continue;
                int[] point = wispPoint(item, effect, hash, trailProgress);
                float tail = (1f - trail / (float) (effect.wispTrail() + 1)) * 0.52f;
                int color = withAlpha(0xFF000000 | baseRgb,
                        baseOpacity * wispLife(trailProgress) * tail);
                graphics.fill(point[0], point[1], point[0] + 1, point[1] + 1, color);
            }

            int[] head = wispPoint(item, effect, hash, progress);
            int halo = withAlpha(0xFF000000 | baseRgb, opacity * 0.30f);
            int core = withAlpha(0xFF000000 | coreRgb, opacity);
            if (twinkle > 0.88f) {
                graphics.fill(head[0] - 1, head[1], head[0], head[1] + 1, halo);
                graphics.fill(head[0] + 1, head[1], head[0] + 2, head[1] + 1, halo);
                graphics.fill(head[0], head[1] - 1, head[0] + 1, head[1], halo);
                graphics.fill(head[0], head[1] + 1, head[0] + 1, head[1] + 2, halo);
            }
            graphics.fill(head[0], head[1], head[0] + 1, head[1] + 1, core);
        }
    }

    private static float wispLife(float progress) {
        return Math.min(1f, progress * 7f) * Math.min(1f, (1f - progress) * 7f);
    }

    private static int[] wispPoint(Placed item, ResourceSyncS2CPayload.Effect effect,
                                   int hash, float progress) {
        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        float lane = (hash & 0xFFFF) / 65535f;
        float phase = ((hash >>> 16) & 0xFFFF) / 65535f * (float) Math.PI * 2f;
        float angle = progress * (float) Math.PI * 2f + phase;
        float wander = effect.wispWander();

        if (isSquircle(item.shape())) {
            float around = lane * item.fraction()
                    + (float) Math.sin(angle) * (0.015f + wander * 0.045f);
            int radius = 14 + Math.round((float) Math.cos(angle * 0.85f + phase)
                    * (1f + wander * 4f));
            int[] point = squirclePoint(around, radius);
            return new int[]{item.x() + 20 + point[0], item.y() + 20 + point[1]};
        }

        boolean vertical = "vertical".equals(item.shape());
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        if (vertical) {
            int filled = Math.max(1, Math.round(innerHeight * item.fraction()));
            int baseX = innerX + innerWidth / 2;
            int baseY = innerY + innerHeight - 1 - Math.round(lane * Math.max(0, filled - 1));
            int px = baseX + Math.round((float) Math.sin(angle * 1.35f) * (2f + wander * 5f));
            int py = baseY + Math.round((float) Math.cos(angle) * (2f + wander * 8f)
                    + (float) Math.sin(angle * 2.3f + phase) * wander * 2f);
            return new int[]{px, py};
        }

        int filled = Math.max(1, Math.round(innerWidth * item.fraction()));
        int baseX = innerX + Math.round(lane * Math.max(0, filled - 1));
        int baseY = innerY + innerHeight / 2;
        int px = baseX + Math.round((float) Math.cos(angle) * (2f + wander * 8f)
                + (float) Math.sin(angle * 2.3f + phase) * wander * 2f);
        int py = baseY + Math.round((float) Math.sin(angle * 1.35f) * (2f + wander * 5f));
        return new int[]{px, py};
    }

    /** Brief, stationary pixel-star flares distributed across the currently filled resource. */
    private static void renderSparkles(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = sparkleEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0x1F83D9AB;
        int baseRgb = effect.color() < 0 ? 0xFFF2B8 : effect.color();
        int coreRgb = blend(baseRgb, 0xFFFFFF, 0.82f);
        float duty = 0.16f + effect.sparkleTwinkle() * 0.38f;
        float baseOpacity = item.alpha() * accessibility * effect.strength();

        for (int sparkle = 0; sparkle < effect.sparkleCount(); sparkle++) {
            int hash = electricHash(seed, sparkle, 0x5A17);
            float offset = ((hash >>> 8) & 0xFFFF) / 65535f;
            float speedVariance = 0.78f + ((hash >>> 24) & 0xFF) / 255f * 0.44f;
            float phase = now / 1050f * effect.speed() * speedVariance + offset;
            phase -= (float) Math.floor(phase);
            if (phase >= duty) continue;

            float local = phase / duty;
            float flare = (float) Math.sin(local * Math.PI);
            flare *= flare;
            if (flare <= 0.025f) continue;
            int[] point = sparklePoint(item, hash);
            int arm = Math.max(1, Math.round(effect.sparkleSize() * (0.32f + flare * 0.68f)));
            int halo = withAlpha(0xFF000000 | baseRgb, baseOpacity * flare * 0.52f);
            int core = withAlpha(0xFF000000 | coreRgb, baseOpacity * flare);

            for (int distance = 1; distance <= arm; distance++) {
                float fade = 1f - (distance - 1f) / (arm + 1f);
                int ray = withAlpha(0xFF000000 | baseRgb, baseOpacity * flare * fade * 0.72f);
                graphics.fill(point[0] - distance, point[1], point[0] - distance + 1, point[1] + 1, ray);
                graphics.fill(point[0] + distance, point[1], point[0] + distance + 1, point[1] + 1, ray);
                graphics.fill(point[0], point[1] - distance, point[0] + 1, point[1] - distance + 1, ray);
                graphics.fill(point[0], point[1] + distance, point[0] + 1, point[1] + distance + 1, ray);
            }
            if (arm >= 3 && flare > 0.58f) {
                graphics.fill(point[0] - 1, point[1] - 1, point[0], point[1], halo);
                graphics.fill(point[0] + 1, point[1] - 1, point[0] + 2, point[1], halo);
                graphics.fill(point[0] - 1, point[1] + 1, point[0], point[1] + 2, halo);
                graphics.fill(point[0] + 1, point[1] + 1, point[0] + 2, point[1] + 2, halo);
            }
            graphics.fill(point[0], point[1], point[0] + 1, point[1] + 1, core);
        }
    }

    private static int[] sparklePoint(Placed item, int hash) {
        ResourceSyncS2CPayload.Bar bar = item.bar();
        if (isSquircle(item.shape())) {
            float around = ((hash >>> 1) & 0xFFFF) / 65535f * item.fraction();
            int radius = 12 + ((hash >>> 18) & 3);
            int[] point = squirclePoint(around, radius);
            return new int[]{item.x() + 20 + point[0], item.y() + 20 + point[1]};
        }

        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        float along = ((hash >>> 1) & 0xFFFF) / 65535f;
        float across = ((hash >>> 17) & 0x7FFF) / 32767f;
        if (vertical) {
            int filled = Math.max(1, Math.round(innerHeight * item.fraction()));
            return new int[]{innerX + Math.round(across * Math.max(0, innerWidth - 1)),
                    innerY + innerHeight - 1 - Math.round(along * Math.max(0, filled - 1))};
        }
        int filled = Math.max(1, Math.round(innerWidth * item.fraction()));
        return new int[]{innerX + Math.round(along * Math.max(0, filled - 1)),
                innerY + Math.round(across * Math.max(0, innerHeight - 1))};
    }

    /** One-shot and state-driven reactions render after, and independently from, persistent effects. */
    private static void renderReactions(GuiGraphics graphics, Placed item, long now) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f || item.bar().reactions().isEmpty()) return;
        float previous = ResourceHudMath.normalized(item.reactions().previousValue(),
                item.bar().min(), item.bar().max());
        for (ResourceSyncS2CPayload.Reaction reaction : item.bar().reactions()) {
            String type = normalized(reaction.type());
            int color = reaction.color() < 0
                    ? blend(item.fillColor(), 0xFFFFFFFF, 0.76f)
                    : 0xFF000000 | reaction.color();
            float strength = reaction.strength() * accessibility;
            switch (type) {
                case "townstead:gain_flash" -> {
                    float p = eventProgress(item.reactions().valueChangedAtMillis(), reaction.duration(), now);
                    if (p >= 0f && item.fraction() > previous) {
                        float center = previous + (item.fraction() - previous) * smoothStep(p);
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                path >= previous && path <= item.fraction()
                                        ? band(path, center, 0.055f + 0.035f / reaction.speed())
                                        * (1f - p) * strength : 0f);
                    }
                }
                case "townstead:spend_flash" -> {
                    float p = eventProgress(item.reactions().valueChangedAtMillis(), reaction.duration(), now);
                    if (p >= 0f && item.fraction() < previous) {
                        if ("pips".equals(normalized(item.bar().fillMode()))) {
                            renderSpentPips(graphics, item, color, previous, p, strength, now);
                        } else {
                            renderReactionMask(graphics, item, color, (path, cross, x, y) -> {
                                if (path < item.fraction() || path > previous) return 0f;
                                float noise = (pixelHash(x, y, item.bar().resourceId()) & 0xFFFF) / 65535f;
                                return noise > p * 0.92f ? (1f - p * 0.65f) * strength : 0f;
                            });
                        }
                    }
                }
                case "townstead:change_ripple" -> {
                    float p = eventProgress(item.reactions().valueChangedAtMillis(), reaction.duration(), now);
                    if (p >= 0f) {
                        float center = smoothStep(p);
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                path <= Math.max(item.fraction(), 0.025f)
                                        ? band(path, center, 0.075f) * (1f - p * 0.6f) * strength : 0f);
                    }
                }
                case "townstead:full_charge" -> renderConfiguredReaction(graphics, item, reaction,
                        item.reactions().fullChargeAtMillis(), color, strength, now, true);
                case "townstead:low_warning" -> {
                    if (item.fraction() > 0f && item.fraction() <= reaction.threshold()) {
                        float phase = now / 1000f * reaction.speed() * 5.2f;
                        float wave = "flicker".equals(normalized(reaction.mode()))
                                ? 0.35f + 0.65f * Math.abs((float) Math.sin(phase * 2.31f)
                                * (float) Math.sin(phase * 0.73f))
                                : 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(phase));
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                path <= item.fraction() ? strength * wave * 0.72f : 0f);
                    }
                }
                case "townstead:empty_warning" -> {
                    float p = eventProgress(item.reactions().emptyAtMillis(), reaction.duration(), now);
                    if (p >= 0f) {
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                strength * (1f - p) * (0.55f + 0.45f
                                        * Math.abs((float) Math.sin(p * Math.PI * 5f))));
                    } else if (item.fraction() <= 0f && reaction.continuing() > 0f) {
                        float pulse = 0.35f + 0.25f * (float) Math.sin(
                                now / 1000f * reaction.speed() * 3.5f);
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                strength * reaction.continuing() * pulse);
                    }
                }
                case "townstead:regeneration_tick" -> {
                    float p = eventProgress(item.reactions().regenerationAtMillis(), reaction.duration(), now);
                    if (p >= 0f) {
                        float center = Math.min(item.fraction(), Math.max(0.02f,
                                previous + (item.fraction() - previous) * smoothStep(p)));
                        renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                                path <= item.fraction()
                                        ? band(path, center, 0.035f) * (1f - p) * strength : 0f);
                        renderReactionBurst(graphics, item, color, p, strength * 0.55f, 3);
                    }
                }
                case "townstead:ability_ready" -> renderConfiguredReaction(graphics, item, reaction,
                        item.reactions().abilityReadyAtMillis(), color, strength, now, false);
                default -> { }
            }
        }
    }

    /**
     * A continuous meter can leave a rectangular ghost behind when spent; pips cannot. Removed
     * pips instead linger as their own glyphs and wink out from the new value boundary outward.
     */
    private static void renderSpentPips(GuiGraphics graphics, Placed item, int color,
                                        float previousFraction, float progress, float strength,
                                        long now) {
        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean squircle = isSquircle(item.shape());
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int length = vertical ? (sprite ? 56 : Math.max(1, 64 - 2
                * Math.max(1, Math.min(4, bar.frameThickness()))))
                : (sprite ? 80 : Math.max(1, 82 - 2
                * Math.max(1, Math.min(4, bar.frameThickness()))));
        int units = squircle ? Math.max(2, Math.min(32, bar.segments()))
                : Math.max(2, Math.min(bar.segments(), Math.max(2, length / 3)));
        units = Math.min(units, squircle ? squirclePipCapacity(bar.pipStyle())
                : linearPipCapacity(bar.pipStyle(), length));
        int currentFilled = ResourceHudMath.filledUnits(item.fraction(), units);
        int previousFilled = ResourceHudMath.filledUnits(previousFraction, units);
        if (previousFilled <= currentFilled) return;

        ResourceSyncS2CPayload.Effect liquid = liquidEffect(bar);
        float liquidAccessibility = Accessibility.effectIntensity();
        LiquidSurface liquidSurface = liquid != null && liquidAccessibility > 0f
                ? liquidSurface(bar, liquid, now) : null;
        int removed = previousFilled - currentFilled;
        for (int i = currentFilled; i < previousFilled; i++) {
            float order = removed <= 1 ? 0f : (i - currentFilled) / (float) (removed - 1);
            float local = clamp01(progress * 1.28f - order * 0.18f);
            float opacity = (1f - smoothStep(local)) * strength * item.alpha();
            if (opacity <= 0.015f) continue;

            float along = (i + 0.5f) / units;
            int px;
            int py;
            boolean horizontalAxis;
            if (squircle) {
                int radius = liquidSurface == null ? 13 : 13 + Math.round(
                        liquidSurface.sample(along) * liquid.strength()
                                * liquidAccessibility * 1.5f);
                int[] point = squirclePoint(along, radius);
                px = item.x() + 20 + point[0];
                py = item.y() + 20 + point[1];
                horizontalAxis = Math.abs(point[0]) >= Math.abs(point[1]);
            } else {
                int t = Math.max(1, Math.min(4, bar.frameThickness()));
                int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
                int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
                int innerWidth = sprite ? (vertical ? 8 : 80)
                        : Math.max(1, (vertical ? 14 : 82) - t * 2);
                int innerHeight = sprite ? (vertical ? 56 : 8)
                        : Math.max(1, (vertical ? 64 : 10) - t * 2);
                int inset = pipInset(bar.pipStyle());
                int span = Math.max(0, length - 1 - inset * 2);
                float position = units <= 1 ? inset + span * 0.5f
                        : inset + i * span / (float) (units - 1);
                px = vertical ? innerX + innerWidth / 2 : innerX + Math.round(position);
                py = vertical ? innerY + innerHeight - 1 - Math.round(position)
                        : innerY + innerHeight / 2;
                if (liquidSurface != null) {
                    int bob = Math.round(liquidSurface.sample(along)
                            * liquid.strength() * liquidAccessibility * 1.5f);
                    if (vertical) px += bob;
                    else py += bob;
                }
                horizontalAxis = vertical;
            }

            int ghost = withAlpha(0xFF000000 | (color & 0xFFFFFF), opacity);
            int highlight = lighten(ghost, 0.52f);
            drawPip(graphics, px, py, bar.pipStyle(), ghost, highlight,
                    horizontalAxis, true);
        }
    }

    private static void renderConfiguredReaction(GuiGraphics graphics, Placed item,
                                                   ResourceSyncS2CPayload.Reaction reaction,
                                                   long startedAt, int color, float strength,
                                                   long now, boolean limitToFill) {
        float p = eventProgress(startedAt, reaction.duration(), now);
        if (p < 0f) return;
        String mode = normalized(reaction.mode());
        if ("sparkle_burst".equals(mode)) {
            renderReactionBurst(graphics, item, color, p, strength, 12);
        } else if ("edge_sweep".equals(mode)) {
            float center = smoothStep(p);
            renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                    (!limitToFill || path <= item.fraction())
                            ? band(path, center, 0.055f) * (1f - p * 0.45f) * strength : 0f);
        } else {
            float opacity = "pulse".equals(mode)
                    ? (float) Math.sin(p * Math.PI) * strength : (1f - p) * strength;
            renderReactionMask(graphics, item, color, (path, cross, x, y) ->
                    (!limitToFill || path <= item.fraction()) ? opacity : 0f);
        }
    }

    @FunctionalInterface
    private interface ReactionPixel {
        float opacity(float path, float cross, int x, int y);
    }

    private static void renderReactionMask(GuiGraphics graphics, Placed item, int color,
                                           ReactionPixel pixel) {
        ResourceSyncS2CPayload.Bar bar = item.bar();
        if (isSquircle(item.shape())) {
            int cx = item.x() + 20;
            int cy = item.y() + 20;
            for (int py = -15; py <= 15; py++) {
                for (int px = -15; px <= 15; px++) {
                    if (!insideRoundedSquare(px, py, 15, 5)
                            || insideRoundedSquare(px, py, 10, 3)) continue;
                    double angle = Math.atan2(px, -py);
                    if (angle < 0d) angle += Math.PI * 2d;
                    float path = (float) (angle / (Math.PI * 2d));
                    float cross = clamp01((Math.max(Math.abs(px), Math.abs(py)) - 10) / 5f);
                    float opacity = clamp01(pixel.opacity(path, cross, cx + px, cy + py)) * item.alpha();
                    if (opacity > 0.015f) graphics.fill(cx + px, cy + py, cx + px + 1, cy + py + 1,
                            withAlpha(0xFF000000 | color, opacity));
                }
            }
            return;
        }

        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        for (int py = 0; py < innerHeight; py++) {
            for (int px = 0; px < innerWidth; px++) {
                float path = vertical ? 1f - py / (float) Math.max(1, innerHeight - 1)
                        : px / (float) Math.max(1, innerWidth - 1);
                float cross = vertical ? px / (float) Math.max(1, innerWidth - 1)
                        : py / (float) Math.max(1, innerHeight - 1);
                int sx = innerX + px;
                int sy = innerY + py;
                float opacity = clamp01(pixel.opacity(path, cross, sx, sy)) * item.alpha();
                if (opacity > 0.015f) graphics.fill(sx, sy, sx + 1, sy + 1,
                        withAlpha(0xFF000000 | color, opacity));
            }
        }
    }

    private static void renderReactionBurst(GuiGraphics graphics, Placed item, int color,
                                            float progress, float strength, int count) {
        int cx;
        int cy;
        int rx;
        int ry;
        if (isSquircle(item.shape())) {
            cx = item.x() + 20; cy = item.y() + 20; rx = 18; ry = 18;
        } else if ("vertical".equals(item.shape())) {
            cx = item.x() + (hasSprite(item.bar()) ? 10 : 7); cy = item.y() + 32;
            rx = hasSprite(item.bar()) ? 10 : 7; ry = 32;
        } else {
            cx = item.x() + (hasSprite(item.bar()) ? 50 : 41);
            cy = item.y() + (hasSprite(item.bar()) ? 8 : 5);
            rx = hasSprite(item.bar()) ? 50 : 41; ry = hasSprite(item.bar()) ? 8 : 5;
        }
        float life = (float) Math.sin(progress * Math.PI) * strength * item.alpha();
        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.PI * 2d * i / count + (i % 3) * 0.17d);
            float outward = 1f + progress * (3f + i % 4);
            int px = cx + Math.round((rx + outward) * (float) Math.cos(angle));
            int py = cy + Math.round((ry + outward) * (float) Math.sin(angle));
            int argb = withAlpha(0xFF000000 | color, life * (0.65f + (i % 3) * 0.15f));
            graphics.fill(px, py, px + 1, py + 1, argb);
            if (i % 4 == 0 && progress < 0.7f) {
                graphics.fill(px - 1, py, px + 2, py + 1, argb);
                graphics.fill(px, py - 1, px + 1, py + 2, argb);
            }
        }
    }

    private static float eventProgress(long startedAt, float durationSeconds, long now) {
        if (startedAt == Long.MIN_VALUE || now < startedAt) return -1f;
        float progress = (now - startedAt) / Math.max(100f, durationSeconds * 1000f);
        return progress <= 1f ? progress : -1f;
    }

    private static float smoothStep(float value) {
        float n = clamp01(value);
        return n * n * (3f - 2f * n);
    }

    private static float band(float value, float center, float halfWidth) {
        return clamp01(1f - Math.abs(value - center) / Math.max(0.001f, halfWidth));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int pixelHash(int x, int y, String resourceId) {
        int hash = (resourceId == null ? 0 : resourceId.hashCode())
                ^ x * 0x45d9f3b ^ y * 0x27d4eb2d;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        return hash ^ hash >>> 15;
    }

    /** Tiny authored glyphs moving beneath the resource fill; packs may replace the glyph sheet. */
    private static void renderRunes(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = runeEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        int seed = (item.bar().resourceId() == null ? 0 : item.bar().resourceId().hashCode())
                ^ 0x6A09E667;
        int rgb = effect.color() < 0
                ? blend(item.fillColor(), 0xFFFFFFFF, 0.74f) & 0xFFFFFF : effect.color();
        boolean blink = "blink".equals(effect.runeMode());
        ResourceLocation texture = effect.runeTexture().isBlank()
                ? null : ResourceLocation.tryParse(effect.runeTexture());

        if (isSquircle(item.shape())) {
            int advance = Math.max(7, effect.runeGlyphWidth() + effect.runeSpacing() + 2);
            int count = Math.max(1, (int) Math.floor(104f * item.fraction() / advance));
            float scroll = blink ? 0f : (now / 6000f * effect.speed()) % 1f;
            for (int rune = 0; rune < count; rune++) {
                float around = ((rune + 0.5f) / count * item.fraction()
                        + scroll * item.fraction()) % Math.max(0.01f, item.fraction());
                int[] point = squirclePoint(around, 13);
                float opacity = runeOpacity(effect, blink, now, rune, seed, accessibility, item.alpha());
                drawRuneGlyph(graphics, item, item.x() + 20 + point[0] - effect.runeGlyphWidth() / 2,
                        item.y() + 20 + point[1] - effect.runeGlyphHeight() / 2,
                        rune, seed, rgb, opacity, effect, texture);
            }
            return;
        }

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        int filled = Math.max(1, Math.round((vertical ? innerHeight : innerWidth) * item.fraction()));
        int fillX = innerX;
        int fillY = vertical ? innerY + innerHeight - filled : innerY;
        int fillWidth = vertical ? innerWidth : filled;
        int fillHeight = vertical ? filled : innerHeight;
        int glyphWidth = effect.runeGlyphWidth();
        int glyphHeight = effect.runeGlyphHeight();
        int advance = (vertical ? glyphHeight : glyphWidth) + effect.runeSpacing();
        int scroll = blink ? 0 : (int) (now / 140f * effect.speed()) % Math.max(1, advance);
        int slots = (vertical ? fillHeight : fillWidth) / Math.max(1, advance) + 3;

        for (int rune = -1; rune < slots; rune++) {
            int drawX = vertical ? fillX + (fillWidth - glyphWidth) / 2
                    : fillX + rune * advance - scroll;
            int drawY = vertical ? fillY + fillHeight - glyphHeight - rune * advance - scroll
                    : fillY + (fillHeight - glyphHeight) / 2;
            float opacity = runeOpacity(effect, blink, now, rune, seed,
                    accessibility, item.alpha());
            drawRuneGlyph(graphics, item, drawX, drawY, rune, seed, rgb, opacity,
                    effect, texture);
        }
    }

    private static float runeOpacity(ResourceSyncS2CPayload.Effect effect, boolean blink,
                                     long now, int rune, int seed, float accessibility,
                                     float barAlpha) {
        if (!blink) return effect.strength() * accessibility * barAlpha;
        int hash = electricHash(seed, rune, 0x7A11);
        float phase = now / 720f * effect.speed() + (hash & 0xFFFF) / 65535f * 6.2831855f;
        float light = Math.max(0f, (float) Math.sin(phase));
        return effect.strength() * accessibility * barAlpha * (0.12f + light * light * 0.88f);
    }

    private static void drawRuneGlyph(GuiGraphics graphics, Placed item,
                                      int x, int y, int rune, int seed,
                                      int rgb, float opacity,
                                      ResourceSyncS2CPayload.Effect effect,
                                      ResourceLocation texture) {
        int glyphs = Math.max(1, effect.runeColumns() * effect.runeRows());
        int index = Math.floorMod(rune + seed, texture == null ? BUILTIN_RUNES.length : glyphs);
        if (texture != null) {
            for (int py = 0; py < effect.runeGlyphHeight(); py++) {
                for (int px = 0; px < effect.runeGlyphWidth(); px++) {
                    if (!runePixelVisible(item, x + px, y + py)) return;
                }
            }
            int column = index % effect.runeColumns();
            int row = index / effect.runeColumns();
            RenderSystem.setShaderColor(((rgb >>> 16) & 0xFF) / 255f,
                    ((rgb >>> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, opacity);
            try {
                graphics.blit(texture, x, y, effect.runeGlyphWidth(), effect.runeGlyphHeight(),
                        (float) (column * effect.runeGlyphWidth()),
                        (float) (row * effect.runeGlyphHeight()),
                        effect.runeGlyphWidth(), effect.runeGlyphHeight(),
                        effect.runeGlyphWidth() * effect.runeColumns(),
                        effect.runeGlyphHeight() * effect.runeRows());
            } finally {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            return;
        }

        String pattern = BUILTIN_RUNES[index];
        int color = withAlpha(0xFF000000 | rgb, opacity);
        for (int py = 0; py < 5; py++) {
            for (int px = 0; px < 3; px++) {
                if (pattern.charAt(py * 3 + px) == '1') {
                    int screenX = x + px;
                    int screenY = y + py;
                    if (runePixelVisible(item, screenX, screenY)) {
                        graphics.fill(screenX, screenY, screenX + 1, screenY + 1, color);
                    }
                }
            }
        }
    }

    private static boolean runePixelVisible(Placed item, int screenX, int screenY) {
        if (isSquircle(item.shape())) {
            int px = screenX - (item.x() + 20);
            int py = screenY - (item.y() + 20);
            if (!insideRoundedSquare(px, py, 15, 5)
                    || insideRoundedSquare(px, py, 10, 3)) return false;
            double angle = Math.atan2(px, -py);
            if (angle < 0d) angle += Math.PI * 2d;
            return angle / (Math.PI * 2d) <= item.fraction();
        }

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        if (vertical) {
            int filled = Math.max(1, Math.round(innerHeight * item.fraction()));
            return screenX >= innerX && screenX < innerX + innerWidth
                    && screenY >= innerY + innerHeight - filled
                    && screenY < innerY + innerHeight;
        }
        int filled = Math.max(1, Math.round(innerWidth * item.fraction()));
        return screenX >= innerX && screenX < innerX + filled
                && screenY >= innerY && screenY < innerY + innerHeight;
    }

    /** A quieter second layer: a handful of intact glyphs orbit just beyond the frame. */
    private static void renderFlyingRunes(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = runeEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f
                || effect.runeEscape() <= 0f) return;

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean sprite = hasSprite(bar);
        boolean vertical = "vertical".equals(item.shape());
        int width = isSquircle(item.shape()) ? 40 : vertical ? (sprite ? 20 : 14) : (sprite ? 100 : 82);
        int height = isSquircle(item.shape()) ? 40 : vertical ? (sprite ? 68 : 64) : (sprite ? 16 : 10);
        float centerX = item.x() + width / 2f;
        float centerY = item.y() + height / 2f;
        int seed = (bar.resourceId() == null ? 0 : bar.resourceId().hashCode()) ^ 0xBB67AE85;
        int rgb = effect.color() < 0
                ? blend(item.fillColor(), 0xFFFFFFFF, 0.74f) & 0xFFFFFF : effect.color();
        ResourceLocation texture = effect.runeTexture().isBlank()
                ? null : ResourceLocation.tryParse(effect.runeTexture());
        int glyphWidth = texture == null ? 3 : effect.runeGlyphWidth();
        int glyphHeight = texture == null ? 5 : effect.runeGlyphHeight();
        int count = 2 + Math.round(effect.runeEscape() * 4f);

        for (int rune = 0; rune < count; rune++) {
            int hash = electricHash(seed, rune, 0x52DCE729);
            float offset = (hash & 0xFFFF) / 65535f;
            float variance = 0.76f + ((hash >>> 16) & 0xFF) / 255f * 0.42f;
            float direction = ((hash >>> 25) & 1) == 0 ? 1f : -1f;
            float phase = now / 3800f * effect.speed() * variance * direction
                    + offset * 6.2831855f;
            float wobble = (float) Math.sin(phase * 2.3f + rune * 1.7f)
                    * (1f + effect.runeEscape() * 2f);
            float radiusX;
            float radiusY;
            if (isSquircle(item.shape())) {
                radiusX = 19f + effect.runeEscape() * 5f + wobble;
                radiusY = radiusX;
            } else if (vertical) {
                radiusX = width / 2f + 4f + effect.runeEscape() * 4f + wobble * 0.35f;
                radiusY = height * 0.40f;
            } else {
                radiusX = width * 0.42f;
                radiusY = height / 2f + 5f + effect.runeEscape() * 5f + wobble * 0.35f;
            }
            int drawX = Math.round(centerX + (float) Math.cos(phase) * radiusX) - glyphWidth / 2;
            int drawY = Math.round(centerY + (float) Math.sin(phase) * radiusY) - glyphHeight / 2;
            float pulse = 0.45f + 0.55f * (0.5f + 0.5f
                    * (float) Math.sin(phase * 1.7f + offset * 5f));
            float opacity = item.alpha() * accessibility * effect.strength()
                    * effect.runeEscape() * pulse;
            drawFlyingRuneGlyph(graphics, drawX, drawY, rune, seed, rgb, opacity,
                    effect, texture);
        }
    }

    private static void drawFlyingRuneGlyph(GuiGraphics graphics, int x, int y,
                                             int rune, int seed, int rgb, float opacity,
                                             ResourceSyncS2CPayload.Effect effect,
                                             ResourceLocation texture) {
        int glyphs = Math.max(1, effect.runeColumns() * effect.runeRows());
        int index = Math.floorMod(rune + seed, texture == null ? BUILTIN_RUNES.length : glyphs);
        if (texture != null) {
            int column = index % effect.runeColumns();
            int row = index / effect.runeColumns();
            RenderSystem.setShaderColor(((rgb >>> 16) & 0xFF) / 255f,
                    ((rgb >>> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, opacity);
            try {
                graphics.blit(texture, x, y, effect.runeGlyphWidth(), effect.runeGlyphHeight(),
                        (float) (column * effect.runeGlyphWidth()),
                        (float) (row * effect.runeGlyphHeight()),
                        effect.runeGlyphWidth(), effect.runeGlyphHeight(),
                        effect.runeGlyphWidth() * effect.runeColumns(),
                        effect.runeGlyphHeight() * effect.runeRows());
            } finally {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
            return;
        }

        String pattern = BUILTIN_RUNES[index];
        int color = withAlpha(0xFF000000 | rgb, opacity);
        for (int py = 0; py < 5; py++) {
            for (int px = 0; px < 3; px++) {
                if (pattern.charAt(py * 3 + px) == '1') {
                    graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                }
            }
        }
    }

    /** Temporary crawling voids overlay the fill without moving its real endpoint. */
    private static void renderCorruption(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = corruptionEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        int seed = (item.bar().resourceId() == null ? 0 : item.bar().resourceId().hashCode())
                ^ 0xA54FF53A;
        int defaultRgb = blend(darken(item.bar().backgroundColor(), 0.58f),
                0xFF51245F, 0.38f) & 0xFFFFFF;
        int rgb = effect.color() < 0 ? defaultRgb : effect.color();
        float cycleTime = 2100f / effect.speed();
        float baseTime = now / cycleTime;

        for (int patch = 0; patch < effect.corruptionCount(); patch++) {
            int phaseHash = electricHash(seed, patch, 0x1B873593);
            float time = baseTime + (phaseHash & 0xFFFF) / 65535f;
            int cycle = (int) Math.floor(time);
            float local = time - cycle;
            float eased = local * local * (3f - 2f * local);
            float life = (float) Math.sin(local * Math.PI);
            if (life <= 0.02f) continue;
            int radius = Math.max(1, Math.round(effect.corruptionSize() * life));
            int currentHash = electricHash(seed, patch, cycle);
            int nextHash = electricHash(seed, patch, cycle + 1);
            int[] current = corruptionPoint(item, currentHash);
            int[] next = corruptionPoint(item, nextHash);
            int centerX = Math.round(current[0] + (next[0] - current[0]) * eased);
            int centerY = Math.round(current[1] + (next[1] - current[1]) * eased);
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int distance = Math.abs(dx) + Math.abs(dy);
                    if (distance > radius + 1) continue;
                    int pixelHash = electricHash(currentHash, dx + radius,
                            dy + radius * 17);
                    if (distance > radius - 1 && (pixelHash & 3) == 0) continue;
                    int screenX = centerX + dx;
                    int screenY = centerY + dy;
                    if (!runePixelVisible(item, screenX, screenY)) continue;
                    float edge = 1f - distance / (float) Math.max(2, radius + 1);
                    float opacity = item.alpha() * accessibility * effect.strength()
                            * life * (0.62f + edge * 0.38f);
                    graphics.fill(screenX, screenY, screenX + 1, screenY + 1,
                            withAlpha(0xFF000000 | rgb, opacity));
                }
            }
        }
    }

    private static int[] corruptionPoint(Placed item, int hash) {
        if (isSquircle(item.shape())) {
            float around = ((hash >>> 1) & 0xFFFF) / 65535f * item.fraction();
            int radius = 11 + ((hash >>> 18) & 3);
            int[] point = squirclePoint(around, radius);
            return new int[]{item.x() + 20 + point[0], item.y() + 20 + point[1]};
        }

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        float along = ((hash >>> 1) & 0xFFFF) / 65535f;
        float across = ((hash >>> 17) & 0x7FFF) / 32767f;
        if (vertical) {
            int filled = Math.max(1, Math.round(innerHeight * item.fraction()));
            return new int[]{innerX + Math.round(across * Math.max(0, innerWidth - 1)),
                    innerY + innerHeight - 1 - Math.round(along * Math.max(0, filled - 1))};
        }
        int filled = Math.max(1, Math.round(innerWidth * item.fraction()));
        return new int[]{innerX + Math.round(along * Math.max(0, filled - 1)),
                innerY + Math.round(across * Math.max(0, innerHeight - 1))};
    }

    /** Falling dust/snow/ash/petals pass through the fill and continue below the frame. */
    private static void renderFallingMotes(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = fallingMotesEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;
        int seed = (item.bar().resourceId() == null ? 0 : item.bar().resourceId().hashCode())
                ^ 0xB7E15162;
        int rgb = effect.color() < 0 ? lighten(item.fillColor(), 0.72f) & 0xFFFFFF
                : effect.color();
        ResourceLocation texture = effect.fallingTexture().isBlank()
                ? null : ResourceLocation.tryParse(effect.fallingTexture());

        for (int mote = 0; mote < effect.fallingCount(); mote++) {
            int hash = electricHash(seed, mote, 0x85EBCA6B);
            float offset = (hash & 0xFFFF) / 65535f;
            float variance = 0.72f + ((hash >>> 16) & 0xFF) / 255f * 0.56f;
            float progress = now / 4300f * effect.speed() * variance + offset;
            progress -= (float) Math.floor(progress);
            int[] point = fallingPoint(item, hash, progress, effect.fallingDrift(), mote);
            float opacity = item.alpha() * accessibility * effect.strength()
                    * (0.55f + 0.45f * (float) Math.sin(progress * Math.PI));
            if (texture != null) {
                drawFallingTexture(graphics, point[0], point[1], mote, hash,
                        rgb, opacity, effect, texture);
            } else {
                drawFallingMark(graphics, point[0], point[1], mote, rgb, opacity,
                        effect.fallingSize());
            }
        }
    }

    private static int[] fallingPoint(Placed item, int hash, float progress,
                                      float drift, int mote) {
        if (isSquircle(item.shape())) {
            int x = item.x() + 4 + Math.floorMod(hash, 33);
            int y = item.y() + 2 + Math.round(progress * 50f);
            x += Math.round((float) Math.sin(progress * 6.2831855f + mote) * drift * 3f);
            return new int[]{x, y};
        }
        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        int filled = Math.max(1, Math.round((vertical ? innerHeight : innerWidth) * item.fraction()));
        int xSpan = vertical ? innerWidth : filled;
        int x = innerX + Math.floorMod(hash, Math.max(1, xSpan));
        int yStart = (vertical ? innerY + innerHeight - filled : innerY) - 2;
        int ySpan = (vertical ? filled : innerHeight) + 13;
        x += Math.round((float) Math.sin(progress * 6.2831855f + mote * 1.7f)
                * drift * Math.min(3f, xSpan * 0.18f));
        return new int[]{x, yStart + Math.round(progress * ySpan)};
    }

    private static void drawFallingMark(GuiGraphics graphics, int x, int y,
                                        int index, int rgb, float opacity, int size) {
        int color = withAlpha(0xFF000000 | rgb, opacity);
        graphics.fill(x, y, x + 1, y + 1, color);
        if (size <= 1) return;
        switch (Math.floorMod(index, 4)) {
            case 0 -> {
                graphics.fill(x - 1, y, x + 2, y + 1, color);
                graphics.fill(x, y - 1, x + 1, y + 2, color);
            }
            case 1 -> graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
            case 2 -> graphics.fill(x, y - 1, x + 1, y, color);
            default -> graphics.fill(x - 1, y + 1, x, y + 2, color);
        }
        if (size >= 3) graphics.fill(x + 1, y - 1, x + 2, y, color);
        if (size >= 4) graphics.fill(x - 1, y - 1, x, y, color);
    }

    private static void drawFallingTexture(GuiGraphics graphics, int centerX,
                                           int centerY, int index, int hash, int rgb,
                                           float opacity, ResourceSyncS2CPayload.Effect effect,
                                           ResourceLocation texture) {
        int width = effect.fallingMarkWidth();
        int height = effect.fallingMarkHeight();
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        int cells = Math.max(1, effect.fallingColumns() * effect.fallingRows());
        int cell = Math.floorMod(index + hash, cells);
        RenderSystem.setShaderColor(((rgb >>> 16) & 0xFF) / 255f,
                ((rgb >>> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, opacity);
        try {
            graphics.blit(texture, x, y, width, height,
                    (float) ((cell % effect.fallingColumns()) * width),
                    (float) ((cell / effect.fallingColumns()) * height),
                    width, height, width * effect.fallingColumns(),
                    height * effect.fallingRows());
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /** Soft biological motes drift away from every side of the frame. */
    private static void renderSpores(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = sporeEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;
        int seed = (item.bar().resourceId() == null ? 0 : item.bar().resourceId().hashCode())
                ^ 0xC6EF3720;
        int rgb = effect.color() < 0 ? 0xC9E88A : effect.color();
        boolean vertical = "vertical".equals(item.shape());
        int width = isSquircle(item.shape()) ? 40 : vertical ? (hasSprite(item.bar()) ? 20 : 14)
                : (hasSprite(item.bar()) ? 100 : 82);
        int height = isSquircle(item.shape()) ? 40 : vertical ? (hasSprite(item.bar()) ? 68 : 64)
                : (hasSprite(item.bar()) ? 16 : 10);
        for (int spore = 0; spore < effect.sporeCount(); spore++) {
            int hash = electricHash(seed, spore, 0x27D4EB2F);
            float offset = (hash & 0xFFFF) / 65535f;
            float variance = 0.68f + ((hash >>> 16) & 0xFF) / 255f * 0.64f;
            float progress = now / 5200f * effect.speed() * variance + offset;
            progress -= (float) Math.floor(progress);
            float lane = ((hash >>> 8) & 0xFFFF) / 65535f;
            int direction = Math.floorMod(spore + seed, 8);
            float angle = direction * ((float) Math.PI / 4f);
            float directionX = (float) Math.cos(angle);
            float directionY = (float) Math.sin(angle);
            float originX;
            float originY;
            if (isSquircle(item.shape())) {
                originX = item.x() + width / 2f + directionX * 17f;
                originY = item.y() + height / 2f + directionY * 17f;
            } else {
                float secondLane = ((hash >>> 16) & 0xFFFF) / 65535f;
                originX = directionX < -0.25f ? item.x()
                        : directionX > 0.25f ? item.x() + width - 1f
                        : item.x() + lane * Math.max(1, width - 1);
                originY = directionY < -0.25f ? item.y()
                        : directionY > 0.25f ? item.y() + height - 1f
                        : item.y() + secondLane * Math.max(1, height - 1);
            }
            float reach = 8f + effect.sporeDrift() * 12f;
            float wobble = (float) Math.sin(progress * 7.2f + spore * 1.31f)
                    * (1f + effect.sporeDrift() * 3f);
            int x = Math.round(originX + directionX * progress * reach - directionY * wobble);
            int y = Math.round(originY + directionY * progress * reach + directionX * wobble);
            float fade = (float) Math.sin(progress * Math.PI);
            drawSoftMote(graphics, x, y, rgb,
                    item.alpha() * accessibility * effect.strength() * fade,
                    effect.sporeSize(), hash);
        }
    }

    private static void drawSoftMote(GuiGraphics graphics, int x, int y, int rgb,
                                     float opacity, int size, int hash) {
        if (opacity <= 0.02f) return;
        int core = withAlpha(0xFF000000 | rgb, opacity);
        graphics.fill(x, y, x + 1, y + 1, core);
        if (size >= 2) {
            int soft = withAlpha(0xFF000000 | rgb, opacity * 0.48f);
            graphics.fill(x + ((hash & 1) == 0 ? 1 : -1), y,
                    x + ((hash & 1) == 0 ? 2 : 0), y + 1, soft);
            graphics.fill(x, y - 1, x + 1, y, soft);
        }
        if (size >= 3) {
            int faint = withAlpha(0xFF000000 | rgb, opacity * 0.26f);
            graphics.fill(x, y + 1, x + 1, y + 2, faint);
            graphics.fill(x - 1, y, x, y + 1, faint);
        }
    }

    /** Translucent glass: a bevel, a few broad planes, and a slow three-colour refraction. */
    private static void renderCrystalline(GuiGraphics graphics, Placed item, long now) {
        ResourceSyncS2CPayload.Effect effect = crystallineEffect(item.bar());
        float accessibility = Accessibility.effectIntensity();
        if (effect == null || accessibility <= 0f || item.fraction() <= 0.01f) return;

        int tint = effect.color() < 0 ? item.fillColor() & 0xFFFFFF : effect.color();
        int highlightRgb = blend(0xFF000000 | tint, 0xFFFFFFFF, 0.72f) & 0xFFFFFF;
        int shadowRgb = blend(0xFF000000 | tint, 0xFF101626, 0.62f) & 0xFFFFFF;
        float opacity = item.alpha() * accessibility * effect.strength();
        float glintProgress = now / 4800f * effect.speed();
        glintProgress -= (float) Math.floor(glintProgress);

        if (isSquircle(item.shape())) {
            renderCrystallineSquircle(graphics, item, effect, tint, highlightRgb,
                    shadowRgb, opacity, glintProgress);
            return;
        }

        ResourceSyncS2CPayload.Bar bar = item.bar();
        boolean vertical = "vertical".equals(item.shape());
        boolean sprite = hasSprite(bar);
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = item.x() + (sprite ? (vertical ? 6 : 10) : t);
        int innerY = item.y() + (sprite ? (vertical ? 6 : 4) : t);
        int innerWidth = sprite ? (vertical ? 8 : 80)
                : Math.max(1, (vertical ? 14 : 82) - t * 2);
        int innerHeight = sprite ? (vertical ? 56 : 8)
                : Math.max(1, (vertical ? 64 : 10) - t * 2);
        int filled = Math.max(1, Math.round(
                (vertical ? innerHeight : innerWidth) * item.fraction()));
        int fillX = innerX;
        int fillY = vertical ? innerY + innerHeight - filled : innerY;
        int fillWidth = vertical ? innerWidth : filled;
        int fillHeight = vertical ? filled : innerHeight;
        int depth = Math.min(effect.crystalDepth(),
                Math.max(1, Math.min(fillWidth, fillHeight) / 2));

        // The fill reads as one solid piece of glass, even when too small for facets.
        for (int inset = 0; inset < depth; inset++) {
            int left = fillX + inset;
            int top = fillY + inset;
            int right = fillX + fillWidth - inset;
            int bottom = fillY + fillHeight - inset;
            if (left >= right || top >= bottom) break;
            float fade = 1f - inset / (float) Math.max(1, depth);
            int light = withAlpha(0xFF000000 | highlightRgb, opacity * 0.46f * fade);
            int dark = withAlpha(0xFF000000 | shadowRgb, opacity * 0.42f * fade);
            graphics.fill(left, top, right, top + 1, light);
            graphics.fill(left, top, left + 1, bottom, light);
            graphics.fill(left, bottom - 1, right, bottom, dark);
            graphics.fill(right - 1, top, right, bottom, dark);
        }

        int available = vertical ? fillHeight : fillWidth;
        int facetCount = crystallineFacetCount(available, effect.crystalCount());
        int facetLight = blend(0xFF000000 | tint, 0xFFFFFFFF, 0.30f) & 0xFFFFFF;
        int facetDark = blend(0xFF000000 | tint, 0xFF101626, 0.26f) & 0xFFFFFF;
        for (int py = 0; py < fillHeight; py++) {
            for (int px = 0; px < fillWidth; px++) {
                float along = vertical
                        ? 1f - normalizedPixel(py, fillHeight)
                        : normalizedPixel(px, fillWidth);
                float cross = vertical
                        ? normalizedPixel(px, fillWidth)
                        : normalizedPixel(py, fillHeight);
                renderCrystalFacetPixel(graphics, fillX + px, fillY + py,
                        along, cross, facetCount, facetLight, facetDark, opacity);
                renderPrismaticPixel(graphics, fillX + px, fillY + py,
                        along, cross, tint, opacity, effect.crystalGlint(), glintProgress);
            }
        }
    }

    private static void renderCrystallineSquircle(GuiGraphics graphics, Placed item,
                                                   ResourceSyncS2CPayload.Effect effect,
                                                   int tint, int highlightRgb, int shadowRgb,
                                                   float opacity, float glintProgress) {
        int centerX = item.x() + 20;
        int centerY = item.y() + 20;
        int facetCount = crystallineFacetCount(Math.round(104f * item.fraction()),
                effect.crystalCount());
        int facetLight = blend(0xFF000000 | tint, 0xFFFFFFFF, 0.30f) & 0xFFFFFF;
        int facetDark = blend(0xFF000000 | tint, 0xFF101626, 0.26f) & 0xFFFFFF;
        for (int py = -15; py <= 15; py++) {
            for (int px = -15; px <= 15; px++) {
                if (!insideRoundedSquare(px, py, 15, 5)
                        || insideRoundedSquare(px, py, 10, 3)) continue;
                double angle = Math.atan2(px, -py);
                if (angle < 0d) angle += Math.PI * 2d;
                float around = (float) (angle / (Math.PI * 2d));
                if (around > item.fraction()) continue;
                float along = around / Math.max(0.01f, item.fraction());
                float radial = Math.max(0f, Math.min(1f,
                        (Math.max(Math.abs(px), Math.abs(py)) - 10) / 5f));
                int screenX = centerX + px;
                int screenY = centerY + py;

                float directional = Math.max(-1f, Math.min(1f, -(px + py) / 18f));
                int bevelColor = directional >= 0f ? highlightRgb : shadowRgb;
                float bevelAlpha = opacity * (0.10f + Math.abs(directional) * 0.24f)
                        * (0.45f + Math.abs(radial - 0.5f));
                graphics.fill(screenX, screenY, screenX + 1, screenY + 1,
                        withAlpha(0xFF000000 | bevelColor, bevelAlpha));

                renderCrystalFacetPixel(graphics, screenX, screenY, along, radial,
                        facetCount, facetLight, facetDark, opacity);
                renderPrismaticPixel(graphics, screenX, screenY, around, radial,
                        tint, opacity, effect.crystalGlint(), glintProgress);
            }
        }
    }

    private static int crystallineFacetCount(int availablePixels, int maximum) {
        int capacity = availablePixels < 16 ? 0
                : availablePixels < 34 ? 1
                : availablePixels < 58 ? 2 : 3;
        return Math.min(maximum, capacity);
    }

    private static void renderCrystalFacetPixel(GuiGraphics graphics, int x, int y,
                                                float along, float cross, int count,
                                                int lightRgb, int darkRgb, float opacity) {
        if (count <= 0) return;
        float plane = along + (cross - 0.5f) * 0.24f;
        float halfWidth = 0.075f;
        for (int facet = 0; facet < count; facet++) {
            float center = (facet + 1f) / (count + 1f);
            float distance = Math.abs(plane - center);
            if (distance >= halfWidth) continue;
            float envelope = 1f - distance / halfWidth;
            int rgb = facet % 2 == 0 ? lightRgb : darkRgb;
            graphics.fill(x, y, x + 1, y + 1,
                    withAlpha(0xFF000000 | rgb, opacity * envelope * 0.20f));
            return;
        }
    }

    private static void renderPrismaticPixel(GuiGraphics graphics, int x, int y,
                                             float along, float cross, int tint,
                                             float opacity, float strength, float progress) {
        if (strength <= 0f) return;
        float center = -0.16f + progress * 1.32f;
        float coordinate = along + (cross - 0.5f) * 0.30f;
        float distance = coordinate - center;
        float envelope = 1f - Math.abs(distance) / 0.095f;
        if (envelope <= 0f) return;
        int prism = distance < -0.025f ? 0x7FF5FF
                : distance > 0.025f ? 0xFFE58A : 0xC89BFF;
        int target = blend(0xFF000000 | tint, 0xFF000000 | prism, 0.86f) & 0xFFFFFF;
        graphics.fill(x, y, x + 1, y + 1,
                withAlpha(0xFF000000 | target, opacity * strength * envelope * 0.78f));
    }

    private static void renderHorizontal(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                         int x, int y, float fraction, int fillColor, float alpha, long now) {
        boolean sprite = hasSprite(bar);
        int width = sprite ? 100 : 82;
        int height = sprite ? 16 : 10;
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = x + (sprite ? 10 : t);
        int innerY = y + (sprite ? 4 : t);
        int innerWidth = sprite ? 80 : Math.max(1, width - t * 2);
        int innerHeight = sprite ? 8 : Math.max(1, height - t * 2);
        boolean customArt = usesCustomFrameArt(bar);
        if (customArt) {
            graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight,
                    troughColor(bar, alpha));
        } else {
            frame(graphics, bar, x, y, width, height, alpha);
        }
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, false, now);
        if (customArt) drawCustomFrameArt(graphics, bar, x, y, width, height, alpha);
    }

    private static void renderVertical(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                       int x, int y, float fraction, int fillColor, float alpha, long now) {
        boolean sprite = hasSprite(bar);
        int width = sprite ? 20 : 14;
        int height = sprite ? 68 : 64;
        int t = Math.max(1, Math.min(4, bar.frameThickness()));
        int innerX = x + (sprite ? 6 : t);
        int innerY = y + (sprite ? 6 : t);
        int innerWidth = sprite ? 8 : Math.max(1, width - t * 2);
        int innerHeight = sprite ? 56 : Math.max(1, height - t * 2);
        boolean customArt = usesCustomFrameArt(bar);
        if (customArt) {
            graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight,
                    troughColor(bar, alpha));
        } else {
            frame(graphics, bar, x, y, width, height, alpha);
        }
        renderLinear(graphics, bar, innerX, innerY, innerWidth, innerHeight, fraction, fillColor, true, now);
        if (customArt) drawCustomFrameArt(graphics, bar, x, y, width, height, alpha);
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
            units = Math.min(units, linearPipCapacity(bar.pipStyle(), length));
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
        int empty = lighten(bar.backgroundColor(), 0.24f);
        int length = vertical ? height : width;
        int inset = pipInset(bar.pipStyle());
        int span = Math.max(0, length - 1 - inset * 2);
        ResourceSyncS2CPayload.Effect liquid = liquidEffect(bar);
        float liquidAccessibility = Accessibility.effectIntensity();
        LiquidSurface liquidSurface = liquid != null && liquidAccessibility > 0f
                ? liquidSurface(bar, liquid, now) : null;
        for (int i = 0; i < units; i++) {
            float along = (i + 0.5f) / units;
            float position = units <= 1 ? inset + span * 0.5f
                    : inset + i * span / (float) (units - 1);
            int px = vertical ? x + width / 2 : x + Math.round(position);
            int py = vertical ? y + height - 1 - Math.round(position) : y + height / 2;
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
            drawPip(graphics, px, py, bar.pipStyle(), color, highlight, vertical, i < filled);
        }
    }

    private static int linearPipCapacity(String rawStyle, int length) {
        int inset = pipInset(rawStyle);
        int usable = Math.max(0, length - 1 - inset * 2);
        return Math.max(2, 1 + usable / pipPitch(rawStyle));
    }

    private static int squirclePipCapacity(String rawStyle) {
        return switch (normalized(rawStyle)) {
            case "beads", "bead", "pearls", "shards", "shard", "crystals" -> 16;
            case "notches", "notch", "ticks" -> 28;
            default -> 24;
        };
    }

    private static int pipInset(String rawStyle) {
        return switch (normalized(rawStyle)) {
            case "beads", "bead", "pearls", "shards", "shard", "crystals" -> 2;
            default -> 1;
        };
    }

    private static int pipPitch(String rawStyle) {
        return switch (normalized(rawStyle)) {
            case "beads", "bead", "pearls" -> 5;
            case "shards", "shard", "crystals" -> 4;
            case "notches", "notch", "ticks" -> 3;
            default -> 4;
        };
    }

    /** Pixel-art marks centered at (x,y); pips remain compact glyphs, never tiny bar slices. */
    private static void drawPip(GuiGraphics graphics, int x, int y, String rawStyle,
                                int color, int highlight, boolean horizontalAxis,
                                boolean filled) {
        String style = normalized(rawStyle);
        int shadow = darken(color, 0.55f);
        if (!filled) {
            drawEmptyPip(graphics, x, y, style, color, horizontalAxis);
            return;
        }
        switch (style) {
            case "notches", "notch", "ticks" -> {
                if (horizontalAxis) {
                    graphics.fill(x - 2, y - 1, x + 2, y + 1, color);
                    graphics.fill(x - 2, y - 1, x - 1, y + 1, highlight);
                    graphics.fill(x + 1, y, x + 2, y + 1, shadow);
                } else {
                    graphics.fill(x - 1, y - 2, x + 1, y + 2, color);
                    graphics.fill(x - 1, y - 2, x + 1, y - 1, highlight);
                    graphics.fill(x, y + 1, x + 1, y + 2, shadow);
                }
            }
            case "beads", "bead", "pearls" -> {
                graphics.fill(x - 1, y - 2, x + 1, y - 1, color);
                graphics.fill(x - 2, y - 1, x + 2, y + 1, color);
                graphics.fill(x - 1, y + 1, x + 1, y + 2, color);
                graphics.fill(x - 1, y - 2, x + 1, y - 1, highlight);
                graphics.fill(x - 2, y - 1, x - 1, y + 1, highlight);
                graphics.fill(x - 1, y + 1, x + 1, y + 2, shadow);
                graphics.fill(x + 1, y - 1, x + 2, y + 1, shadow);
            }
            case "shards", "shard", "crystals" -> {
                if (horizontalAxis) {
                    graphics.fill(x - 2, y, x, y + 1, color);
                    graphics.fill(x - 1, y - 1, x + 2, y, color);
                    graphics.fill(x - 2, y, x - 1, y + 1, highlight);
                    graphics.fill(x - 1, y - 1, x, y, highlight);
                    graphics.fill(x + 1, y - 1, x + 2, y, shadow);
                } else {
                    graphics.fill(x, y - 2, x + 1, y, color);
                    graphics.fill(x - 1, y - 1, x, y + 2, color);
                    graphics.fill(x, y - 2, x + 1, y - 1, highlight);
                    graphics.fill(x - 1, y - 1, x, y, highlight);
                    graphics.fill(x - 1, y + 1, x, y + 2, shadow);
                }
            }
            default -> {
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
                graphics.fill(x - 1, y - 1, x, y, highlight);
                graphics.fill(x, y, x + 1, y + 1, shadow);
            }
        }
    }

    private static void drawEmptyPip(GuiGraphics graphics, int x, int y, String style,
                                     int color, boolean horizontalAxis) {
        switch (style) {
            case "notches", "notch", "ticks" -> {
                if (horizontalAxis) graphics.fill(x - 2, y, x + 2, y + 1, color);
                else graphics.fill(x, y - 2, x + 1, y + 2, color);
            }
            case "beads", "bead", "pearls" -> {
                graphics.fill(x - 1, y - 2, x + 1, y - 1, color);
                graphics.fill(x - 2, y - 1, x - 1, y + 1, color);
                graphics.fill(x + 1, y - 1, x + 2, y + 1, color);
                graphics.fill(x - 1, y + 1, x + 1, y + 2, color);
            }
            case "shards", "shard", "crystals" -> {
                if (horizontalAxis) {
                    graphics.fill(x - 2, y, x, y + 1, color);
                    graphics.fill(x - 1, y - 1, x + 2, y, color);
                } else {
                    graphics.fill(x, y - 2, x + 1, y, color);
                    graphics.fill(x - 1, y - 1, x, y + 2, color);
                }
            }
            default -> {
                graphics.fill(x, y, x + 1, y + 1, color);
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
        boolean customArt = usesCustomFrameArt(bar);
        if (!customArt) {
            if (hasSprite(bar) && !Accessibility.highContrast()) frame(graphics, bar, x, y, 40, 40, alpha);
            else squircleFrame(graphics, bar, centerX, centerY, alpha);
        }
        String mode = normalized(bar.fillMode());
        int empty = troughColor(bar, alpha);
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
            units = Math.min(units, squirclePipCapacity(bar.pipStyle()));
            int filled = ResourceHudMath.filledUnits(displayedFraction, units);
            int emptyPip = withAlpha(lighten(bar.backgroundColor(), 0.24f), alpha);
            for (int i = 0; i < units; i++) {
                float along = (i + 0.5f) / units;
                int radius = liquidSurface != null
                        ? 13 + Math.round(liquidSurface.sample(along)
                        * liquid.strength() * effectAccessibility * 1.5f) : 13;
                int[] point = squirclePoint(along, radius);
                int px = centerX + point[0];
                int py = centerY + point[1];
                int pipColor = applyBarEffects(bar, fillColor, 0.82f, along, 0.64f,
                        along, px, py, now);
                int pipHighlight = applyBarEffects(
                        bar, lighten(fillColor, 0.45f), 0.08f, along, 0.92f,
                        along, px, py, now);
                boolean horizontalAxis = Math.abs(point[0]) >= Math.abs(point[1]);
                drawPip(graphics, px, py, bar.pipStyle(), i < filled ? pipColor : emptyPip,
                        i < filled ? pipHighlight : darken(emptyPip, 0.72f),
                        horizontalAxis, i < filled);
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
        if (customArt) drawCustomFrameArt(graphics, bar, x, y, 40, 40, alpha);
    }

    /** Rounded-square trough modeled after the original GIF: transparent corners and centre. */
    private static void squircleFrame(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                    int centerX, int centerY, float alpha) {
        int background = troughColor(bar, alpha);
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

    private static boolean usesCustomFrameArt(ResourceSyncS2CPayload.Bar bar) {
        if (Accessibility.highContrast()) return false;
        return availableFrameTexture(bar.frameBaseTexture()) != null
                || availableFrameTexture(bar.framePrimaryTexture()) != null
                || availableFrameTexture(bar.frameSecondaryTexture()) != null;
    }

    private static ResourceLocation availableFrameTexture(String raw) {
        if (raw == null || raw.isBlank()) return null;
        ResourceLocation texture = ResourceLocation.tryParse(raw);
        if (texture == null) return null;
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()
                ? texture : null;
    }

    private static void drawCustomFrameArt(GuiGraphics graphics, ResourceSyncS2CPayload.Bar bar,
                                           int x, int y, int width, int height, float alpha) {
        drawFrameArtLayer(graphics, availableFrameTexture(bar.frameBaseTexture()),
                x, y, width, height, 0xFFFFFF, alpha);
        drawFrameArtLayer(graphics, availableFrameTexture(bar.framePrimaryTexture()),
                x, y, width, height, bar.framePrimaryColor(), alpha);
        drawFrameArtLayer(graphics, availableFrameTexture(bar.frameSecondaryTexture()),
                x, y, width, height, bar.frameSecondaryColor(), alpha);
    }

    private static void drawFrameArtLayer(GuiGraphics graphics, ResourceLocation texture,
                                          int x, int y, int width, int height,
                                          int rgb, float alpha) {
        if (texture == null) return;
        RenderSystem.setShaderColor(((rgb >>> 16) & 0xFF) / 255f,
                ((rgb >>> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        try {
            graphics.blit(texture, x, y, width, height,
                    0f, 0f, width, height, width, height);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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
        int background = troughColor(bar, alpha);
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

    /**
     * The frame background is a trough tint, not an opaque backing plate. A small amount of the
     * primary frame colour keeps very dark authored backgrounds identifiable while transparency
     * lets the world remain visible through the unfilled part of the meter.
     */
    private static int troughColor(ResourceSyncS2CPayload.Bar bar, float alpha) {
        if (Accessibility.highContrast()) {
            return withAlpha(0xFF000000, alpha * 0.86f);
        }
        int tinted = blend(bar.backgroundColor(),
                0xFF000000 | (bar.framePrimaryColor() & 0xFFFFFF), 0.18f);
        return withAlpha(tinted, alpha * TROUGH_OPACITY);
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
            } else if ("townstead:prismatic".equals(effect.type())) {
                result = prismaticEffectColor(result, pathAlong, now,
                        bar.resourceId(), effect);
            } else if ("townstead:void".equals(effect.type())) {
                result = voidEffectColor(result, bar.backgroundColor(), pathAlong,
                        pixelX, pixelY, now, bar.resourceId(), effect);
            }
        }
        return result;
    }

    /** A restrained travelling refraction band; this is intentionally hue-changing. */
    private static int prismaticEffectColor(int color, float pathAlong, long now,
                                             String resourceId,
                                             ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;
        int seed = resourceId == null ? 0 : resourceId.hashCode();
        float center = now / 4600f * effect.speed()
                + Math.floorMod(seed, 997) / 997f;
        center -= (float) Math.floor(center);
        float distance = Math.abs(pathAlong - center);
        distance = Math.min(distance, 1f - distance);
        float halfWidth = effect.prismaticWidth() * 0.5f;
        float envelope = 1f - distance / Math.max(0.01f, halfWidth);
        if (envelope <= 0f) return color;
        envelope = envelope * envelope * (3f - 2f * envelope);
        float hueTurn = (center * 0.72f + pathAlong * 0.28f) % 1f;
        int shifted = rotateHue(color, hueTurn);
        return blend(color, shifted, effect.strength() * accessibility * envelope);
    }

    /** Temporal holes cut through the fill in small, blocky glitch clusters. */
    private static int voidEffectColor(int color, int backgroundColor, float pathAlong,
                                       int pixelX, int pixelY, long now, String resourceId,
                                       ResourceSyncS2CPayload.Effect effect) {
        float accessibility = Accessibility.effectIntensity();
        if (accessibility <= 0f) return color;
        int seed = (resourceId == null ? 0 : resourceId.hashCode()) ^ 0x243F6A88;
        int cell = effect.voidInstability() > 0.72f ? 2 : 1;
        int tickRate = Math.max(45, Math.round(260f - effect.voidInstability() * 190f));
        int tick = (int) (now / tickRate);
        int cellX = Math.floorDiv(pixelX, cell);
        int cellY = Math.floorDiv(pixelY, cell);
        int hash = electricHash(seed ^ tick * 0x9E3779B9, cellX, cellY);
        float chance = (0.012f + effect.voidCount() * 0.008f
                + effect.voidInstability() * 0.055f)
                * effect.strength() * accessibility;
        float noise = (hash & 0xFFFF) / 65535f;
        boolean missing = noise < chance;

        // Brief horizontal tears make the absence read as a glitch instead of glitter.
        int tearHash = electricHash(seed, cellY, tick / 2);
        float tearCenter = ((tearHash >>> 8) & 0xFFFF) / 65535f;
        float tearWidth = 0.012f + effect.voidInstability() * 0.035f;
        float tearChance = (0.04f + effect.voidCount() / 80f)
                * effect.strength() * accessibility;
        boolean tear = ((tearHash >>> 24) & 0xFF) / 255f < tearChance
                && Math.abs(pathAlong - tearCenter) < tearWidth;
        if (!missing && !tear) return color;

        float alpha = ((color >>> 24) & 0xFF) / 255f;
        return withAlpha(0xFF000000 | (backgroundColor & 0xFFFFFF), alpha);
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

    private static ResourceSyncS2CPayload.Effect flameEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:flames".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect steamEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:steam".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect electricEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:electric".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect wispEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:wisps".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect sparkleEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:sparkle".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect crystallineEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:crystalline".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect runeEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:runes".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect corruptionEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:corruption".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect sporeEffect(ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:spores".equals(effect.type()) && effect.strength() > 0f) return effect;
        }
        return null;
    }

    private static ResourceSyncS2CPayload.Effect fallingMotesEffect(
            ResourceSyncS2CPayload.Bar bar) {
        for (ResourceSyncS2CPayload.Effect effect : bar.effects()) {
            if ("townstead:falling_motes".equals(effect.type()) && effect.strength() > 0f) {
                return effect;
            }
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

    private static int rotateHue(int color, float turn) {
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta <= 0.0001f) hue = 0f;
        else if (max == r) hue = ((g - b) / delta) / 6f;
        else if (max == g) hue = ((b - r) / delta + 2f) / 6f;
        else hue = ((r - g) / delta + 4f) / 6f;
        hue = hue - (float) Math.floor(hue) + turn;
        hue -= (float) Math.floor(hue);
        float saturation = max <= 0f ? 0f : delta / max;
        float scaled = hue * 6f;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = max * (1f - saturation);
        float q = max * (1f - saturation * fraction);
        float t = max * (1f - saturation * (1f - fraction));
        float nr;
        float ng;
        float nb;
        switch (Math.floorMod(sector, 6)) {
            case 0 -> { nr = max; ng = t; nb = p; }
            case 1 -> { nr = q; ng = max; nb = p; }
            case 2 -> { nr = p; ng = max; nb = t; }
            case 3 -> { nr = p; ng = q; nb = max; }
            case 4 -> { nr = t; ng = p; nb = max; }
            default -> { nr = max; ng = p; nb = q; }
        }
        return withAlpha(0xFF000000 | (Math.round(nr * 255f) << 16)
                | (Math.round(ng * 255f) << 8) | Math.round(nb * 255f), alpha);
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

    private static TownsteadConfig.ResourceHudExitStyle effectiveExitStyle() {
        TownsteadConfig.ResourceHudExitStyle style = ResourceHudConfig.exitStyle();
        if (Accessibility.isReduceMotion()
                && (style == TownsteadConfig.ResourceHudExitStyle.SLIDE
                || style == TownsteadConfig.ResourceHudExitStyle.FLICKER)) {
            return TownsteadConfig.ResourceHudExitStyle.FADE;
        }
        return style;
    }

    private static float previewTransitionAlpha(long now) {
        long phase = Math.floorMod(now, 6000L);
        if (phase < 3500L) return 1f;
        if (phase < 5000L) return 1f - (phase - 3500L) / 1500f;
        if (phase < 5500L) return 0f;
        return (phase - 5500L) / 500f;
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
                          float fraction, int fillColor, String shape,
                          ResourceClientStore.ReactionState reactions) {}
}
