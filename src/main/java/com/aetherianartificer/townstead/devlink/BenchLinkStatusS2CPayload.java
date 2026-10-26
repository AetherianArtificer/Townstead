package com.aetherianartificer.townstead.devlink;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: Bench Link's state for the item's screen. {@code open} asks the client to open
 * the screen (the item was used); otherwise an open screen just refreshes. The token is only sent
 * to players allowed to use Bench Link, so the screen can copy it for a hand-made connection.
 */
//? if neoforge {
public record BenchLinkStatusS2CPayload(boolean open, boolean running, int port, String token, int sessions,
                                        int subjectId, String subjectName, String world)
        implements CustomPacketPayload {
//?} else {
/*public record BenchLinkStatusS2CPayload(boolean open, boolean running, int port, String token, int sessions,
                                        int subjectId, String subjectName, String world) {
*///?}

    //? if neoforge {
    public static final Type<BenchLinkStatusS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "bench_link_status"));

    public static final StreamCodec<FriendlyByteBuf, BenchLinkStatusS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), BenchLinkStatusS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeBoolean(running);
        buf.writeVarInt(port);
        buf.writeUtf(token);
        buf.writeVarInt(sessions);
        buf.writeInt(subjectId);
        buf.writeUtf(subjectName);
        buf.writeUtf(world);
    }

    public static BenchLinkStatusS2CPayload read(FriendlyByteBuf buf) {
        return new BenchLinkStatusS2CPayload(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(),
                buf.readVarInt(), buf.readInt(), buf.readUtf(), buf.readUtf());
    }
}
