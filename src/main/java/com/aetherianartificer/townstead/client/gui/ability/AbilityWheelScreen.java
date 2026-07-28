package com.aetherianartificer.townstead.client.gui.ability;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.root.ClientAbilityLoadout;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutS2CPayload;
import com.aetherianartificer.townstead.root.ability.AbilityViewRequestC2SPayload;
import com.aetherianartificer.townstead.root.ability.ActivateAbilityC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The ability wheel: a struck dial carrying the frames you prepared.
 *
 * <p>Held open, fired on RELEASE. A wheel you open, aim and click is three actions for something
 * competing with one keypress; hold-aim-release is one gesture and is what every radial a player has
 * already used does.</p>
 *
 * <p>ROUND AND SQUARE MEAN DIFFERENT THINGS HERE, and it is the same rule the career board settled
 * on. The dial is round because it is an instrument you aim, and the hub is round because it is a
 * seal. Everything holding a 16-pixel item icon is a SQUARE FRAME, because a circle has to out-size
 * the sprite it carries; the first wheel let icons float unheld in tinted wedges, which made it the
 * one surface in the mod that broke its own vocabulary.</p>
 *
 * <p>The outer band reads AVAILABILITY rather than cooldown. Full means ready, or ON for a toggle;
 * draining means cooling; dark means off. A toggle has no cooldown, so drawing it one was drawing a
 * lie, and one honest ring covers both kinds.</p>
 */
public final class AbilityWheelScreen extends Screen {

    /** Positions on the dial. Twenty-four slots exist; eight are shown at a time. */
    private static final int SLOTS = 8;
    /** Plain, shift, control. Mirrors {@code ActiveAbilities.POOL_SIZE / LAYER_SIZE}. */
    private static final int LAYERS = 3;

    private static final int R_OUT = 84;
    private static final int R_RIM = 83;
    private static final int R_ARC_OUT = 82;
    private static final int R_ARC_IN = 77;
    private static final int R_FACE = 26;
    /** How far from the centre a frame sits, and how big it is. */
    private static final int R_SLOT = 56;
    private static final int FRAME = 26;
    /** Inside this radius nothing is selected, so releasing cancels. */
    private static final int DEAD_ZONE = R_FACE;

    private int hovered = -1;
    /**
     * Which eight the dial is showing: 0 normally, 1 on shift, 2 on control.
     *
     * <p>Derived every frame rather than stored, because it is not a mode. Nothing about holding a
     * key should outlive letting go of it.</p>
     */
    private int layer;

    public AbilityWheelScreen() {
        super(Component.translatable("townstead.ability.wheel.title"));
    }

    @Override
    protected void init() {
        // Ask for a fresh view every time. The login push can land before the player's Root has
        // resolved, and learning or respeccing moves the answer afterwards.
        AbilityViewRequestC2SPayload request = new AbilityViewRequestC2SPayload();
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(request);
        *///?}
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The dial is the background; no vanilla blur (1.21 re-blur family fix). */
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawVignette(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        drawVignette(g);
    }
    *///?}

    /**
     * A pool of shade under the dial, fading out before it reaches the edges.
     *
     * <p>A flat screen-wide dim is what an inventory does, because an inventory wants your whole
     * attention. This is a half-second gesture taken mid-fight, and dimming the world you are
     * standing in is heavier than the moment deserves.</p>
     */
    private void drawVignette(GuiGraphics g) {
        int cx = width / 2;
        int cy = wheelCentreY();
        int reach = R_OUT + 74;
        for (int i = 0; i < 13; i++) {
            WheelArt.disc(g, cx, cy, reach - (reach - R_FACE) * i / 13, 0x11000000);
        }
    }

    /** Lifted, so the label plate below the dial still has room on a short window. */
    private int wheelCentreY() {
        return height / 2 - 12;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}
        int cx = width / 2;
        int cy = wheelCentreY();
        hovered = wedgeAt(mouseX - cx, mouseY - cy);
        // The player's OWN bindings, not hardcoded shift and control: those could not be rebound and
        // were invisible to controller remapping. Third wins over second, so a stray press while
        // reaching for the other does not silently land you on the wrong eight.
        layer = com.aetherianartificer.townstead.client.TownsteadKeybinds.isHeld(minecraft,
                com.aetherianartificer.townstead.client.TownsteadKeybinds.LAYER_THIRD) ? 2
                : com.aetherianartificer.townstead.client.TownsteadKeybinds.isHeld(minecraft,
                        com.aetherianartificer.townstead.client.TownsteadKeybinds.LAYER_SECOND) ? 1 : 0;
        long now = minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime();

        drawDial(g, cx, cy, now);
        for (int i = 0; i < SLOTS; i++) drawSlot(g, cx, cy, i, now);
        drawHub(g, cx, cy);
        drawPlate(g, cx, cy);
    }

