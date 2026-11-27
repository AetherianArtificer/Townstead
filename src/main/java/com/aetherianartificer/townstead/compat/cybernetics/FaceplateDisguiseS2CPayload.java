package com.aetherianartificer.townstead.compat.cybernetics;

//? if neoforge {
import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server → client: how the player with {@code entityId} looks behind a faceplate. {@code shown} is
 * the player the alias names, whose Root rides under {@link FaceplateDisguise#dataId}; null with
 * {@code worn} means an alias that names no one here.
 */
public record FaceplateDisguiseS2CPayload(int entityId, boolean worn, UUID shown) implements CustomPacketPayload {

    public static final Type<FaceplateDisguiseS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "faceplate_disguise_s2c"));

    public static final StreamCodec<FriendlyByteBuf, FaceplateDisguiseS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), FaceplateDisguiseS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(worn);
        buf.writeBoolean(shown != null);
        if (shown != null) buf.writeUUID(shown);
    }

    public static FaceplateDisguiseS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        boolean worn = buf.readBoolean();
        UUID shown = buf.readBoolean() ? buf.readUUID() : null;
        return new FaceplateDisguiseS2CPayload(entityId, worn, shown);
    }
}
//?}
