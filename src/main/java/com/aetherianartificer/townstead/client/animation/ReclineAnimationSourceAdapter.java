package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceSampler;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativeClipRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** Furniture chooses the resting clip; gesture layers can still animate the upper body. */
public final class ReclineAnimationSourceAdapter implements AnimationSourceAdapter {
    public record Profile(String clip, float angle) {}

    public static Profile profile(LivingEntity entity) {
        if (!(entity instanceof net.conczin.mca.entity.VillagerEntityMCA)) return null;
        var mount = entity.getVehicle();
        if (mount == null) return null;
        for (var pos : List.of(mount.blockPosition(), mount.blockPosition().below())) {
            BlockState state = entity.level().getBlockState(pos);
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (block.equals("beachparty:beach_sun_lounger")) {
                var down = state.getBlock().getStateDefinition().getProperty("down");
                boolean raised = down != null && "false".equals(state.getValues().get(down).toString());
                return raised ? new Profile("recline_lounger", 65F) : new Profile("recline", 90F);
            }
            if (block.equals("beachparty:beach_towel")) return new Profile("recline", 90F);
        }
        return null;
    }

    @Override public String id() { return "recline"; }
    @Override public boolean isAvailable() { return true; }
    @Override public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        Profile profile = profile(context.entity());
        if (profile == null) return List.of();
        var clip = NativeClipRegistry.getBedrock(ResourceLocation.tryParse("townstead_performance:" + profile.clip())).orElse(null);
        return clip == null ? List.of() : BedrockPerformanceSampler.sample(clip,
                context.animationProgress() + 4F, Float.MAX_VALUE, AnimationTargetMap.forMcaModel(context.model()));
    }
}
