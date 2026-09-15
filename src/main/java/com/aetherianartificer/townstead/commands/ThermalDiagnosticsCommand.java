package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.temperature.RoomHeat;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.aetherianartificer.townstead.block.RoomThermostatBlock;

/** Read-only thermal diagnostics and permission-gated thermostat configuration. */
public final class ThermalDiagnosticsCommand {
    private ThermalDiagnosticsCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var configure = Commands.literal("thermostat").requires(source -> source.hasPermission(2));
        var position = Commands.argument("position", BlockPosArgument.blockPos());
        for (var mode : RoomThermostatBlock.Mode.values()) {
            position.then(Commands.literal(mode.getSerializedName())
                    .then(Commands.argument("target", IntegerArgumentType.integer(5,35)).executes(context -> {
                        var source = context.getSource();
                        var pos = BlockPosArgument.getLoadedBlockPos(context,"position");
                        var state = source.getLevel().getBlockState(pos);
                        if (!(state.getBlock() instanceof RoomThermostatBlock thermostat)) {
                            source.sendFailure(Component.literal("That block is not a Room Thermostat."));
                            return 0;
                        }
                        state = state.setValue(RoomThermostatBlock.MODE,mode)
                                .setValue(RoomThermostatBlock.TARGET,IntegerArgumentType.getInteger(context,"target"))
                                .setValue(RoomThermostatBlock.POWERED,false);
                        source.getLevel().setBlock(pos,state,net.minecraft.world.level.block.Block.UPDATE_ALL);
                        thermostat.updateDemand(state,source.getLevel(),pos);
                        int target = state.getValue(RoomThermostatBlock.TARGET);
                        source.sendSuccess(() -> Component.literal("Thermostat " + mode.getSerializedName()+" at "+target+" C; hysteresis +/-1 C."),false);
                        return 1;
                    })));
        }
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("thermal").then(configure.then(position))));
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("thermal")
                .then(Commands.literal("diagnose").executes(context -> {
                    var source = context.getSource();
                    BlockPos pos = BlockPos.containing(source.getPosition());
                    String detail = RoomHeat.describe(source.getLevel(), pos).orElseGet(() -> String.format(java.util.Locale.ROOT,
                            "No bounded thermal volume resolved; native environment %.1f C; air estimate %.1f C",
                            TemperatureData.ambientCelsius(source.getLevel(), pos), TemperatureData.airCelsius(source.getLevel(), pos)));
                    source.sendSuccess(() -> Component.literal("Backend " + TemperatureBridgeResolver.get().id() + "; " + detail), false);
                    return 1;
                }))));
    }
}
