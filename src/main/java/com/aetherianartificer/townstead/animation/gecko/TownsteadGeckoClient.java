package com.aetherianartificer.townstead.animation.gecko;

import com.aetherianartificer.townstead.animation.VillagerResponseAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
//?} else if forge {
/*import net.minecraftforge.eventbus.api.IEventBus;*/
//?}

public final class TownsteadGeckoClient {
    private TownsteadGeckoClient() {}

    //? if neoforge {
    public static void register(IEventBus modBus) {
    }
    //?} else if forge {
    /*public static void register(IEventBus modBus) {
    }*/
    //?}

    public static void triggerResponseAnimation(int entityId, VillagerResponseAnimation animation) {
        if (animation == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity == null) return;

        TownsteadVillagerReplacedGeoAnimatable animatable = resolveAnimatable(entity);
        if (animatable == null) return;

        animatable.triggerResponse(entity, animation);
    }

    public static TownsteadVillagerReplacedGeoAnimatable resolveAnimatable(Entity entity) {
        if (entity == null) return null;

        return switch (entity.getType().getDescriptionId()) {
            case "entity.mca.male_villager" -> TownsteadVillagerReplacedGeoAnimatable.MALE;
            case "entity.mca.female_villager" -> TownsteadVillagerReplacedGeoAnimatable.FEMALE;
            default -> null;
        };
    }
}
