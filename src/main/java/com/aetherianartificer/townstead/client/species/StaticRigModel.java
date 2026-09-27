package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * A custom-geometry rig body: a baked Bedrock {@code .geo.json} model with no built-in gait. Unlike the
 * vanilla-model generic rigs (spider, etc.), a custom model has no {@code setupAnim} to animate it, so this
 * is a static body posed by the rig's keyframe clips ({@link RigClips}), data poses ({@code applyRigPose})
 * and emotes. {@link HierarchicalModel} provides {@code renderToBuffer} (it draws {@link #root()}); we only
 * hold the baked root, and {@code setupAnim} returns every bone to rest.
 */
public class StaticRigModel<T extends LivingEntity> extends HierarchicalModel<T> {

    private final ModelPart root;

    public StaticRigModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // No built-in gait: start every frame from the baked rest pose (the model is shared, and clips,
        // data poses and emotes add onto it), then let those drive the bones.
        root.getAllParts().forEach(part -> {
            part.resetPose();
            part.xScale = 1f;
            part.yScale = 1f;
            part.zScale = 1f;
        });
    }
}
