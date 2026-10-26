package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.gui.calendar.StampCatalog;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * A hand stamp: the die at rest or in hand, the press, and the wax cartouche it leaves. The Career
 * record registers with it and the Charter signs with it, so both marks come from the same tool.
 */
public final class Signet {
    public static final int DIE_W = 28;
    public static final int DIE_H = 25;
    /** The die at rest is drawn smaller than in hand: a held object reads larger than a shelved one. */
    public static final int REST_W = 20;
    public static final int REST_H = 18;
    /** Past about seven degrees a 62x24 mark clips its own corner in a 72x32 field. */
    public static final float TILT_LIMIT = 0.12f;
    /** The cartouche's natural height: two text rows, the rule and the double border. */
    public static final int MARK_H = 29;
    public static final int INK = 0xFFA8322A;
    public static final int INK_LIGHT = 0xFFC8564A;
    private static final float REST_ROTATION = -0.09f;
    private static final long PRESS_MS = 900L;

    //? if >=1.21 {
    private static final ResourceLocation TOOL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "townstead", "textures/gui/career/stamp_tool.png");
    //?} else {
    /*private static final ResourceLocation TOOL_TEXTURE = new ResourceLocation(
            "townstead", "textures/gui/career/stamp_tool.png");
    *///?}

    private final Font font;
    private boolean held;
    private double toolX;
    private double toolY;
    private float rotation = REST_ROTATION;
    private long pressedAt = -1L;
    private int pressX;
    private int pressY;
    private float pressRot;

    public Signet(Font font) { this.font = font; }

    public boolean held() { return held; }
    /** True while the press animation plays; the die is neither at rest nor in hand. */
    public boolean pressing() { return pressedAt >= 0; }
    public float rotation() { return rotation; }
    public int centreX() { return (int) Math.round(toolX + DIE_W / 2.0); }
    public int centreY() { return (int) Math.round(toolY + DIE_H / 2.0); }

    public void pickUp(double mx, double my) {
        held = true;
        moveTo(mx, my);
    }

    public void moveTo(double mx, double my) {
        toolX = mx - DIE_W / 2.0;
        toolY = my - DIE_H / 2.0;
    }

    public void putDown() {
        held = false;
        rotation = REST_ROTATION;
    }

    public void rotate(double delta) {
        if (held) rotation = Mth.clamp(rotation + (delta > 0 ? -0.03f : 0.03f), -TILT_LIMIT, TILT_LIMIT);
    }

    /** Starts the press where the die is now. The caller decides whether the die stays in hand. */
    public void press() {
        pressedAt = Util.getMillis();
        pressX = centreX();
        pressY = centreY();
        pressRot = rotation;
    }

    /** The die standing at rest, its foot on {@code footY}. */
    public void drawResting(GuiGraphics g, int x, int footY, float alpha) {
        if (held || pressing()) return;
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(TOOL_TEXTURE, x, footY - REST_H, REST_W, REST_H, 0f, 0f, DIE_W, DIE_H, DIE_W, DIE_H);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Whether the pointer is over the resting die at this spot, with a little slack. */
    public static boolean overResting(double mx, double my, int x, int footY) {
        return mx >= x - 3 && mx < x + REST_W + 3 && my >= footY - REST_H && my < footY + 3;
    }

    /** The die in hand with its shadow; the shadow reddens when it would not land anywhere. */
    public void drawHeld(GuiGraphics g, boolean validDrop) {
        if (!held) return;
        int x = (int) Math.round(toolX);
        int y = (int) Math.round(toolY);
        g.pose().pushPose();
        g.pose().translate(x + DIE_W / 2f, y + DIE_H - 1f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        g.fill(-DIE_W / 2 + 1, -4, DIE_W / 2 - 1, 4, validDrop ? 0x4D000000 : 0x4D8A2018);
        g.pose().popPose();
        drawTool(g, x, y, rotation, 1f);
    }

    public void drawPressAnimation(GuiGraphics g) {
        if (pressedAt < 0) return;
        long age = Util.getMillis() - pressedAt;
        if (age > PRESS_MS) { pressedAt = -1L; return; }
        float t = age / (float) PRESS_MS;
        if (age < 400) {
            int lift = Math.round(10f * (age < 150 ? 1f - age / 150f : (age - 150) / 250f));
            boolean squash = age >= 130 && age < 200;
            drawTool(g, pressX - DIE_W / 2 + (squash ? 1 : 0),
                    pressY - DIE_H / 2 - lift + (squash ? 1 : 0), pressRot, 1f - t * 0.3f);
        }
        for (int i = 0; i < 14; i++) {
            double angle = (i / 14.0) * Math.PI * 2;
            double dist = 10 + t * 44;
            int px = pressX + (int) Math.round(Math.cos(angle) * dist * 1.4);
            int py = pressY + (int) Math.round(Math.sin(angle) * dist * 0.55);
            int alpha = (int) (Math.max(0f, 1f - t) * 130f) << 24;
            g.fill(px, py, px + 1, py + 1, alpha | 0x00CDB98E);
        }
    }

    private void drawTool(GuiGraphics g, int x, int y, float rot, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x + DIE_W / 2f, y + DIE_H / 2f, 550);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rot)));
        g.setColor(1f, 1f, 1f, Mth.clamp(alpha, 0f, 1f));
        g.blit(TOOL_TEXTURE, -DIE_W / 2, -DIE_H / 2, DIE_W, DIE_H, 0f, 0f, DIE_W, DIE_H, DIE_W, DIE_H);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    /**
     * One mark, art or cartouche, clamped inside the target field. The cartouche carries the
     * authority on top and the seal with its date beneath the rule.
     */
    public void drawImpression(GuiGraphics g, String textureId, String authority, String seal, String date,
                               int centreX, int centreY, float rotation,
                               int targetX, int targetY, int targetW, int targetH) {
        if (!textureId.isEmpty() && StampCatalog.hasTexture(textureId)) {
            drawArtImpression(g, textureId, date, centreX, centreY, rotation, targetX, targetY, targetW, targetH);
            return;
        }
        String top1 = authority.toUpperCase(Locale.ROOT);
        String sub = date.isEmpty() ? seal : seal.isEmpty() ? date : seal + "  " + date;
        int room = targetW - 12;

        // The day is part of the mark, not expendable copy. If a long seal cannot share the line
        // even at the legibility floor, the date stays and the seal goes.
        if (!date.isEmpty() && font.width(sub) * 0.5f > room) {
            sub = date;
        }
        float topFace = faceScale(font.width(top1), room);
        float subFace = compactFaceScale(font.width(sub), room);
        if (scaled(font.width(top1), topFace) > room) {
            top1 = truncate(top1, Math.round(room / topFace));
        }
        if (scaled(font.width(sub), subFace) > room) {
            sub = date.isEmpty() ? truncate(sub, Math.round(room / subFace)) : date;
            subFace = compactFaceScale(font.width(sub), room);
        }
        int w = Math.min(targetW, Math.max(scaled(font.width(top1), topFace), scaled(font.width(sub), subFace)) + 12);
        int h = Math.min(targetH, 2 * font.lineHeight + 11);
        g.pose().pushPose();
        g.pose().translate(Mth.clamp(centreX, targetX + w / 2, targetX + targetW - w / 2),
                Mth.clamp(centreY, targetY + h / 2, targetY + targetH - h / 2), 220);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        int left = -w / 2;
        int top = -h / 2;
        g.fill(left + 2, top, left + w - 2, top + 2, INK);
        g.fill(left + 2, top + h - 2, left + w - 2, top + h, INK);
        g.fill(left, top + 2, left + 2, top + h - 2, INK);
        g.fill(left + w - 2, top + 2, left + w, top + h - 2, INK);
        g.fill(left + 4, top + 3, left + w - 4, top + 4, INK_LIGHT);
        g.fill(left + 4, top + h - 4, left + w - 4, top + h - 3, INK_LIGHT);
        drawFace(g, top1, left, w, top + 3, topFace);
        int ruleY = top + 4 + font.lineHeight;
        g.fill(left + 6, ruleY, left + w - 6, ruleY + 1, INK_LIGHT);
        drawFace(g, sub, left, w, ruleY + 2, subFace);
        g.pose().popPose();
    }

    /** Resource-pack seal art keeps its authored face, with the date as part of the mark. */
    private void drawArtImpression(GuiGraphics g, String textureId, String date,
                                   int centreX, int centreY, float rotation,
                                   int targetX, int targetY, int targetW, int targetH) {
        int dateRoom = targetW - 4;
        float dateFace = date.isEmpty() ? 1f : compactFaceScale(font.width(date), dateRoom);
        int dateH = date.isEmpty() ? 0 : font.lineHeight;
        int size = Math.min(24, Math.min(targetW, targetH - dateH - (date.isEmpty() ? 0 : 1)));
        int w = Math.min(targetW, Math.max(size, date.isEmpty() ? 0 : scaled(font.width(date), dateFace)) + 4);
        int h = size + (date.isEmpty() ? 0 : dateH + 1);
        //? if >=1.21 {
        ResourceLocation texture = ResourceLocation.parse(textureId);
        //?} else {
        /*ResourceLocation texture = new ResourceLocation(textureId);
        *///?}
        g.pose().pushPose();
        g.pose().translate(Mth.clamp(centreX, targetX + w / 2, targetX + targetW - w / 2),
                Mth.clamp(centreY, targetY + h / 2, targetY + targetH - h / 2), 220);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.toDegrees(rotation)));
        g.setColor(0.78f, 0.18f, 0.14f, 0.92f);
        int artY = -h / 2;
        g.blit(texture, -size / 2, artY, size, size, 0f, 0f, size, size, size, size);
        g.setColor(1f, 1f, 1f, 1f);
        if (!date.isEmpty()) {
            int ruleY = artY + size;
            g.fill(-w / 2 + 2, ruleY, w / 2 - 2, ruleY + 1, INK_LIGHT);
            drawFace(g, date, -w / 2, w, ruleY + 1, dateFace);
        }
        g.pose().popPose();
    }

    /** 8px, then 7, then 6; below 6 Minecraft's font stops being readable, so only then is text cut. */
    private float faceScale(int widest, int room) {
        if (widest <= room) return 1f;
        for (float step : new float[] {0.875f, 0.75f}) {
            if (scaled(widest, step) <= room) return step;
        }
        return 0.75f;
    }

    /** The dated second line may use the compact 5px face rather than losing the date. */
    private float compactFaceScale(int widest, int room) {
        if (widest <= 0 || widest <= room) return 1f;
        return Mth.clamp(room / (float) widest, 0.5f, 1f);
    }

    private int scaled(int width, float face) {
        return Math.round(width * face);
    }

    private void drawFace(GuiGraphics g, String text, int left, int w, int y, float face) {
        if (face >= 0.999f) {
            g.drawString(font, text, left + (w - font.width(text)) / 2, y, INK, false);
            return;
        }
        int drawn = scaled(font.width(text), face);
        g.pose().pushPose();
        g.pose().translate(left + (w - drawn) / 2f, y, 0);
        g.pose().scale(face, face, 1f);
        g.drawString(font, text, 0, 0, INK, false);
        g.pose().popPose();
    }

    private String truncate(String text, int room) {
        if (font.width(text) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut + "…") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
