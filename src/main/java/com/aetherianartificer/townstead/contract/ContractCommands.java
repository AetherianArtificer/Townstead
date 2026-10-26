package com.aetherianartificer.townstead.contract;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /townstead contract list} shows the loaded contracts by pool; {@code /townstead contract
 * validate} names every contract file that was rejected at the last reload, and why.
 */
public final class ContractCommands {
    private ContractCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("contract")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("validate").executes(c -> validate(c.getSource())))));
    }

    private static int list(CommandSourceStack source) {
        Map<String, Integer> byPool = new TreeMap<>();
        Contracts.loaded().values().forEach(def -> byPool.merge(def.pool().toString(), 1, Integer::sum));
        if (byPool.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No contracts loaded."), false);
            return 0;
        }
        byPool.forEach((pool, n) -> source.sendSuccess(() -> Component.literal(pool + ": " + n + " contract(s)"), false));
        return byPool.size();
    }

    private static int validate(CommandSourceStack source) {
        Map<String, String> errors = new TreeMap<>();
        Contracts.rejected().forEach((id, error) -> errors.put(id.toString(), error));
        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("All " + Contracts.loaded().size() + " contracts are valid."), false);
            return 1;
        }
        errors.forEach((id, error) -> source.sendFailure(Component.literal(id + ": " + error)));
        return 0;
    }
}
