package com.aetherianartificer.townstead.inventory;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the render flag of one of a villager's Curios slots, sent to the toggling player and
 * to everyone tracking the villager so the curio appears or vanishes on them for all viewers.
 */
//? if neoforge {
public record VillagerCurioRenderS2CPayload(int entityId, String slotId, int index, boolean render)
        implements CustomPacketPayload {
//?} else {
/*public record VillagerCurioRenderS2CPayload(int entityId, String slotId, int index, boolean render) {
*///?}

    //? if neoforge {
    public static final Type<VillagerCurioRenderS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_curio_render_s2c"));

    public static final StreamCodec<FriendlyByteBuf, VillagerCurioRenderS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VillagerCurioRenderS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_curio_render_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_curio_render_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(slotId);
        buf.writeVarInt(index);
        buf.writeBoolean(render);
    }

    public static VillagerCurioRenderS2CPayload read(FriendlyByteBuf buf) {
        return new VillagerCurioRenderS2CPayload(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readBoolean());
    }
}
