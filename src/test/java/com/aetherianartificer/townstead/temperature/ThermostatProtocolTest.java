package com.aetherianartificer.townstead.temperature;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermostatProtocolTest {
    @Test void requestRoundTripPreservesPositionAndDoesNotTurnReadIntoMutation() {
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var read=new ThermostatRequestPayload(new BlockPos(-301,72,190),-1,0);
            read.write(buf);
            assertEquals(read,ThermostatRequestPayload.read(buf));
            assertFalse(ThermostatSettingsPolicy.valid(read.mode(),read.target()));
            var edit=new ThermostatRequestPayload(read.pos(),2,5);
            edit.write(buf);
            assertEquals(edit,ThermostatRequestPayload.read(buf));
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void snapshotPreservesUnavailableSensorAndAccessFlags() {
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var snapshot=new ThermostatSnapshotPayload(new BlockPos(8,64,-5),1,20,Float.NaN,false,false,true,true);
            snapshot.write(buf);
            assertEquals(snapshot,ThermostatSnapshotPayload.read(buf));
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void malformedSettingsCannotReachBlockStateProperties() {
        for(int mode=0;mode<3;mode++) {
            assertTrue(ThermostatSettingsPolicy.valid(mode,5));
            assertTrue(ThermostatSettingsPolicy.valid(mode,35));
            for(int target:new int[]{Integer.MIN_VALUE,4,36,Integer.MAX_VALUE})
                assertFalse(ThermostatSettingsPolicy.valid(mode,target));
        }
        for(int mode:new int[]{Integer.MIN_VALUE,-1,3,Integer.MAX_VALUE})
            assertFalse(ThermostatSettingsPolicy.valid(mode,20));
    }
}
