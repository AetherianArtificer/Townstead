package com.aetherianartificer.townstead.inventory;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: flip whether the curio in a villager's Curios slot renders on them, the toggle Curios
 * offers on the player's own slots. The server answers with {@link VillagerCurioRenderS2CPayload}.
 */
//? if neoforge {
public record VillagerCurioRenderC2SPayload(int entityId, String slotId, int index) implements CustomPacketPayload {
//?} else {
/*public record VillagerCurioRenderC2SPayload(int entityId, String slotId, int index) {
*///?}

    //? if neoforge {
    public static final Type<VillagerCurioRenderC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_curio_render_c2s"));

    public static final StreamCodec<FriendlyByteBuf, VillagerCurioRenderC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VillagerCurioRenderC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_curio_render_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_curio_render_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(slotId);
        buf.writeVarInt(index);
    }

    public static VillagerCurioRenderC2SPayload read(FriendlyByteBuf buf) {
        return new VillagerCurioRenderC2SPayload(buf.readVarInt(), buf.readUtf(), buf.readVarInt());
    }
}
