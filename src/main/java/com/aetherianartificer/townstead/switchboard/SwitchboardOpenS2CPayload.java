package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server to client: open the Switchboard. Carries the world's own values, the modpack defaults and
 * locks so the screen can show where each value comes from. {@code firstJoin} is the new-world pass
 * that runs before Destiny.
 */
//? if neoforge {
public record SwitchboardOpenS2CPayload(boolean firstJoin, String world, String pack, String locked, String catalog)
        implements CustomPacketPayload {
//?} else {
/*public record SwitchboardOpenS2CPayload(boolean firstJoin, String world, String pack, String locked, String catalog) {
*///?}

    private static final int MAX_LENGTH = 1 << 20;

    //? if neoforge {
    public static final Type<SwitchboardOpenS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "switchboard_open_s2c"));

    public static final StreamCodec<FriendlyByteBuf, SwitchboardOpenS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SwitchboardOpenS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public static SwitchboardOpenS2CPayload of(boolean firstJoin, Map<String, JsonElement> world,
                                               Map<String, JsonElement> pack, Collection<String> locked,
                                               String catalog) {
        JsonArray lockedArray = new JsonArray();
        locked.forEach(lockedArray::add);
        return new SwitchboardOpenS2CPayload(firstJoin, object(world).toString(), object(pack).toString(),
                lockedArray.toString(), catalog);
    }

    public Map<String, JsonElement> worldValues() { return map(world); }
    public Map<String, JsonElement> packValues() { return map(pack); }

    public Set<String> lockedKeys() {
        Set<String> out = new LinkedHashSet<>();
        try {
            JsonParser.parseString(locked).getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        } catch (RuntimeException ignored) {}
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(firstJoin);
        buf.writeUtf(world, MAX_LENGTH);
        buf.writeUtf(pack, MAX_LENGTH);
        buf.writeUtf(locked, MAX_LENGTH);
        buf.writeUtf(catalog, MAX_LENGTH);
    }

    public static SwitchboardOpenS2CPayload read(FriendlyByteBuf buf) {
        return new SwitchboardOpenS2CPayload(buf.readBoolean(), buf.readUtf(MAX_LENGTH),
                buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH), buf.readUtf(MAX_LENGTH));
    }

    static JsonObject object(Map<String, JsonElement> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::add);
        return object;
    }

    static Map<String, JsonElement> map(String json) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        try {
            JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(e -> out.put(e.getKey(), e.getValue()));
        } catch (RuntimeException ignored) {}
        return out;
    }
}
