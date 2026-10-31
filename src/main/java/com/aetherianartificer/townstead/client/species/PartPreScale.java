package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.model.geom.ModelPart;

/**
 * A model part's scale applied before its rotation, in the parent's frame, where the part's own
 * {@code xScale}/{@code yScale}/{@code zScale} apply after it. Cancelling a non-uniform scale
 * from outside the part has to happen here: cancelled after the rotation, a pitched head or a
 * swinging limb comes out sheared. Carried by {@code copyFrom}; see {@code ModelPartPreScaleMixin}.
 */
public interface PartPreScale {

    void townstead$setPreScale(float x, float y, float z);

    float townstead$preScaleX();

    float townstead$preScaleY();

    static void set(ModelPart part, float x, float y, float z) {
        ((PartPreScale) (Object) part).townstead$setPreScale(x, y, z);
    }

    /** The part's whole sideways scale, before and after its rotation. */
    static float totalX(ModelPart part) {
        return ((PartPreScale) (Object) part).townstead$preScaleX() * part.xScale;
    }

    /** The part's whole vertical scale, before and after its rotation. */
    static float totalY(ModelPart part) {
        return ((PartPreScale) (Object) part).townstead$preScaleY() * part.yScale;
    }
}
