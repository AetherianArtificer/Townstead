package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the cultures and naming traditions a player can pick. Both are datapack data. */
//? if neoforge {
public record NamingCatalogS2CPayload(List<Entry> entries) implements CustomPacketPayload {
//?} else {
/*public record NamingCatalogS2CPayload(List<Entry> entries) {
*///?}

    /** A pick. With a culture, {@code label} is its name and {@code id} is whatever tradition it declares. */
    public record Entry(String id, NamingTradition.FamilyType rule, String label, String culture) {}

    public NamingCatalogS2CPayload {
        entries = List.copyOf(entries);
    }

    public static NamingCatalogS2CPayload snapshot() {
        List<Entry> entries = new ArrayList<>();
        for (com.aetherianartificer.townstead.culture.Culture culture : NamingTraditions.pickableCultures()) {
            NamingTradition tradition = NamingTraditions.get(culture.namingTradition());
            entries.add(new Entry(tradition == null ? "" : tradition.id().toString(),
                    tradition == null ? NamingTradition.FamilyType.NONE : tradition.family().type(),
                    culture.displayName().getString(), culture.id().toString()));
        }
        for (NamingTradition tradition : NamingTraditions.pickable()) {
            entries.add(new Entry(tradition.id().toString(), tradition.family().type(), "", ""));
        }
        return new NamingCatalogS2CPayload(entries);
    }

    //? if neoforge {
    public static final Type<NamingCatalogS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "naming_catalog"));

    public static final StreamCodec<FriendlyByteBuf, NamingCatalogS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), NamingCatalogS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "naming_catalog");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "naming_catalog");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.id());
            buf.writeEnum(entry.rule());
            buf.writeUtf(entry.label());
            buf.writeUtf(entry.culture());
        }
    }

    public static NamingCatalogS2CPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readEnum(NamingTradition.FamilyType.class), buf.readUtf(), buf.readUtf()));
        }
        return new NamingCatalogS2CPayload(entries);
    }
}
