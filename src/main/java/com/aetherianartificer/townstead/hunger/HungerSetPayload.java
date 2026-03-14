package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server packet: request setting a villager's hunger to a specific value (debug editor).
 */
//? if neoforge {
public record HungerSetPayload(int entityId, int hunger) implements CustomPacketPayload {
//?} else {
/*public record HungerSetPayload(int entityId, int hunger) {
*///?}

    //? if neoforge {
    public static final Type<HungerSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_set"));

    public static final StreamCodec<FriendlyByteBuf, HungerSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HungerSetPayload::entityId,
                    ByteBufCodecs.VAR_INT, HungerSetPayload::hunger,
                    HungerSetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "hunger_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "hunger_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(hunger);
    }

    public static HungerSetPayload read(FriendlyByteBuf buf) {
        return new HungerSetPayload(buf.readVarInt(), buf.readVarInt());
    }
    *///?}
}
