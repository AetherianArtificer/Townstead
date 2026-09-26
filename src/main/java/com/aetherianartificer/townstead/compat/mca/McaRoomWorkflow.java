package com.aetherianartificer.townstead.compat.mca;

//? if >=1.21 {
import com.aetherianartificer.townstead.mixin.accessor.RoomWorkflowInvoker;
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.RegisteredRoomUpdate;
import net.conczin.mca.server.world.data.RoomWorkflow;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
//?}

/**
 * Analyze-only access to MCA's room workflow. Townstead's automatic discovery and diagnostics
 * inspect a scan before they decide whether to commit it; MCA's public workflow entry points
 * analyze and commit in one call.
 */
public final class McaRoomWorkflow {
    private McaRoomWorkflow() {}

    //? if >=1.21 {
    public static RoomWorkflow workflow(ServerLevel level) {
        return new RoomWorkflow(VillageManager.get(level), level);
    }

    private static RoomWorkflowInvoker invoker(ServerLevel level) {
        return (RoomWorkflowInvoker) (Object) workflow(level);
    }

    public static BuildingScanResult analyzeRoom(ServerLevel level, BlockPos source) {
        return invoker(level).townstead$analyzeRoom(source);
    }

    public static BuildingScanResult analyzeBuildingAddition(ServerLevel level, BlockPos source) {
        return invoker(level).townstead$analyzeBuildingAddition(source);
    }

    public static RegisteredRoomUpdate analyzeRegisteredRoomUpdate(
            ServerLevel level, Village village, int roomId, BlockPos source) {
        return invoker(level).townstead$analyzeRegisteredRoomUpdate(village, roomId, source);
    }
    //?}
}
