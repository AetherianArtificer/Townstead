package com.aetherianartificer.townstead.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /townstead root_icons <id> [rolls|calibrate]}: picker icons for a pack's roots ({@code <id>} a
 * namespace) or one root ({@code <id>} a root id), rendered by the running player's client and saved to
 * its {@code townstead-dump/icons} folder. See {@code RootIcons}.
 */
public final class RootIconsCommand {
    private RootIconsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("root_icons")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(c -> send(c, 1))
                        .then(Commands.literal("calibrate").executes(c -> send(c, 0)))
                        .then(Commands.argument("rolls", IntegerArgumentType.integer(1, 8))
                                .executes(c -> send(c, IntegerArgumentType.getInteger(c, "rolls")))))));
    }

    /**
     * The {@code id} as typed: a namespace alone ({@code runic_roots}) parses as
     * {@code minecraft:runic_roots}, so without a colon in the input it stands for the namespace.
     */
    private static String id(CommandContext<CommandSourceStack> c) {
        ResourceLocation id = ResourceLocationArgument.getId(c, "id");
        return c.getInput().contains(id.getNamespace() + ":") ? id.toString() : id.getPath();
    }

    private static int send(CommandContext<CommandSourceStack> c, int rolls) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.aetherianartificer.townstead.root.RootIconsS2CPayload(id(c), rolls));
        c.getSource().sendSuccess(() -> Component.literal("Rendering root icons…"), false);
        return 1;
        //?} else {
        /*c.getSource().sendFailure(Component.literal("Root icons render on 1.21 only for now."));
        return 0;
        *///?}
    }
}
