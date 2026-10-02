package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.commands.CommandTargets;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /townstead scene}: jump a villager's story to a scene, for testing. Operator only.
 * <ul>
 * <li>{@code /townstead scene <checkpoint> [target]}: applies one of the villager's story checkpoints
 * (its variables, counters and actions), then opens its scene.</li>
 * <li>{@code /townstead scene knot <knot> [target] [name=value ...]}: opens any knot, with variables set.</li>
 * </ul>
 * As in the other Townstead commands, {@code target} is an entity (a selector, name or UUID), and
 * without one it is the villager you look at, else the nearest.
 */
public final class StoryDebug {
    private StoryDebug() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        var word = com.mojang.brigadier.arguments.StringArgumentType.word();
        var target = net.minecraft.commands.arguments.EntityArgument.entity();
        dispatcher.register(net.minecraft.commands.Commands.literal("townstead").then(net.minecraft.commands.Commands.literal("scene")
                .requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("knot")
                        .then(net.minecraft.commands.Commands.argument("knot", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(c -> play(c.getSource(), lookedAt(c.getSource()), knot(c), ""))
                                .then(net.minecraft.commands.Commands.argument("target", target)
                                        .executes(c -> play(c.getSource(), target(c), knot(c), ""))
                                        .then(net.minecraft.commands.Commands.argument("vars", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(c -> play(c.getSource(), target(c), knot(c),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "vars")))))))
                .then(net.minecraft.commands.Commands.argument("checkpoint", word).suggests(CHECKPOINTS)
                        .executes(c -> checkpoint(c.getSource(), lookedAt(c.getSource()), checkpointName(c)))
                        .then(net.minecraft.commands.Commands.argument("target", target)
                                .executes(c -> checkpoint(c.getSource(), target(c), checkpointName(c)))))));
    }

    private static String knot(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(c, "knot");
    }

    private static String checkpointName(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(c, "checkpoint");
    }

    private static @Nullable VillagerEntityMCA target(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return net.minecraft.commands.arguments.EntityArgument.getEntity(c, "target") instanceof VillagerEntityMCA v ? v : null;
    }

    private static final SuggestionProvider<CommandSourceStack> CHECKPOINTS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(checkpointsOf(ctx.getSource(), lookedAt(ctx.getSource())), builder);

    private static java.util.Collection<String> checkpointsOf(CommandSourceStack source, @Nullable VillagerEntityMCA villager) {
        ServerPlayer player = source.getPlayer();
        StoryDefinition story = player == null || villager == null ? null : StoryService.storyOf(player, villager);
        return story == null ? List.of() : story.checkpoints().keySet();
    }

    private static @Nullable VillagerEntityMCA lookedAt(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : CommandTargets.lookedAtOrNearest(player, null);
    }

    /** {@code vars} is {@code name=value} pairs: true, false, a whole number, or text. */
    static int play(CommandSourceStack source, @Nullable VillagerEntityMCA villager, String knot, String vars) {
        ServerPlayer player = source.getPlayer();
        if (player == null || villager == null || StoryService.storyOf(player, villager) == null) {
            source.sendFailure(Component.literal("That villager tells you no story."));
            return 0;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String pair : vars.trim().split("\\s+")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            values.put(pair.substring(0, eq), value(new JsonPrimitive(pair.substring(eq + 1))));
        }
        if (!StoryService.play(player, villager, knot, values)) {
            source.sendFailure(Component.literal("Could not open '" + knot + "'. Check the knot name."));
            return 0;
        }
        return 1;
    }

    static int checkpoint(CommandSourceStack source, @Nullable VillagerEntityMCA villager, String name) {
        ServerPlayer player = source.getPlayer();
        StoryDefinition story = player == null || villager == null ? null : StoryService.storyOf(player, villager);
        JsonObject checkpoint = story == null ? null : story.checkpoints().get(name);
        if (checkpoint == null) {
            source.sendFailure(Component.literal(villager == null ? "No villager found." : "No checkpoint '" + name + "' in their story."));
            return 0;
        }
        counters(player, player.getUUID(), checkpoint, "counters");
        counters(player, villager.getUUID(), checkpoint, "villager_counters");
        for (JsonElement act : GsonHelper.getAsJsonArray(checkpoint, "act", new com.google.gson.JsonArray())) {
            Action action = story.actions().get(act.getAsString());
            if (action == null) source.sendFailure(Component.literal("No action '" + act.getAsString() + "' in the story."));
            else action.run(new ActionContext(villager, player));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> var : GsonHelper.getAsJsonObject(checkpoint, "vars", new JsonObject()).entrySet()) {
            values.put(var.getKey(), value(var.getValue()));
        }
        if (checkpoint.has("note")) source.sendSuccess(() -> Component.literal(GsonHelper.getAsString(checkpoint, "note")), false);
        String knot = GsonHelper.getAsString(checkpoint, "knot", "greet");
        if (!StoryService.play(player, villager, knot, values)) {
            source.sendFailure(Component.literal("Could not open '" + knot + "'."));
            return 0;
        }
        return 1;
    }

    /** Raises each counter to at least the given amount. */
    private static void counters(ServerPlayer player, java.util.UUID subject, JsonObject checkpoint, String field) {
        for (Map.Entry<String, JsonElement> counter : GsonHelper.getAsJsonObject(checkpoint, field, new JsonObject()).entrySet()) {
            int want = counter.getValue().getAsInt();
            int have = Chronicles.count(player.server, subject, counter.getKey());
            if (want > have) Chronicles.addCounter(player.server, subject, counter.getKey(), want - have);
        }
    }

    private static @Nullable Object value(JsonElement raw) {
        if (!raw.isJsonPrimitive()) return null;
        JsonPrimitive p = raw.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        String text = p.getAsString();
        if (text.equals("true") || text.equals("false")) return Boolean.parseBoolean(text);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return text;
        }
    }
}
