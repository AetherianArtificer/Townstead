package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.devlink.BenchLink;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /townstead bench open|close|status}: the scriptable twin of the Bench Link item's
 * server switch.
 */
public final class BenchLinkCommand {

    private BenchLinkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("bench")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("open").executes(c -> open(c.getSource())))
                .then(Commands.literal("close").executes(c -> close(c.getSource())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))));
    }

    private static int open(CommandSourceStack source) {
        if (BenchLink.running()) return status(source);
        if (!BenchLink.start(source.getServer())) {
            source.sendFailure(Component.translatable("townstead.bench_link.start_failed"));
            return 0;
        }
        return status(source);
    }

    private static int close(CommandSourceStack source) {
        boolean stopped = BenchLink.stop();
        source.sendSuccess(() -> Component.translatable(stopped
                ? "townstead.bench_link.stopped" : "townstead.bench_link.not_running"), false);
        return stopped ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        if (!BenchLink.running()) {
            source.sendSuccess(() -> Component.translatable("townstead.bench_link.not_running"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("townstead.bench_link.listening",
                BenchLink.port(), BenchLink.sessions()), false);
        return 1;
    }
}
