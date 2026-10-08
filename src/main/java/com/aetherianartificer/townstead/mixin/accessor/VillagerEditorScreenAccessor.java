package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VillagerEditorScreen.class)
public interface VillagerEditorScreenAccessor {
    /** MCA's own weighted hair-style reroll, so a policy-aware Random Hair still rolls the style. */
    @Invoker(value = "randomHairStyle", remap = false)
    void townstead$randomHairStyle();

    /**
     * MCA's hair colour mode flag: true shows its HSV dye sliders instead of the genetic picker.
     * MCA flips it on at load whenever the character carries any hair dye, which a policy-managed
     * character always does (palette) or must never do (genetic range).
     */
    @Accessor(value = "hsvColoredHair", remap = false)
    void townstead$setHsvColoredHair(boolean hsv);

    /**
     * The editor's second preview entity, used for the preset-compare panel and the
     * clothing/hair/skin selection grids. {@code getVillager()} only exposes the main
     * preview entity, but both render with the editor's wall-clock preview time.
     */
    @Accessor(value = "villagerVisualization", remap = false)
    VillagerEntityMCA townstead$getVillagerVisualization();
}
