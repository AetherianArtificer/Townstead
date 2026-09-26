package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Client to server: replace the world's Switchboard values. {@code finishSetup} closes the new-world pass. */
//? if neoforge {
public record SwitchboardSaveC2SPayload(String world, boolean finishSetup) implements CustomPacketPayload {
//?} else {
/*public record SwitchboardSaveC2SPayload(String world, boolean finishSetup) {
*///?}

    private static final int MAX_LENGTH = 1 << 20;

    //? if neoforge {
    public static final Type<SwitchboardSaveC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "switchboard_save_c2s"));

    public static final StreamCodec<FriendlyByteBuf, SwitchboardSaveC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SwitchboardSaveC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public static SwitchboardSaveC2SPayload of(Map<String, JsonElement> world, boolean finishSetup) {
        return new SwitchboardSaveC2SPayload(SwitchboardOpenS2CPayload.object(world).toString(), finishSetup);
    }

    public Map<String, JsonElement> worldValues() {
        return SwitchboardOpenS2CPayload.map(world);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(world, MAX_LENGTH);
        buf.writeBoolean(finishSetup);
    }

    public static SwitchboardSaveC2SPayload read(FriendlyByteBuf buf) {
        return new SwitchboardSaveC2SPayload(buf.readUtf(MAX_LENGTH), buf.readBoolean());
    }
}
