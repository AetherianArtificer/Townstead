package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.CharRenderState;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffect;
import com.aetherianartificer.townstead.client.gui.dialogue.effect.DialogueEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Renders text lines character-by-character with a {@link DialogueEffect} applied.
 * Falls back to normal drawString when the effect is {@link DialogueEffects#NORMAL}.
 */
public final class EffectRenderer {
    private static final CharRenderState STATE = new CharRenderState();

    private EffectRenderer() {}

    /**
     * Render lines of text with the given effect applied per-character.
     *
     * @param graphics      the render context
     * @param font          the font
     * @param lines         text lines to render
     * @param baseX         left x position
     * @param baseY         top y position of the first line
     * @param lineHeight    vertical spacing between lines
     * @param baseColor     default ARGB color
     * @param effect        the effect to apply (null or NORMAL for plain rendering)
     * @param globalIndex   character index offset (for multi-call continuity)
     * @param totalChars    total characters across all lines
     * @return the number of characters rendered
     */
    public static int renderLines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines,
                                  int baseX, int baseY, int lineHeight, int baseColor,
                                  DialogueEffect effect, int globalIndex, int totalChars) {
        if (effect == null || effect == DialogueEffects.NORMAL) {
            // Fast path: no per-character work needed
            int y = baseY;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, baseX, y, baseColor);
                y += lineHeight;
            }
            return countChars(lines);
        }

        float time = (net.minecraft.Util.getMillis() % 100000L) * 0.01f;
        float baseR = ((baseColor >> 16) & 0xFF) / 255.0f;
        float baseG = ((baseColor >> 8) & 0xFF) / 255.0f;
        float baseB = (baseColor & 0xFF) / 255.0f;
        float baseA = ((baseColor >> 24) & 0xFF) / 255.0f;

        int charIndex = globalIndex;
        int lineY = baseY;

        for (FormattedCharSequence line : lines) {
            int[] lineX = {baseX};
            int finalCharIndex = charIndex;
            int finalLineY = lineY;

            line.accept((index, style, codePoint) -> {
                int ci = finalCharIndex + index;
                STATE.reset(baseR, baseG, baseB, baseA);
                effect.apply(STATE, ci, totalChars, time);

                String ch = new String(Character.toChars(codePoint));
                float drawX = lineX[0] + STATE.x;
                float drawY = finalLineY + STATE.y;

                if (Math.abs(STATE.scale - 1.0f) > 0.01f) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(drawX, drawY, 0);
                    pose.scale(STATE.scale, STATE.scale, 1.0f);
                    graphics.drawString(font, ch, 0, 0, STATE.toArgb());
                    pose.popPose();
                    lineX[0] += (int) (font.width(ch) * STATE.scale);
                } else {
                    graphics.drawString(font, ch, (int) drawX, (int) drawY, STATE.toArgb());
                    lineX[0] += font.width(ch);
                }
                return true;
            });

            charIndex += charLength(line);
            lineY += lineHeight;
        }

        return charIndex - globalIndex;
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
