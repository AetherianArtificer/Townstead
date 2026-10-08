package com.aetherianartificer.townstead.pheno.cosmetic;

//? if neoforge {
import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

/** Server → client: one entity's current cosmetics. An empty list clears them. */
//? if neoforge {
public record CosmeticWearS2CPayload(int entityId, List<CosmeticWear.Worn> worn) implements CustomPacketPayload {
//?} else {
/*public record CosmeticWearS2CPayload(int entityId, List<CosmeticWear.Worn> worn) {
*///?}
    private static final int MAX = 16;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        int size = Math.min(MAX, worn.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            CosmeticWear.Worn w = worn.get(i);
            buf.writeUtf(w.slot().getName());
            buf.writeResourceLocation(w.item());
            buf.writeLong(w.until());
        }
    }

    public static CosmeticWearS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int size = Math.min(MAX, buf.readVarInt());
        List<CosmeticWear.Worn> worn = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            EquipmentSlot slot = CosmeticWear.slotByName(buf.readUtf());
            ResourceLocation item = buf.readResourceLocation();
            long until = buf.readLong();
            if (slot != null) worn.add(new CosmeticWear.Worn(slot, item, until));
        }
        return new CosmeticWearS2CPayload(entityId, worn);
    }

    //? if neoforge {
    public static final Type<CosmeticWearS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "cosmetic_wear_sync"));
    public static final StreamCodec<FriendlyByteBuf, CosmeticWearS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CosmeticWearS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
