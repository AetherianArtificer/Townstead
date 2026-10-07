package com.aetherianartificer.townstead.livery;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The livery one wearer shows, already resolved: which style's art to draw, whether to tint it, the
 * two colours, the armour materials tinted where the style has no art, and the trims per slot. It is
 * all a client needs to draw it.
 */
public record LiveryView(ResourceLocation style, boolean tint, int primary, int secondary, Set<String> tintArmor,
                         Map<String, Trim> trims) {
    /** A vanilla armour trim: pattern and material registry ids. */
    public record Trim(String pattern, String material) {}

    public LiveryView {
        tintArmor = Set.copyOf(tintArmor);
        trims = Map.copyOf(trims);
    }

    public static LiveryView of(LiveryStyle style, int primary, int secondary) {
        return new LiveryView(style.id(), style.tint(), primary, secondary, style.tintArmor(), style.trims());
    }

    public LiveryView withColors(int primary, int secondary) {
        return new LiveryView(style, tint, primary, secondary, tintArmor, trims);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(style.toString(), 256);
        buf.writeBoolean(tint);
        buf.writeInt(primary);
        buf.writeInt(secondary);
        buf.writeVarInt(tintArmor.size());
        tintArmor.forEach(material -> buf.writeUtf(material, 256));
        buf.writeVarInt(trims.size());
        trims.forEach((slot, trim) -> {
            buf.writeUtf(slot, 16);
            buf.writeUtf(trim.pattern(), 256);
            buf.writeUtf(trim.material(), 256);
        });
    }

    public static LiveryView read(FriendlyByteBuf buf) {
        ResourceLocation style = ResourceLocation.tryParse(buf.readUtf(256));
        boolean tint = buf.readBoolean();
        int primary = buf.readInt(), secondary = buf.readInt();
        int materials = buf.readVarInt();
        if (materials < 0 || materials > 64) throw new IllegalArgumentException("Invalid livery tint material count: " + materials);
        Set<String> tintArmor = new LinkedHashSet<>();
        for (int i = 0; i < materials; i++) tintArmor.add(buf.readUtf(256));
        int count = buf.readVarInt();
        if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid livery trim count: " + count);
        Map<String, Trim> trims = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) trims.put(buf.readUtf(16), new Trim(buf.readUtf(256), buf.readUtf(256)));
        //? if >=1.21 {
        return new LiveryView(style == null ? ResourceLocation.withDefaultNamespace("none") : style, tint, primary, secondary, tintArmor, trims);
        //?} else {
        /*return new LiveryView(style == null ? new ResourceLocation("none") : style, tint, primary, secondary, tintArmor, trims);
        *///?}
    }
}
