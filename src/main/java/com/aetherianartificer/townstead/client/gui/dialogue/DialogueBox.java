package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffect;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.ScreenParticles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Bordered RPG-style panel at the bottom of the screen.
 * Features a decorative frame, name plate tab, typewriter-animated text
 * with effects, screen-space particles, and smooth fade-in/out.
 */
public class DialogueBox {
    // Frame colors — Minecraft stone/button style bevel
    private static final int BG_FILL = 0xCC0E0E0E;            // near-black
    private static final int BORDER_OUTER_LIGHT = 0xFFA0A0A0; // top/left highlight (like MC button)
    private static final int BORDER_OUTER_DARK = 0xFF373737;   // bottom/right shadow
    private static final int BORDER_INNER_LIGHT = 0xFF606060;
    private static final int BORDER_INNER_DARK = 0xFF252525;
    private static final int BORDER_ACCENT = 0xFF707070;       // inner accent line
    private static final int CORNER_ACCENT = 0xFFB0B0B0;       // corner decoration

    // Name tab
    private static final int NAME_TAB_BG = 0xDD0E0E0E;
    private static final int NAME_COLOR = 0xFFFFD700;

    // Text
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INDICATOR_COLOR = 0xFFFFFFFF;

    // Layout
    private static final int MARGIN = 20;
    private static final int FRAME_THICKNESS = 3;
    private static final int PADDING = 10;
    private static final int NAME_TAB_HPAD = 10;
    private static final int NAME_TAB_VPAD = 4;
    private static final int NAME_TAB_HEIGHT = 16;
    private static final int TEXT_TOP_GAP = 8;
    private static final int LINE_HEIGHT = 12;

    // Animation
    private static final int FADE_TICKS = 6;

    private final TypewriterText typewriter = new TypewriterText();
    private final ScreenParticles particles = new ScreenParticles();
    private Component villagerName = Component.empty();
    private DialogueEffect activeEffect = DialogueEffects.NORMAL;

    private int x, y, width, height;
    private float fadeAlpha = 0f;
    private boolean fadeIn;
    private boolean fadeOut;
    private int fadeTick;

    public void layout(int screenWidth, int screenHeight) {
        this.x = MARGIN;
        this.width = screenWidth - MARGIN * 2;
        this.height = (int) (screenHeight * 0.25);
        this.y = screenHeight - this.height - 10;
    }

    public void setVillagerName(Component name) {
        this.villagerName = name;
    }

    public void setText(Component text, Font font) {
        int textWidth = width - (FRAME_THICKNESS + PADDING) * 2;
        typewriter.setText(text, font, textWidth);
        // Calculate how many lines fit: from text start to indicator area
        int textStartY = FRAME_THICKNESS + PADDING + TEXT_TOP_GAP;
        int textEndY = height - FRAME_THICKNESS - 14; // 14 for indicator row
        int textAreaHeight = textEndY - textStartY;
        typewriter.setMaxVisibleLines(Math.max(2, textAreaHeight / LINE_HEIGHT));
        particles.clear();

        // Start fade-in
        fadeIn = true;
        fadeOut = false;
        fadeTick = 0;

        // Determine particle effect from last character's tag
        int total = typewriter.getTotalChars();
        if (total > 0) {
            DialogueEffects lastCharEffect = typewriter.getEffectAt(total - 1);
            if (lastCharEffect != null && lastCharEffect != DialogueEffects.NORMAL) {
                particles.setEffect(lastCharEffect);
            } else if (activeEffect instanceof DialogueEffects globalEffect
                    && globalEffect != DialogueEffects.NORMAL) {
                particles.setEffect(globalEffect);
            }
        }
    }

    public void beginFadeOut() {
        fadeOut = true;
        fadeIn = false;
        fadeTick = 0;
    }

    public boolean isFadeOutComplete() {
        return fadeOut && fadeTick >= FADE_TICKS;
    }

