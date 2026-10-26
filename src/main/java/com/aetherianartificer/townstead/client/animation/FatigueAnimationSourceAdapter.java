package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceSampler;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativeClipRegistry;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativePlaybackRegistry;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Persistent upper-body fatigue layer; walking keeps ownership of every leg channel. */
public final class FatigueAnimationSourceAdapter implements AnimationSourceAdapter {
    @Override public String id() { return "fatigue"; }
    @Override public boolean isAvailable() { return true; }

    public static boolean eligible(int fatigue, boolean asleep, boolean mounted, boolean collapsed) {
        return fatigue >= FatigueData.TIRED_THRESHOLD && !asleep && !mounted && !collapsed;
    }

    /** A staggered 45-second cycle, with one 3.8-second yawn. */
    public static float phase(long tick, int seed, float partial) {
        return Math.floorMod(tick + Math.floorMod(seed, 900), 900) + partial;
    }

    /** Shared with EMF arbitration so its later render pass cannot erase this layer. */
    public static boolean ownsPose(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof VillagerEntityMCA && entity.isAlive()
                && !entity.isFallFlying()
                && com.aetherianartificer.townstead.TownsteadConfig.isVillagerFatigueEnabled()
                && FatigueClientStore.hasFatigue(entity.getId())
                && eligible(FatigueClientStore.getFatigue(entity.getId()), entity.isSleeping(), entity.isPassenger(),
                        FatigueClientStore.isCollapsed(entity.getId())
                                || NativePlaybackRegistry.hasCollapse(entity.getId(), entity.level().getGameTime()));
    }

    @Override public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        var entity = context.entity();
        long now = entity.level().getGameTime();
        if (!ownsPose(entity)) return List.of();
        float partial = context.animationProgress() - entity.tickCount;
        float phase = phase(now, entity.getUUID().hashCode(), partial);
        boolean yawn = phase < 76;
        String name = yawn ? "fatigue_yawn" : "fatigue_tired";
        //? if neoforge {
        var id = ResourceLocation.fromNamespaceAndPath("townstead_performance", name);
        //?} else {
        /*var id = new ResourceLocation("townstead_performance", name);
        *///?}
        var clip = NativeClipRegistry.getBedrock(id).orElse(null);
        if (clip == null) return List.of();
        // Both clips belong to a persistent posture. Sample without the one-shot
        // fade to upright walking, since the yawn's endpoints already match tired.
        var continuous = new com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip(
                clip.durationTicks(), com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip.Loop.LOOP,
                clip.bones());
        float elapsed = clip.durationTicks() + (yawn ? phase : (phase - 76) % clip.durationTicks());
        return BedrockPerformanceSampler.sample(continuous, elapsed, Float.MAX_VALUE,
                AnimationTargetMap.forMcaModel(context.model()));
    }
}
