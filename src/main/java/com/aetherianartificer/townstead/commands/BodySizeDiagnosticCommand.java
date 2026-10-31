package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.rig.BodySizeDiagnostics;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** /townstead debug size [entity selector] */
public final class BodySizeDiagnosticCommand {
    private BodySizeDiagnosticCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("debug")
                .then(Commands.literal("size").requires(s -> s.hasPermission(2))
                        .executes(c -> report(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(c -> report(c.getSource(), EntityArgument.getEntity(c, "entity")))))));
    }
    private static int report(CommandSourceStack source, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Select a living entity."));
            return 0;
        }
        String report = BodySizeDiagnostics.describe(living);
        source.sendSuccess(() -> Component.literal(report), false);
        org.slf4j.LoggerFactory.getLogger("townstead/body-size").info(report);
        return 1;
    }
}