    public void tick() {
        typewriter.tick();
        particles.tick();

        fadeTick++;
        if (fadeIn) {
            fadeAlpha = Math.min(1f, (float) fadeTick / FADE_TICKS);
            if (fadeTick >= FADE_TICKS) fadeIn = false;
        } else if (fadeOut) {
            fadeAlpha = Math.max(0f, 1f - (float) fadeTick / FADE_TICKS);
        } else if (fadeAlpha < 1f) {
            // Initial appearance
            fadeAlpha = Math.min(1f, fadeAlpha + 1f / FADE_TICKS);
        }
    }

    public void render(GuiGraphics g, Font font) {
        if (fadeAlpha <= 0.01f) return;
        float a = fadeAlpha;

        renderFrame(g, a);
        renderNameTab(g, font, a);
        renderText(g, font, a);
        renderParticles(g);
        renderIndicator(g, font, a);
    }

    private void renderFrame(GuiGraphics g, float a) {
        // Background fill — scale by MC's Text Background Opacity (default 0.5)
        float bgOpacity = DialogueAccessibility.backgroundAlpha();
        // Map MC's 0.0-1.0 range: at 0 = transparent, at 0.5 (default) = normal, at 1.0 = fully opaque
        int bgAlphaInt = (int)(bgOpacity * 2f * 0xCC); // 0xCC is the base alpha in BG_FILL
        bgAlphaInt = Math.min(bgAlphaInt, 0xFF);
        int bgColor = (bgAlphaInt << 24) | (BG_FILL & 0x00FFFFFF);
        g.fill(x, y, x + width, y + height, aa(bgColor, a));

        // Outer border — bevel effect (light top/left, dark bottom/right)
        // Top
        g.fill(x, y, x + width, y + 1, aa(BORDER_OUTER_LIGHT, a));
        g.fill(x, y + 1, x + width, y + 2, aa(BORDER_INNER_LIGHT, a));
        // Left
        g.fill(x, y, x + 1, y + height, aa(BORDER_OUTER_LIGHT, a));
        g.fill(x + 1, y, x + 2, y + height, aa(BORDER_INNER_LIGHT, a));
        // Bottom
        g.fill(x, y + height - 1, x + width, y + height, aa(BORDER_OUTER_DARK, a));
        g.fill(x, y + height - 2, x + width, y + height - 1, aa(BORDER_INNER_DARK, a));
        // Right
        g.fill(x + width - 1, y, x + width, y + height, aa(BORDER_OUTER_DARK, a));
        g.fill(x + width - 2, y, x + width - 1, y + height, aa(BORDER_INNER_DARK, a));

        // Inner accent line (inset by FRAME_THICKNESS)
        int ix = x + FRAME_THICKNESS;
        int iy = y + FRAME_THICKNESS;
        int iw = width - FRAME_THICKNESS * 2;
        int ih = height - FRAME_THICKNESS * 2;
        g.fill(ix, iy, ix + iw, iy + 1, aa(BORDER_ACCENT, a));
        g.fill(ix, iy, ix + 1, iy + ih, aa(BORDER_ACCENT, a));
        g.fill(ix, iy + ih - 1, ix + iw, iy + ih, aa(BORDER_INNER_DARK, a));
        g.fill(ix + iw - 1, iy, ix + iw, iy + ih, aa(BORDER_INNER_DARK, a));

        // Corner accents — small L-shaped decorations
        int cs = 6; // corner size
        // Top-left
        g.fill(x + 1, y + 1, x + 1 + cs, y + 2, aa(CORNER_ACCENT, a));
        g.fill(x + 1, y + 1, x + 2, y + 1 + cs, aa(CORNER_ACCENT, a));
        // Top-right
        g.fill(x + width - 1 - cs, y + 1, x + width - 1, y + 2, aa(CORNER_ACCENT, a));
        g.fill(x + width - 2, y + 1, x + width - 1, y + 1 + cs, aa(CORNER_ACCENT, a));
        // Bottom-left
        g.fill(x + 1, y + height - 2, x + 1 + cs, y + height - 1, aa(CORNER_ACCENT, a));
        g.fill(x + 1, y + height - 1 - cs, x + 2, y + height - 1, aa(CORNER_ACCENT, a));
        // Bottom-right
        g.fill(x + width - 1 - cs, y + height - 2, x + width - 1, y + height - 1, aa(CORNER_ACCENT, a));
        g.fill(x + width - 2, y + height - 1 - cs, x + width - 1, y + height - 1, aa(CORNER_ACCENT, a));
    }

