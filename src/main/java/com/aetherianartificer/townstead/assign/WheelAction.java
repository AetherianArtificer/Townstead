package com.aetherianartificer.townstead.assign;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A datapack-declared action: {@code data/<ns>/wheel_action/*.json}.
 *
 * <p>The extension path, and deliberately the ONLY one most packs will need. A mod that exposes a
 * command or a keybind can be put on the wheel by a JSON file, with no Java and no dependency in
 * either direction; the mod does not have to know we exist, and we do not have to track its
 * releases. Bridges stay available for the cases that genuinely need one, which is mostly reading a
 * live cooldown.</p>
 *
 * <pre>
 * {
 *   "name":   { "translate": "pack.wheel.summon" },
 *   "icon":   "goety:wand",
 *   "source": { "translate": "pack.wheel.source.goety" },
 *   "kind":   "command",
 *   "command": "goety cast summon_skeleton",
 *   "requires": { "advancement": "goety:necromancy" },
 *   "cooldown": 60
 * }
 * </pre>
 */
public record WheelAction(
        ResourceLocation id,
        Component name,
        String icon,
        Component source,
        Assignable.Kind kind,
        /** The command, keybind name or item id, depending on {@link #kind}. */
        String value,
        /** An advancement the player must hold, or null. */
        @Nullable ResourceLocation requiresAdvancement,
        int cooldownTicks) {

    public static WheelAction parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        Component name = DataPackLang.parseComponent(json.get("name"), id.toString(), lang);
        Component source = json.has("source")
                ? DataPackLang.parseComponent(json.get("source"), id + ".source", lang)
                : Component.translatable("townstead.ability.source.pack");
        String kindKey = GsonHelper.getAsString(json, "kind", "command")
                .toLowerCase(java.util.Locale.ROOT);
        Assignable.Kind kind = switch (kindKey) {
            case "keybind" -> Assignable.Kind.KEYBIND;
            case "item" -> Assignable.Kind.ITEM;
            default -> Assignable.Kind.COMMAND;
        };
        String value = switch (kind) {
            case KEYBIND -> GsonHelper.getAsString(json, "keybind", "");
            case ITEM -> GsonHelper.getAsString(json, "item", "");
            default -> GsonHelper.getAsString(json, "command", "");
        };
        if (value.isEmpty()) {
            throw new IllegalArgumentException("wheel_action " + id + " declares no " + kindKey);
        }
        // A leading slash is what a player types, not what the command dispatcher wants.
        if (kind == Assignable.Kind.COMMAND && value.startsWith("/")) value = value.substring(1);

        ResourceLocation advancement = null;
        if (json.has("requires")) {
            JsonObject requires = GsonHelper.getAsJsonObject(json, "requires");
            if (requires.has("advancement")) {
                advancement = DataPackLang.parseId(
                        GsonHelper.getAsString(requires, "advancement", ""));
            }
        }
        int cooldown = Math.max(0, GsonHelper.getAsInt(json, "cooldown", 0)) * 20;
        return new WheelAction(id, name, GsonHelper.getAsString(json, "icon", ""), source, kind,
                value, advancement, cooldown);
    }
}
