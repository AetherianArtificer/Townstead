package com.aetherianartificer.townstead.assign;

import com.aetherianartificer.townstead.root.ability.AbilityNames;
import com.aetherianartificer.townstead.root.ability.ActiveAbilities;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Townstead's own abilities, offered through the same seam a datapack uses.
 *
 * <p>This is the point of the exercise. Our abilities could resolve directly, since the slot code
 * already knows how to fire them, and that shortcut is exactly what would let the extension path rot
 * unnoticed: every other source would get whatever this one did not need. Going through the
 * interface means the seam is exercised on every press by the built-in case.</p>
 *
 * <p>Registered FIRST, so a datapack cannot shadow a built-in ability by claiming its id.</p>
 */
public final class AbilityAssignableProvider implements AssignableProvider {

    @Override
    public void collect(ServerPlayer player, List<Assignable> out) {
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (ActiveAbilities.Slotted slotted : ActiveAbilities.arrangeable(player)) {
            ResourceLocation id = slotted.geneId();
            if (!seen.add(id)) continue;
            int cooldown = 0;
            int costAmount = 0;
            String costLabel = "";
            int costColor = 0;
            if (slotted.instance() instanceof com.aetherianartificer.townstead.root.gene.types
                    .ActiveAbilityGeneType.Instance active) {
                cooldown = Math.max(0, active.cooldownTicks());
                if (active.costResource() != null && active.costAmount() > 0) {
                    costAmount = active.costAmount();
                    costLabel = AbilityNames.resource(active.costResource());
                    costColor = ResourceValues.colorOf(player, active.costResource());
                }
            }
            out.add(new Assignable(id, Component.literal(AbilityNames.display(id)),
                    AbilityNames.icon(id), Component.literal(AbilityNames.source(id)),
                    Assignable.Kind.ABILITY, cooldown, costAmount, costLabel, costColor, ""));
        }
    }

    @Override
    public boolean invoke(ServerPlayer player, ResourceLocation id) {
        // Ownership is "is it slottable for this player", the same question collect() asks, so the
        // two can never disagree about what this provider is responsible for.
        for (ActiveAbilities.Slotted slotted : ActiveAbilities.slottables(player)) {
            if (slotted.geneId().equals(id)) {
                return ActiveAbilities.fireSlotted(player, slotted);
            }
        }
        return false;
    }
}