    private void renderNameTab(GuiGraphics g, Font font, float a) {
        int nameW = font.width(villagerName) + NAME_TAB_HPAD * 2;
        int tabX = x + FRAME_THICKNESS + PADDING;
        int tabY = y - NAME_TAB_HEIGHT + 2; // overlaps the top border slightly

        // Tab background
        g.fill(tabX, tabY, tabX + nameW, y + 1, aa(NAME_TAB_BG, a));

        // Tab border (top and sides only, bottom merges with box)
        g.fill(tabX, tabY, tabX + nameW, tabY + 1, aa(BORDER_OUTER_LIGHT, a));
        g.fill(tabX, tabY, tabX + 1, y, aa(BORDER_OUTER_LIGHT, a));
        g.fill(tabX + nameW - 1, tabY, tabX + nameW, y, aa(BORDER_OUTER_DARK, a));
        // Accent on tab
        g.fill(tabX + 1, tabY + 1, tabX + nameW - 1, tabY + 2, aa(BORDER_ACCENT, a));

        // Name text
        g.drawString(font, villagerName, tabX + NAME_TAB_HPAD, tabY + NAME_TAB_VPAD, aa(NAME_COLOR, a));
    }

    private void renderText(GuiGraphics g, Font font, float a) {
        int textX = x + FRAME_THICKNESS + PADDING;
        int textY = y + FRAME_THICKNESS + PADDING + TEXT_TOP_GAP;
        int clipRight = x + width - FRAME_THICKNESS;
        int clipBottom = y + height - FRAME_THICKNESS - PADDING;

        // Scissor clips only horizontal overflow (scaled text like yell)
        // Top/bottom use full screen range so animations aren't cut off
        g.enableScissor(textX, 0, clipRight, y + height + 100);
        List<FormattedCharSequence> lines = typewriter.getRevealedLines();
        EffectRenderer.renderLines(g, font, lines,
                textX, textY, LINE_HEIGHT, aa(TEXT_COLOR, a),
                activeEffect, typewriter);
        g.disableScissor();
    }

    private void renderParticles(GuiGraphics g) {
        // Trail mode: follow the typewriter cursor
        particles.setEmitPosition(EffectRenderer.getLastCharX(), EffectRenderer.getLastCharY());

        // Switch to spread mode when typewriter finishes and we have a tagged region
        if (typewriter.isComplete() && !particles.isSpreadMode()) {
            int[] region = EffectRenderer.getTagRegion();
            if (region != null) {
                particles.setSpreadRegion(region[0], region[1], region[2], region[3]);
            }
        }

        particles.render(g);
    }

    private void renderIndicator(GuiGraphics g, Font font, float a) {
        if (typewriter.shouldShowPageIndicator()) {
            // Page advance indicator — blinking down arrow
            if ((typewriter.isPaused() || typewriter.hasMorePages()) && (fadeTick / 8) % 2 == 0) {
                String indicator = "\u25BC"; // down arrow
                int ix = x + width / 2 - font.width(indicator) / 2;
                int iy = y + height - FRAME_THICKNESS - PADDING - 2;
                g.drawString(font, indicator, ix, iy, aa(INDICATOR_COLOR, a));
            }
        } else if (typewriter.shouldShowIndicator()) {
            // Final done indicator — right arrow
            String indicator = "\u25B6";
            int ix = x + width - FRAME_THICKNESS - PADDING - font.width(indicator);
            int iy = y + height - FRAME_THICKNESS - PADDING - 2;
            g.drawString(font, indicator, ix, iy, aa(INDICATOR_COLOR, a));
        }
    }

    /** Apply alpha multiplier to a packed ARGB color. */
    private static int aa(int argb, float alpha) {
        int origA = (argb >> 24) & 0xFF;
        return ((int) (origA * alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public void setEffect(DialogueEffect effect) {
        this.activeEffect = effect != null ? effect : DialogueEffects.NORMAL;
    }

    public DialogueEffect getEffect() {
        return activeEffect;
    }

    public TypewriterText getTypewriter() {
        return typewriter;
    }

    public float getFadeAlpha() {
        return fadeAlpha;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
