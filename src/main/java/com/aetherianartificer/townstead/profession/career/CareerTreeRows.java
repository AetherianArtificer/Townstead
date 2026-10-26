package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the server-rendered career tree (careers by parent graph, evidence counters, skill
 * choices) shared by the Career screen payload and the {@code /townstead career} readout.
 * States follow progressive disclosure: a hidden, unmet specialization renders as such
 * rather than exposing its recipe.
 */
public final class CareerTreeRows {
    private CareerTreeRows() {}

    /** One server-rendered display row; rows with a skill id are actionable choices. */
    public record Row(int depth, String text, String skillId) {}

    public static List<Row> build(MinecraftServer server, LivingEntity entity) {
        List<Row> rows = new ArrayList<>();
        CareerProfile profile = CareerProfiles.of(entity);
        ProfessionXpStore store = store(entity);
        if (profile == null || store == null) return rows;
        rows.add(new Row(0, "Primary vocation: "
                + (profile.primaryVocation() == null ? "Unchosen" : profile.primaryVocation()), ""));
        // Careers are flat: every def lists at the top level.
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            appendCareer(rows, server, entity, profile, store, def, 0);
        }
        return rows;
    }

    private static void appendCareer(List<Row> rows, MinecraftServer server,
                                     LivingEntity entity, CareerProfile profile,
                                     ProfessionXpStore store, ProfessionDef def, int depth) {
        if (depth > 8) return;
        ResourceLocation careerId = def.id();
        int xp = ProfessionProgress.getXp(store, careerId);
        boolean primary = careerId.equals(profile.primaryVocation());
        boolean acquired = def.isRoot() ? (xp > 0 || primary) : profile.acquiredCareers().contains(careerId);
        final String state;
        if (def.isRoot()) {
            int next = ProfessionProgress.getXpToNextTier(store, careerId);
            state = "tier " + ProfessionProgress.getTier(store, careerId) + ", " + xp + " XP"
                    + (next > 0 ? " (" + next + " to next tier)" : " (mastered)");
        } else if (acquired) {
            state = "acquired, tier " + ProfessionProgress.getTier(store, careerId) + ", " + xp + " XP";
        } else {
            state = def.eligible(entity) ? "ready to discover" : def.hidden() ? "hidden, locked" : "locked";
        }
        boolean interesting = xp > 0 || primary || acquired || !def.isRoot();
        if (interesting) {
            rows.add(new Row(depth, def.displayName().getString()
                    + (primary ? " [primary]" : "") + ": " + state, ""));
            if (xp > 0 || acquired) {
                for (String counter : CareerActivities.counters(def)) {
                    int count = Chronicles.count(server, entity.getUUID(), counter);
                    rows.add(new Row(depth + 1,
                            "history: " + counter + " = " + count, ""));
                }
            }
            if (acquired) {
                for (ResourceLocation choice : def.skills()) {
                    SkillDef skill = SkillDefs.byId(choice);
                    boolean active = CareerChoices.isActive(entity, choice);
                    String name = skill == null ? choice.toString() : skill.displayName().getString();
                    rows.add(new Row(depth + 1,
                            (active ? "[equipped] " : "[available] ") + name, choice.toString()));
                }
            }
        }
    }

    public static ProfessionXpStore storeOf(LivingEntity entity) {
        return store(entity);
    }

    private static ProfessionXpStore store(LivingEntity entity) {
        if (entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
            return com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).professionMemory();
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return PlayerCareers.xpStore(player);
        }
        return null;
    }
}
