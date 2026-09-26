package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/** Client intent only; the server revalidates the assembly, distance and every selected id. */
//? if neoforge {
public record CharterActionC2SPayload(BlockPos lectern, int action, String name, String profile,
                                     String culture, String operation, String target, String argument, long revision) implements CustomPacketPayload {
    public static final Type<CharterActionC2SPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead", "charter_action"));
    public static final StreamCodec<FriendlyByteBuf, CharterActionC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), CharterActionC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record CharterActionC2SPayload(BlockPos lectern, int action, String name, String profile,
                                     String culture, String operation, String target, String argument, long revision) {
*///?}
    public CharterActionC2SPayload(BlockPos lectern, int action, String name, String profile, String culture) {
        this(lectern, action, name, profile, culture, "", "", "", 0);
    }
    public static final int MEMBERSHIP = 4, CIVIC = 5, HERALDRY = 6, IDENTITY = 7;
    public static final int REFRESH = 0, PREPARE = 1, CANCEL = 2, LINK_EXISTING = 3;
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(lectern);
        buf.writeVarInt(action);
        buf.writeUtf(name, 48);
        buf.writeUtf(profile, 256);
        buf.writeUtf(culture, 256);
        buf.writeUtf(operation, 256); buf.writeUtf(target, 512); buf.writeUtf(argument, 1024); buf.writeLong(revision);
    }
    public static CharterActionC2SPayload read(FriendlyByteBuf buf) {
        return new CharterActionC2SPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(48),
                buf.readUtf(256), buf.readUtf(256), buf.readUtf(256), buf.readUtf(512), buf.readUtf(1024), buf.readLong());
    }
}
