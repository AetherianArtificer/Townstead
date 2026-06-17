package com.aetherianartificer.townstead.origin;

/**
 * A species' held-item anchoring: per hand, which rig bone the item is gripped from, plus an
 * offset (model pixels) and rotation (degrees) nudge on top of the standard grip frame. Authored as
 * {@code "hold": { "mainhand": { "bone": "right_arm", "offset": [x,y,z], "rotation": [x,y,z] }, ... }}.
 *
 * <p>A null {@link Grip} (hand key omitted) means that hand cannot hold, so its item is not
 * rendered. {@link #NONE} (both hands null) means the rig holds nothing. The bone name is resolved
 * against the rig model's baked parts client-side by the rig render layer; for a wolf both hands
 * would name {@code "head"}, for a spider a front leg, and so on.</p>
 */
public record Hold(Grip mainhand, Grip offhand) {

    public static final Hold NONE = new Hold(null, null);

    /** One hand's grip: the anchor bone plus an offset (pixels) and rotation (degrees) nudge. */
    public record Grip(String bone, float[] offset, float[] rotation) {
        public Grip {
            offset = offset == null || offset.length < 3 ? new float[]{0f, 0f, 0f} : offset;
            rotation = rotation == null || rotation.length < 3 ? new float[]{0f, 0f, 0f} : rotation;
        }
    }
}
