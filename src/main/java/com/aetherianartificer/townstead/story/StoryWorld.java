package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.naming.VillagerNames;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.story.goal.GoalContext;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/** World lookups for the Ink helpers that name people. */
final class StoryWorld {
    private StoryWorld() {}

    /**
     * {@code who("cook")}: the name of a resident of the teller's village, other than the teller,
     * whose profession has that id or path. Empty when nobody fits.
     */
    static String who(String role, GoalContext ctx) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "pheno:profession");
        json.addProperty("profession", role.contains(":") ? role : "minecraft:" + role);
        Condition profession = Conditions.parse(json);
        Optional<Village> village = ctx.village();
        if (profession == null || village.isEmpty()) return "";
        return village.get().getResidentsUUIDs()
                .filter(id -> !id.equals(ctx.speakerId()))
                .sorted()
                .map(id -> find(ctx, id))
                .filter(v -> v != null && profession.test(new ConditionContext(v)))
                .findFirst()
                .map(v -> VillagerNames.display(v).getString())
                .orElse("");
    }

    private static VillagerEntityMCA find(GoalContext ctx, UUID id) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) return villager;
        }
        return null;
    }
}
