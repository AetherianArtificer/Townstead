package com.aetherianartificer.townstead.reaction.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: notify that the RPG dialogue screen has opened or
 * closed for a specific villager. The server stores this in
 * {@code DialogueStateTracker} so {@link
 * com.aetherianartificer.townstead.reaction.trigger.event.ContextResolver}
 * can emit {@code in_dialogue_with_player} and {@code dialogue_just_ended}.
 */
//? if neoforge {
public record DialogueStateC2SPayload(int villagerEntityId, boolean isOpen) implements CustomPacketPayload {
//?} else {
/*public record DialogueStateC2SPayload(int villagerEntityId, boolean isOpen) {
*///?}

    //? if neoforge {
    public static final Type<DialogueStateC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "dialogue_state_c2s"));

    public static final StreamCodec<FriendlyByteBuf, DialogueStateC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DialogueStateC2SPayload::villagerEntityId,
                    ByteBufCodecs.BOOL, DialogueStateC2SPayload::isOpen,
                    DialogueStateC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "dialogue_state_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "dialogue_state_c2s");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerEntityId);
        buf.writeBoolean(isOpen);
    }

    public static DialogueStateC2SPayload read(FriendlyByteBuf buf) {
        return new DialogueStateC2SPayload(buf.readVarInt(), buf.readBoolean());
    }
    *///?}
}
