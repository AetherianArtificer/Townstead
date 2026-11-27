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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

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
        // A player's editor targets themselves, and naming is all it commits here.
        if (player.getUUID().equals(payload.villagerUuid())) {
            if (payload.hasTradition()) com.aetherianartificer.townstead.naming.PlayerNames.setIdentity(player, payload.culture(), payload.tradition());
            if (payload.hasFamilyName()) com.aetherianartificer.townstead.naming.PlayerNames.setFamily(player, payload.familyName());
            return null;
        }
        VillagerEntityMCA villager = editableVillager(player, payload.villagerUuid());
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

        // A name another mod holds cannot be typed over. The publish below still corrects the screen.
        if (payload.hasFamilyName() && !com.aetherianartificer.townstead.naming.VillagerNames.parts(villager).familyLocked()) {
            TownsteadVillager.Life life = state.life();
            String typed = payload.familyName() == null ? "" : payload.familyName().trim();
            life.setFamilyName(typed);
            // A name somebody typed outranks a name a rule produced, so it is marked as chosen and
            // nothing derived replaces it afterwards. Emptying the field hands the name back to the
            // villager's tradition, which settles a new one the next time it is asked for.
            life.setFamilyNameFixed(!typed.isEmpty());
        }

        if (payload.hasTradition()) {
            com.aetherianartificer.townstead.naming.Naming.reassign(player.serverLevel(), villager, payload.culture(), payload.tradition());
        }

        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync =
                lifeChanged ? Townstead.townstead$lifeSync(villager, player) : null;

        // Editor changes must reach the entity-backed snapshot before the
        // player can leave or the entity unloads. The runtime state is the
        // source of truth while loaded, but it is deliberately not persisted
        // by each individual setter.
        TownsteadVillagers.flush(villager);
        // After the flush, so every surface that draws a name is told at once: the nameplate, the
        // dialogue header, and MCA Capitals' identity where it is installed.
        if (payload.hasFamilyName() || payload.hasTradition()) {
            com.aetherianartificer.townstead.naming.VillagerNames.publish(villager);
        }
        return new Result(villager, hungerSync, thirstSync, fatigueSync, lifeSync);
    }

    // Same gate as MCA's VillagerEditorSyncRequest: a living villager in the sender's level,
    // and the sender is an operator or holds the Villager Editor.
    private static VillagerEntityMCA editableVillager(ServerPlayer player, java.util.UUID uuid) {
        if (uuid == null) return null;
        Entity entity = player.serverLevel().getEntity(uuid);
        // A zombie villager is edited through the person they still are; its state saves to the zombie.
        if (entity instanceof net.minecraft.world.entity.Mob mob && !(entity instanceof VillagerEntityMCA)
                && mob.isAlive()) {
            entity = ZombifiedVillagers.person(mob);
        }
        if (!(entity instanceof VillagerEntityMCA villager) || !villager.isAlive()) return null;
        if (player.hasPermissions(2)
                || isVillagerEditor(player.getMainHandItem())
                || isVillagerEditor(player.getOffhandItem())) {
            return villager;
        }
        return null;
    }

    private static boolean isVillagerEditor(ItemStack stack) {
        return !stack.isEmpty() && VILLAGER_EDITOR_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static final String VILLAGER_EDITOR_ID = "mca:villager_editor";
}
