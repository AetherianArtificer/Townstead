package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.storage.PlaqueResidents;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** {@code /townstead homes}: the homes whose Ownership Plaque lists the player. */
public final class HomesCommand {
    private HomesCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("homes")
                .executes(c -> list(c.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        List<PlaqueResidents.Home> homes = PlaqueResidents.homes(source.getServer(), player.getUUID());
        if (homes.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("townstead.homes.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("townstead.homes.header"), false);
        for (PlaqueResidents.Home home : homes) {
            source.sendSuccess(() -> Component.literal("  ").append(Component.translatable(
                    "townstead.homes.entry", home.placeName(), home.villageName())), false);
        }
        return homes.size();
    }
}
