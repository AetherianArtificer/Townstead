package com.aetherianartificer.townstead.client.blueprint;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.item.BlueprintStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Gives a styled MCA blueprint the look of its surface. Each {@code <ns>:models/item/blueprint/<name>.json}
 * is the model for surface {@code <ns>:<name>}: layer0 is the surface, layer1 the line art tinted by the
 * ink. MCA's model is wrapped, never replaced, so unstyled blueprints keep MCA's art.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class BlueprintModelEvents {
    private static final String DIR = "models/item/blueprint/";
    private static final Map<ResourceLocation, ResourceLocation> SURFACE_MODELS = new HashMap<>();

    private BlueprintModelEvents() {}

    @SubscribeEvent
    public static void register(ModelEvent.RegisterAdditional event) {
        SURFACE_MODELS.clear();
        BlueprintStyle.knownSurfaces().clear();
        Minecraft.getInstance().getResourceManager()
                .listResources("models/item/blueprint", file -> file.getPath().endsWith(".json"))
                .keySet().forEach(file -> {
                    String name = file.getPath().substring(DIR.length(), file.getPath().length() - ".json".length());
                    if (name.contains("/")) return;
                    ResourceLocation model = id(file.getNamespace(), "item/blueprint/" + name);
                    SURFACE_MODELS.put(id(file.getNamespace(), name), model);
                    BlueprintStyle.knownSurfaces().add(id(file.getNamespace(), name));
                    //? if >=1.21 {
                    event.register(ModelResourceLocation.standalone(model));
                    //?} else {
                    /*event.register(model);
                    *///?}
                });
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        //? if >=1.21 {
        ModelResourceLocation location = ModelResourceLocation.inventory(BlueprintStyle.MCA_BLUEPRINT);
        //?} else {
        /*ModelResourceLocation location = new ModelResourceLocation(BlueprintStyle.MCA_BLUEPRINT, "inventory");
        *///?}
        BakedModel base = event.getModels().get(location);
        if (base == null) return;
        Map<ResourceLocation, BakedModel> surfaces = new HashMap<>();
        SURFACE_MODELS.forEach((surface, model) -> {
            //? if >=1.21 {
            BakedModel baked = event.getModels().get(ModelResourceLocation.standalone(model));
            //?} else {
            /*BakedModel baked = event.getModels().get(model);
            *///?}
            if (baked != null) surfaces.put(surface, baked);
        });
        event.getModels().put(location, new StyledBlueprintModel(base, surfaces));
    }

    @SubscribeEvent
    public static void tint(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            BlueprintStyle style = BlueprintStyle.of(stack);
            return style == null ? -1 : 0xFF000000 | style.inkColor();
        }, BlueprintStyle.blueprintItem());
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    private static final class StyledBlueprintModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        StyledBlueprintModel(BakedModel original, Map<ResourceLocation, BakedModel> surfaces) {
            super(original);
            this.overrides = new ItemOverrides() {
                @Override
                public @Nullable BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                                    @Nullable LivingEntity entity, int seed) {
                    BlueprintStyle style = BlueprintStyle.of(stack);
                    if (style != null && !style.matchesMcaArt()) {
                        BakedModel surface = surfaces.get(style.surface());
                        if (surface != null) return surface;
                    }
                    return original.getOverrides().resolve(model, stack, level, entity, seed);
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
