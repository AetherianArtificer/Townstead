package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ThirstSetPayload(int entityId, int thirst) implements CustomPacketPayload {

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
}
