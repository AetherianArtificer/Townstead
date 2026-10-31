package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.profession.ProfessionAutoAssign;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;

/** Runs the task-based auto-assignment policies that Townstead owns. */
public final class ProfessionAutoAssignTicker {
    private static final int ASSIGN_INTERVAL_TICKS = 200;
    private static final ResourceLocation COOK = ResourceLocation.tryParse("townstead:cook");
    private static final ResourceLocation BEVERAGE_ARTISAN =
            ResourceLocation.tryParse("townstead:beverage_artisan");

    private ProfessionAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager == null || villager.tickCount % ASSIGN_INTERVAL_TICKS != 0) return;
        for (ProfessionDef def : ProfessionDefs.all().values().stream()
                .filter(ProfessionAutoAssign::managesDefinition)
                .sorted(Comparator.comparingInt(ProfessionAutoAssignTicker::priority)
                        .thenComparing(ProfessionDef::id))
                .toList()) {
            ProfessionAutoAssign.tick(villager, def, ProfessionAutoAssign.enabled(def), 0);
        }
    }

    // Preserve the former Cook -> Beverage Artisan precedence; all pack careers then sort by id.
    private static int priority(ProfessionDef def) {
        if (COOK.equals(def.id())) return 0;
        if (BEVERAGE_ARTISAN.equals(def.id())) return 1;
        return 2;
    }
}
