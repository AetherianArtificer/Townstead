package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.politics.relations.FactionMembership;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Where a person's order keeps its altar. */
public final class OrderPlaces {
    private OrderPlaces() {}

    /** The altar of the order {@code person} trains for, else of an order they belong to. */
    public static @Nullable GlobalPos altar(ServerLevel level, LivingEntity person) {
        OrderAltars altars = OrderAltars.get(level.getServer());
        ResourceLocation training = OrderRecruits.get(level.getServer()).orderOf(person.getUUID());
        if (training != null && altars.altar(training) != null) return altars.altar(training);
        for (ResourceLocation order : FactionMembership.of(person)) {
            GlobalPos altar = altars.altar(order);
            if (altar != null) return altar;
        }
        return null;
    }
}
