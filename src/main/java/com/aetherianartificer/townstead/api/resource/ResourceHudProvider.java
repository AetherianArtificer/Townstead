package com.aetherianartificer.townstead.api.resource;

import com.aetherianartificer.townstead.root.gene.types.ResourceDisplay;
import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Read-only integration seam for another mod's mana, energy, charge, or similar value.
 * Providers retain ownership of their gameplay state; Townstead only presents returned meters.
 */
@FunctionalInterface
public interface ResourceHudProvider {

    void collect(LivingEntity entity, List<Meter> output);

    record Meter(ResourceLocation id, int value, int min, int max, int restingValue,
                 int color,
                 ResourceDisplay.Shape shape, ResourceDisplay.FillMode fillMode,
                 ResourceDisplay.PipStyle pipStyle,
                 ResourceLocation frame, ResourceLocation colorTheme,
                 List<ResourceDisplay.BarEffect> effects,
                 List<ResourceDisplay.BarReaction> reactions,
                 boolean abilityReady, int regenerationSequence,
                 ResourceDisplay.Anchor anchor, int segments, int priority) {
        public Meter {
            if (id == null) throw new IllegalArgumentException("Resource HUD meter id cannot be null");
            shape = shape == null ? ResourceDisplay.Shape.HORIZONTAL : shape;
            fillMode = fillMode == null ? ResourceDisplay.FillMode.CONTINUOUS : fillMode;
            pipStyle = pipStyle == null ? ResourceDisplay.PipStyle.DOTS : pipStyle;
            frame = frame == null ? DataPackLang.parseId("townstead:plain") : frame;
            colorTheme = colorTheme == null ? DataPackLang.parseId("townstead:arcane") : colorTheme;
            effects = effects == null ? List.of() : List.copyOf(effects);
            reactions = reactions == null ? List.of() : List.copyOf(reactions);
            anchor = anchor == null ? ResourceDisplay.Anchor.TOP_LEFT : anchor;
            segments = Math.max(2, Math.min(64, segments));
        }
    }
}
