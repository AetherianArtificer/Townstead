package com.aetherianartificer.townstead.compat.jade;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Replaces Jade's growth timer on MCA villagers. That timer reads the vanilla breeding age, which
 * Townstead pins to the calendar life stage, so it never counts down. This class is only ever
 * loaded by Jade's plugin scan, so it is safe when Jade is absent.
 */
@WailaPlugin
public class TownsteadJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(NextStageProvider.INSTANCE, VillagerEntityMCA.class);
    }

    private enum NextStageProvider implements IEntityComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.tryParse("townstead:life_stage");
        private static final ResourceLocation MOB_GROWTH = ResourceLocation.tryParse("minecraft:mob_growth");

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof VillagerEntityMCA villager)) return;
            tooltip.remove(MOB_GROWTH);
            // Adult and senior both present as MCA's ADULT: nothing left to grow into.
            if (villager.getAgeState() == AgeState.ADULT) return;

            LifeClientStore.Snapshot life = LifeClientStore.get(villager.getId());
            if (life == null || !life.hasCycle() || life.immortal() || life.ageless()) return;
            int current = life.currentStageIndex();
            if (current < 0 || current + 1 >= life.stageCount()) return;

            int stageEnd = 0;
            for (int i = 0; i <= current; i++) stageEnd += Math.max(0, life.stageDays()[i]);
            int days = Math.max(1, stageEnd - life.bioAgeDays());
            tooltip.add(Component.translatable(
                    days == 1 ? "townstead.jade.next_stage.one" : "townstead.jade.next_stage.many",
                    life.stageLabel(current + 1), days));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        // After Jade's own growth provider, so its line exists to be removed.
        @Override
        public int getDefaultPriority() {
            return 1000;
        }
    }
}
