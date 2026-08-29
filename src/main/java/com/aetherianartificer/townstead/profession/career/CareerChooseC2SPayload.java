package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player asks to equip a skill choice from the Career screen. The server validates that
 * the skill belongs to one of the player's acquired careers before learning/equipping, then
 * answers with a fresh {@link CareerTreeS2CPayload}; the client sends an intention, never an
 * outcome.
 */
//? if neoforge {
public record CareerChooseC2SPayload(String skillId) implements CustomPacketPayload {
//?} else {
/*public record CareerChooseC2SPayload(String skillId) {
*///?}

    public void write(FriendlyByteBuf buf) { buf.writeUtf(skillId); }

    public static CareerChooseC2SPayload read(FriendlyByteBuf buf) {
        return new CareerChooseC2SPayload(buf.readUtf());
    }

    //? if neoforge {
    public static final Type<CareerChooseC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_choose"));
    public static final StreamCodec<FriendlyByteBuf, CareerChooseC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerChooseC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_choose");
    *///?}
}
