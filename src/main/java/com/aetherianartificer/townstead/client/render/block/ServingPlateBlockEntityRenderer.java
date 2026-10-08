package com.aetherianartificer.townstead.client.render.block;

import com.aetherianartificer.townstead.block.ServingPlateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Treats the plate as a small display surface. Placeable dishes use their real world block model;
 * ordinary foods use their item model. This keeps mod-specific pizzas, cakes, pies, and feasts out
 * of the plate implementation itself.
 */
public final class ServingPlateBlockEntityRenderer implements BlockEntityRenderer<ServingPlateBlockEntity> {
    private static final Set<String> CONSUMED_PORTION_PROPERTIES = Set.of("slices", "bites", "servings");

    private final ItemRenderer items;
    private final BlockRenderDispatcher blocks;

    public ServingPlateBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ServingPlateBlockEntity plate, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack display = plate.displayStack();
        if (display.isEmpty()) return;
        if (renderBlockDish(plate, display, pose, buffers, light, overlay)) return;

        pose.pushPose();
        pose.translate(0.5, 0.115, 0.5);
        pose.scale(0.72f, 0.72f, 0.72f);
        items.renderStatic(display, ItemDisplayContext.GROUND,
                light, overlay, pose, buffers, plate.getLevel(), 0);
        pose.popPose();
    }

    private boolean renderBlockDish(ServingPlateBlockEntity plate, ItemStack display, PoseStack pose,
                                    MultiBufferSource buffers, int light, int overlay) {
        if (!(display.getItem() instanceof BlockItem blockItem)) return false;

        BlockState state = applyConsumedPortions(blockItem.getBlock().defaultBlockState(), plate);
        BlockEntity visualEntity = createVisualEntity(blockItem, state, display, plate);
        Object modelData = visualEntity == null ? null : getModelData(visualEntity);

        pose.pushPose();
        pose.translate(0.5, 0.0825, 0.5);
        pose.scale(0.78f, 0.78f, 0.78f);
        pose.translate(-0.5, 0.0, -0.5);
        boolean rendered = renderWithModelData(state, pose, buffers, light, overlay, modelData);
        pose.popPose();
        return rendered;
    }

    private static BlockState applyConsumedPortions(BlockState state, ServingPlateBlockEntity plate) {
        int consumed = Math.max(0, plate.originalPortions() - plate.portions());
        if (consumed == 0) return state;
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof IntegerProperty integer)
                    || !CONSUMED_PORTION_PROPERTIES.contains(property.getName())) continue;
            int value = integer.getPossibleValues().stream()
                    .filter(candidate -> candidate <= consumed)
                    .max(Integer::compareTo)
                    .orElse(integer.getPossibleValues().iterator().next());
            return state.setValue(integer, value);
        }
        return state;
    }

    private static BlockEntity createVisualEntity(BlockItem item, BlockState state, ItemStack display,
                                                   ServingPlateBlockEntity plate) {
        if (!(item.getBlock() instanceof EntityBlock entityBlock)) return null;
        BlockEntity visual = entityBlock.newBlockEntity(BlockPos.ZERO, state);
        if (visual == null) return null;
        visual.setLevel(plate.getLevel());
        copyItemComponents(visual, display);
        return visual;
    }

    /** Uses the same component hand-off that BlockItem placement uses, when present in this MC version. */
    private static void copyItemComponents(BlockEntity visual, ItemStack display) {
        try {
            Method method = BlockEntity.class.getMethod("applyComponentsFromItemStack", ItemStack.class);
            method.invoke(visual, display);
        } catch (ReflectiveOperationException ignored) {
            // 1.20 has no component system. Its ordinary block model can still be shown.
        }
    }

    private static Object getModelData(BlockEntity visual) {
        try {
            return visual.getClass().getMethod("getModelData").invoke(visual);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Forge and NeoForge both add a seven-argument model-data overload, but the ModelData class
     * lives in different packages. Reflection lets the shared renderer support both without a
     * compatibility dependency. The vanilla overload remains the universal fallback.
     */
    private boolean renderWithModelData(BlockState state, PoseStack pose, MultiBufferSource buffers,
                                        int light, int overlay, Object modelData) {
        if (modelData != null) {
            for (Method method : blocks.getClass().getMethods()) {
                if (!method.getName().equals("renderSingleBlock") || method.getParameterCount() != 7
                        || !method.getParameterTypes()[5].isInstance(modelData)) continue;
                try {
                    method.invoke(blocks, state, pose, buffers, light, overlay, modelData, null);
                    return true;
                } catch (ReflectiveOperationException ignored) {
                    break;
                }
            }
        }
        blocks.renderSingleBlock(state, pose, buffers, light, overlay);
        return true;
    }
}
