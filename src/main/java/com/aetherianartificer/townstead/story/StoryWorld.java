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

/** World lookups for the Ink helpers that name people and places. */
final class StoryWorld {
    private StoryWorld() {}

    /**
     * {@code who("cook")}: the name of a resident of the teller's village, other than the teller,
     * whose profession has that id or path. Empty when nobody fits.
     */
    static String who(String role, GoalContext ctx) {
        // who("visitor:fledgling"): the visitor with that role near the teller.
        if (role.startsWith("visitor:")) {
            VillagerEntityMCA visitor = ctx.speaker() == null ? null
                    : com.aetherianartificer.townstead.visitor.Visitors.near(ctx.speaker(), role.substring("visitor:".length()), 64);
            return visitor == null ? "" : VillagerNames.display(visitor).getString();
        }
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

    /**
     * {@code building("raised")}: the building the player most recently raised in the teller's
     * village, as a name token the client shows in the reader's language. {@code "upgraded"} for
     * the last one upgraded, or {@code ""} for either. Empty when the player has done neither there.
     */
    static String building(String kind, GoalContext ctx) {
        Optional<com.aetherianartificer.townstead.api.v1.model.VillageId> village = ctx.villageId(true);
        if (village.isEmpty()) return "";
        String prefix = kind.isBlank() ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT) + ":";
        var settlement = new com.aetherianartificer.townstead.politics.state.SettlementRef(
                village.get().dimension(), village.get().villageId());
        for (String key : com.aetherianartificer.townstead.politics.standing.DeedLedger.get(ctx.server())
                .recent(settlement, ctx.playerId())) {
            if (!key.startsWith("raised:") && !key.startsWith("upgraded:")) continue;
            if (!prefix.isEmpty() && !key.startsWith(prefix)) continue;
            String[] parts = key.split(":", 3);
            if (parts.length == 3 && !parts[2].isEmpty()) return StoryText.building(parts[2]);
        }
        return "";
    }

    /** The name of the first resident of the teller's village, other than the teller, that {@code condition} holds for. */
    static String whoMatching(Condition condition, net.minecraft.world.entity.LivingEntity player, GoalContext ctx) {
        Optional<Village> village = ctx.village();
        if (village.isEmpty()) return "";
        return village.get().getResidentsUUIDs()
                .filter(id -> !id.equals(ctx.speakerId()))
                .sorted()
                .map(id -> find(ctx, id))
                .filter(v -> v != null && condition.test(new ConditionContext(v, player)))
                .findFirst()
                .map(v -> VillagerNames.display(v).getString())
                .orElse("");
    }

    /** The resident of the teller's village shown under {@code name}, or null. */
    static @org.jetbrains.annotations.Nullable VillagerEntityMCA named(String name, GoalContext ctx) {
        Optional<Village> village = ctx.village();
        if (name.isBlank() || village.isEmpty()) return null;
        return village.get().getResidentsUUIDs()
                .map(id -> find(ctx, id))
                .filter(v -> v != null && name.equals(VillagerNames.display(v).getString()))
                .findFirst()
                .orElse(null);
    }

    private static VillagerEntityMCA find(GoalContext ctx, UUID id) {
        for (ServerLevel level : ctx.server().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) return villager;
        }
        return null;
    }
}
