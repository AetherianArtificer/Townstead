package com.aetherianartificer.townstead.temperature;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerEnvironmentWireTest {
    @Test void thermometerMeasurementPreservesDimensionAndNegativeCelsius() {
        var original = new PlayerEnvironmentPayload(ResourceLocation.tryParse("minecraft:overworld"), 42, -8.5f);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buffer);
            assertEquals(original, PlayerEnvironmentPayload.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
