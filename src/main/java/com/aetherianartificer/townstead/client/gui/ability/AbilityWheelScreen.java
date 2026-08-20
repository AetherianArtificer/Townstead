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
        // Re-read what the owning mods say before drawing anything: a spellbook swap changes what
        // a quick-cast slot holds, and no packet would have told us.
        ClientAbilityLoadout.refreshLocal();
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
        boolean[] tracked = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(slotFor(i));
            // A track is only drawn for something that HAS a state to report: an on/off switch, or
            // a cooldown. A borrowed keybind has neither, and a permanently full ring is worse than
            // none, because it cannot be told apart from a cooldown that happens to be ready.
            tracked[i] = entry != null
                    && (entry.toggle() || entry.cooldownTicks() > 0 || entry.costAmount() > 0);
            if (entry == null) continue;
            steady[i] = entry.toggle();
            fill[i] = entry.toggle() ? (entry.toggledOn() ? 1f : 0f) : readyFraction(entry, now);
            // A cost you cannot pay is an AVAILABILITY answer, so the ring owns it. Without
            // this the wheel showed a full ring for something that would refuse the press,
            // and the only feedback was nothing happening.
            if (!affordable(entry)) fill[i] = 0f;
        }

        // The dark outline first, so the rim's gradient can run right to the dial's edge.
        WheelArt.disc(g, cx, cy, R_OUT + 1, Palette.DESK_EDGE);
        // The face runs the whole way out and the rim paints OVER its outer band. Reserving the band
        // here instead left a ring of pixels claimed by neither: this pass cuts on a rounded
        // hypotenuse and the rim on a scanned half-width, and the two disagree by a pixel per row.
        WheelArt.paintRing(g, cx, cy, R_OUT, R_FACE, SLOTS, (sector, within, dx, dy) -> {
            int offset = Math.abs(dx);
            // Each layer keeps its own ground tone, so which set you are on is answered by the dial
            // rather than by a caption naming a concept at you. THE AIMED SECTOR IS THE POINTER: a
            // needle from the hub was correct and looked like a stray mark, one or two pixels wide
            // and redundant beside a lit frame and a brass arc. Lighting the whole wedge cannot be
            // mistaken for an artefact and still answers "where am I aimed" when every slot is empty.
            boolean aimed = sector == hovered;
            int face = switch (layer) {
                case 1 -> aimed ? 0xFF31404E : 0xFF1B1E24;
                case 2 -> aimed ? 0xFF2F3D27 : 0xFF1C2119;
                default -> aimed ? 0xFF4A3618 : 0xFF221A0F;
            };

            // THE BAND IS ONE RING, and nothing crosses it. Bounded by halfAt, the same cut the rim
            // uses: a rounded hypotenuse frayed its outer edge into the rim.
            //
            // Above and below the band's inner circle there is no hole to leave, and halfAt reports
            // 0 for those rows. Testing `offset > 0` then excluded dx = 0 on every one of them,
            // which drew a one-pixel dark seam straight down the middle of the top and bottom of
            // the ring: the thin black line in the arc.
            int innerHalf = WheelArt.halfAt(R_ARC_IN, dy);
            if (offset <= WheelArt.halfAt(R_ARC_OUT, dy)
                    && (innerHalf == 0 || offset > innerHalf)) {
                // An EMPTY sector gets no track, and no divider either. Returning face here rather
                // than falling through is the point: the fall-through drew a divider through the
                // ring on untracked sectors and not on tracked ones, so a stripe appeared to slice
                // into the neighbouring arc and stop.
                if (!tracked[sector]) return face;
                if (within > fill[sector]) return 0xFF332818;
                // A held toggle reads as FULL, not as a highlight. BRASS_HOT here was a near-white
                // arc beside a brass rim, which looked like a rendering fault rather than "on".
                return fill[sector] < 1f && !steady[sector] ? Palette.BRASS_DEEP : Palette.BRASS;
            }

            // Engraved dividers, so a frame sits IN a sector rather than floating on a disc. Width
            // is set in PIXELS: `within` is an ANGLE, so a constant slice of it draws a wedge that
            // is hairline at the hub and fans out to a ragged three or four pixels at the rim.
            double edge = 0.55 / Math.max(1d, Math.sqrt(dx * dx + dy * dy)) / (2 * Math.PI / SLOTS);
            if (within < edge || within > 1 - edge) return Palette.DESK_EDGE;
            return face;
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
        // Both answers are "not now", so both dull the frame. The plate says which.
        boolean cooling = ready < 1f || !affordable(entry);
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
            int seconds = (int) Math.ceil((ClientAbilityLoadout.readyAt(entry) - now) / 20d);
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

    /** The three strings and the colour one slot's label needs. */
    private record Label(String name, String tag, String kind, String cost, int costColor,
                         float costFill, boolean short_) {
        static final Label EMPTY = new Label("", "", "", "", 0, -1f, false);
    }

    /** Builds a slot's label, or {@link Label#EMPTY} for a slot holding nothing. */
    private Label labelFor(AbilityLoadoutS2CPayload.Entry entry) {
        if (entry == null) return Label.EMPTY;
        String cost = "";
        int costColor = 0xFFB79A6C;
        float fill = -1f;
        boolean lacking = false;
        if (entry.costAmount() > 0 && !entry.costLabel().isEmpty()) {
            cost = Component.translatable("townstead.ability.wheel.cost",
                    entry.costAmount(), entry.costLabel()).getString();
            costColor = entry.costColor() == 0 ? 0xFFB79A6C : 0xFF000000 | entry.costColor();
            // The gauge fills toward the COST, not across the pool: full means you can pay, which
            // is the question being asked. How much you are carrying overall is already a bar on
            // the HUD, and repeating it here would answer a question nobody is holding a key to ask.
            fill = Mth.clamp(entry.costHave() / (float) entry.costAmount(), 0f, 1f);
            lacking = !affordable(entry);
        }
        return new Label(entry.name(), entry.source(),
                kindWord(entry.toggle(), entry.toggledOn(), entry.kind()),
                cost, costColor, fill, lacking);
    }

    /** Meta sits at three quarters, so the name leads instead of tying with it. */
    private static final float META = 0.75f;
    private static final int GAUGE_W = 26;

    private int metaWidth(Label label) {
        if (label.kind().isEmpty()) return 0;
        int width = font.width(label.kind());
        if (!label.tag().isEmpty()) width += font.width(label.tag()) + 8 + 5;
        if (!label.cost().isEmpty()) width += font.width("  ·  ") + font.width(label.cost());
        if (label.costFill() >= 0f) width += 5 + GAUGE_W;
        return Math.round(width * META);
    }

    /**
     * The label, on a plate BELOW the dial.
     *
     * <p>It used to sit in the hub, where "Deepwood gnome vanish" does not fit and collided with
     * the boss. Out here it gets the dial's full width, and the second line can carry the cost
     * instead of fighting the name for room.</p>
     *
     * <p>WIDTH IS FIXED FOR THE WHOLE LAYER, measured across all eight slots rather than from the
     * one under the cursor. Sizing it to the hovered slot made it jump on every one of the eight
     * as you swept round, which is motion in the corner of your eye at exactly the moment you are
     * trying to aim. It still adapts to what you actually have prepared; it just stops moving
     * while you are using it.</p>
     */
    private void drawPlate(GuiGraphics g, int cx, int cy) {
        AbilityLoadoutS2CPayload.Entry entry =
                hovered < 0 ? null : ClientAbilityLoadout.slot(slotFor(hovered));
        Label label = labelFor(entry);
        String name = label.name();
        if (entry == null) {
            name = Component.translatable(ClientAbilityLoadout.isEmpty()
                    ? "townstead.ability.wheel.nothing_prepared"
                    : hovered < 0 ? "townstead.ability.wheel.cancel"
                            : "townstead.ability.wheel.empty_slot").getString();
        }

        int widest = font.width(name);
        for (int i = 0; i < SLOTS; i++) {
            Label other = labelFor(ClientAbilityLoadout.slot(slotFor(i)));
            widest = Math.max(widest, Math.max(font.width(other.name()), metaWidth(other)));
        }
        int metaW = metaWidth(label);
        int plateW = Math.max(140, widest + 20);
        int plateH = label.kind().isEmpty() ? 16 : 26;
        int left = cx - plateW / 2;
        int top = cy + R_OUT + 12;
        g.fill(left - 1, top - 1, left + plateW + 1, top + plateH + 1, 0xFF0F0A05);
        g.fill(left, top, left + plateW, top + plateH, 0xFF2A2013);
        g.fill(left, top, left + plateW, top + 1, Palette.DESK_LIP);
        g.drawString(font, name, cx - font.width(name) / 2, top + 4,
                entry == null ? 0xFFB79A6C : Palette.BRASS_HOT, false);
        if (label.kind().isEmpty()) return;

        g.pose().pushPose();
        g.pose().translate(cx - metaW / 2f, top + 15, 0);
        g.pose().scale(META, META, 1f);
        int x = 0;
        if (!label.tag().isEmpty()) {
            int tagW = font.width(label.tag()) + 8;
            g.fill(x, -2, x + tagW, 9, 0xFF1C1509);
            g.fill(x, -2, x + tagW, -1, 0xFF3A2E1E);
            g.fill(x, 8, x + tagW, 9, 0xFF120D07);
            g.drawString(font, label.tag(), x + 4, 0, 0xFFC9AD7C, false);
            x += tagW + 5;
        }
        String join = label.cost().isEmpty() ? "" : "  ·  ";
        g.drawString(font, label.kind() + join, x, 0, 0xFFB79A6C, false);
        x += font.width(label.kind() + join);
        if (!label.cost().isEmpty()) {
            g.drawString(font, label.cost(), x, 0, label.costColor(), false);
            x += font.width(label.cost());
        }
        if (label.costFill() >= 0f) {
            // A GAUGE, not a second number. "Can I pay" is a level, and the eye reads a level
            // without stopping; it is also the same green as the meter already on the HUD.
            x += 5;
            int filled = Math.round(GAUGE_W * label.costFill());
            g.fill(x, 0, x + GAUGE_W, 7, 0xFF120D07);
            g.fill(x + 1, 1, x + GAUGE_W - 1, 6, 0xFF1A1309);
            if (filled > 2) {
                g.fill(x + 1, 1, x + Math.max(2, filled - 1), 6,
                        label.short_() ? 0xFFC96A4A : label.costColor());
            }
        }
        g.pose().popPose();
    }

    /**
     * What pressing this does, in one word.
     *
     * <p>A borrowed keybind is not CAST. We do not know what the other mod does with it, only that
     * we press it once, and calling someone else's inventory key a cast was the wheel describing
     * its own vocabulary rather than the thing in the slot.</p>
     */
    public static String kindWord(boolean toggle, boolean toggledOn, int kind) {
        if (kind == com.aetherianartificer.townstead.assign.Assignable.Kind.KEYBIND.ordinal()) {
            return Component.translatable("townstead.ability.wheel.kind.trigger").getString();
        }
        return Component.translatable(toggle
                ? (toggledOn ? "townstead.ability.wheel.kind.toggle_on"
                        : "townstead.ability.wheel.kind.toggle_off")
                : "townstead.ability.wheel.kind.cast").getString();
    }

    /** The slot a dial position points at on the layer currently shown. */
    private int slotFor(int wedge) {
        return layer * SLOTS + wedge + 1;
    }

    /** False when the cost resource is short. Entries with no cost are always affordable. */
    static boolean affordable(AbilityLoadoutS2CPayload.Entry entry) {
        return entry == null || entry.costAmount() <= 0 || entry.costHave() >= entry.costAmount();
    }

    /** Reads the MERGED cooldown, so a client-performed action's ring is not always full. */
    private static float readyFraction(AbilityLoadoutS2CPayload.Entry entry, long now) {
        long ready = ClientAbilityLoadout.readyAt(entry);
        if (entry == null || entry.cooldownTicks() <= 0 || ready <= now) return 1f;
        long left = ready - now;
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

    /** Fires a slot and remembers it, so a tap of the key can repeat it without opening. */
    public static void fire(int slot) {
        ClientAbilityLoadout.rememberUsed(slot);
        dispatch(slot);
        Minecraft mc = Minecraft.getInstance();
        if (!Accessibility.isReduceMotion() && mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
        }
    }

    /**
     * Performs a slot: here when the client owns the action, on the server otherwise.
     *
     * <p>A keybind press cannot happen on a server, so a datapack action of that kind never
     * round-trips. Everything else does, and the server resolves the slot again: nothing here
     * decides whether a press is allowed.</p>
     */
    public static void dispatch(int slot) {
        AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(slot);
        if (entry != null
                && entry.kind() == com.aetherianartificer.townstead.assign.Assignable.Kind.KEYBIND.ordinal()) {
            // Gated here because this press never reaches the server, so the server's table cannot
            // see it. Everything else is gated there, where it belongs.
            if (!ClientAbilityLoadout.isReady(entry)) return;
            if (com.aetherianartificer.townstead.client.input.SyntheticKey.press(entry.clientValue())) {
                ClientAbilityLoadout.startLocalCooldown(entry.id(), entry.cooldownTicks());
            }
            return;
        }
        ActivateAbilityC2SPayload payload = new ActivateAbilityC2SPayload(slot);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
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
