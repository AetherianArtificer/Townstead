package com.aetherianartificer.townstead.client.charter;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.politics.charter.CharterLecternAccess;
import com.aetherianartificer.townstead.politics.heraldry.EmblemItems;
import com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;
*///?}
import org.jetbrains.annotations.Nullable;

/**
 * A lectern holding a charter: an open charter book on the reading plate, sealed once founded, and
 * a cloth hanging from the plate's front edge in the faction's emblem (plain yellow while prepared).
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class CharterLecternRenderer {
    private static final ResourceLocation BOOK = id("townstead:block/charter_lectern_book");
    private static final ResourceLocation SEALED_BOOK = id("townstead:block/charter_lectern_book_sealed");
    /**
     * The banner flag's 20 by 40 pixels at this scale hang 0.36 by 0.72 of a block. Model parts already
     * divide by 16, so this is against a banner's own 2/3, not a block.
     */
    private static final float CLOTH_SCALE = 0.288F;

    private static @Nullable ModelPart cloth;
    private static String clothKey = "";
    private static @Nullable ItemStack clothStack;

    private CharterLecternRenderer() {}

    private static ResourceLocation id(String name) {
        //? if >=1.21 {
        return ResourceLocation.parse(name);
        //?} else {
        /*return new ResourceLocation(name);
        *///?}
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        //? if >=1.21 {
        event.register(ModelResourceLocation.standalone(BOOK));
        event.register(ModelResourceLocation.standalone(SEALED_BOOK));
        //?} else {
        /*event.register(BOOK);
        event.register(SEALED_BOOK);
        *///?}
    }

    public static void render(LecternBlockEntity lectern, CharterLecternAccess access, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay) {
        int state = access.townstead$charterState();
        Direction facing = lectern.getBlockState().getValue(LecternBlock.FACING);
        renderBook(state == CharterLecternAccess.FOUNDED ? SEALED_BOOK : BOOK, facing, pose, buffers, light, overlay);
        renderCloth(lectern, access, state, facing, pose, buffers, light, overlay);
    }

    /** The book model is authored for a north-facing lectern, turned the way the lectern's own model turns. */
    private static void renderBook(ResourceLocation name, Direction facing, PoseStack pose, MultiBufferSource buffers,
                                   int light, int overlay) {
        var models = Minecraft.getInstance().getModelManager();
        //? if >=1.21 {
        BakedModel model = models.getModel(ModelResourceLocation.standalone(name));
        //?} else {
        /*BakedModel model = models.getModel(name);
        *///?}
        if (model == models.getMissingModel()) return;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), null, model, 1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();
    }

    /** Hangs from the plate's top front corner, clear of the slanted lip, and stops just above the base. */
    private static void renderCloth(LecternBlockEntity lectern, CharterLecternAccess access, int state, Direction facing,
                                    PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (lectern.getLevel() == null) return;
        ItemStack banner = clothStack(lectern, access, state);
        pose.pushPose();
        pose.translate(0.5F, 0.85F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0.0F, 0.0F, 0.47F);
        pose.scale(CLOTH_SCALE, -CLOTH_SCALE, -CLOTH_SCALE);
        //? if >=1.21 {
        var patterns = banner.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY);
        DyeColor base = banner.getItem() instanceof net.minecraft.world.item.BannerItem item ? item.getColor() : DyeColor.WHITE;
        BannerRenderer.renderPatterns(pose, buffers, light, overlay, cloth(), ModelBakery.BANNER_BASE, true, base, patterns);
        //?} else {
        /*DyeColor base = banner.getItem() instanceof net.minecraft.world.item.BannerItem item ? item.getColor() : DyeColor.WHITE;
        var patterns = net.minecraft.world.level.block.entity.BannerBlockEntity.createPatterns(base,
                net.minecraft.world.level.block.entity.BannerBlockEntity.getItemPatterns(banner));
        BannerRenderer.renderPatterns(pose, buffers, light, overlay, cloth(), ModelBakery.BANNER_BASE, true, patterns);
        *///?}
        pose.popPose();
    }

    /** The cloth's banner, rebuilt only when the lectern's design changes. */
    private static ItemStack clothStack(LecternBlockEntity lectern, CharterLecternAccess access, int state) {
        String key = state + "|" + (state == CharterLecternAccess.FOUNDED ? access.townstead$emblem() : "");
        if (clothStack != null && key.equals(clothKey)) return clothStack;
        ItemStack stack = state == CharterLecternAccess.FOUNDED
                ? EmblemItems.banner(lectern.getLevel().registryAccess(), EmblemRecipe.safe(access.townstead$emblem()))
                : new ItemStack(state == CharterLecternAccess.PREPARED
                        ? net.minecraft.world.item.Items.YELLOW_BANNER : net.minecraft.world.item.Items.WHITE_BANNER);
        clothKey = key;
        clothStack = stack;
        return stack;
    }

    /** The banner's flag, as the vanilla banner model lays it out on its texture. */
    private static ModelPart cloth() {
        if (cloth == null) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("flag", CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, 0.0F, -2.0F, 20.0F, 40.0F, 1.0F),
                    PartPose.ZERO);
            cloth = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("flag");
        }
        return cloth;
    }
}
