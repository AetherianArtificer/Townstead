package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "tell me my ability slots again", sent when the wheel opens.
 *
 * <p>The view is also pushed on login, but login is the WORST moment to trust it: the player's Root
 * has to resolve before {@code Powers.active} returns anything, and a view sent before that is an
 * empty wheel that never corrects itself. Learning a skill, respeccing, or a datapack reload move
 * the answer too. Asking at the point of use makes all of those cases the same case.</p>
 *
 * <p>Carries nothing. The sender is the subject, and the server reads that from the connection.</p>
 */
//? if neoforge {
public record AbilityViewRequestC2SPayload() implements CustomPacketPayload {
//?} else {
/*public record AbilityViewRequestC2SPayload() {
*///?}

    public void write(FriendlyByteBuf buf) {
    }

    public static AbilityViewRequestC2SPayload read(FriendlyByteBuf buf) {
        return new AbilityViewRequestC2SPayload();
    }

    //? if neoforge {
    public static final Type<AbilityViewRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ability_view_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, AbilityViewRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AbilityViewRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
