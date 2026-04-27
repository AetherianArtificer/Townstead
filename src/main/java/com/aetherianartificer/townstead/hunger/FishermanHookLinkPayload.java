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
 * Server→client link between a FishingHook and the villager that "owns" it for
 * rendering purposes. The hook's actual owner is a FakePlayer that clients never
 * see, so vanilla's FishingHookRenderer skips the line. Clients stash this link
 * and FishermanLineRenderer draws a custom line from the villager's rod hand
 * to the hook.
 *
 * A villagerEntityId of -1 means "forget the link for this hook".
 */
//? if neoforge {
public record FishermanHookLinkPayload(int hookEntityId, int villagerEntityId, double x, double y, double z) implements CustomPacketPayload {
//?} else {
/*public record FishermanHookLinkPayload(int hookEntityId, int villagerEntityId, double x, double y, double z) {
*///?}

    //? if neoforge {
    public static final Type<FishermanHookLinkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fisherman_hook_link"));

    public static final StreamCodec<FriendlyByteBuf, FishermanHookLinkPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FishermanHookLinkPayload::hookEntityId,
                    ByteBufCodecs.VAR_INT, FishermanHookLinkPayload::villagerEntityId,
                    ByteBufCodecs.DOUBLE, FishermanHookLinkPayload::x,
                    ByteBufCodecs.DOUBLE, FishermanHookLinkPayload::y,
                    ByteBufCodecs.DOUBLE, FishermanHookLinkPayload::z,
                    FishermanHookLinkPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fisherman_hook_link");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "fisherman_hook_link");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(hookEntityId);
        buf.writeVarInt(villagerEntityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static FishermanHookLinkPayload read(FriendlyByteBuf buf) {
        return new FishermanHookLinkPayload(buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    *///?}
}
