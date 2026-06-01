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
import java.util.Optional;

/** Server -> Client: full snapshot of all known shift templates. */
//? if neoforge {
public record ShiftTemplateSyncPayload(List<ShiftTemplate> templates) implements CustomPacketPayload {
//?} else {
/*public record ShiftTemplateSyncPayload(List<ShiftTemplate> templates) {
*///?}

    //? if neoforge {
    public static final Type<ShiftTemplateSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_sync"));

    public static final StreamCodec<FriendlyByteBuf, ShiftTemplateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftTemplateSyncPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftTemplateSyncPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_template_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_template_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(templates.size());
        for (ShiftTemplate t : templates) {
            buf.writeResourceLocation(t.id());
            buf.writeUtf(t.displayName());
            int[] shifts = t.copyShifts();
            buf.writeVarInt(shifts.length);
            for (int s : shifts) buf.writeVarInt(s);
            buf.writeBoolean(t.chronotype().isPresent());
            t.chronotype().ifPresent(buf::writeUtf);
            buf.writeBoolean(t.builtIn());
        }
    }

    public static ShiftTemplateSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ShiftTemplate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String name = buf.readUtf();
            int len = buf.readVarInt();
            int[] shifts = new int[len];
            for (int j = 0; j < len; j++) shifts[j] = buf.readVarInt();
            Optional<String> chrono = buf.readBoolean()
                    ? Optional.of(buf.readUtf())
                    : Optional.empty();
            boolean builtIn = buf.readBoolean();
            try {
                out.add(new ShiftTemplate(id, name, shifts, chrono, builtIn));
            } catch (IllegalArgumentException ignored) {}
        }
        return new ShiftTemplateSyncPayload(out);
    }
}
