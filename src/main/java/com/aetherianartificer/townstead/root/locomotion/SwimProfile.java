package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A swimmer's resolved movement. Aquatic roots are the ones with {@code water_breathing}
 * or {@code swimming}; everyone else keeps MCA's own water handling and has no profile.
 * An authored {@code style} on either gene wins (the more aquatic one, if both name one);
 * otherwise a water-breather dives and a strong swimmer who must breathe paddles.
 *
 * @param breathes whether the bearer breathes water, so dives are not limited by air
 * @param explicit whether a gene named the style
 */
public record SwimProfile(SwimStyle style, boolean breathes, boolean explicit) {

    /** The bearer's swim profile, or null for a non-swimmer. Scans active genes; call at decision points. */
    @Nullable
    public static SwimProfile of(LivingEntity entity) {
        boolean breathes = false;
        boolean swims = false;
        SwimStyle authored = null;
        for (Power power : Powers.active(entity)) {
            if (!(power.component() instanceof AbilityGeneType.Instance ability)) continue;
            if (ability.ability() == Ability.WATER_BREATHING) breathes = true;
            else if (ability.ability() == Ability.SWIMMING) swims = true;
            else continue;
            if (ability.swimStyle() != null && (authored == null || ability.swimStyle().ordinal() > authored.ordinal())) {
                authored = ability.swimStyle();
            }
        }
        if (!breathes && !swims) return null;
        if (authored != null) return new SwimProfile(authored, breathes, true);
        return new SwimProfile(breathes ? SwimStyle.DIVE : SwimStyle.PADDLE, breathes, false);
    }

    /** One line for debug reports. */
    public String describe() {
        return "swim " + style.key() + (explicit ? "" : " (inferred)") + (breathes ? ", breathes water" : ", holds breath");
    }
}
