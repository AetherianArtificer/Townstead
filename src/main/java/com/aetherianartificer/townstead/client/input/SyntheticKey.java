package com.aetherianartificer.townstead.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Presses another mod's keybind on the player's behalf.
 *
 * <p>THE WHOLE COMPAT STORY, and it costs no dependency on anyone. Iron's Spells ships fifteen
 * "Quick Cast Slot" bindings and Ars Nouveau ten; both solved WHICH spell with numbered slots and
 * then hit the wall this wheel exists for, because nobody can bind fifteen keys. Pointing a slot at
 * one of those bindings makes us the front end they are missing, without an API, a license question
 * or a version to track.</p>
 *
 * <p>Presses go to ONE mapping, never through {@link KeyMapping#click}. That static bumps every
 * mapping sharing a key, and the bindings worth reaching are unbound by default, so clicking
 * {@code UNKNOWN} would fire every unbound binding in the game at once.</p>
 *
 * <p>Both halves of a press are sent because mods read keys three ways. {@code setDown} covers
 * anything polling {@code isDown()} and anything latching a rising edge (Iron's
 * {@code ExtendedKeyMapping} does the latter, which is why their unbound slots work at all). The
 * click counter covers {@code consumeClick()}. A key event is posted as well, for mods that only look
 * at their keys when one arrives. A mod reading raw GLFW still cannot be reached this way.</p>
 */
public final class SyntheticKey {

    private SyntheticKey() {}

    /** Held for two ticks: one is enough to miss a mod whose tick handler runs before ours. */
    private static final int HOLD_TICKS = 2;

    private static final Map<String, KeyMapping> BY_NAME = new LinkedHashMap<>();
    private static final Map<KeyMapping, Integer> HELD = new LinkedHashMap<>();

    private static Field clickCount;
    private static boolean clickCountResolved;

    /**
     * Presses the binding with this translation key, e.g.
     * {@code key.irons_spellbooks.spell_quick_cast_1}. False when no such binding is registered,
     * which is the normal answer when the mod that owned it is not installed.
     */
    public static boolean press(String name) {
        KeyMapping mapping = find(name);
        if (mapping == null) return false;
        // A move that rides on a jump (Werewolves' leap) only fires with Jump held as well.
        if (RIDES_ON_JUMP.contains(name)) hold(Minecraft.getInstance().options.keyJump);
        hold(mapping);
        bumpClickCount(mapping);
        postKeyEvent(mapping);
        return true;
    }

    /** Bindings whose mod only acts on them with Jump down, as their own key handler checks. */
    private static final java.util.Set<String> RIDES_ON_JUMP = java.util.Set.of("keys.werewolves.leap");

    private static void hold(KeyMapping mapping) {
        mapping.setDown(true);
        HELD.put(mapping, HOLD_TICKS);
    }

    /**
     * Sends the key event a real press makes, for mods that only look at their keys when one arrives
     * (Werewolves reads Leap and Bite this way). They check which binding is down, which is now ours.
     */
    private static void postKeyEvent(KeyMapping mapping) {
        com.mojang.blaze3d.platform.InputConstants.Key key = mapping.getKey();
        int code = key == null || key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue() : key.getValue();
        try {
            //? if neoforge {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.InputEvent.Key(
                    code, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0));
            //?} else {
            /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.InputEvent.Key(
                    code, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0));
            *///?}
        } catch (RuntimeException ignored) {
            // A listener that failed on a press it did not expect; the binding is still held down.
        }
    }

    /** Releases anything whose hold has run out. Call once per client tick. */
    public static void tick() {
        if (HELD.isEmpty()) return;
        HELD.entrySet().removeIf(entry -> {
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                return false;
            }
            entry.getKey().setDown(false);
            return true;
        });
    }

    /** Every binding registered by anyone, by translation key. */
    private static KeyMapping find(String name) {
        if (name == null || name.isEmpty()) return null;
        KeyMapping cached = BY_NAME.get(name);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (name.equals(mapping.getName())) {
                BY_NAME.put(name, mapping);
                return mapping;
            }
        }
        return null;
    }

    /**
     * Bumps one mapping's private click counter, for the {@code consumeClick()} readers.
     *
     * <p>Reflection rather than an accessor mixin because the field is named in Mojang mappings on
     * 1.21.1 and in SRG on 1.20.1, and a mixin would need a version-gated target for a client-side
     * convenience. Falling back to the sole non-static {@code int} on the class covers the remap:
     * {@code clickCount} is the only one, on both loaders.</p>
     */
    private static void bumpClickCount(KeyMapping mapping) {
        if (!clickCountResolved) {
            clickCountResolved = true;
            clickCount = resolveClickCount();
        }
        if (clickCount == null) return;
        try {
            clickCount.setInt(mapping, clickCount.getInt(mapping) + 1);
        } catch (IllegalAccessException ignored) {
            clickCount = null;
        }
    }

    private static Field resolveClickCount() {
        try {
            Field named = KeyMapping.class.getDeclaredField("clickCount");
            named.setAccessible(true);
            return named;
        } catch (NoSuchFieldException | RuntimeException ignored) {
            // Remapped name: take the only non-static int the class declares.
        }
        Field only = null;
        for (Field field : KeyMapping.class.getDeclaredFields()) {
            if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) continue;
            if (only != null) return null;
            only = field;
        }
        if (only == null) return null;
        try {
            only.setAccessible(true);
            return only;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Dropped on disconnect: bindings survive a world change, a stuck held key must not. */
    public static void clear() {
        for (KeyMapping mapping : HELD.keySet()) mapping.setDown(false);
        HELD.clear();
    }
}
