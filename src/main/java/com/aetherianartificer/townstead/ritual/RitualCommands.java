package com.aetherianartificer.townstead.ritual;

import com.aetherianartificer.townstead.commands.CommandTargets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Test commands until rituals arise in play: {@code /townstead ritual start <ritual>} holds it for the
 * villager you look at; {@code /townstead ritual join <ritual>} holds it for you.
 */
public final class RitualCommands {
    private static final SuggestionProvider<CommandSourceStack> RITUALS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(Rituals.ids(), builder);

    private RitualCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("ritual")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").then(Commands.argument("ritual", ResourceLocationArgument.id()).suggests(RITUALS)
                        .executes(context -> start(context.getSource(), ResourceLocationArgument.getId(context, "ritual"), false))))
                .then(Commands.literal("join").then(Commands.argument("ritual", ResourceLocationArgument.id()).suggests(RITUALS)
                        .executes(context -> start(context.getSource(), ResourceLocationArgument.getId(context, "ritual"), true))))));
    }

    private static int start(CommandSourceStack source, ResourceLocation ritual, boolean self) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        LivingEntity candidate = self ? player : CommandTargets.lookedAtOrNearest(player, null);
        if (candidate == null) {
            source.sendFailure(Component.translatable("ritual.townstead.refused.no_candidate"));
            return 0;
        }
        String refused = RitualService.start(source.getLevel(), ritual, candidate);
        if (refused != null) {
            source.sendFailure(Component.translatable(refused));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("ritual.townstead.begun", candidate.getName()), true);
        return 1;
    }
}
