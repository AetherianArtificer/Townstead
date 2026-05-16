package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: delete a user template by id. Built-ins are rejected server-side. */
//? if neoforge {
public record ShiftTemplateDeletePayload(ResourceLocation id) implements CustomPacketPayload {
//?} else {
/*public record ShiftTemplateDeletePayload(ResourceLocation id) {
*///?}

    //? if neoforge {
    public static final Type<ShiftTemplateDeletePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_delete"));

    public static final StreamCodec<FriendlyByteBuf, ShiftTemplateDeletePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftTemplateDeletePayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftTemplateDeletePayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_delete");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_template_delete");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
    }

    public static ShiftTemplateDeletePayload read(FriendlyByteBuf buf) {
        return new ShiftTemplateDeletePayload(buf.readResourceLocation());
    }
}
