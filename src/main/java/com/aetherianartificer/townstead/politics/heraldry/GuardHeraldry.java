package com.aetherianartificer.townstead.politics.heraldry;

import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The arms a villager's standard-issue shield carries: those of the nearest body they belong to that
 * has proclaimed an emblem. That is their settlement first, then its faction, then each faction
 * above it up to the sovereign. With none proclaimed, the shield stays plain.
 */
public final class GuardHeraldry {
    private GuardHeraldry() {}

    /** A plain shield comes back painted with the villager's arms; anything else comes back as it was. */
    public static ItemStack dress(VillagerEntityMCA villager, ItemStack stack) {
        if (!stack.is(Items.SHIELD) || !plain(stack) || !(villager.level() instanceof ServerLevel level)) return stack;
        EmblemRecipe arms = arms(level, villager);
        if (arms == null) return stack;
        ItemStack dressed = EmblemItems.stampedCopy(level.registryAccess(), stack, arms);
        return dressed.isEmpty() ? stack : dressed;
    }

    static @Nullable EmblemRecipe arms(ServerLevel level, VillagerEntityMCA villager) {
        var home = villager.getResidency().getHomeVillage().orElse(null);
        if (home == null) return null;
        SettlementRef settlement = new SettlementRef(level.dimension().location(), home.getId());
        HeraldrySavedData heraldry = HeraldrySavedData.get(level.getServer());
        EmblemRecipe found = proclaimed(level, heraldry, HeraldryService.settlement(settlement));
        if (found != null) return found;
        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        Faction faction = politics.faction(settlement);
        ResourceLocation current = faction == null || !faction.active() ? null : faction.id();
        Set<ResourceLocation> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            found = proclaimed(level, heraldry, HeraldryService.faction(current));
            if (found != null) return found;
            current = FactionBonds.parent(politics, current);
        }
        return null;
    }

    private static @Nullable EmblemRecipe proclaimed(ServerLevel level, HeraldrySavedData heraldry, String actor) {
        var entry = heraldry.get(actor);
        return entry.revision() > 0 && EmblemItems.valid(level.registryAccess(), entry.recipe()) ? entry.recipe() : null;
    }

    /** Only an undecorated shield is painted, so a real one handed to a guard keeps its own design. */
    private static boolean plain(ItemStack stack) {
        //? if >=1.21 {
        return stack.getComponentsPatch().isEmpty();
        //?} else {
        /*return !stack.hasTag();
        *///?}
    }
}
