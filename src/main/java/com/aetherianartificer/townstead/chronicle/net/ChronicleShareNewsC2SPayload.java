package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server: deliver the player's best unheard story to a villager
 * through the {@code townstead:player_word} channel, earning news points.
 */
//? if neoforge {
public record ChronicleShareNewsC2SPayload(UUID villager) implements CustomPacketPayload {
//?} else {
/*public record ChronicleShareNewsC2SPayload(UUID villager) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villager);
    }

    public static ChronicleShareNewsC2SPayload read(FriendlyByteBuf buf) {
        return new ChronicleShareNewsC2SPayload(buf.readUUID());
    }

    //? if neoforge {
    public static final Type<ChronicleShareNewsC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "chronicle_share_news"));

    public static final StreamCodec<FriendlyByteBuf, ChronicleShareNewsC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ChronicleShareNewsC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "chronicle_share_news");
    *///?}
}
