package com.aetherianartificer.townstead.block.haze;

//? if neoforge {
import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: every loaded haze kind, in block-state index order. The client keeps the
 * order exactly, because the index is what a haze block's state carries. The pheno {@code inside}
 * behaviour stays on the server.
 */
//? if neoforge {
public record HazeKindsSyncPayload(List<HazeKind> kinds) implements CustomPacketPayload {
//?} else {
/*public record HazeKindsSyncPayload(List<HazeKind> kinds) {
*///?}

    public HazeKindsSyncPayload {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
    }

    public static HazeKindsSyncPayload snapshot() {
        return new HazeKindsSyncPayload(HazeKinds.server().all());
    }

    public void write(FriendlyByteBuf buf) {
        int size = Math.min(HazeBlock.MAX_KIND + 1, kinds.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            HazeKind kind = kinds.get(i);
            buf.writeResourceLocation(kind.id());
            buf.writeVarInt(kind.shape().ordinal());
            buf.writeInt(kind.color());
            buf.writeVarInt(kind.duration());
            buf.writeBoolean(kind.conceals());
            buf.writeFloat(kind.fogDistance());
            buf.writeUtf(kind.particle() == null ? "" : kind.particle().toString());
            buf.writeUtf(kind.texture() == null ? "" : kind.texture().toString());
        }
    }

    public static HazeKindsSyncPayload read(FriendlyByteBuf buf) {
        int size = Math.min(HazeBlock.MAX_KIND + 1, buf.readVarInt());
        HazeKind.Shape[] shapes = HazeKind.Shape.values();
        List<HazeKind> kinds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            HazeKind.Shape shape = shapes[Math.floorMod(buf.readVarInt(), shapes.length)];
            int color = buf.readInt();
            int duration = buf.readVarInt();
            boolean conceals = buf.readBoolean();
            float fog = buf.readFloat();
            String particle = buf.readUtf();
            String texture = buf.readUtf();
            kinds.add(new HazeKind(id, shape, color, duration, conceals, fog,
                    particle.isEmpty() ? null : ResourceLocation.tryParse(particle),
                    texture.isEmpty() ? null : ResourceLocation.tryParse(texture),
                    null, null, 10));
        }
        return new HazeKindsSyncPayload(kinds);
    }

    //? if neoforge {
    public static final Type<HazeKindsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "haze_kinds_sync"));
    public static final StreamCodec<FriendlyByteBuf, HazeKindsSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), HazeKindsSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
