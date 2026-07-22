package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.pheno.capability.Capabilities;
import com.aetherianartificer.townstead.pheno.capability.CapabilityKey;
import com.aetherianartificer.townstead.pheno.capability.CapabilityView;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One work-completion path for player and NPC Career progression: XP (with skill-granted
 * capability bonuses), the semantic chronicle tap, and the acquisition sweep. Careers are keyed
 * by profession registry id; every work engine routes through here so all careers get the same
 * treatment. Advanced work also advances every ancestor career (a Pizzaiolo making pizza is
 * still cooking), so a specialist's parent history never stalls.
 */
public final class CareerProgression {
    private CareerProgression() {}

    public static ProfessionProgress.GainResult completeWork(
            LivingEntity worker, ResourceLocation career, int baseXp, long gameTime,
            String chronicleVerb, ResourceLocation objectId, String paramName, float magnitude) {
        return completeWork(worker, career, baseXp, gameTime, chronicleVerb, objectId,
                paramName, magnitude, Map.of());
    }

    public static ProfessionProgress.GainResult completeWork(
            LivingEntity worker, ResourceLocation career, int baseXp, long gameTime,
            String chronicleVerb, ResourceLocation objectId, String paramName, float magnitude,
            Map<String, String> semanticParams) {
        career = ProfessionDefs.canonicalId(career);
        ProfessionXpStore store = store(worker);
        if (store == null) return new ProfessionProgress.GainResult(0, 1, 1, false);
        setPrimaryIfAbsent(worker, career);
        int xp = withSkillBonus(worker, career, baseXp, magnitude);
        ProfessionProgress.GainResult result = ProfessionProgress.addXp(store, career, xp, gameTime);
        Set<ResourceLocation> affected = careerAndAncestors(career);
        for (ResourceLocation ancestor : affected) {
            if (!ancestor.equals(career)) ProfessionProgress.addXp(store, ancestor, xp, gameTime);
        }
        ChronicleTaps.work(worker, chronicleVerb, objectId, paramName, magnitude, semanticParams);
        for (ResourceLocation acquired : CareerAcquisitions.acquireEligible(
                worker, "self_discovery", affected)) {
            if (worker instanceof Player player) {
                ProfessionDef def = ProfessionDefs.byId(acquired);
                player.displayClientMessage(Component.translatable(
                        "townstead.career.acquired",
                        def == null ? Component.literal(acquired.toString()) : def.displayName()), false);
            }
        }
        if (result.tierUp() && worker instanceof VillagerEntityMCA) {
            SkillPoints.autoSpend(worker, affected);
        }
        if (worker instanceof Player player) {
            CareerProfile profile = CareerProfiles.of(player);
            if (profile != null) {
                for (ResourceLocation trackedId : profile.trackedCareers()) {
                    ProfessionDef def = ProfessionDefs.byId(trackedId);
                    if (def == null || def.isRoot()
                            || profile.acquiredCareers().contains(trackedId)
                            || def.parents().stream().noneMatch(affected::contains)
                            || !def.eligible(worker)) continue;
                    player.displayClientMessage(Component.translatable(
                            "townstead.career.tracked.ready", def.displayName()), false);
                    PlayerCareers.mutate(player, stored -> stored.untrack(trackedId));
                }
            }
        }
        return result;
    }

    /** The career plus every ancestor through the def parent graph, cycle-safe. */
    private static Set<ResourceLocation> careerAndAncestors(ResourceLocation career) {
        Set<ResourceLocation> visited = new LinkedHashSet<>();
        Deque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(career);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.poll();
            if (!visited.add(current)) continue;
            ProfessionDef def = ProfessionDefs.byId(current);
            if (def != null) pending.addAll(def.parents());
        }
        return visited;
    }

    /**
     * Skill-granted XP tuning, resolved through the shared capability layer per career:
     * {@code townstead:<career>_xp_flat} adds once per completion and
     * {@code townstead:<career>_xp_per_tier} adds per point of work magnitude.
     */
    private static int withSkillBonus(LivingEntity worker, ResourceLocation career,
                                      int baseXp, float magnitude) {
        CapabilityView capabilities = Capabilities.resolve(worker);
        double flat = capabilities.numeric(
                CapabilityKey.additive(id("townstead", career.getPath() + "_xp_flat")), 0d);
        double perTier = capabilities.numeric(
                CapabilityKey.additive(id("townstead", career.getPath() + "_xp_per_tier")), 0d);
        return Math.max(1, baseXp + (int) Math.floor(flat + perTier * magnitude));
    }

    private static ProfessionXpStore store(LivingEntity worker) {
        if (worker instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).professionMemory();
        }
        if (worker instanceof Player player) return PlayerCareers.xpStore(player);
        return null;
    }

    private static void setPrimaryIfAbsent(LivingEntity worker, ResourceLocation career) {
        if (worker instanceof VillagerEntityMCA villager) {
            CareerProfile profile = TownsteadVillagers.get(villager).professionMemory().careerProfile();
            if (profile.primaryVocation() == null) {
                TownsteadVillagers.get(villager).professionMemory().setPrimaryVocation(career);
            }
        } else if (worker instanceof Player player) {
            PlayerCareers.mutate(player, profile -> {
                if (profile.primaryVocation() == null) profile.setPrimaryVocation(career);
            });
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
