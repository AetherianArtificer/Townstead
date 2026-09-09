package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.appearance.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class HairPayloadTest {
    private final List<HairColorRange> ranges = IntStream.range(0, 65)
            .mapToObj(i -> new HairColorRange(new GeneRange(0, 1), new GeneRange(0, 1), i + 1)).toList();
    private final List<HairColorChoice> colors = IntStream.range(0, 65)
            .mapToObj(i -> new HairColorChoice(i * 100, i + 1)).toList();
    private final List<HairGradient> gradients = IntStream.range(0, 65)
            .mapToObj(i -> new HairGradient(IntStream.range(0, 65).boxed().toList(), i + 1, HairGradient.Space.HSV)).toList();

    @Test void expressedPacketConsumesAllEntriesAndStops() {
        var original = new ExpressedGenesS2CPayload(9, List.of("test:gene"), true, ranges, colors, gradients);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buffer);
            buffer.writeInt(0x12345678);
            assertEquals(original, ExpressedGenesS2CPayload.read(buffer));
            assertEquals(0x12345678, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void catalogPacketPreservesFieldsAfterLargeHairLists() {
        var entry = new RootCatalogEntry("test:root", "Root", "", "", "", "", "", "",
                List.of(), List.of(), "", "", "", "", "", "", "", "mca:villager", 1f,
                Animations.DEFAULT, true, true, ranges, colors, gradients, List.of("test:adult"), null, true);
        var original = new RootCatalogSyncPayload(List.of(entry), List.of(), List.of(), List.of(), List.of());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buffer);
            buffer.writeInt(0x12345678);
            var decoded = RootCatalogSyncPayload.read(buffer).entries().get(0);
            assertEquals(ranges, decoded.hairColorRanges());
            assertEquals(colors, decoded.hairColors());
            assertEquals(gradients, decoded.hairGradients());
            assertEquals(List.of("test:adult"), decoded.stageRigs());
            assertTrue(decoded.blocked());
            assertEquals(0x12345678, buffer.readInt());
        } finally { buffer.release(); }
    }
}
