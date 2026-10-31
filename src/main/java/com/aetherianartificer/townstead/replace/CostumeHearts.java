package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.root.disposition.Disposition;
import com.aetherianartificer.townstead.root.disposition.Dispositions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Hearts with the person a costumed mob shows. The person is a whole MCA villager kept in the
 * costume, so hearts live in its own MCA memories: a daily talk and MCA's own gifts move them.
 * At the replacement's {@code settle_hearts} the person settles for good, as a cure would turn them.
 */
public final class CostumeHearts {
    private static final String TALKED = "townstead:costume_talked";
    private static final int FRIENDLY_TALK = 3;
    private static final int NEUTRAL_TALK = 1;
    private static final int DEFAULT_SETTLE = 25;

    private CostumeHearts() {}

    /** A conversation with a costumed person: the first each day warms them a little. */
    public static void talked(ServerPlayer player, VillagerEntityMCA person) {
        Mob mob = WildCostume.wearer(person);
        if (mob == null) return;
        long day = TownsteadCalendar.worldDay(player.server);
        CompoundTag talked = player.getPersistentData().getCompound(TALKED);
        String key = mob.getUUID().toString();
        if (talked.contains(key) && talked.getLong(key) == day) return;
        talked.putLong(key, day);
        player.getPersistentData().put(TALKED, talked);
        Disposition stance = Dispositions.between(mob, player);
        int gain = stance == Disposition.FRIENDLY ? FRIENDLY_TALK : stance == Disposition.NEUTRAL ? NEUTRAL_TALK : 0;
        if (gain == 0) return;
        Memories memories = person.getVillagerBrain().getMemoriesForPlayer(player);
        memories.modHearts(gain);
        WildCostume.dress(mob, person);
        settleIfReady(player, mob, memories);
    }

    /** A sneaking click with something in hand: MCA's own gift, given to the person. False when not one. */
    public static boolean gift(ServerPlayer player, Mob mob) {
        if (!player.isShiftKeyDown() || player.getMainHandItem().isEmpty() || !WildCostume.has(mob)) return false;
        if (Dispositions.between(mob, player) == Disposition.HOSTILE) return false;
        VillagerEntityMCA person = WildCostume.person(mob);
        if (person == null) return false;
        Memories memories = person.getVillagerBrain().getMemoriesForPlayer(player);
        person.getRelationships().giveGift(player, memories);
        WildCostume.dress(mob, person);
        settleIfReady(player, mob, memories);
        return true;
    }

    private static void settleIfReady(ServerPlayer player, Mob mob, Memories memories) {
        VillagerEntityMCA person = WildCostume.person(mob);
        MobReplacement replacement = person == null ? null : MobReplacer.replacementOf(person);
        int needed = replacement == null ? DEFAULT_SETTLE : replacement.settleHearts();
        if (memories.getHearts() < needed) return;
        VillagerEntityMCA settled = WildCostume.convert(mob, true);
        if (settled == null) return;
        player.displayClientMessage(Component.translatable("message.townstead.costume.settled",
                settled.getDisplayName()), false);
    }
}
