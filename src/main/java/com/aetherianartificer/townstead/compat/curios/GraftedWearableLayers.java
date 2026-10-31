package com.aetherianartificer.townstead.compat.curios;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wearable mods that draw through their own render layer instead of a Curios renderer, but whose layer
 * is already written against any living entity with a humanoid model. Those layers can simply be added
 * to the villager renderer, and they then find the villager's worn item the same way they find a
 * player's. Each entry names the layer by string so nothing here needs the mod at compile time; a mod
 * that is absent, or whose layer no longer fits, is skipped with one log line.
 */
public final class GraftedWearableLayers {

    /** How to build one mod's layer for a renderer. */
    @FunctionalInterface
    private interface LayerFactory {
        Object build(RenderLayerParent<?, ?> renderer, EntityModelSet models) throws ReflectiveOperationException;
    }

    private record Graft(String modId, String layerClass, LayerFactory factory) {}

    private static final List<Graft> GRAFTS = List.of(
            new Graft("reliable_backpacks", "com.evandev.reliable_backpacks.client.rendering.BackpackLayer",
                    (renderer, models) -> Class.forName("com.evandev.reliable_backpacks.client.rendering.BackpackLayer")
                            .getConstructor(RenderLayerParent.class, EntityModelSet.class)
                            .newInstance(renderer, models))
    );

    /** Item namespaces whose worn look a grafted layer now owns, so no fallback draws them a second time. */
    private static final Set<String> CLAIMED = new HashSet<>();

    private GraftedWearableLayers() {}

    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity, M extends EntityModel<T>> void addAll(LivingEntityRenderer<T, M> renderer) {
        EntityModelSet models = Minecraft.getInstance().getEntityModels();
        for (Graft graft : GRAFTS) {
            if (!ModCompat.isLoaded(graft.modId())) continue;
            try {
                Object layer = graft.factory().build(renderer, models);
                renderer.addLayer((RenderLayer<T, M>) layer);
                CLAIMED.add(graft.modId());
            } catch (ReflectiveOperationException | RuntimeException e) {
                Townstead.LOGGER.warn("Could not add {}'s wearable layer to villagers ({}); its items will use the plain item look",
                        graft.modId(), graft.layerClass(), e);
            }
        }
    }

    /** Whether a grafted layer draws this item on villagers already. */
    public static boolean claims(ItemStack stack) {
        return !CLAIMED.isEmpty() && CLAIMED.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }
}
