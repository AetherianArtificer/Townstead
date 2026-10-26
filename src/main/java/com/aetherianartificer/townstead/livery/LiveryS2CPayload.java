package com.aetherianartificer.townstead.livery;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The livery an entity shows, or none, keyed by its network id. */
//? if neoforge {
public record LiveryS2CPayload(int entity, @Nullable LiveryView view) implements CustomPacketPayload {
//?} else {
/*public record LiveryS2CPayload(int entity, @Nullable LiveryView view) {
*///?}
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        buf.writeBoolean(view != null);
        if (view != null) view.write(buf);
    }

    public static LiveryS2CPayload read(FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        return new LiveryS2CPayload(entity, buf.readBoolean() ? LiveryView.read(buf) : null);
    }

    //? if neoforge {
    public static final Type<LiveryS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "livery"));
    public static final StreamCodec<FriendlyByteBuf, LiveryS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), LiveryS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "livery");
    *///?}
}
