package com.aetherianartificer.townstead.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//? if >=1.21 {
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.RegisteredRoomUpdate;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}

/**
 * Exposes MCA's package-private room analysis (7.7.37+ moved it from VillageManager into
 * RoomWorkflow). Pseudo: the class does not exist on the pre-floor 1.20.1 line.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.server.world.data.RoomWorkflow", remap = false)
public interface RoomWorkflowInvoker {
    //? if >=1.21 {
    @Invoker(value = "analyzeRoom", remap = false)
    BuildingScanResult townstead$analyzeRoom(BlockPos source);

    @Invoker(value = "analyzeBuildingAddition", remap = false)
    BuildingScanResult townstead$analyzeBuildingAddition(BlockPos source);

    @Invoker(value = "analyzeRegisteredRoomUpdate", remap = false)
    RegisteredRoomUpdate townstead$analyzeRegisteredRoomUpdate(Village village, int roomId, BlockPos source);
    //?}
}
