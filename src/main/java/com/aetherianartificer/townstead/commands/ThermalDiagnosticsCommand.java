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
    private static void describeVillager(CommandSourceStack source, net.conczin.mca.entity.VillagerEntityMCA villager) {
        var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs();
        var exposure = com.aetherianartificer.townstead.temperature.ThermalExposure.at(source.getLevel(), villager,
                villager.blockPosition(), com.aetherianartificer.townstead.temperature.ThermalExposure.activity(villager));
        float body = TemperatureData.celsius(needs.bodyTempTenths());
        var forecast = exposure.forecast(body, 120);
        var work = com.aetherianartificer.townstead.clothing.dress.ThermalDressing.exposure(source.getLevel(), villager);
        String text = String.format(java.util.Locale.ROOT,
                "%s [%s]: enabled=%s; core %.1f C -> %.1f C (120s %.1f C); neutral %.1f +/-%.1f; air %.1f C; felt %.1f C; wet %.0f%%; protection %s; owner=%s; reason=%s; work target %.1f C",
                villager.getName().getString(), TemperatureBridgeResolver.get().id(),
                com.aetherianartificer.townstead.temperature.ThermalExposure.enabled(villager),
                body, exposure.target(), forecast.body(), exposure.profile().neutral(), exposure.profile().band(),
                TemperatureData.airCelsius(source.getLevel(), villager.blockPosition()), exposure.ambient(), exposure.wetness() * 100,
                exposure.protection(), com.aetherianartificer.townstead.temperature.ThermalCare.owner(villager),
                needs.reliefDebug(), work.target());
        source.sendSuccess(() -> Component.literal(text), false);
        var worn = com.aetherianartificer.townstead.clothing.ClothingSources.worn(villager);
        source.sendSuccess(() -> Component.literal("Worn: " + worn.stream().map(p -> p.entry() == null ? p.source()
                : p.entry().id().toString()).collect(java.util.stream.Collectors.joining(", "))), false);
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("thermal")
                .then(Commands.literal("villager").then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.entity())
                        .executes(context -> {
                            var entity = net.minecraft.commands.arguments.EntityArgument.getEntity(context, "target");
                            if (!(entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager)) {
                                context.getSource().sendFailure(Component.literal("Select a Townstead/MCA villager."));
                                return 0;
                            }
                            describeVillager(context.getSource(), villager);
                            return 1;
                        })))
                .then(Commands.literal("nearby").executes(context -> {
                    var source = context.getSource();
                    var villagers = source.getLevel().getEntitiesOfClass(net.conczin.mca.entity.VillagerEntityMCA.class,
                            new net.minecraft.world.phys.AABB(BlockPos.containing(source.getPosition())).inflate(32));
                    source.sendSuccess(() -> Component.literal("Thermal survey: " + villagers.size() + " loaded villagers within 32 blocks."), false);
                    villagers.stream().limit(16).forEach(villager -> describeVillager(source, villager));
                    return villagers.size();
                }))));
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
