package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record FatigueSetPayload(int entityId, int fatigue) implements CustomPacketPayload {
//?} else {
/*public record FatigueSetPayload(int entityId, int fatigue) {
*///?}

    //? if neoforge {
    public static final Type<FatigueSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fatigue_set"));

    public static final StreamCodec<FriendlyByteBuf, FatigueSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FatigueSetPayload::entityId,
                    ByteBufCodecs.VAR_INT, FatigueSetPayload::fatigue,
                    FatigueSetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fatigue_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "fatigue_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(fatigue);
    }

    public static FatigueSetPayload read(FriendlyByteBuf buf) {
        return new FatigueSetPayload(buf.readVarInt(), buf.readVarInt());
    }
    *///?}
}
