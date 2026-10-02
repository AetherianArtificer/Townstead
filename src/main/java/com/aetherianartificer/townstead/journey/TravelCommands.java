package com.aetherianartificer.townstead.journey;

import com.aetherianartificer.townstead.commands.CommandTargets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands: {@code /townstead travel start [leave_pets]} has the villager you look at travel
 * with you; {@code /townstead travel end} ends it.
 */
public final class TravelCommands {
    private TravelCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var start = Commands.literal("start")
                .executes(c -> start(c.getSource(), false))
                .then(Commands.argument("leave_pets", BoolArgumentType.bool())
                        .executes(c -> start(c.getSource(), BoolArgumentType.getBool(c, "leave_pets"))));
        var end = Commands.literal("end").executes(c -> end(c.getSource()));
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("travel")
                .requires(source -> source.hasPermission(2))
                .then(start).then(end)));
    }

    private static int start(CommandSourceStack source, boolean leavePets) {
        ServerPlayer player = source.getPlayer();
        VillagerEntityMCA villager = player == null ? null : CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null) {
            source.sendFailure(Component.literal("Look at a villager first."));
            return 0;
        }
        Companions.start(villager, player, leavePets);
        source.sendSuccess(() -> Component.literal(villager.getName().getString() + " is travelling with you."), true);
        return 1;
    }

    private static int end(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        VillagerEntityMCA villager = player == null ? null : CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null || Companions.leader(villager) == null) {
            source.sendFailure(Component.literal("Look at a villager who is travelling with someone."));
            return 0;
        }
        Companions.end(villager);
        source.sendSuccess(() -> Component.literal(villager.getName().getString() + " stopped travelling."), true);
        return 1;
    }
}
