package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server to client: the world's effective Switchboard overrides, as a JSON object. */
//? if neoforge {
public record SwitchboardSyncS2CPayload(String json) implements CustomPacketPayload {
//?} else {
/*public record SwitchboardSyncS2CPayload(String json) {
*///?}

    private static final int MAX_LENGTH = 1 << 20;

    //? if neoforge {
    public static final Type<SwitchboardSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "switchboard_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, SwitchboardSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SwitchboardSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public static SwitchboardSyncS2CPayload of(Map<String, JsonElement> overrides) {
        JsonObject object = new JsonObject();
        overrides.forEach(object::add);
        return new SwitchboardSyncS2CPayload(object.toString());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX_LENGTH);
    }

    public static SwitchboardSyncS2CPayload read(FriendlyByteBuf buf) {
        return new SwitchboardSyncS2CPayload(buf.readUtf(MAX_LENGTH));
    }

    public void apply() {
        Map<String, JsonElement> overrides = new LinkedHashMap<>();
        try {
            JsonParser.parseString(json).getAsJsonObject().entrySet()
                    .forEach(e -> overrides.put(e.getKey(), e.getValue()));
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("[Switchboard] Ignoring malformed sync payload", e);
            return;
        }
        Switchboard.apply(overrides);
    }
}
