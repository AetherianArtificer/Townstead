package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player pressed the Archives stamp onto their record to register a skill.
 *
 * <p>Carries only WHERE the stamp landed. The server decides whether the press is legal and what it
 * costs, exactly as it did for the button this replaces: a client that lies about the position gets
 * a badly placed mark on its own page, and a client that lies about anything else gets refused.</p>
 */
//? if neoforge {
public record CareerStampC2SPayload(String skillId, int x, int y,
                                    float rotation) implements CustomPacketPayload {
//?} else {
/*public record CareerStampC2SPayload(String skillId, int x, int y, float rotation) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeFloat(rotation);
    }

    public static CareerStampC2SPayload read(FriendlyByteBuf buf) {
        return new CareerStampC2SPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat());
    }

    //? if neoforge {
    public static final Type<CareerStampC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_stamp"));
    public static final StreamCodec<FriendlyByteBuf, CareerStampC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerStampC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_stamp");
    *///?}
}
