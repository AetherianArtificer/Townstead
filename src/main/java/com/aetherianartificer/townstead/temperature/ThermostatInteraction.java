package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.block.RoomThermostatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/** Server-authoritative settings and sensor snapshots. Does not load chunks or trust client readings. */
public final class ThermostatInteraction {
    private ThermostatInteraction() {}
    public static void handle(ThermostatRequestPayload request,ServerPlayer player) {
        var level=player.serverLevel(); var pos=request.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return;
        var state=level.getBlockState(pos);
        if (!(state.getBlock() instanceof RoomThermostatBlock thermostat)) return;
        if (request.mode()!=-1) {
            if (!ThermostatSettingsPolicy.valid(request.mode(),request.target()) || !player.mayBuild() || !level.mayInteract(player,pos)) return;
            // Preserve hysteresis when merely changing a target; reset it when changing operating mode.
            var mode=RoomThermostatBlock.Mode.values()[request.mode()];
            if (mode!=state.getValue(RoomThermostatBlock.MODE)) state=state.setValue(RoomThermostatBlock.POWERED,false);
            state=state.setValue(RoomThermostatBlock.MODE,mode).setValue(RoomThermostatBlock.TARGET,request.target());
            level.setBlock(pos,state,Block.UPDATE_ALL);
            thermostat.updateDemand(state,level,pos);
        }
        send(player,pos,false);
    }
    public static void send(ServerPlayer player,BlockPos pos,boolean open) {
        var level=player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) return;
        var state=level.getBlockState(pos);
        if (!(state.getBlock() instanceof RoomThermostatBlock)) return;
        var air=RoomHeat.controlAirAt(level,pos.relative(state.getValue(RoomThermostatBlock.FACING)));
        var payload=new ThermostatSnapshotPayload(pos,state.getValue(RoomThermostatBlock.MODE).ordinal(),
                state.getValue(RoomThermostatBlock.TARGET),(float)air.orElse(Double.NaN),state.getValue(RoomThermostatBlock.POWERED),
                player.mayBuild() && level.mayInteract(player,pos),open,
                com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.get()
                        ==com.aetherianartificer.townstead.compat.temperature.ColdSweatTemperatureBridge.INSTANCE);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player,payload);
        *///?}
    }
}
