package com.aetherianartificer.townstead.client;

import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.KeyMapping;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Townstead keybinds: the RPG dialogue key plus the pool of remappable "Root
 * Ability" keys. The ability keys are real {@link KeyMapping}s (registered at
 * startup, default unbound), so they appear in the Controls screen and are picked
 * up by controller/VR rebinding layers; an active-ability gene binds to one by slot.
 */
public final class TownsteadKeybinds {
    public static final KeyMapping TALK = new KeyMapping(
            "townstead.key.talk",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "townstead.key.category"
    );

    /**
     * Opens the ability wheel while held. Default unbound like the slot keys: a mod that seizes a
     * key on first launch is a mod that fights whatever the player already had there.
     */
    public static final KeyMapping WHEEL = new KeyMapping(
            "townstead.key.wheel",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "townstead.key.category"
    );

    /**
     * Reaching the dial's second and third sets.
     *
     * <p>REAL keybinds, not {@code hasShiftDown()}. Hardcoding the modifier meant it could not be
     * rebound, never appeared in the Controls screen, and was invisible to the controller and VR
     * remapping layers that read the keybind registry. It also meant the UI could only ever CLAIM
     * it was shift, whether or not that was true for the player reading it.</p>
     */
    public static final KeyMapping LAYER_SECOND = new KeyMapping(
            "townstead.key.layer_second",
            InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,
            "townstead.key.category");

    public static final KeyMapping LAYER_THIRD = new KeyMapping(
            "townstead.key.layer_third",
            InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,
            "townstead.key.category");

    /**
     * One Root Ability key per slot on the dial's FIRST layer; default unbound.
     *
     * <p>Deliberately eight rather than {@code ActiveAbilities.POOL_SIZE}, which is sixteen. The
     * upper eight are reached by holding shift on the wheel; giving them keys as well would mean
     * sixteen unbound entries in the Controls screen to serve a layer most players will drive with
     * the dial anyway.</p>
     */
    public static final int ABILITY_KEYS = 8;
    public static final KeyMapping[] ABILITIES = new KeyMapping[ABILITY_KEYS];

    static {
        for (int i = 0; i < ABILITY_KEYS; i++) {
            ABILITIES[i] = new KeyMapping(
                    "townstead.key.ability" + (i + 1),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    "townstead.key.category");
        }
    }

    private TownsteadKeybinds() {}

    /** The remappable key for a 1-based ability slot, or null when the slot is out of range. */
    public static KeyMapping abilityKey(int slot) {
        return slot >= 1 && slot <= ABILITY_KEYS ? ABILITIES[slot - 1] : null;
    }

    /** True while the wheel key is physically held, which is how the wheel knows to stay open. */
    private static boolean wheelPrevDown = false;
    /** When the wheel key went down, so a quick tap can be told from a deliberate hold. */
    private static long wheelPressedAt = 0L;
    /** Long enough to be a tap on a bad connection, short enough that a real aim is never one. */
    private static final long TAP_MS = 220L;

    /**
     * Is this binding physically down right now?
     *
     * <p>Polls the WINDOW, not {@code KeyMapping.isDown()}: vanilla stops feeding key state into
     * bindings while a Screen is open, and every one of these is read with a screen up.</p>
     */
    public static boolean isHeld(Minecraft mc, KeyMapping mapping) {
        if (mc == null || mc.getWindow() == null || mapping == null) return false;
        InputConstants.Key bound = mapping.getKey();
        if (bound == null || bound.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        long handle = mc.getWindow().getWindow();
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, bound.getValue())
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(handle, bound.getValue());
    }

    /** The bound key's own name, so a UI never has to guess at what to tell the player to press. */
    public static String keyName(KeyMapping mapping) {
        return mapping == null ? "" : mapping.getTranslatedKeyMessage().getString();
    }

    private static boolean wheelKeyDown(Minecraft mc) {
        return isHeld(mc, WHEEL);
    }

