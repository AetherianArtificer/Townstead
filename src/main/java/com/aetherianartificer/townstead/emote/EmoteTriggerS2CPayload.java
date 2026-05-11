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
 * Server → client: play the named emote on the entity identified by {@code
 * entityId} (network entity id, not UUID — these payloads are sent to trackers).
 *
 * <p>{@code loopOverride} = -1 means "honor the parsed emote's own loopType";
 * otherwise the byte's value is the ordinal of {@link
 * com.aetherianartificer.townstead.client.animation.emote.ParsedEmote.LoopType}.</p>
 */
//? if neoforge {
public record EmoteTriggerS2CPayload(int entityId, String emoteId, byte loopOverride, float speed)
        implements CustomPacketPayload {
//?} else {
/*public record EmoteTriggerS2CPayload(int entityId, String emoteId, byte loopOverride, float speed) {
*///?}

    //? if neoforge {
    public static final Type<EmoteTriggerS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "emote_trigger_s2c"));

    public static final StreamCodec<FriendlyByteBuf, EmoteTriggerS2CPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EmoteTriggerS2CPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, EmoteTriggerS2CPayload::emoteId,
                    ByteBufCodecs.BYTE, EmoteTriggerS2CPayload::loopOverride,
                    ByteBufCodecs.FLOAT, EmoteTriggerS2CPayload::speed,
                    EmoteTriggerS2CPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "emote_trigger_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "emote_trigger_s2c");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(emoteId);
        buf.writeByte(loopOverride);
        buf.writeFloat(speed);
    }

    public static EmoteTriggerS2CPayload read(FriendlyByteBuf buf) {
        return new EmoteTriggerS2CPayload(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readByte(),
                buf.readFloat());
    }
    *///?}
}
