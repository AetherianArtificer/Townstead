package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.SwitchboardPack;
import com.aetherianartificer.townstead.switchboard.SwitchboardServer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * {@code /townstead switchboard [list | get <key> | set <key> <value> | reset <key> | reload]}; with no
 * argument it opens the screen.
 * Operator only. Values are JSON; a bare word is read as text.
 */
public final class SwitchboardCommands {
    private SwitchboardCommands() {}

    private static final SuggestionProvider<CommandSourceStack> KEYS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(SettingIndex.all().stream().map(SettingIndex.Entry::key), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("switchboard")
                .requires(s -> s.hasPermission(2))
                .executes(c -> open(c.getSource()))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("get")
                        .then(Commands.argument("key", StringArgumentType.string()).suggests(KEYS)
                                .executes(c -> get(c.getSource(), StringArgumentType.getString(c, "key")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("key", StringArgumentType.string()).suggests(KEYS)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "key"),
                                                StringArgumentType.getString(c, "value"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("key", StringArgumentType.string()).suggests(KEYS)
                                .executes(c -> reset(c.getSource(), StringArgumentType.getString(c, "key")))))
                .then(Commands.literal("reload").executes(c -> reload(c.getSource())))));
    }

    private static int open(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SwitchboardServer.open(source.getPlayerOrException(), false);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        Map<String, JsonElement> overrides = Switchboard.overrides();
        if (overrides.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Every setting uses the config file."), false);
            return 1;
        }
        overrides.forEach((key, value) -> {
            String from = SwitchboardServer.sourceOf(source.getServer(), key);
            source.sendSuccess(() -> Component.literal(key + " = " + value + " (" + from + ")"), false);
        });
        return overrides.size();
    }

    private static int get(CommandSourceStack source, String key) {
        SettingIndex.Entry entry = SettingIndex.get(key);
        if (entry == null) return fail(source, "Unknown setting: " + key);
        Object value = Switchboard.get(entry.value());
        String from = SwitchboardServer.sourceOf(source.getServer(), key);
        source.sendSuccess(() -> Component.literal(key + " = " + SettingIndex.toJson(value) + " (" + from + ")"), false);
        return 1;
    }

    private static int set(CommandSourceStack source, String key, String raw) {
        return report(source, key, SwitchboardServer.set(source.getServer(), key, parse(raw)),
                key + " set to " + raw.trim());
    }

    private static int reset(CommandSourceStack source, String key) {
        return report(source, key, SwitchboardServer.reset(source.getServer(), key),
                key + " now follows the " + (SwitchboardPack.values().containsKey(key) ? "modpack" : "config file"));
    }

    private static int reload(CommandSourceStack source) {
        SwitchboardPack.reload();
        SwitchboardServer.refresh(source.getServer());
        source.sendSuccess(() -> Component.literal("Reloaded the modpack Switchboard file."), true);
        return 1;
    }

    private static int report(CommandSourceStack source, String key, SwitchboardServer.Result result, String ok) {
        return switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.literal(ok), true);
                yield 1;
            }
            case UNKNOWN -> fail(source, "Unknown setting: " + key);
            case LOCKED -> fail(source, key + " is set by the modpack and cannot change.");
            case INVALID -> fail(source, "That value does not fit " + key + ".");
        };
    }

    private static JsonElement parse(String raw) {
        String text = raw.trim();
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException e) {
            return new JsonPrimitive(text);
        }
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
