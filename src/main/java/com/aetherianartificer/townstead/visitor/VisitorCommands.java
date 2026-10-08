package com.aetherianartificer.townstead.visitor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Test commands: {@code /townstead visitor arrive <role> <state> [thrall <state>]} brings a visitor
 * with that state (at 1) to you; {@code /townstead visitor stay|leave <role>} decides their fate.
 */
public final class VisitorCommands {
    private VisitorCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var withThrall = Commands.literal("thrall").then(Commands.argument("thrall_state", ResourceLocationArgument.id())
                .executes(c -> arrive(c.getSource(), StringArgumentType.getString(c, "role"),
                        ResourceLocationArgument.getId(c, "state"), ResourceLocationArgument.getId(c, "thrall_state"))));
        var arrive = Commands.literal("arrive").then(Commands.argument("role", StringArgumentType.word())
                .then(Commands.argument("state", ResourceLocationArgument.id())
                        .executes(c -> arrive(c.getSource(), StringArgumentType.getString(c, "role"),
                                ResourceLocationArgument.getId(c, "state"), null))
                        .then(withThrall)));
        var stay = Commands.literal("stay").then(Commands.argument("role", StringArgumentType.word())
                .executes(c -> decide(c.getSource(), StringArgumentType.getString(c, "role"), true)));
        var leave = Commands.literal("leave").then(Commands.argument("role", StringArgumentType.word())
                .executes(c -> decide(c.getSource(), StringArgumentType.getString(c, "role"), false)));
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("visitor")
                .requires(source -> source.hasPermission(2))
                .then(arrive).then(stay).then(leave)));
    }

    private static int arrive(CommandSourceStack source, String role, ResourceLocation state, @Nullable ResourceLocation thrallState) {
        ServerPlayer player = source.getPlayer();
        Village village = player == null ? null : VillageManager.get(player.serverLevel())
                .findNearestVillage(player.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            source.sendFailure(Component.literal("Stand in a village first."));
            return 0;
        }
        Visitors.Spec thrall = thrallState == null ? null : new Visitors.Spec(role, Map.of(thrallState, 1.0), null, null, null);
        VillagerEntityMCA visitor = Visitors.arrive(player, village, new Visitors.Spec(role, Map.of(state, 1.0), null, null, thrall));
        if (visitor == null) {
            source.sendFailure(Component.literal("No visitor could arrive."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(visitor.getName().getString() + " is on their way (" + role + ")."), true);
        return 1;
    }

    private static int decide(CommandSourceStack source, String role, boolean stay) {
        ServerPlayer player = source.getPlayer();
        VillagerEntityMCA visitor = player == null ? null : Visitors.near(player, role, 64);
        if (visitor == null) {
            source.sendFailure(Component.literal("No " + role + " visitor nearby."));
            return 0;
        }
        String name = visitor.getName().getString();
        if (stay) Visitors.settle(visitor);
        else Visitors.dismiss(visitor);
        source.sendSuccess(() -> Component.literal(name + (stay ? " is staying." : " is leaving.")), true);
        return 1;
    }
}
