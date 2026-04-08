package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.CharRenderState;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffect;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Renders text lines character-by-character with effects applied.
 * Supports both a global effect (set on the dialogue box) and per-character
 * inline effects (from {@code <happy>text</happy>} tags).
 */
public final class EffectRenderer {
    private static final CharRenderState STATE = new CharRenderState();

    /** Position of the last rendered character (for particle emitter). */
    private static int lastCharX, lastCharY;

    private EffectRenderer() {}

    public static int getLastCharX() { return lastCharX; }
    public static int getLastCharY() { return lastCharY; }

    /**
     * Render lines with per-character effects from both a global effect and inline tags.
     */
    public static void renderLines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines,
                                   int baseX, int baseY, int lineHeight, int baseColor,
                                   DialogueEffect globalEffect, TypewriterText typewriter) {
        boolean hasGlobal = globalEffect != null && globalEffect != DialogueEffects.NORMAL;
        boolean hasInline = typewriter != null && typewriter.hasEffectTags();

        if (!hasGlobal && !hasInline) {
            // Fast path
            int y = baseY;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, baseX, y, baseColor);
                y += lineHeight;
            }
            return;
        }

        int totalChars = typewriter != null ? typewriter.getTotalChars() : countChars(lines);
        float time = (net.minecraft.Util.getMillis() % 100000L) * 0.01f;
        float baseR = ((baseColor >> 16) & 0xFF) / 255.0f;
        float baseG = ((baseColor >> 8) & 0xFF) / 255.0f;
        float baseB = (baseColor & 0xFF) / 255.0f;
        float baseA = ((baseColor >> 24) & 0xFF) / 255.0f;

        int charOffset = 0;
        int lineY = baseY;

        for (FormattedCharSequence line : lines) {
            int[] lineX = {baseX};
            int lineCharOffset = charOffset;
            int finalLineY = lineY;

            line.accept((index, style, codePoint) -> {
                int ci = lineCharOffset + index;
                STATE.reset(baseR, baseG, baseB, baseA);

                // Apply inline tag effect first (if any)
                if (hasInline) {
                    DialogueEffects inlineEffect = typewriter.getEffectAt(ci);
                    if (inlineEffect != null && inlineEffect != DialogueEffects.NORMAL) {
                        inlineEffect.apply(STATE, ci, totalChars, time);
                    } else if (hasGlobal) {
                        globalEffect.apply(STATE, ci, totalChars, time);
                    }
                } else if (hasGlobal) {
                    globalEffect.apply(STATE, ci, totalChars, time);
                }

                String ch = new String(Character.toChars(codePoint));
                float drawX = lineX[0] + STATE.x;
                float drawY = finalLineY + STATE.y;

                int charW;
                if (Math.abs(STATE.scale - 1.0f) > 0.01f) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(drawX, drawY, 0);
                    pose.scale(STATE.scale, STATE.scale, 1.0f);
                    graphics.drawString(font, ch, 0, 0, STATE.toArgb());
                    pose.popPose();
                    charW = (int) (font.width(ch) * STATE.scale);
                } else {
                    graphics.drawString(font, ch, (int) drawX, (int) drawY, STATE.toArgb());
                    charW = font.width(ch);
                }
                lastCharX = lineX[0] + charW;
                lastCharY = finalLineY;
                lineX[0] += charW;
                return true;
            });

            charOffset += charLength(line);
            lineY += lineHeight;
        }
    }

    private static int countChars(List<FormattedCharSequence> lines) {
        int count = 0;
        for (FormattedCharSequence line : lines) {
            count += charLength(line);
        }
        return count;
    }

    private static int charLength(FormattedCharSequence seq) {
        int[] count = {0};
        seq.accept((index, style, codePoint) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }
}
