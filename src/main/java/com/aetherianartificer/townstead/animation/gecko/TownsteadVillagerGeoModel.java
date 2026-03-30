package com.aetherianartificer.townstead.animation.gecko;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class TownsteadVillagerGeoModel extends GeoModel<TownsteadVillagerReplacedGeoAnimatable> {
    //? if neoforge {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("townstead", "geo/mca_villager.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mca", "textures/entity/skins/normal/0.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath("townstead", "animations/mca_responses.animation.json");
    //?} else if forge {
    /*private static final ResourceLocation MODEL = new ResourceLocation("townstead", "geo/mca_villager.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("mca", "textures/entity/skins/normal/0.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation("townstead", "animations/mca_responses.animation.json");
    *///?}

    @Override
    public ResourceLocation getModelResource(TownsteadVillagerReplacedGeoAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TownsteadVillagerReplacedGeoAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TownsteadVillagerReplacedGeoAnimatable animatable) {
        return ANIMATIONS;
    }
}
