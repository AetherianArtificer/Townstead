package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: ask the server to broadcast an emote trigger on the target
 * entity (or self if {@code targetEntityId} is the sender's own entity id).
 *
 * <p>Server validates the target type (player or {@code VillagerEntityMCA}) before
 * broadcasting an {@link EmoteTriggerS2CPayload} to the entity's trackers.</p>
 */
//? if neoforge {
public record EmoteTriggerC2SPayload(int targetEntityId, String emoteId, byte loopOverride, float speed)
        implements CustomPacketPayload {
//?} else {
/*public record EmoteTriggerC2SPayload(int targetEntityId, String emoteId, byte loopOverride, float speed) {
*///?}

    //? if neoforge {
    public static final Type<EmoteTriggerC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "emote_trigger_c2s"));

    public static final StreamCodec<FriendlyByteBuf, EmoteTriggerC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmoteTriggerC2SPayload::targetEntityId,
                    ByteBufCodecs.STRING_UTF8, EmoteTriggerC2SPayload::emoteId,
                    ByteBufCodecs.BYTE, EmoteTriggerC2SPayload::loopOverride,
                    ByteBufCodecs.FLOAT, EmoteTriggerC2SPayload::speed,
                    EmoteTriggerC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "emote_trigger_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "emote_trigger_c2s");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
        buf.writeUtf(emoteId);
        buf.writeByte(loopOverride);
        buf.writeFloat(speed);
    }

    public static EmoteTriggerC2SPayload read(FriendlyByteBuf buf) {
        return new EmoteTriggerC2SPayload(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readByte(),
                buf.readFloat());
    }
    *///?}
}
