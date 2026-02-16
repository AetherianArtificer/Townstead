package com.aetherianartificer.townstead.hunger.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

final class ButcherProfileFallbacks {
    private ButcherProfileFallbacks() {}

    static ButcherProfileDefinition defaultL1() {
        Set<ResourceLocation> inputs = new LinkedHashSet<>();
        inputs.add(ResourceLocation.withDefaultNamespace("chicken"));
        inputs.add(ResourceLocation.withDefaultNamespace("rabbit"));
        inputs.add(ResourceLocation.withDefaultNamespace("cod"));
        inputs.add(ResourceLocation.withDefaultNamespace("salmon"));

        Set<ResourceLocation> fuels = new LinkedHashSet<>();
        fuels.add(ResourceLocation.withDefaultNamespace("charcoal"));
        fuels.add(ResourceLocation.withDefaultNamespace("coal"));
        fuels.add(ResourceLocation.withDefaultNamespace("blaze_rod"));
        fuels.add(ResourceLocation.withDefaultNamespace("dried_kelp_block"));
        fuels.add(ResourceLocation.withDefaultNamespace("coal_block"));

        Set<ResourceLocation> outputs = new LinkedHashSet<>();
        outputs.add(ResourceLocation.withDefaultNamespace("cooked_chicken"));
        outputs.add(ResourceLocation.withDefaultNamespace("cooked_rabbit"));
        outputs.add(ResourceLocation.withDefaultNamespace("cooked_cod"));
        outputs.add(ResourceLocation.withDefaultNamespace("cooked_salmon"));

        return new ButcherProfileDefinition(
                "smokehouse_core_l1",
                ButcherProfileRegistry.DEFAULT_PROFILE_ID,
                1,
                1,
                ButcherProfileDefinition.normalizeSet(inputs),
                Set.of(),
                ButcherProfileDefinition.normalizeSet(fuels),
                Set.of(),
                ButcherProfileDefinition.normalizeSet(outputs),
                1.0d,
                1.0d,
                1.0d
        );
    }
}
