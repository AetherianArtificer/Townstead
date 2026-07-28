package com.aetherianartificer.townstead.client.input;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.assign.Assignable;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutS2CPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Every keybind registered by anyone, offered as something you can put on the wheel.
 *
 * <p>NOT a datapack. A pack cannot name bindings it has never heard of, and asking authors to write
 * fifteen files of translation keys per mod is the same authoring wall the wheel exists to remove.
 * The client already holds the complete list at runtime, with each binding's own category, so the
 * catalogue can simply read it. Install a mod and its keys are assignable; remove it and they stop
 * being offered. Nobody writes anything.</p>
 *
 * <p>CLIENT-SIDE by necessity: a server has no idea what bindings a client has. The arrangement
 * still lives with the server, which stores the id without resolving it, so one loadout covers
 * abilities and bindings alike and there is no second store to fall out of step.</p>
 */
public final class KeybindAssignables {

    private KeybindAssignables() {}

    /**
     * Bindings live under our namespace with the binding's own translation key as the path.
     *
     * <p>Reversible on purpose, so the client can get back to the exact binding without a lookup
     * table the server would have to carry. Translation keys are conventionally lowercase with dots
     * and underscores, which is already a legal path; anything else is skipped rather than mangled
     * into an id that would not press the thing it names.</p>
     */
    public static ResourceLocation idOf(KeyMapping mapping) {
        return mapping == null ? null
                : ResourceLocation.tryBuild(Townstead.MOD_ID, mapping.getName());
    }

    /** The binding an id refers to, or empty when the id is not one of ours. */
    public static String keyOf(String id) {
        ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
        return parsed != null && Townstead.MOD_ID.equals(parsed.getNamespace())
                && parsed.getPath().startsWith("key.") ? parsed.getPath() : "";
    }

    /**
     * Everything assignable, grouped by the binding's own category.
     *
     * <p>Our own keys are left out. The ability slot keys and the wheel key already reach this
     * system, so offering them here would let a slot fire the key that opens the wheel.</p>
     */
    public static List<AbilityLoadoutS2CPayload.Option> options() {
        Minecraft mc = Minecraft.getInstance();
        List<AbilityLoadoutS2CPayload.Option> out = new ArrayList<>();
        if (mc == null || mc.options == null) return out;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) continue;
            String category = mapping.getCategory();
            if (category != null && category.startsWith("townstead.key.")) continue;
            ResourceLocation id = idOf(mapping);
            if (id == null) continue;
            KeybindDetails.Detail pack = KeybindDetails.of(mapping.getName());
            KeybindDetails.Detail live = LiveKeybinds.resolve(mapping.getName());
            out.add(new AbilityLoadoutS2CPayload.Option(
                    id.toString(),
                    nameOf(mapping, pack, live),
                    iconOf(pack, live),
                    // SOURCE stays with the pack. It is the tab heading, and a live source answers
                    // per binding, so taking it from there would scatter one mod's keys across as
                    // many tabs as it has spells.
                    sourceOf(mapping.getName(), pack, category),
                    false,
                    0, 0, "",
                    Assignable.Kind.KEYBIND.ordinal(), 0, 0));
        }
        return out;
    }

    /**
     * The mod that owns a binding, from its own translation key.
     *
     * <p>Bindings are named {@code key.<modid>.<what>} by convention, so the middle segment is the
     * owner. Better than the CATEGORY for a heading: a category is free text and half of them are
     * either a vanilla bucket like "Gameplay" or a title too long for a tab, neither of which tells
     * you which mod you are looking at. Vanilla's own keys have no middle segment and fall through
     * to their category, which is right for them.</p>
     */
    private static String modNameOf(String keybind) {
        String[] parts = keybind.split("\\.");
        if (parts.length < 3 || !"key".equals(parts[0])) return "";
        String modId = parts[1];
        //? if neoforge {
        return net.neoforged.fml.ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse("");
        //?} else {
        /*return net.minecraftforge.fml.ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse("");
        *///?}
    }

    /**
     * LIVE data wins over a pack's, which inverts the usual "explicit beats derived".
     *
     * <p>Deliberately. A pack cannot know what is in a quick-cast slot, so its icon is a guess
     * about a slot whose contents change; the owning mod's answer is the truth. A pack still has
     * the last word on bindings no bridge claims, which is nearly all of them.</p>
     */
    private static String iconOf(KeybindDetails.Detail pack, KeybindDetails.Detail live) {
        return KeybindDetails.pick(live.icon(), pack.icon());
    }

    /** A pack's heading, else the owning mod's name, else the binding's own category. */
    private static String sourceOf(String keybind, KeybindDetails.Detail pack, String category) {
        return KeybindDetails.pick(pack.source(), KeybindDetails.pick(modNameOf(keybind),
                Component.translatable(category == null ? "" : category).getString()));
    }

    private static String nameOf(KeyMapping mapping, KeybindDetails.Detail pack,
                                 KeybindDetails.Detail live) {
        return KeybindDetails.pick(live.name(), KeybindDetails.pick(pack.name(),
                Component.translatable(mapping.getName()).getString()));
    }

    /** Fills in a slot the server could only pass through, since only we can name a binding. */
    public static AbilityLoadoutS2CPayload.Entry resolve(AbilityLoadoutS2CPayload.Entry entry) {
        String key = keyOf(entry.id());
        if (key.isEmpty()) return entry;
        Minecraft mc = Minecraft.getInstance();
        KeybindDetails.Detail pack = KeybindDetails.of(key);
        KeybindDetails.Detail live = LiveKeybinds.resolve(key);
        String name = KeybindDetails.pick(live.name(),
                KeybindDetails.pick(pack.name(), Component.translatable(key).getString()));
        String category = "";
        if (mc != null && mc.options != null) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (key.equals(mapping.getName())) {
                    category = mapping.getCategory() == null ? "" : mapping.getCategory();
                    break;
                }
            }
        }
        String source = sourceOf(key, pack, category);
        return new AbilityLoadoutS2CPayload.Entry(entry.slot(), entry.id(), name,
                iconOf(pack, live), false, false, entry.cooldownTicks(), entry.readyAt(),
                0, "", Assignable.Kind.KEYBIND.ordinal(), key, source, 0, 0);
    }
}
