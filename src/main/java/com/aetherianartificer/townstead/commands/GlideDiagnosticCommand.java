package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.ability.GlideAI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /townstead debug glide} — gate-by-gate flight report for villagers near the
 * executing player, so "they just stand there" separates into its causes: no
 * flight gene, no lift ability, a travel target outside the launch envelope, a
 * blocked line, or a joy-target search starved by cramped terrain.
 */
public final class GlideDiagnosticCommand {

    private static final double RANGE = 16.0;
    private static final int MAX_REPORTS = 5;

    private GlideDiagnosticCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("debug")
                .then(Commands.literal("glide").executes(c -> dump(c.getSource())))));
    }

    private static int dump(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<VillagerEntityMCA> villagers = player.level().getEntitiesOfClass(
                VillagerEntityMCA.class, player.getBoundingBox().inflate(RANGE));
        if (villagers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.townstead.glide.no_villagers", (int) RANGE), false);
            return 0;
        }
        villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < villagers.size() && i < MAX_REPORTS; i++) {
            if (i > 0) out.append('\n');
            out.append(GlideAI.diagnose(villagers.get(i)));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
