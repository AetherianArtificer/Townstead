package com.aetherianartificer.townstead.chronicle.world;

import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.LifeCycle;
import com.aetherianartificer.townstead.root.LifeStage;

/**
 * What life stage a subject presented as at a given apparent age. A fabricated
 * past has to answer this about a person's younger self, which no live stage
 * lookup can do, and it must answer it per Root: a role gated on {@code adult}
 * means eighteen for a human-like cycle and something else entirely for a Root
 * whose adulthood starts at eighty.
 */
@FunctionalInterface
public interface AgeBands {

    CanonicalStage stageAt(float apparentYears);

    /** {@link CanonicalStage}'s own default human-like bands (baby 0-2 … senior 65+). */
    AgeBands DEFAULT = years -> {
        for (CanonicalStage stage : CanonicalStage.values()) {
            if (years < stage.defaultNarrativeEnd()) return stage;
        }
        return CanonicalStage.SENIOR;
    };

    /** The bands a Root's authored life cycle declares, falling back to the defaults. */
    static AgeBands of(LifeCycle cycle) {
        if (cycle == null || cycle.isEmpty()) return DEFAULT;
        return years -> {
            LifeStage last = null;
            for (int i = 0; i < cycle.size(); i++) {
                LifeStage stage = cycle.stageAt(i);
                if (years < stage.narrativeEnd()) return stage.presentsAs();
                last = stage;
            }
            return last == null ? CanonicalStage.ADULT : last.presentsAs();
        };
    }
}
