package com.aetherianartificer.townstead.aspect;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, from the editor's Aspects page: with {@code pick} false, asks what
 * {@code entityId} can be and is; with {@code pick} true, makes them {@code aspect} (empty for
 * none); with an {@code option}, sets that look of their aspect to {@code value}. The server
 * answers with an {@link AspectS2CPayload} either way.
 */
//? if neoforge {
public record AspectC2SPayload(int entityId, String aspect, boolean pick, String option, int value) implements CustomPacketPayload {
//?} else {
/*public record AspectC2SPayload(int entityId, String aspect, boolean pick, String option, int value) {
*///?}

    //? if neoforge {
    public static final Type<AspectC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "aspect_c2s"));
    public static final StreamCodec<FriendlyByteBuf, AspectC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AspectC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(aspect, 256);
        buf.writeBoolean(pick);
        buf.writeUtf(option, 64);
        buf.writeVarInt(value);
    }

    /** A query, or a pick of {@code aspect}. */
    public AspectC2SPayload(int entityId, String aspect, boolean pick) {
        this(entityId, aspect, pick, "", 0);
    }

    public static AspectC2SPayload read(FriendlyByteBuf buf) {
        return new AspectC2SPayload(buf.readInt(), buf.readUtf(256), buf.readBoolean(), buf.readUtf(64), buf.readVarInt());
    }

    /** Answers on the server thread: picks when asked, then sends what they can be and are. */
    public static void handle(net.minecraft.server.level.ServerPlayer sp, AspectC2SPayload payload) {
        net.minecraft.world.entity.LivingEntity target = AspectPicks.target(sp, payload.entityId());
        if (target == null) return;
        if (!payload.option().isEmpty()) {
            AspectPicks.setOption(sp, target, payload.option(), payload.value());
        } else if (payload.pick()) {
            AspectPicks.pick(sp, target, payload.aspect().isEmpty() ? null : ResourceLocation.tryParse(payload.aspect()));
        }
        java.util.List<String> options = new java.util.ArrayList<>();
        for (var definition : AspectPicks.pickable()) options.add(definition.id().toString());
        ResourceLocation current = AspectPicks.current(target);
        java.util.List<AspectS2CPayload.Look> looks = new java.util.ArrayList<>();
        var definition = AspectPicks.currentDefinition(target);
        if (definition != null) {
            for (var option : definition.aspect().options()) {
                looks.add(new AspectS2CPayload.Look(option.id(), option.count(), AspectPicks.optionValue(target, option)));
            }
        }
        AspectS2CPayload reply = new AspectS2CPayload(payload.entityId(), options,
                current == null ? "" : current.toString(), AspectPicks.mayPick(sp, target), looks, AspectPicks.mayStyle(sp, target));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, reply);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, reply);
        *///?}
    }
}
