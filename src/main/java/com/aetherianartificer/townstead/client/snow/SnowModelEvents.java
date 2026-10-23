package com.aetherianartificer.townstead.client.snow;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.SnowCoatedBlock;
import com.aetherianartificer.townstead.snow.SnowCoating;
import com.aetherianartificer.townstead.snow.SnowOverlayDefinitions;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.Property;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.QuadTransformers;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.QuadTransformers;
*///?}
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class SnowModelEvents {
    private SnowModelEvents() {}

    private static ResourceLocation id(String name) {
        //? if >=1.21 {
        return ResourceLocation.parse(name);
        //?} else {
        /*return new ResourceLocation(name);
        *///?}
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        if (!SnowCoating.active()) return;
        SnowOverlayDefinitions.load().values().stream().flatMap(List::stream)
                .map(v -> v.shape().model()).distinct().forEach(name -> {
                    //? if >=1.21 {
                    event.register(ModelResourceLocation.standalone(id(name)));
                    //?} else {
                    /*event.register(id(name));
                    *///?}
                });
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        if (!SnowCoating.active()) return;
        Map<SnowOverlayDefinitions.Shape, List<BakedQuad>> cache = new HashMap<>();
        SnowOverlayDefinitions.load().forEach((name, variants) -> {
            var block = BuiltInRegistries.BLOCK.get(id(name));
            if (!(block instanceof SnowCoatedBlock)) return;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!state.getValue(SnowCoatedBlock.SNOW_COATED)) continue;
                Map<String, String> properties = new HashMap<>();
                state.getValues().forEach((key, value) -> properties.put(key.getName(), propertyName(key, value)));
                var variant = variants.stream().filter(v -> v.matches(properties)).findFirst().orElse(null);
                if (variant == null) {
                    Townstead.LOGGER.warn("[Snow] No snow overlay for {}", state);
                    continue;
                }
                var location = BlockModelShaper.stateToModelLocation(state);
                BakedModel base = event.getModels().get(location);
                if (base == null) continue;
                var quads = cache.computeIfAbsent(variant.shape(), shape -> {
                    //? if >=1.21 {
                    var overlay = event.getModels().get(ModelResourceLocation.standalone(id(shape.model())));
                    //?} else {
                    /*var overlay = event.getModels().get(id(shape.model()));
                    *///?}
                    if (overlay == null) {
                        Townstead.LOGGER.warn("[Snow] Missing baked snow overlay {}", shape.model());
                        return List.of();
                    }
                    return rotate(overlay, shape);
                });
                event.getModels().put(location, new SnowCoatedModel(base, quads));
                // Vanilla groups states using the JSON before wrappers are installed. A coating
                // change must invalidate the chunk mesh even though its base JSON is identical.
                event.getModelBakery().getModelGroups().removeInt(state);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyName(Property property, Comparable value) {
        return property.getName(value);
    }

    /** Rotate about the block center exactly as the original blockstate does, including lighting normals. */
    private static List<BakedQuad> rotate(BakedModel model, SnowOverlayDefinitions.Shape shape) {
        var rotation = BlockModelRotation.by(shape.x(), shape.y()).getRotation();
        var transform = QuadTransformers.applying(new Transformation(new Matrix4f()
                .translation(0.5f, 0.5f, 0.5f).mul(rotation.getMatrix()).translate(-0.5f, -0.5f, -0.5f)));
        List<BakedQuad> source = new ArrayList<>(model.getQuads(null, null, RandomSource.create(0)));
        // Snow caps are thin surface details, so keep their faces unculled even on touching blocks.
        for (Direction face : Direction.values()) source.addAll(model.getQuads(null, face, RandomSource.create(0)));
        return source.stream().map(quad -> {
            BakedQuad moved = transform.process(quad);
            Direction direction = Direction.rotate(rotation.getMatrix(), quad.getDirection());
            //? if >=1.21 {
            return new BakedQuad(moved.getVertices(), moved.getTintIndex(), direction, moved.getSprite(),
                    moved.isShade(), moved.hasAmbientOcclusion());
            //?} else {
            /*return new BakedQuad(moved.getVertices(), moved.getTintIndex(), direction, moved.getSprite(), moved.isShade());
            *///?}
        }).toList();
    }
}
