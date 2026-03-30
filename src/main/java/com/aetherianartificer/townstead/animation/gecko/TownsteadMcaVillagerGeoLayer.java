package com.aetherianartificer.townstead.animation.gecko;

//? if neoforge {
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
//?} else if forge {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;*/
//?}

//? if neoforge {
abstract class TownsteadMcaVillagerGeoLayer extends GeoRenderLayer<TownsteadVillagerReplacedGeoAnimatable> {
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    protected final VillagerEntityModelMCA<VillagerEntityMCA> model;

    protected TownsteadMcaVillagerGeoLayer(
            TownsteadVillagerGeoRenderer renderer,
            VillagerEntityModelMCA<VillagerEntityMCA> model
    ) {
        super(renderer);
        this.model = model;
    }

    protected abstract ResourceLocation getSkin(VillagerEntityMCA villager);

    protected ResourceLocation getOverlay(VillagerEntityMCA villager) {
        return null;
    }

    protected int getColor(VillagerEntityMCA villager, float partialTick) {
        return 0xFFFFFFFF;
    }

    protected boolean isTranslucent() {
        return false;
    }

    protected void configureVisibility(VillagerEntityMCA villager) {
        this.model.setAllVisible(true);
    }

    @Override
    public void render(
            PoseStack poseStack,
            TownsteadVillagerReplacedGeoAnimatable animatable,
            BakedGeoModel bakedModel,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay
    ) {
        VillagerEntityMCA villager = ((TownsteadVillagerGeoRenderer) getRenderer()).getCurrentEntity();
        if (villager == null) return;

        applyBasePose(villager, partialTick);
        applyGeckoPose(bakedModel);
        configureVisibility(villager);

        boolean visible = !villager.isInvisible();
        boolean glowing = Minecraft.getInstance().shouldEntityAppearGlowing(villager);
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0.0f);

        ResourceLocation skin = getSkin(villager);
        if (canUse(skin)) {
            renderModel(poseStack, bufferSource, packedLight, overlay, getColor(villager, partialTick), skin, visible, glowing);
        }

