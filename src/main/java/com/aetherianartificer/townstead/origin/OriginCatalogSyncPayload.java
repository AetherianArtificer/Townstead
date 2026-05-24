package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the full list of selectable origins (flattened display data)
 * so the picker can render on a client whose datapack-driven
 * {@link OriginRegistry} is empty. Sent on login and on datapack reload, mirror
 * of {@code CalendarSyncPayload}.
 */
//? if neoforge {
public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries) implements CustomPacketPayload {
//?} else {
/*public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries) {
*///?}

    //? if neoforge {
    public static final Type<OriginCatalogSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, OriginCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OriginCatalogSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "origin_catalog_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (OriginCatalogEntry e : entries) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.demonymSingular());
            buf.writeUtf(e.demonymPlural());
            buf.writeUtf(e.backstory());
        }
    }

    public static OriginCatalogSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<OriginCatalogEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new OriginCatalogEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        return new OriginCatalogSyncPayload(entries);
    }
}
