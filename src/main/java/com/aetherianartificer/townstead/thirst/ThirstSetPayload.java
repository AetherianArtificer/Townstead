package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record ThirstSetPayload(int entityId, int thirst) implements CustomPacketPayload {
//?} else {
/*public record ThirstSetPayload(int entityId, int thirst) {
*///?}

    //? if neoforge {
    public static final Type<ThirstSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_set"));

    public static final StreamCodec<FriendlyByteBuf, ThirstSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ThirstSetPayload::entityId,
                    ByteBufCodecs.VAR_INT, ThirstSetPayload::thirst,
                    ThirstSetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "thirst_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(thirst);
    }

    public static ThirstSetPayload read(FriendlyByteBuf buf) {
        return new ThirstSetPayload(buf.readVarInt(), buf.readVarInt());
    }
    *///?}
}
