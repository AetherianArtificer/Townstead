package com.aetherianartificer.townstead.animation.gecko;

import com.aetherianartificer.townstead.animation.VillagerResponseAnimation;
//? if neoforge {
import net.conczin.mca.registry.EntitiesMCA;
//?} else if forge {
/*import forge.net.mca.entity.EntitiesMCA;*/
//?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
//? if neoforge {
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
//?} else if forge {
/*import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;*/
/*import software.bernie.geckolib.core.object.PlayState;*/
//?}
import software.bernie.geckolib.util.GeckoLibUtil;

public final class TownsteadVillagerReplacedGeoAnimatable implements GeoReplacedEntity {
    //? if neoforge {
    public static final TownsteadVillagerReplacedGeoAnimatable MALE = new TownsteadVillagerReplacedGeoAnimatable(EntitiesMCA.MALE_VILLAGER);
    public static final TownsteadVillagerReplacedGeoAnimatable FEMALE = new TownsteadVillagerReplacedGeoAnimatable(EntitiesMCA.FEMALE_VILLAGER);
    //?} else if forge {
    /*public static final TownsteadVillagerReplacedGeoAnimatable MALE = new TownsteadVillagerReplacedGeoAnimatable(EntitiesMCA.MALE_VILLAGER.get());
    public static final TownsteadVillagerReplacedGeoAnimatable FEMALE = new TownsteadVillagerReplacedGeoAnimatable(EntitiesMCA.FEMALE_VILLAGER.get());*/
    //?}

    private final EntityType<?> replacedEntityType;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private TownsteadVillagerReplacedGeoAnimatable(EntityType<?> replacedEntityType) {
        this.replacedEntityType = replacedEntityType;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return this.replacedEntityType;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        //? if neoforge {
        controllers.add(new AnimationController<>(this, TownsteadVillagerAnimationClips.RESPONSE_CONTROLLER, state -> {
                    if (state.getController().isPlayingTriggeredAnimation()) {
                        return PlayState.CONTINUE;
                    }

                    return state.setAndContinue(TownsteadVillagerAnimationClips.NEUTRAL_IDLE);
                }).receiveTriggeredAnimations()
                .triggerableAnim(VillagerResponseAnimation.NEUTRAL_IDLE.id(), TownsteadVillagerAnimationClips.NEUTRAL_IDLE)
                .triggerableAnim(VillagerResponseAnimation.WAVE_BACK.id(), TownsteadVillagerAnimationClips.WAVE_BACK)
                .triggerableAnim(VillagerResponseAnimation.CAUTIOUS_ACK.id(), TownsteadVillagerAnimationClips.CAUTIOUS_ACK)
                .triggerableAnim(VillagerResponseAnimation.APPLAUD.id(), TownsteadVillagerAnimationClips.APPLAUD)
                .triggerableAnim(VillagerResponseAnimation.BASHFUL.id(), TownsteadVillagerAnimationClips.BASHFUL)
                .triggerableAnim(VillagerResponseAnimation.COMFORT.id(), TownsteadVillagerAnimationClips.COMFORT)
                .triggerableAnim(VillagerResponseAnimation.AWKWARD.id(), TownsteadVillagerAnimationClips.AWKWARD)
                .triggerableAnim(VillagerResponseAnimation.ACKNOWLEDGE.id(), TownsteadVillagerAnimationClips.ACKNOWLEDGE)
                .triggerableAnim(VillagerResponseAnimation.PUZZLED.id(), TownsteadVillagerAnimationClips.PUZZLED)
                .triggerableAnim(VillagerResponseAnimation.AMUSED.id(), TownsteadVillagerAnimationClips.AMUSED)
                .triggerableAnim(VillagerResponseAnimation.STARTLED.id(), TownsteadVillagerAnimationClips.STARTLED));
        //?} else if forge {
        /*controllers.add(new AnimationController<>(this, TownsteadVillagerAnimationClips.RESPONSE_CONTROLLER, state -> {
                    if (state.getController().isPlayingTriggeredAnimation()) {
                        return PlayState.CONTINUE;
                    }

                    return state.setAndContinue(TownsteadVillagerAnimationClips.NEUTRAL_IDLE);
                })
                .receiveTriggeredAnimations()
                .triggerableAnim(VillagerResponseAnimation.NEUTRAL_IDLE.id(), TownsteadVillagerAnimationClips.NEUTRAL_IDLE)
                .triggerableAnim(VillagerResponseAnimation.WAVE_BACK.id(), TownsteadVillagerAnimationClips.WAVE_BACK)
                .triggerableAnim(VillagerResponseAnimation.CAUTIOUS_ACK.id(), TownsteadVillagerAnimationClips.CAUTIOUS_ACK)
                .triggerableAnim(VillagerResponseAnimation.APPLAUD.id(), TownsteadVillagerAnimationClips.APPLAUD)
                .triggerableAnim(VillagerResponseAnimation.BASHFUL.id(), TownsteadVillagerAnimationClips.BASHFUL)
                .triggerableAnim(VillagerResponseAnimation.COMFORT.id(), TownsteadVillagerAnimationClips.COMFORT)
                .triggerableAnim(VillagerResponseAnimation.AWKWARD.id(), TownsteadVillagerAnimationClips.AWKWARD)
                .triggerableAnim(VillagerResponseAnimation.ACKNOWLEDGE.id(), TownsteadVillagerAnimationClips.ACKNOWLEDGE)
                .triggerableAnim(VillagerResponseAnimation.PUZZLED.id(), TownsteadVillagerAnimationClips.PUZZLED)
                .triggerableAnim(VillagerResponseAnimation.AMUSED.id(), TownsteadVillagerAnimationClips.AMUSED)
                .triggerableAnim(VillagerResponseAnimation.STARTLED.id(), TownsteadVillagerAnimationClips.STARTLED));*/
        //?}
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object relatedObject) {
        if (relatedObject instanceof Entity entity) {
            return entity.tickCount;
        }

        return 0;
    }

    public void triggerResponse(Entity entity, VillagerResponseAnimation animation) {
        String triggerName = TownsteadVillagerAnimationClips.triggerName(animation);
        if (entity == null || triggerName == null || triggerName.isBlank()) return;

        triggerAnim(entity, TownsteadVillagerAnimationClips.RESPONSE_CONTROLLER, triggerName);
    }
}
