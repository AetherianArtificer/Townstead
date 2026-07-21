package com.aetherianartificer.townstead.profession.career;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;

/**
 * Player persistence adapter for the same CareerProfile used by villagers. Reads go through a
 * parsed cache because the power and capability layers resolve at tick rate; every mutation
 * writes through the cache to the persistent root tag, and logout invalidates.
 */
public final class PlayerCareers {
    private static final String KEY = "townsteadCareerProfile";
    private static final Map<UUID, CareerProfile> CACHE = new ConcurrentHashMap<>();

    private PlayerCareers() {}

    /** Read-only use. Mutations must go through {@link #mutate} or they will not persist. */
    public static CareerProfile get(Player player) {
        return CACHE.computeIfAbsent(player.getUUID(),
                uuid -> CareerProfile.fromTag(root(player).getCompound(KEY)));
    }

    public static void mutate(Player player, Consumer<CareerProfile> mutation) {
        CareerProfile profile = get(player);
        mutation.accept(profile);
        CompoundTag root = root(player);
        root.put(KEY, profile.toTag());
        store(player, root);
    }

    /** Drop the parsed cache entry; the persistent tag is untouched. Wired to player logout. */
    public static void invalidate(UUID uuid) {
        CACHE.remove(uuid);
    }

    public static ProfessionXpStore xpStore(Player player) {
        return new ProfessionXpStore() {
            @Override public ProfessionXp professionXp(String professionId) {
                return get(player).professionXp(professionId);
            }
            @Override public void setProfessionXp(String professionId, ProfessionXp value) {
                mutate(player, profile -> profile.setProfessionXp(professionId, value));
            }
        };
    }

    private static CompoundTag root(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA);
        //?} else {
        /*return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        *///?}
    }

    private static void store(Player player, CompoundTag root) {
        //? if neoforge {
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA, root);
        //?} else {
        /*player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        *///?}
    }
}
