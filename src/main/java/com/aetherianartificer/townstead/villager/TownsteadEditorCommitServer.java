package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.FatigueSyncPayload;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class TownsteadEditorCommitServer {
    private TownsteadEditorCommitServer() {}

    public record Result(
            VillagerEntityMCA villager,
            HungerSyncPayload hungerSync,
            ThirstSyncPayload thirstSync,
            FatigueSyncPayload fatigueSync,
            com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync
    ) {}

    public static Result apply(ServerPlayer player, TownsteadEditorCommitPayload payload) {
        if (payload == null || payload.isEmpty()) return null;
        VillagerEntityMCA villager = findVillager(player.getServer(), payload.villagerUuid());
        if (villager == null) return null;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        HungerSyncPayload hungerSync = null;
        ThirstSyncPayload thirstSync = null;
        FatigueSyncPayload fatigueSync = null;
        boolean lifeChanged = false;

        if (payload.hasHunger()) {
            TownsteadVillager.Needs needs = state.needs();
            needs.setHunger(payload.hunger());
            needs.setSaturation(payload.saturation());
            needs.setHungerExhaustion(payload.hungerExhaustion());
            hungerSync = Townstead.townstead$hungerSync(villager, needs.hungerTag());
        }

        if (payload.hasThirst() && ThirstBridgeResolver.isActive()) {
            TownsteadVillager.Needs needs = state.needs();
            needs.setThirst(payload.thirst());
            needs.setQuenched(payload.quenched());
            needs.setThirstExhaustion(payload.thirstExhaustion());
            thirstSync = Townstead.townstead$thirstSync(villager, needs.thirstTag());
        }

        if (payload.hasFatigue()) {
            TownsteadVillager.Needs needs = state.needs();
            needs.setFatigue(payload.fatigue());
            if (payload.fatigue() < FatigueData.COLLAPSE_THRESHOLD) {
                needs.setCollapsed(false);
            }
            if (payload.fatigue() < FatigueData.RECOVERY_GATE) {
                needs.setGated(false);
            }
            fatigueSync = Townstead.townstead$fatigueSync(villager, needs.fatigueTag());
        }

        if (payload.hasBirthday()) {
            TownsteadVillager.Life life = state.life();
            int month = Math.max(1, payload.birthMonth());
            int day = Math.max(1, payload.birthDay());
            if (month != life.birthMonth() || day != life.birthDay()) {
                life.setCelebratedBirthday(month, day);
                lifeChanged = true;
            }
        }

        if (payload.hasBioAge()) {
            MinecraftServer server = player.getServer();
            long newBirth = TownsteadCalendar.lifeDay(server) - Math.max(0, payload.bioAgeDays());
            LifeStageProgression.applyManualAgeEdit(villager, newBirth);
            lifeChanged = true;
        }

        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync =
                lifeChanged ? Townstead.townstead$lifeSync(villager, player) : null;

        // Editor changes must reach the entity-backed snapshot before the
        // player can leave or the entity unloads. The runtime state is the
        // source of truth while loaded, but it is deliberately not persisted
        // by each individual setter.
        TownsteadVillagers.flush(villager);
        return new Result(villager, hungerSync, thirstSync, fatigueSync, lifeSync);
    }

    private static VillagerEntityMCA findVillager(MinecraftServer server, java.util.UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof VillagerEntityMCA villager) return villager;
        }
        return null;
    }
}
