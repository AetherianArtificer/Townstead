package com.aetherianartificer.townstead.animation;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record VillagerResponseAnimationPayload(
        int entityId,
        String animationId,
        long startedAtGameTime,
        int durationTicks
) implements CustomPacketPayload {
//?} else {
/*public record VillagerResponseAnimationPayload(
        int entityId,
        String animationId,
        long startedAtGameTime,
        int durationTicks
) {
*///?}
    //? if neoforge {
    public static final Type<VillagerResponseAnimationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_response_animation"));

    public static final StreamCodec<FriendlyByteBuf, VillagerResponseAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeUtf(payload.animationId());
                buf.writeVarLong(payload.startedAtGameTime());
                buf.writeVarInt(payload.durationTicks());
            },
            buf -> new VillagerResponseAnimationPayload(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarLong(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_response_animation");
    //?} else {
    /*public static final ResourceLocation ID =
            new ResourceLocation(Townstead.MOD_ID, "villager_response_animation");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(animationId);
        buf.writeVarLong(startedAtGameTime);
        buf.writeVarInt(durationTicks);
    }

    public static VillagerResponseAnimationPayload read(FriendlyByteBuf buf) {
        return new VillagerResponseAnimationPayload(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarLong(),
                buf.readVarInt()
        );
    }
    *///?}
}
