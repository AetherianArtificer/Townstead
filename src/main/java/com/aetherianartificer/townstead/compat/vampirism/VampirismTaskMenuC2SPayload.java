package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client to server, from the Quest Ledger's Open: opens Vampirism's own menu, where tasks are handed in. */
//? if neoforge {
public record VampirismTaskMenuC2SPayload() implements CustomPacketPayload {
//?} else {
/*public record VampirismTaskMenuC2SPayload() {
*///?}

    //? if neoforge {
    public static final Type<VampirismTaskMenuC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "vampirism_task_menu_c2s"));
    public static final StreamCodec<FriendlyByteBuf, VampirismTaskMenuC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VampirismTaskMenuC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {}

    public static VampirismTaskMenuC2SPayload read(FriendlyByteBuf buf) {
        return new VampirismTaskMenuC2SPayload();
    }

    public static void handle(net.minecraft.server.level.ServerPlayer sp, VampirismTaskMenuC2SPayload payload) {
        VampirismTasks.openMenu(sp);
    }
}