    /** The dial face, its brass rim, the engraved sector dividers, and the availability band. */
    private void drawDial(GuiGraphics g, int cx, int cy, long now) {
        float[] fill = new float[SLOTS];
        boolean[] steady = new boolean[SLOTS];
        boolean[] present = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(slotFor(i));
            present[i] = entry != null;
            if (entry == null) continue;
            steady[i] = entry.toggle();
            fill[i] = entry.toggle() ? (entry.toggledOn() ? 1f : 0f) : readyFraction(entry, now);
        }

        // The dark outline first, so the rim's gradient can run right to the dial's edge.
        WheelArt.disc(g, cx, cy, R_OUT + 1, Palette.DESK_EDGE);
        // The face runs the whole way out and the rim paints OVER its outer band. Reserving the band
        // here instead left a ring of pixels claimed by neither: this pass cuts on a rounded
        // hypotenuse and the rim on a scanned half-width, and the two disagree by a pixel per row.
        WheelArt.paintRing(g, cx, cy, R_OUT, R_FACE, SLOTS, (sector, within, radius) -> {
            // An EMPTY sector gets no track. Drawing one dark put a near-black band between the
            // wedge and the rim, which on a dark face reads as a hole in the dial rather than as an
            // empty channel. Nothing there, nothing drawn: the face runs straight out to the rim.
            if (radius >= R_ARC_IN && radius < R_ARC_OUT && present[sector]) {
                if (within > fill[sector]) return 0xFF332818;
                return steady[sector] ? Palette.BRASS_HOT
                        : (fill[sector] < 1f ? Palette.BRASS_DEEP : Palette.BRASS);
            }
            // Engraved dividers, so a frame sits IN a sector rather than floating on a disc.
            if (within < 0.012 || within > 0.988) return Palette.DESK_EDGE;
            // THE AIMED SECTOR IS THE POINTER. A needle from the hub was correct and looked like a
            // stray mark: one or two pixels wide, and redundant beside a lit frame, a brass arc and
            // this. Lighting the whole wedge cannot be mistaken for an artefact, needs no rotating
            // sprite, and still answers "where am I aimed" when every slot is empty.
            //
            // Each layer keeps its own ground tone, so which set you are on is answered by the dial
            // rather than by a caption naming a concept at you.
            boolean aimed = sector == hovered;
            return switch (layer) {
                case 1 -> aimed ? 0xFF31404E : 0xFF1B1E24;
                case 2 -> aimed ? 0xFF2F3D27 : 0xFF1C2119;
                default -> aimed ? 0xFF4A3618 : 0xFF221A0F;
            };
        });
        WheelArt.rim(g, cx, cy, R_OUT, R_RIM - 1);
    }

    /** One slot: its frame, its icon, its numeral, and the seconds it has left. */
    private void drawSlot(GuiGraphics g, int cx, int cy, int index, long now) {
        AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(slotFor(index));
        double angle = wedgeCentre(index);
        int sx = cx + (int) Math.round(Math.cos(angle) * R_SLOT);
        int sy = cy + (int) Math.round(Math.sin(angle) * R_SLOT);
        boolean aimed = index == hovered;
        // The aimed frame LIFTS. Pixel art has always said "this one" with a pixel of height, and it
        // survives at GUI scales where a colour shift washes out.
        if (aimed) sy -= 1;

        int numeral = slotFor(index);
        if (entry == null) {
            WheelArt.socket(g, sx, sy, FRAME);
            WheelArt.number(g, sx + FRAME / 2 - 2, sy + FRAME / 2 - 7, numeral, 0xFF4A4034);
            return;
        }

        float ready = entry.toggle() ? 1f : readyFraction(entry, now);
        boolean cooling = ready < 1f;
        int rim;
        int inner;
        int edge;
        if (aimed) {
            rim = Palette.BRASS_HOT;
            inner = 0xFF3D2C10;
            edge = 0xFFFFF8E0;
        } else if (cooling) {
            rim = Palette.BRASS_DEEP;
            inner = 0xFF241A0E;
            edge = 0xFF5E5142;
        } else {
            rim = Palette.BRASS;
            inner = 0xFF33260F;
            edge = Palette.BRASS_HOT;
        }
        int x = sx - FRAME / 2;
        int y = sy - FRAME / 2;
        g.fill(x + 1, y + (aimed ? 3 : 2), x + FRAME + 1, y + FRAME + (aimed ? 3 : 2), 0x73000000);
        g.fill(x, y, x + FRAME, y + FRAME, Palette.DESK_EDGE);
        g.fill(x + 1, y + 1, x + FRAME - 1, y + FRAME - 1, rim);
        g.fill(x + 2, y + 2, x + FRAME - 2, y + FRAME - 2, inner);
        g.fill(x + 1, y + 1, x + FRAME - 1, y + 2, edge);

        if (!com.aetherianartificer.townstead.client.gui.common.IconArt
                .drawCentred(g, entry.icon(), sx, sy, 1f)) {
            // Initials, NOT a shared fallback sprite. One symbol for everything unauthored would
            // make a dial of identical cells, which is no more readable than no icon at all.
            String mark = com.aetherianartificer.townstead.root.ability.AbilityNames
                    .initialsOf(entry.name());
            g.drawString(font, mark, sx - font.width(mark) / 2, sy - 4,
                    aimed ? Palette.BRASS_HOT : 0xFFC0A46E, false);
        }

        // Flat quads over an item icon lose the depth test to it, so the wash needs the flush.
        g.flush();
        if (cooling) g.fill(x + 2, y + 2, x + FRAME - 2, y + FRAME - 2, 0x99140F08);
        // Kind, in the corner: a switch you hold on, or a spark you cast once. The switch also
        // carries its own state, so one mark answers both questions.
        if (entry.toggle()) {
            WheelArt.switchMark(g, x + 3, y + 3, entry.toggledOn());
        } else {
            WheelArt.sparkMark(g, x + 3, y + 3, cooling ? 0xFF6E5A38 : Palette.BRASS_DEEP);
        }
        WheelArt.number(g, x + FRAME - 2, y + FRAME - 7, numeral,
                aimed ? Palette.BRASS_HOT : 0xFF8A7048);
        if (cooling && entry.cooldownTicks() > 0) {
            // The arc is for glancing; the numeral is for deciding whether it is worth waiting.
            int seconds = (int) Math.ceil((entry.readyAt() - now) / 20d);
            if (seconds > 0) WheelArt.number(g, x + 6, y + FRAME - 7, seconds, Palette.BRASS_HOT);
        }
    }

    /**
     * The hub does two jobs that region was already doing invisibly: it is the cancel well, lifting
     * and showing its mark while the cursor rests in it, and it carries the layer pips.
     */
    private void drawHub(GuiGraphics g, int cx, int cy) {
        boolean armed = hovered < 0;
        WheelArt.hub(g, cx, cy, R_FACE, armed);
        if (armed) WheelArt.cancelMark(g, cx, cy, 0xFF6E5A38);
        for (int i = 0; i < LAYERS; i++) {
            // Three 3px pips on a 5px pitch is 13 wide, so the row starts 6 left of centre.
            int px = cx - 6 + i * 5;
            int py = cy + 13;
            g.fill(px, py, px + 3, py + 3, i == layer ? layerAccent() : 0xFF493A22);
        }
    }

    /** The tone the dial is currently wearing, lit enough to read at three pixels. */
    private int layerAccent() {
        return switch (layer) {
            case 1 -> 0xFF8FB4D6;
            case 2 -> 0xFF9CC77E;
            default -> Palette.BRASS_HOT;
        };
    }

    /**
     * The label, on a plate BELOW the dial.
     *
     * <p>It used to sit in the hub, where "Deepwood gnome vanish" does not fit and collided with the
     * boss. Out here it gets the dial's full width, and the second line can carry the cost instead of
     * fighting the name for room.</p>
     */
    private void drawPlate(GuiGraphics g, int cx, int cy) {
        AbilityLoadoutS2CPayload.Entry entry =
                hovered < 0 ? null : ClientAbilityLoadout.slot(slotFor(hovered));
        String name;
        String under = "";
        if (entry != null) {
            name = entry.name();
            // Kind first, because "is this a switch or a cast" changes what pressing it MEANS, and
            // the corner mark can only say so much at five pixels.
            under = Component.translatable(entry.toggle()
                    ? (entry.toggledOn() ? "townstead.ability.wheel.kind.toggle_on"
                            : "townstead.ability.wheel.kind.toggle_off")
                    : "townstead.ability.wheel.kind.cast").getString();
            if (entry.costAmount() > 0 && !entry.costLabel().isEmpty()) {
                under = under + "  ·  " + Component.translatable("townstead.ability.wheel.cost",
                        entry.costAmount(), entry.costLabel()).getString();
            }
        } else if (ClientAbilityLoadout.isEmpty()) {
            name = Component.translatable("townstead.ability.wheel.nothing_prepared").getString();
        } else if (hovered < 0) {
            // Say what letting go does, rather than leaving the dead zone to be found by accident.
            name = Component.translatable("townstead.ability.wheel.cancel").getString();
        } else {
            name = Component.translatable("townstead.ability.wheel.empty_slot").getString();
        }

        int plateW = Math.max(120, Math.max(font.width(name), font.width(under)) + 20);
        int plateH = under.isEmpty() ? 16 : 26;
        int left = cx - plateW / 2;
        int top = cy + R_OUT + 12;
        g.fill(left - 1, top - 1, left + plateW + 1, top + plateH + 1, 0xFF0F0A05);
        g.fill(left, top, left + plateW, top + plateH, 0xFF2A2013);
        g.fill(left, top, left + plateW, top + 1, Palette.DESK_LIP);
        g.drawString(font, name, cx - font.width(name) / 2, top + 4,
                entry == null ? 0xFFB79A6C : Palette.BRASS_HOT, false);
        if (!under.isEmpty()) {
            g.drawString(font, under, cx - font.width(under) / 2, top + 15, 0xFFB79A6C, false);
        }
    }

    /** The slot a dial position points at on the layer currently shown. */
    private int slotFor(int wedge) {
        return layer * SLOTS + wedge + 1;
    }

    private static float readyFraction(AbilityLoadoutS2CPayload.Entry entry, long now) {
        if (entry == null || entry.cooldownTicks() <= 0 || entry.readyAt() <= now) return 1f;
        long left = entry.readyAt() - now;
        return Mth.clamp(1f - left / (float) entry.cooldownTicks(), 0f, 1f);
    }

    /** Slot 1 sits at twelve o'clock and they run clockwise. */
    private static double wedgeCentre(int index) {
        return -Math.PI / 2 + index * (2 * Math.PI / SLOTS);
    }

    /** Which sector a cursor offset falls in, or -1 inside the dead zone. */
    private static int wedgeAt(int dx, int dy) {
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / SLOTS;
        while (angle < 0) angle += 2 * Math.PI;
        return (int) (angle / (2 * Math.PI / SLOTS)) % SLOTS;
    }

    /** Fires whatever the cursor was over. Called on key release, and on a click. */
    public void select() {
        if (hovered < 0 || ClientAbilityLoadout.slot(slotFor(hovered)) == null) return;
        fire(slotFor(hovered));
    }

    /** Sends a slot and remembers it, so a tap of the key can repeat it without opening. */
    public static void fire(int slot) {
        ClientAbilityLoadout.rememberUsed(slot);
        ActivateAbilityC2SPayload payload = new ActivateAbilityC2SPayload(slot);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        Minecraft mc = Minecraft.getInstance();
        if (!Accessibility.isReduceMotion() && mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
        }
    }

    /** True when the cursor is in the dead zone, so a release should cancel rather than fire. */
    public boolean aimingAtNothing() {
        return hovered < 0;
    }

    /**
     * Left fires; right or shift edits.
     *
     * <p>The same split MineMenu uses on its own wedges, which is worth matching rather than
     * inventing: a player reaching for a radial has probably met that convention already.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // RIGHT-click only. Shift-click used to open this too, but shift now means "show the other
        // eight", so a shift-click would have been asking two questions with one gesture.
        if (hovered >= 0 && button == 1) {
            if (minecraft != null) {
                minecraft.setScreen(new AbilityCatalogueScreen(slotFor(hovered), null));
            }
            return true;
        }
        if (button == 0) {
            select();
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft mc = minecraft == null ? Minecraft.getInstance() : minecraft;
        mc.setScreen(null);
    }
}
