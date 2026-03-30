package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//? if neoforge {
public record PlayerEmoteRelayPayload(String providerId, UUID emoteId, List<String> aliases) implements CustomPacketPayload {
//?} else {
/*public record PlayerEmoteRelayPayload(String providerId, UUID emoteId, List<String> aliases) {
*///?}

    //? if neoforge {
    public static final Type<PlayerEmoteRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "player_emote_relay"));

    public static final StreamCodec<FriendlyByteBuf, PlayerEmoteRelayPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerEmoteRelayPayload decode(FriendlyByteBuf buf) {
                    String providerId = buf.readUtf();
                    UUID emoteId = buf.readUUID();
                    int count = buf.readVarInt();
                    List<String> aliases = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        aliases.add(buf.readUtf());
                    }
                    return new PlayerEmoteRelayPayload(providerId, emoteId, List.copyOf(aliases));
                }

                @Override
                public void encode(FriendlyByteBuf buf, PlayerEmoteRelayPayload payload) {
                    buf.writeUtf(payload.providerId());
                    buf.writeUUID(payload.emoteId());
                    buf.writeVarInt(payload.aliases().size());
                    for (String alias : payload.aliases()) {
                        buf.writeUtf(alias);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public PlayerEmoteRelayPayload {
        aliases = List.copyOf(aliases);
    }

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "player_emote_relay");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "player_emote_relay");
    *///?}
}