    /**
     * Hold to open, release to fire.
     *
     * <p>Polls the WINDOW, not {@code KeyMapping.isDown()}. Vanilla stops feeding key state into
     * bindings while a Screen is open, so the moment the wheel appeared the binding would read as
     * released and the wheel would fire and shut itself on the very next tick. Every radial menu
     * that works does raw GLFW polling for exactly this reason; MineMenu's own handler is where I
     * confirmed it rather than guessing.</p>
     *
     * <p>{@code consumeClick} is no use here either: it reports that a press happened, not that it
     * is still happening, and this gesture is defined entirely by how long the key is held.</p>
     */
    private static void tickWheel(Minecraft mc) {
        boolean down = wheelKeyDown(mc);
        if (down == wheelPrevDown) return;
        wheelPrevDown = down;
        if (down) {
            wheelPressedAt = Util.getMillis();
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new com.aetherianartificer.townstead.client.gui.ability
                        .AbilityWheelScreen());
            }
            return;
        }
        if (!(mc.screen instanceof com.aetherianartificer.townstead.client.gui.ability
                .AbilityWheelScreen wheel)) {
            return;
        }
        // A TAP repeats the last ability without you ever aiming. The common case in a fight is not
        // choosing, it is casting the same thing again, and making that cost a full hold-aim-release
        // is what turns a wheel into a tax. Only when the cursor never left the dead zone, so a fast
        // deliberate aim still fires what it was pointed at.
        if (wheel.aimingAtNothing() && Util.getMillis() - wheelPressedAt <= TAP_MS) {
            int repeat = com.aetherianartificer.townstead.client.root.ClientAbilityLoadout
                    .lastUsedSlot();
            if (repeat > 0) {
                com.aetherianartificer.townstead.client.gui.ability.AbilityWheelScreen.fire(repeat);
            }
        } else {
            wheel.select();
        }
        wheel.onClose();
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        tickWheel(mc);
        while (TALK.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit) {
                Entity entity = entityHit.getEntity();
                if (entity instanceof VillagerLike<?> villager) {
                    mc.setScreen(new RpgDialogueScreen(villager));
                }
            }
        }
        for (int i = 0; i < ABILITIES.length; i++) {
            int slot = i + 1;
            while (ABILITIES[i].consumeClick()) {
                if (mc.player == null || mc.screen != null) continue;
                // Firing by key counts as using it, or the wheel's tap-to-repeat would remember
                // only what you last cast THROUGH the wheel and disagree with what you just did.
                com.aetherianartificer.townstead.client.root.ClientAbilityLoadout.rememberUsed(slot);
                com.aetherianartificer.townstead.root.ability.ActivateAbilityC2SPayload payload =
                        new com.aetherianartificer.townstead.root.ability.ActivateAbilityC2SPayload(slot);
                //? if neoforge {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
                //?} else {
                /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
                *///?}
            }
        }

        // Gene-glide start: vanilla LocalPlayer only sends START_FALL_FLYING when the chest
        // slot holds an actual elytra — it never consults tryToStartFallFlying on the client —
        // so an elytra_flight bearer must send the command packet themself. The server
        // validates through PlayerElytraGeneMixin. Latched so one press sends one packet.
        townstead$tryGeneGlide(mc);

        // Observe (do not consume) the vanilla keys so press triggers can react without stealing the
        // key from movement. Edge-detected: a packet only on the press, not while held.
        boolean active = mc.player != null && mc.screen == null;
        for (int i = 0; i < PRESS_KEYS.length; i++) {
            KeyMapping mapping = pressKey(mc, PRESS_KEYS[i]);
            boolean down = active && mapping != null && mapping.isDown();
            if (down && !PRESS_PREV[i]) sendKeyPress(PRESS_KEYS[i]);
            PRESS_PREV[i] = down;
        }
    }

    private static boolean GLIDE_PREV_DOWN = false;

    private static void townstead$tryGeneGlide(Minecraft mc) {
        var player = mc.player;
        if (player == null) { GLIDE_PREV_DOWN = false; return; }
        boolean down = mc.screen == null && mc.options.keyJump.isDown();
        boolean freshPress = down && !GLIDE_PREV_DOWN;
        GLIDE_PREV_DOWN = down;
        // Deliberately stricter than vanilla's held-key check: a permanent glider would
        // deploy at the apex of every held-jump hop, so deploying takes the classic
        // double-tap — a fresh jump press while already airborne and falling.
        if (!freshPress) return;
        if (player.onGround() || player.isFallFlying() || player.isInWater()
                || player.getAbilities().flying || player.getDeltaMovement().y >= 0.0) {
            return;
        }
        if (!com.aetherianartificer.townstead.client.root.ClientAbilities.isActive(
                player, com.aetherianartificer.townstead.root.ability.Ability.ELYTRA_FLIGHT)) {
            return;
        }
        player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    /** Vanilla keys observable by a {@code press} trigger; those with their own server signal stay out. */
    private static final String[] PRESS_KEYS = {"jump", "sneak", "sprint"};
    private static final boolean[] PRESS_PREV = new boolean[PRESS_KEYS.length];

    private static KeyMapping pressKey(Minecraft mc, String name) {
        return switch (name) {
            case "jump" -> mc.options.keyJump;
            case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint;
            default -> null;
        };
    }

    private static void sendKeyPress(String key) {
        com.aetherianartificer.townstead.root.trigger.KeyPressC2SPayload payload =
                new com.aetherianartificer.townstead.root.trigger.KeyPressC2SPayload(key);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
