package com.aetherianartificer.townstead.animation.gecko;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

public final class TownsteadVillagerGeoRenderer
        extends GeoReplacedEntityRenderer<VillagerEntityMCA, TownsteadVillagerReplacedGeoAnimatable> {

    public TownsteadVillagerGeoRenderer(
            EntityRendererProvider.Context context,
            TownsteadVillagerReplacedGeoAnimatable animatable
    ) {
        super(context, new TownsteadVillagerGeoModel(), animatable);

        addRenderLayer(new TownsteadMcaVillagerGeoLayer.Skin(this));
        addRenderLayer(new TownsteadMcaVillagerGeoLayer.Face(this, "normal"));
        addRenderLayer(new TownsteadMcaVillagerGeoLayer.Clothing(this, "normal"));
        addRenderLayer(new TownsteadMcaVillagerGeoLayer.Hair(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VillagerEntityMCA entity) {
        return this.getGeoModel().getTextureResource(this.getAnimatable());
    }

    @Override
    public @Nullable RenderType getRenderType(
            TownsteadVillagerReplacedGeoAnimatable animatable,
            ResourceLocation texture,
            MultiBufferSource bufferSource,
            float partialTick
    ) {
        return null;
    }
}
