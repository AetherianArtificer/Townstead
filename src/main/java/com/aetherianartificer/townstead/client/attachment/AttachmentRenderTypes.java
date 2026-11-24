package com.aetherianartificer.townstead.client.attachment;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * A see-through attachment that doesn't write depth. Faces of one model draw in file order, so with depth
 * on, whichever came first blocks the ones behind it, and which those are changes as the camera moves.
 */
final class AttachmentRenderTypes extends RenderType {

    private static final Function<ResourceLocation, RenderType> SEE_THROUGH = Util.memoize(texture -> create(
            "townstead_attachment_see_through", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)));

    // The same faces again with no colour, only depth: drawn after the colour, so whatever comes later in the frame
    // and sits behind the part (water, glass, particles) is hidden behind it rather than drawn over it.
    private static final Function<ResourceLocation, RenderType> SEE_THROUGH_DEPTH = Util.memoize(texture -> create(
            "townstead_attachment_see_through_depth", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.DEPTH_WRITE)
                    .createCompositeState(false)));

    private AttachmentRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                                  Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static RenderType seeThrough(ResourceLocation texture) {
        return SEE_THROUGH.apply(texture);
    }

    static RenderType seeThroughDepth(ResourceLocation texture) {
        return SEE_THROUGH_DEPTH.apply(texture);
    }
}
