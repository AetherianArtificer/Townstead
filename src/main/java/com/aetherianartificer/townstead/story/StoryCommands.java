package com.aetherianartificer.townstead.story;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * {@code /townstead story [list | errors [id] | reset [id]]}. Operator only. {@code reset} clears
 * your own saved state, so a story can be played again from its greeting.
 */
public final class StoryCommands {
    private StoryCommands() {}

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(
                    java.util.stream.Stream.concat(Stories.all().keySet().stream(), Stories.problems().keySet().stream())
                            .distinct(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("story")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("errors")
                        .executes(c -> errors(c.getSource(), null))
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> errors(c.getSource(), ResourceLocationArgument.getId(c, "id")))))
                .then(Commands.literal("reset")
                        .executes(c -> reset(c.getSource(), null))
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> reset(c.getSource(), ResourceLocationArgument.getId(c, "id")))))));
    }

    private static int list(CommandSourceStack source) {
        if (Stories.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No stories are loaded."), false);
        }
        for (StoryDefinition story : Stories.all().values()) {
            int problems = Stories.problems().getOrDefault(story.id(), List.of()).size();
            String line = story.id() + ": " + story.quests().size() + " quests"
                    + (problems > 0 ? ", " + problems + " warnings" : "");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        for (ResourceLocation failed : Stories.problems().keySet()) {
            if (Stories.all().containsKey(failed)) continue;
            source.sendSuccess(() -> Component.literal(failed + ": not loaded, see /townstead story errors " + failed)
                    .withStyle(ChatFormatting.RED), false);
        }
        return Stories.all().size();
    }

    private static int errors(CommandSourceStack source, ResourceLocation id) {
        int shown = 0;
        for (Map.Entry<ResourceLocation, List<String>> entry : Stories.problems().entrySet()) {
            if (id != null && !id.equals(entry.getKey())) continue;
            for (String problem : entry.getValue()) {
                ChatFormatting color = problem.startsWith("error:") ? ChatFormatting.RED : ChatFormatting.YELLOW;
                source.sendSuccess(() -> Component.literal(entry.getKey() + " " + problem).withStyle(color), false);
                shown++;
            }
        }
        if (shown == 0) source.sendSuccess(() -> Component.literal("No problems."), false);
        return shown;
    }

    private static int reset(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean removed = StoryService.reset(player, id);
        String what = id == null ? "every story" : id.toString();
        source.sendSuccess(() -> Component.literal(removed ? "Reset " + what + "." : "Nothing to reset for " + what + "."), false);
        return removed ? 1 : 0;
    }
}
