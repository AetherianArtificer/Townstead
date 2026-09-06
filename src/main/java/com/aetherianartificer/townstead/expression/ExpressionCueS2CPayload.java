package com.aetherianartificer.townstead.expression;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Resolved presentation travels over the wire; custom icon textures still require a matching client resource pack. */
//? if neoforge {
public record ExpressionCueS2CPayload(int entityId, String cueId, byte kind, String content,
                                     int durationTicks, float rise, float drift, float scale, int color,
                                     int iconCount, float iconSpread, float iconVerticalSpread,
                                     int iconStaggerTicks, float iconScaleVariance,
                                     List<String> textTranslations, float textSpread,
                                     float textVerticalSpread, int textStaggerTicks, float textScaleVariance)
        implements CustomPacketPayload {
//?} else {
/*public record ExpressionCueS2CPayload(int entityId, String cueId, byte kind, String content,
                                     int durationTicks, float rise, float drift, float scale, int color,
                                     int iconCount, float iconSpread, float iconVerticalSpread,
                                     int iconStaggerTicks, float iconScaleVariance,
                                     List<String> textTranslations, float textSpread,
                                     float textVerticalSpread, int textStaggerTicks, float textScaleVariance) {
*///?}
    public static ExpressionCueS2CPayload of(net.minecraft.world.entity.LivingEntity entity, ExpressionCue cue) {
        return new ExpressionCueS2CPayload(entity.getId(), cue.id().toString(), (byte) cue.kind().ordinal(),
                cue.content(), cue.durationTicks(), cue.rise(), cue.drift(), cue.scale(), cue.color(),
                cue.iconBurst().count(), cue.iconBurst().spread(), cue.iconBurst().verticalSpread(),
                cue.iconBurst().staggerTicks(), cue.iconBurst().scaleVariance(),
                cue.textBurst().translations(), cue.textBurst().spread(), cue.textBurst().verticalSpread(),
                cue.textBurst().staggerTicks(), cue.textBurst().scaleVariance());
    }

    //? if neoforge {
    public static final Type<ExpressionCueS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "expression_cue_s2c"));
    public static final StreamCodec<FriendlyByteBuf, ExpressionCueS2CPayload> STREAM_CODEC =
            StreamCodec.of(ExpressionCueS2CPayload::encode, ExpressionCueS2CPayload::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void encode(FriendlyByteBuf buf, ExpressionCueS2CPayload payload) {
        buf.writeVarInt(payload.entityId); buf.writeUtf(payload.cueId); buf.writeByte(payload.kind);
        buf.writeUtf(payload.content); buf.writeVarInt(payload.durationTicks); buf.writeFloat(payload.rise);
        buf.writeFloat(payload.drift); buf.writeFloat(payload.scale); buf.writeInt(payload.color);
        buf.writeVarInt(payload.iconCount); buf.writeFloat(payload.iconSpread);
        buf.writeFloat(payload.iconVerticalSpread); buf.writeVarInt(payload.iconStaggerTicks);
        buf.writeFloat(payload.iconScaleVariance);
        writeStrings(buf, payload.textTranslations); buf.writeFloat(payload.textSpread);
        buf.writeFloat(payload.textVerticalSpread); buf.writeVarInt(payload.textStaggerTicks);
        buf.writeFloat(payload.textScaleVariance);
    }
    private static ExpressionCueS2CPayload decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt(); String cueId = buf.readUtf(); byte kind = buf.readByte();
        String content = buf.readUtf(); int duration = buf.readVarInt(); float rise = buf.readFloat();
        float drift = buf.readFloat(); float scale = buf.readFloat(); int color = buf.readInt();
        int iconCount = buf.readVarInt(); float iconSpread = buf.readFloat();
        float iconVerticalSpread = buf.readFloat(); int iconStagger = buf.readVarInt();
        float iconVariance = buf.readFloat(); List<String> text = readStrings(buf);
        return new ExpressionCueS2CPayload(entityId, cueId, kind, content,
                duration, rise, drift, scale, color,
                iconCount, iconSpread, iconVerticalSpread, iconStagger, iconVariance,
                text, buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }
    //?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId); buf.writeUtf(cueId); buf.writeByte(kind); buf.writeUtf(content);
        buf.writeVarInt(durationTicks); buf.writeFloat(rise); buf.writeFloat(drift); buf.writeFloat(scale); buf.writeInt(color);
        buf.writeVarInt(iconCount); buf.writeFloat(iconSpread); buf.writeFloat(iconVerticalSpread);
        buf.writeVarInt(iconStaggerTicks); buf.writeFloat(iconScaleVariance);
        writeStrings(buf, textTranslations); buf.writeFloat(textSpread); buf.writeFloat(textVerticalSpread);
        buf.writeVarInt(textStaggerTicks); buf.writeFloat(textScaleVariance);
    }
    public static ExpressionCueS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt(); String cueId = buf.readUtf(); byte kind = buf.readByte();
        String content = buf.readUtf(); int duration = buf.readVarInt(); float rise = buf.readFloat();
        float drift = buf.readFloat(); float scale = buf.readFloat(); int color = buf.readInt();
        int iconCount = buf.readVarInt(); float iconSpread = buf.readFloat();
        float iconVerticalSpread = buf.readFloat(); int iconStagger = buf.readVarInt();
        float iconVariance = buf.readFloat(); List<String> text = readStrings(buf);
        return new ExpressionCueS2CPayload(entityId, cueId, kind, content,
                duration, rise, drift, scale, color,
                iconCount, iconSpread, iconVerticalSpread, iconStagger, iconVariance,
                text, buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }
    *///?}

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        int size = Math.min(12, values == null ? 0 : values.size());
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) buf.writeUtf(values.get(i), 256);
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 12) throw new IllegalArgumentException("text burst contains too many entries");
        ArrayList<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buf.readUtf(256));
        return List.copyOf(values);
    }
}
