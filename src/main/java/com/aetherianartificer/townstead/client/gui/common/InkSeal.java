package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.gui.calendar.StampCatalog;
import com.aetherianartificer.townstead.seal.PersonalSeal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A personal seal pressed in ink: a round impression with a double ring and the owner's device in
 * the middle. The ink is uneven, a few pixels thin where the die did not quite meet the paper,
 * and the pattern of that is the seal's own, so one person's mark looks the same every time.
 */
public final class InkSeal {
    public static final int SIZE = 24;
    /** The device's box, centred inside the inner ring. */
    public static final int DEVICE = 11;

    public record Device(String id, Component name) {}

    private static final Map<String, String[]> BITMAPS = Map.of(
            "townstead:star", new String[]{
                    ".....#.....", "....###....", "....###....", "###########", ".#########.",
                    "..#######..", "...#####...", "..###.###..", "..##...##..", ".##.....##.", ".#.......#."},
            "townstead:key", new String[]{
                    "...#####...", "..##...##..", "..#.....#..", "..##...##..", "...#####...",
                    ".....#.....", ".....#.....", ".....####..", ".....#.....", ".....###...", ".....#....."},
            "townstead:tower", new String[]{
                    ".#.#.#.#.#.", ".#########.", "..#######..", "..###.###..", "..##...##..",
                    "..#######..", "..#######..", "..###.###..", "..##...##..", "..##...##..", ".#########."},
            "townstead:crown", new String[]{
                    "...........", "...........", "#....#....#", "##..###..##", "###.###.###",
                    "###########", "#.#.#.#.#.#", "###########", "###########", "...........", "..........."},
            "townstead:tree", new String[]{
                    "....###....", "..#######..", ".#########.", ".#########.", "..#######..",
                    "....###....", ".....#.....", ".....#.....", ".....#.....", "...#####...", "..........."},
            "townstead:moon", new String[]{
                    ".......####", ".....###...", "....##.....", "....##.....", "...##......",
                    "...##......", "...##......", "....##.....", "....##.....", ".....###...", ".......####"},
            "townstead:anchor", new String[]{
                    ".....#.....", "....#.#....", ".....#.....", "..#######..", ".....#.....",
                    ".....#.....", ".....#.....", "#....#....#", "##...#...##", ".##..#..##.", "..#######.."});

    private InkSeal() {}

    /** Every device a seal can carry: the built-ins, then any seal art resource packs provide. */
    public static List<Device> devices() {
        List<Device> out = new ArrayList<>();
        for (String id : PersonalSeal.BUILT_IN) out.add(new Device(id, name(id)));
        for (StampCatalog.Entry art : StampCatalog.list()) {
            if (PersonalSeal.art(art.textureId())) out.add(new Device(art.textureId(), Component.literal(art.displayName())));
        }
        return out;
    }

    public static Component name(String device) {
        if (PersonalSeal.BUILT_IN.contains(device)) {
            return Component.translatable("townstead.seal.device." + device.substring(device.indexOf(':') + 1));
        }
        for (StampCatalog.Entry art : StampCatalog.list()) {
            if (art.textureId().equals(device)) return Component.literal(art.displayName());
        }
        return Component.literal(device);
    }

    public static Component colourName(int dye) {
        return Component.translatable("color.minecraft." + DyeColor.byId(dye).getName());
    }

    /** The dye as ink: its own colour, a little darker, as ink dries on paper. */
    public static int ink(int dye) {
        int rgb = DyeColor.byId(dye).getFireworkColor();
        int r = (rgb >> 16 & 255) * 82 / 100, g = (rgb >> 8 & 255) * 82 / 100, b = (rgb & 255) * 82 / 100;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The whole impression, {@link #SIZE} square, its top left at x, y. */
    public static void draw(GuiGraphics g, Font font, PersonalSeal seal, String initial, int x, int y) {
        int color = ink(seal.dye());
        int faint = (color & 0x00FFFFFF) | 0x80000000;
        int seed = seal.device().hashCode() * 31 + seal.dye();
        double c = (SIZE - 1) / 2.0;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double d = Math.hypot(px - c, py - c);
                boolean outer = d > 9.9 && d <= 11.6;
                boolean inner = d > 7.0 && d <= 8.2;
                if (!outer && !inner) continue;
                g.fill(x + px, y + py, x + px + 1, y + py + 1, thin(seed, px, py) ? faint : color);
            }
        }
        int box = (SIZE - DEVICE) / 2;
        drawDevice(g, font, seal.device(), initial, x + box, y + box, color, seed);
    }

    /** The device alone, in a {@link #DEVICE} square box: for the seal and for pickers. */
    public static void drawDevice(GuiGraphics g, Font font, String device, String initial, int x, int y, int color) {
        drawDevice(g, font, device, initial, x, y, color, 0);
    }

    private static void drawDevice(GuiGraphics g, Font font, String device, String initial, int x, int y, int color, int seed) {
        String[] bitmap = BITMAPS.get(device);
        if (bitmap != null) {
            int faint = (color & 0x00FFFFFF) | 0x80000000;
            for (int row = 0; row < bitmap.length; row++) {
                for (int col = 0; col < bitmap[row].length(); col++) {
                    if (bitmap[row].charAt(col) != '#') continue;
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, seed != 0 && thin(seed, col + 40, row + 40) ? faint : color);
                }
            }
            return;
        }
        if (PersonalSeal.art(device) && StampCatalog.hasTexture(device)) {
            //? if >=1.21 {
            ResourceLocation texture = ResourceLocation.parse(device);
            //?} else {
            /*ResourceLocation texture = new ResourceLocation(device);
            *///?}
            int size = DEVICE + 2;
            g.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f);
            g.blit(texture, x - 1, y - 1, size, size, 0f, 0f, size, size, size, size);
            g.setColor(1f, 1f, 1f, 1f);
            return;
        }
        String letter = initial == null || initial.isEmpty() ? "?" : initial.substring(0, initial.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        g.drawString(font, letter, x + (DEVICE - font.width(letter)) / 2 + 1, y + 2, color, false);
    }

    /** About one pixel in nine prints thin, fixed per seal. */
    private static boolean thin(int seed, int px, int py) {
        int h = seed ^ (px * 73856093) ^ (py * 19349663);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return Math.floorMod(h, 9) == 0;
    }
}
