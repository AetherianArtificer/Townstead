package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Client -> Server: create or update a user template. Pass empty id ("") to
 * request the server assign one (typical for first save).
 */
//? if neoforge {
public record ShiftTemplateSavePayload(String id, String name, int[] shifts, Optional<String> chronotypeName)
        implements CustomPacketPayload {
//?} else {
/*public record ShiftTemplateSavePayload(String id, String name, int[] shifts, Optional<String> chronotypeName) {
*///?}

    //? if neoforge {
    public static final Type<ShiftTemplateSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_save"));

    public static final StreamCodec<FriendlyByteBuf, ShiftTemplateSavePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftTemplateSavePayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftTemplateSavePayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_save");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_template_save");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id == null ? "" : id);
        buf.writeUtf(name == null ? "" : name);
        buf.writeVarInt(shifts.length);
        for (int s : shifts) buf.writeVarInt(s);
        buf.writeBoolean(chronotypeName != null && chronotypeName.isPresent());
        if (chronotypeName != null && chronotypeName.isPresent()) buf.writeUtf(chronotypeName.get());
    }

    public static ShiftTemplateSavePayload read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        int len = buf.readVarInt();
        int[] shifts = new int[len];
        for (int i = 0; i < len; i++) shifts[i] = buf.readVarInt();
        Optional<String> chrono = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
        return new ShiftTemplateSavePayload(id, name, shifts, chrono);
    }
}
