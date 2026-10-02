package com.aetherianartificer.townstead.rebirth;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.profession.career.CareerChoices;
import com.aetherianartificer.townstead.profession.career.PlayerCareers;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.root.ability.GeneAbilityTicker;
import com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fast relearning after a rebirth. The old life's careers are saved as a legacy, and the new person
 * starts over. Reading the journal sets targets: until a career is back at its old XP, work in it
 * pays several times over, with the daily allowance widened to match, and each old skill comes back
 * as soon as there is Insight for it.
 */
public final class Relearning {
    private Relearning() {}

    /** XP to award and how far the daily allowance widens for it. */
    public record Boost(int xp, int capScale) {}

    public static int speed() {
        return Math.max(1, Switchboard.get(TownsteadConfig.REBIRTH_RELEARN_SPEED));
    }

    /**
     * Saves the player's careers as the legacy of the life that ended, then starts them over.
     * Returns false, touching nothing, when there was nothing to keep.
     */
    static boolean capture(ServerPlayer player, UUID memorialId, String name) {
        CompoundTag profile = PlayerCareers.get(player).toTag();
        if (profile.getCompound("progress").isEmpty() && profile.getList("learned", Tag.TAG_STRING).isEmpty()) {
            return false;
        }
        CareerLegacies.get(player.server).putLegacy(memorialId, new CareerLegacies.Legacy(name, profile));
        List<Power> before = List.copyOf(Powers.active(player));
        PlayerCareers.reset(player);
        LearnedSkills.clear(player.getUUID());
        // Passive skill effects only undo themselves while their power is still granted.
        GeneAttributeApplier.removeFor(player, before);
        GeneAbilityTicker.resetPassives(player);
        return true;
    }

    /** Reads a journal: the legacy becomes the reader's relearning targets. */
    public static boolean read(ServerPlayer reader, UUID memorialId) {
        CareerLegacies data = CareerLegacies.get(reader.server);
        CareerLegacies.Legacy legacy = data.legacy(memorialId);
        if (legacy == null) return false;
        CompoundTag profile = legacy.profile();
        Map<String, Integer> xp = new LinkedHashMap<>();
        CompoundTag progress = profile.getCompound("progress");
        for (String career : progress.getAllKeys()) {
            int value = progress.getCompound(career).getInt("xp");
            if (value > 0) xp.put(career, value);
        }
        List<String> skills = new ArrayList<>();
        ListTag learned = profile.getList("learned", Tag.TAG_STRING);
        for (int i = 0; i < learned.size(); i++) skills.add(learned.getString(i));
        Map<String, String> active = new LinkedHashMap<>();
        CompoundTag activeTag = profile.getCompound("activeChoices");
        for (String group : activeTag.getAllKeys()) active.put(group, activeTag.getString(group));
        data.addTargets(reader.getUUID(), xp, skills, active);
        restore(reader);
        return true;
    }

    /** How much of a work award lands, for a player still climbing back in this career. */
    public static Boost boost(Player player, ResourceLocation career, int requested) {
        if (!(player instanceof ServerPlayer sp) || requested <= 0) return new Boost(requested, 1);
        CareerLegacies.Relearn target = CareerLegacies.get(sp.server).relearning(sp.getUUID());
        Integer goal = target == null ? null : target.xp().get(ProfessionDefs.canonicalId(career).toString());
        if (goal == null) return new Boost(requested, 1);
        int current = ProfessionProgress.getXp(PlayerCareers.xpStore(sp), career);
        int needed = goal - current;
        if (needed <= 0) return new Boost(requested, 1);
        int speed = speed();
        long boosted = (long) requested * speed;
        if (boosted <= needed) return new Boost((int) boosted, speed);
        // Only the part that closes the gap is sped up; the rest lands as usual.
        int spent = (needed + speed - 1) / speed;
        return new Boost(needed + Math.max(0, requested - spent), speed);
    }

    /** Takes back every old skill there is Insight for, then drops the targets already reached. */
    public static void restore(ServerPlayer player) {
        CareerLegacies data = CareerLegacies.get(player.server);
        CareerLegacies.Relearn target = data.relearning(player.getUUID());
        if (target == null) return;
        for (String raw : List.copyOf(target.skills())) {
            ResourceLocation skill = ResourceLocation.tryParse(raw);
            if (skill == null || LearnedSkills.has(player, skill)) continue;
            if (!CareerChoices.learn(player, skill).ok()) continue;
            SkillDef def = SkillDefs.byId(skill);
            player.displayClientMessage(Component.translatable("townstead.rebirth.relearn.skill",
                    def == null ? Component.literal(raw) : def.displayName()), false);
        }
        target.active().forEach((group, skill) -> {
            ResourceLocation groupId = ResourceLocation.tryParse(group);
            ResourceLocation skillId = ResourceLocation.tryParse(skill);
            if (groupId != null && skillId != null) CareerChoices.activate(player, groupId, skillId);
        });
        Map<String, Integer> reached = new LinkedHashMap<>();
        for (String career : target.xp().keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(career);
            if (id != null) reached.put(career, ProfessionProgress.getXp(PlayerCareers.xpStore(player), id));
        }
        Set<String> learned = new HashSet<>();
        for (ResourceLocation id : LearnedSkills.learned(player)) learned.add(id.toString());
        data.settle(player.getUUID(), reached, learned);
    }
}
