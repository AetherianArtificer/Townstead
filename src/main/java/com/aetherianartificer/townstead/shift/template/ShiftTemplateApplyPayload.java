package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client -> Server: apply a template to a list of villager UUIDs. */
//? if neoforge {
public record ShiftTemplateApplyPayload(ResourceLocation templateId, List<UUID> villagerUuids)
        implements CustomPacketPayload {
//?} else {
/*public record ShiftTemplateApplyPayload(ResourceLocation templateId, List<UUID> villagerUuids) {
*///?}

    //? if neoforge {
    public static final Type<ShiftTemplateApplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_apply"));

    public static final StreamCodec<FriendlyByteBuf, ShiftTemplateApplyPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftTemplateApplyPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftTemplateApplyPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_apply");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_template_apply");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(templateId);
        buf.writeVarInt(villagerUuids.size());
        for (UUID uuid : villagerUuids) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    }

    public static ShiftTemplateApplyPayload read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int n = buf.readVarInt();
        List<UUID> uuids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            uuids.add(new UUID(buf.readLong(), buf.readLong()));
        }
        return new ShiftTemplateApplyPayload(id, uuids);
    }
}
