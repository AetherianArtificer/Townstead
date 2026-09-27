package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.naming.Naming;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.RootAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.Set;

/**
 * Which villagers tell a story. Each listed field must match when present; an empty field matches
 * everyone. A story with nothing listed attaches to nobody.
 */
public record StoryAttach(Set<String> professions, Set<String> roots, Set<String> cultures,
                          Set<String> villagers, @Nullable Condition when) {

    public static final StoryAttach NONE = new StoryAttach(Set.of(), Set.of(), Set.of(), Set.of(), null);

    public boolean isEmpty() {
        return professions.isEmpty() && roots.isEmpty() && cultures.isEmpty() && villagers.isEmpty() && when == null;
    }

    public boolean matches(VillagerEntityMCA villager, Player player) {
        if (isEmpty()) return false;
        if (!villagers.isEmpty() && !villagers.contains(villager.getUUID().toString())) return false;
        if (!professions.isEmpty()) {
            ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
            if (key == null || !professions.contains(key.toString())) return false;
        }
        if (!roots.isEmpty() && !roots.contains(RootAssignment.currentRoot(villager))) return false;
        if (!cultures.isEmpty() && !cultures.contains(Naming.cultureOf(villager))) return false;
        return when == null || when.test(new ConditionContext(villager, player));
    }
}
