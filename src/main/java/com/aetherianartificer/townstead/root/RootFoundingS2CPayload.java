package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Server to client, the answer to a {@link RootFoundingC2SPayload}. For {@code STATE} and
 * {@code COMMIT} it is what the target is now: its root, its mix (empty for one root), what it is
 * called and the heritage's backstory. For {@code ROLL} it is the rolled founding: its id, its
 * choice, name and backstory, and its {@link FoundingLook}. An empty {@code rootId} means the request
 * was refused.
 */
//? if neoforge {
public record RootFoundingS2CPayload(int requestId, int op, int foundingId, String rootId, RootMix mix,
                                     String name, String backstory, @Nullable FoundingLook look)
        implements CustomPacketPayload {
//?} else {
/*public record RootFoundingS2CPayload(int requestId, int op, int foundingId, String rootId, RootMix mix,
                                     String name, String backstory, @Nullable FoundingLook look) {
*///?}

    //? if neoforge {
    public static final Type<RootFoundingS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_founding_s2c"));
    public static final StreamCodec<FriendlyByteBuf, RootFoundingS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootFoundingS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public static RootFoundingS2CPayload refused(int requestId, int op) {
        return new RootFoundingS2CPayload(requestId, op, 0, "", RootMix.NONE, "", "", null);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeVarInt(op);
        buf.writeVarInt(foundingId);
        buf.writeUtf(rootId);
        mix.write(buf);
        buf.writeUtf(name);
        buf.writeUtf(backstory);
        buf.writeBoolean(look != null);
        if (look != null) look.write(buf);
    }

    public static RootFoundingS2CPayload read(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        int op = buf.readVarInt();
        int foundingId = buf.readVarInt();
        String rootId = buf.readUtf();
        RootMix mix = RootMix.read(buf);
        String name = buf.readUtf();
        String backstory = buf.readUtf();
        FoundingLook look = buf.readBoolean() ? FoundingLook.read(buf) : null;
        return new RootFoundingS2CPayload(requestId, op, foundingId, rootId, mix, name, backstory, look);
    }
}
