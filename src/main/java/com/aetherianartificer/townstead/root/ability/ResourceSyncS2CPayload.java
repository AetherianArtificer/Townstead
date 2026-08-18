package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to owning player: gameplay state plus resolved presentation for resource meters. */
//? if neoforge {
public record ResourceSyncS2CPayload(List<Bar> bars) implements CustomPacketPayload {
//?} else {
/*public record ResourceSyncS2CPayload(java.util.List<ResourceSyncS2CPayload.Bar> bars) {
*///?}

    public record Effect(String type, float strength, String gradientShape,
                         int highlightColor, int shadowColor) {
        public Effect {
            type = type == null || type.isBlank() ? "townstead:none" : type;
            strength = Math.max(0f, Math.min(1f, strength));
            gradientShape = gradientShape == null || gradientShape.isBlank()
                    ? "crosswise" : gradientShape;
            highlightColor = highlightColor < 0 ? -1 : highlightColor & 0xFFFFFF;
            shadowColor = shadowColor < 0 ? -1 : shadowColor & 0xFFFFFF;
        }
    }

    public record Bar(String resourceId, int value, int min, int max, int restingValue,
                      int color,
                      String shape, String fillMode, List<Effect> effects,
                      String frameId, String colorThemeId,
                      String anchor, String pipStyle, int segments, int priority,
                      int backgroundColor, int framePrimaryColor, int frameSecondaryColor, int frameThickness,
                      String frameTexture, int frameSpriteRow) {
        public Bar {
            effects = effects == null ? List.of() : List.copyOf(effects);
            anchor = anchor == null || anchor.isBlank() ? "TOP_LEFT" : anchor;
            pipStyle = pipStyle == null || pipStyle.isBlank() ? "DOTS" : pipStyle;
            frameTexture = frameTexture == null ? "" : frameTexture;
        }
    }

    //? if neoforge {
    public static final Type<ResourceSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, ResourceSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ResourceSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "resource_sync_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(bars.size());
        for (Bar bar : bars) {
            buf.writeUtf(bar.resourceId());
            buf.writeVarInt(bar.value());
            buf.writeVarInt(bar.min());
            buf.writeVarInt(bar.max());
            buf.writeVarInt(bar.restingValue());
            buf.writeInt(bar.color());
            buf.writeUtf(bar.shape());
            buf.writeUtf(bar.fillMode());
            buf.writeVarInt(bar.effects().size());
            for (Effect effect : bar.effects()) {
                buf.writeUtf(effect.type());
                buf.writeFloat(effect.strength());
                buf.writeUtf(effect.gradientShape());
                buf.writeInt(effect.highlightColor());
                buf.writeInt(effect.shadowColor());
            }
            buf.writeUtf(bar.frameId());
            buf.writeUtf(bar.colorThemeId());
            buf.writeUtf(bar.anchor());
            buf.writeUtf(bar.pipStyle());
            buf.writeVarInt(bar.segments());
            buf.writeInt(bar.priority());
            buf.writeInt(bar.backgroundColor());
            buf.writeInt(bar.framePrimaryColor());
            buf.writeInt(bar.frameSecondaryColor());
            buf.writeVarInt(bar.frameThickness());
            buf.writeUtf(bar.frameTexture());
            buf.writeVarInt(bar.frameSpriteRow() + 1);
        }
    }

    public static ResourceSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Bar> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String resourceId = buf.readUtf();
            int value = buf.readVarInt();
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            int resting = buf.readVarInt();
            int color = buf.readInt();
            String shape = buf.readUtf();
            String fill = buf.readUtf();
            int effectCount = buf.readVarInt();
            List<Effect> effects = new ArrayList<>(effectCount);
            for (int effectIndex = 0; effectIndex < effectCount; effectIndex++) {
                effects.add(new Effect(buf.readUtf(), buf.readFloat(), buf.readUtf(),
                        buf.readInt(), buf.readInt()));
            }
            String frame = buf.readUtf();
            String colorTheme = buf.readUtf();
            String anchor = buf.readUtf();
            String pipStyle = buf.readUtf();
            int segments = buf.readVarInt();
            int priority = buf.readInt();
            int background = buf.readInt();
            int framePrimaryColor = buf.readInt();
            int frameSecondaryColor = buf.readInt();
            int thickness = buf.readVarInt();
            String frameTexture = buf.readUtf();
            int frameSpriteRow = buf.readVarInt() - 1;
            bars.add(new Bar(resourceId, value, min, max, resting, color,
                    shape, fill, List.copyOf(effects), frame, colorTheme,
                    anchor, pipStyle,
                    segments, priority, background, framePrimaryColor, frameSecondaryColor, thickness,
                    frameTexture, frameSpriteRow));
        }
        return new ResourceSyncS2CPayload(List.copyOf(bars));
    }
}