        ResourceLocation textureOverlay = getOverlay(villager);
        if (!Objects.equals(skin, textureOverlay) && canUse(textureOverlay)) {
            renderModel(poseStack, bufferSource, packedLight, overlay, 0xFFFFFFFF, textureOverlay, visible, glowing);
        }
    }

    protected final void applyBasePose(VillagerEntityMCA villager, float partialTick) {
        float limbAngle = villager.walkAnimation.position(partialTick);
        float limbDistance = villager.walkAnimation.speed(partialTick);
        float animationProgress = villager.tickCount + partialTick;
        float bodyYaw = lerpDegrees(partialTick, villager.yBodyRotO, villager.yBodyRot);
        float headYaw = lerpDegrees(partialTick, villager.yHeadRotO, villager.yHeadRot) - bodyYaw;
        float headPitch = villager.getViewXRot(partialTick);

        this.model.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
    }

    protected final void applyGeckoPose(BakedGeoModel bakedModel) {
        applyBoneRotation(bakedModel, "head", this.model.head);
        applyBoneRotation(bakedModel, "body", this.model.body);
        applyBoneRotation(bakedModel, "right_arm", this.model.rightArm);
        applyBoneRotation(bakedModel, "left_arm", this.model.leftArm);
        applyBoneRotation(bakedModel, "right_leg", this.model.rightLeg);
        applyBoneRotation(bakedModel, "left_leg", this.model.leftLeg);
    }

    private void applyBoneRotation(BakedGeoModel bakedModel, String boneName, net.minecraft.client.model.geom.ModelPart part) {
        Optional<GeoBone> bone = bakedModel.getBone(boneName);
        if (bone.isEmpty()) return;

        GeoBone geoBone = bone.get();
        part.xRot = geoBone.getRotX();
        part.yRot = geoBone.getRotY();
        part.zRot = geoBone.getRotZ();
        part.x = geoBone.getPosX();
        part.y = geoBone.getPosY();
        part.z = geoBone.getPosZ();
    }

    private void renderModel(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            int color,
            ResourceLocation texture,
            boolean visible,
            boolean glowing
    ) {
        RenderType layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) return;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(layer);
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    protected RenderType getRenderLayer(ResourceLocation texture, boolean showBody, boolean translucent, boolean showOutline) {
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(texture);
        }

        if (showBody) {
            return this.model.renderType(texture);
        }

        return showOutline ? RenderType.outline(texture) : null;
    }

    protected final boolean canUse(ResourceLocation texture) {
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, value -> {
            if (value != null && "immersive_library".equals(value.getNamespace())) {
                return true;
            }

            return value != null && Minecraft.getInstance().getResourceManager().getResource(value).isPresent();
        });
    }

    protected final ResourceLocation cached(String name, Function<String, ResourceLocation> supplier) {
        return TEXTURE_CACHE.computeIfAbsent(name, key -> {
            try {
                return supplier.apply(key);
            } catch (ResourceLocationException ignored) {
                return null;
            }
        });
    }

    protected static VillagerEntityModelMCA<VillagerEntityMCA> createModel(net.minecraft.client.model.geom.builders.MeshDefinition data) {
        return new VillagerEntityModelMCA<>(
                net.minecraft.client.model.geom.builders.LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    protected static float lerpDegrees(float partialTick, float start, float end) {
        return start + partialTick * Mth.wrapDegrees(end - start);
    }

    static final class Skin extends TownsteadMcaVillagerGeoLayer {
        Skin(TownsteadVillagerGeoRenderer renderer) {
            super(renderer, createModel(VillagerEntityModelMCA.bodyData(net.minecraft.client.model.geom.builders.CubeDeformation.NONE)).hideWears());
        }

        @Override
        protected ResourceLocation getSkin(VillagerEntityMCA villager) {
            Genetics genetics = CommonVillagerModel.getVillager(villager).getGenetics();
            int skin = (int) Math.min(4, Math.max(0, genetics.getGene(Genetics.SKIN) * 5));
            return cached("skins/skin/" + genetics.getGender().getDataName() + "/" + skin + ".png", MCA::locate);
        }

        @Override
        protected int getColor(VillagerEntityMCA villager, float partialTick) {
            float albinism = CommonVillagerModel.getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

            return ColorPalette.SKIN.getColor(
                    CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.MELANIN) * albinism,
                    CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                    CommonVillagerModel.getVillager(villager).getInfectionProgress()
            );
        }
    }

    static final class Face extends TownsteadMcaVillagerGeoLayer {
        private static final int FACE_COUNT = 22;
        private final String variant;

        Face(TownsteadVillagerGeoRenderer renderer, String variant) {
            super(renderer, createModel(VillagerEntityModelMCA.bodyData(new net.minecraft.client.model.geom.builders.CubeDeformation(0.01F))).hideWears());
            this.variant = variant;
        }

        @Override
        protected void configureVisibility(VillagerEntityMCA villager) {
            this.model.setAllVisible(false);
            this.model.head.visible = true;
        }

        @Override
        protected boolean isTranslucent() {
            return true;
        }

        @Override
        protected ResourceLocation getSkin(VillagerEntityMCA villager) {
            int index = (int) Math.min(FACE_COUNT - 1, Math.max(0,
                    CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.FACE) * FACE_COUNT));
            int time = villager.tickCount / 2 +
                    (int) (CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
            boolean blink = time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
            boolean heterochromia = "normal".equals(this.variant)
                    && CommonVillagerModel.getVillager(villager).getTraits().hasTrait(Traits.HETEROCHROMIA);
            String gender = CommonVillagerModel.getVillager(villager).getGenetics().getGender().getDataName();
            String suffix = blink ? "_blink" : (heterochromia ? "_hetero" : "");

            return cached("skins/face/" + this.variant + "/" + gender + "/" + index + suffix + ".png", MCA::locate);
        }
    }

    static final class Clothing extends TownsteadMcaVillagerGeoLayer {
        private final String variant;

        Clothing(TownsteadVillagerGeoRenderer renderer, String variant) {
            super(renderer, createModel(VillagerEntityModelMCA.bodyData(new net.minecraft.client.model.geom.builders.CubeDeformation(0.0625F))));
            this.variant = variant;
        }

        @Override
        protected ResourceLocation getSkin(VillagerEntityMCA villager) {
            String resolvedVariant = CommonVillagerModel.getVillager(villager).isBurned() ? "burnt" : this.variant;
            String identifier = CommonVillagerModel.getVillager(villager).getClothes();
            if (identifier.startsWith("immersive_library:")) {
                return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            }

            return cached(identifier + resolvedVariant, clothes -> {
                ResourceLocation id = ResourceLocation.parse(CommonVillagerModel.getVillager(villager).getClothes());
                ResourceLocation variantId = ResourceLocation.fromNamespaceAndPath(
                        id.getNamespace(),
                        id.getPath().replace("normal", resolvedVariant)
                );

                return canUse(variantId) ? variantId : id;
            });
        }
    }

    static final class Hair extends TownsteadMcaVillagerGeoLayer {
        Hair(TownsteadVillagerGeoRenderer renderer) {
            super(renderer, createModel(VillagerEntityModelMCA.hairData(new net.minecraft.client.model.geom.builders.CubeDeformation(0.125F))));
        }

        @Override
        protected void configureVisibility(VillagerEntityMCA villager) {
            this.model.setAllVisible(true);
            this.model.leftLeg.visible = false;
            this.model.rightLeg.visible = false;
        }

        @Override
        protected ResourceLocation getSkin(VillagerEntityMCA villager) {
            String identifier = CommonVillagerModel.getVillager(villager).getHair();
            if (identifier.startsWith("immersive_library:")) {
                return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            }

            return cached(identifier, ResourceLocation::parse);
        }

        @Override
        protected ResourceLocation getOverlay(VillagerEntityMCA villager) {
            return cached(
                    CommonVillagerModel.getVillager(villager).getHair().replace(".png", "_overlay.png"),
                    ResourceLocation::parse
            );
        }

        @Override
        protected int getColor(VillagerEntityMCA villager, float partialTick) {
            if (CommonVillagerModel.getVillager(villager).getTraits().hasTrait(Traits.RAINBOW)) {
                return getRainbow(villager, partialTick);
            }

            int hairDye = CommonVillagerModel.getVillager(villager).getHairDye();
            if (hairDye != 0xFF000000) {
                return hairDye;
            }

            float albinism = CommonVillagerModel.getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

            return ColorPalette.HAIR.getColor(
                    CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                    CommonVillagerModel.getVillager(villager).getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                    0
            );
        }

        private int getRainbow(VillagerEntityMCA villager, float partialTick) {
            int current = Math.abs(villager.tickCount) / 25 + villager.getId();
            int colorCount = DyeColor.values().length;
            int first = current % colorCount;
            int second = (current + 1) % colorCount;
            float delta = ((float) (Math.abs(villager.tickCount) % 25) + partialTick) / 25.0f;
            return FastColor.ARGB32.lerp(delta, Sheep.getColor(DyeColor.byId(first)), Sheep.getColor(DyeColor.byId(second)));
        }
    }
}
//?} else if forge {
/*abstract class TownsteadMcaVillagerGeoLayer extends GeoRenderLayer<TownsteadVillagerReplacedGeoAnimatable> {
    protected TownsteadMcaVillagerGeoLayer(TownsteadVillagerGeoRenderer renderer) {
        super(renderer);
    }

    static final class Skin extends TownsteadMcaVillagerGeoLayer {
        Skin(TownsteadVillagerGeoRenderer renderer) {
            super(renderer);
        }
    }

    static final class Face extends TownsteadMcaVillagerGeoLayer {
        Face(TownsteadVillagerGeoRenderer renderer, String variant) {
            super(renderer);
        }
    }

    static final class Clothing extends TownsteadMcaVillagerGeoLayer {
        Clothing(TownsteadVillagerGeoRenderer renderer, String variant) {
            super(renderer);
        }
    }

    static final class Hair extends TownsteadMcaVillagerGeoLayer {
        Hair(TownsteadVillagerGeoRenderer renderer) {
            super(renderer);
        }
    }
}*/
//?}
