package com.aetherianartificer.townstead.compat.hats;

import com.aetherianartificer.townstead.clothing.ClothingChannel;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingSources;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Hats Renewed keeps hats as records, not items: a player's collection and an equipped hat, and
 * one hat per entity keyed by UUID. Villagers already spawn with them. This bridge reads a
 * villager's hat as a worn accessory and lets a player give the hat they are wearing to a
 * villager, who keeps it. Everything is reflected; the mod is not on the compile path.
 */
public final class HatsCompat {

    public static final String MOD_ID = "hats";
    public static final String SOURCE = "townstead:hats";

    public enum Gift { GIVEN, SWAPPED, NO_HAT, UNAVAILABLE }

    private static boolean initialised;
    private static Method savedDataGet;
    private static Method getPlayer;
    private static Field equippedHat;
    private static Method getEntityHat;
    private static Method setEntityHat;
    private static Method removeEquipped;
    private static Method removeOneFromInventory;
    private static Method addHatToInventory;
    private static Method broadcastEntityHat;
    private static Method syncInventory;
    private static Method hatCopy;
    private static Field hatName;

    private HatsCompat() {}

    public static boolean present() {
        return ModCompat.isLoaded(MOD_ID);
    }

    public static void bootstrap() {
        if (!present()) return;
        ClothingSources.register(new WornHat());
    }

    /** The villager's hat record, or null. */
    public static @Nullable Object entityHat(Level level, UUID entity) {
        initIfNeeded();
        if (savedDataGet == null) return null;
        try {
            Object data = savedDataGet.invoke(null, level);
            return data == null ? null : getEntityHat.invoke(data, entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Gives the player's equipped hat to the villager. The hat leaves the player's collection;
     * a hat the villager already wore comes back into it, so a gift is never a loss for the
     * village. Both sides are re-synced through the mod's own packets.
     */
    public static Gift give(ServerPlayer player, VillagerEntityMCA villager) {
        if (player == null || villager == null) return Gift.UNAVAILABLE;
        initIfNeeded();
        if (savedDataGet == null) return Gift.UNAVAILABLE;
        try {
            Object data = savedDataGet.invoke(null, villager.level());
            if (data == null) return Gift.UNAVAILABLE;
            Object playerData = getPlayer.invoke(data, player.getUUID());
            Object worn = playerData == null ? null : equippedHat.get(playerData);
            if (worn == null) return Gift.NO_HAT;
            String name = (String) hatName.get(worn);
            Object previous = getEntityHat.invoke(data, villager.getUUID());

            Object gift = hatCopy.invoke(worn);
            removeEquipped.invoke(data, player.getUUID());
            removeOneFromInventory.invoke(data, player.getUUID(), name);
            setEntityHat.invoke(data, villager.getUUID(), gift);
            if (previous != null) addHatToInventory.invoke(data, player.getUUID(), hatCopy.invoke(previous));

            broadcastEntityHat.invoke(null, villager);
            syncInventory.invoke(null, player, data);
            return previous != null ? Gift.SWAPPED : Gift.GIVEN;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Gift.UNAVAILABLE;
        }
    }

    public static Component message(Gift result) {
        switch (result) {
            case GIVEN: return Component.translatable("townstead.hats.given");
            case SWAPPED: return Component.translatable("townstead.hats.swapped");
            case NO_HAT: return Component.translatable("townstead.hats.no_hat");
            default: return Component.translatable("townstead.hats.unavailable");
        }
    }

    private static synchronized void initIfNeeded() {
        if (initialised) return;
        initialised = true;
        if (!present()) return;
        try {
            Class<?> savedData = Class.forName("me.guivnf.mods.hats.common.world.HatsSavedData");
            Class<?> playerData = Class.forName("me.guivnf.mods.hats.common.world.PlayerHatData");
            Class<?> hatPart = Class.forName("me.guivnf.mods.hats.common.hat.HatPart");
            Class<?> network = Class.forName("me.guivnf.mods.hats.common.network.NetworkHelper");
            savedDataGet = savedData.getMethod("get", Level.class);
            getPlayer = savedData.getMethod("getPlayer", UUID.class);
            getEntityHat = savedData.getMethod("getEntityHat", UUID.class);
            setEntityHat = savedData.getMethod("setEntityHat", UUID.class, hatPart);
            removeEquipped = savedData.getMethod("removeEquipped", UUID.class);
            removeOneFromInventory = savedData.getMethod("removeOneFromInventory", UUID.class, String.class);
            addHatToInventory = savedData.getMethod("addHatToInventory", UUID.class, hatPart);
            equippedHat = playerData.getField("equippedHat");
            hatCopy = hatPart.getMethod("copy");
            hatName = hatPart.getField("name");
            broadcastEntityHat = network.getMethod("broadcastEntityHat", LivingEntity.class);
            syncInventory = network.getMethod("syncInventory", ServerPlayer.class, savedData);
        } catch (ReflectiveOperationException | RuntimeException e) {
            savedDataGet = null;
        }
    }

    /** A villager's hat as a worn accessory on the head, with no stack and no thermal value. */
    private static final class WornHat implements ClothingSources.Source {
        @Override
        public String id() {
            return SOURCE;
        }

        @Override
        public void collect(LivingEntity entity, Consumer<WornPiece> out) {
            if (!(entity instanceof VillagerEntityMCA villager) || villager.level().isClientSide) return;
            if (entityHat(villager.level(), villager.getUUID()) == null) return;
            out.accept(WornPiece.ofRecord(ClothingLayer.ACCESSORY, ClothingChannel.HEAD, null, SOURCE));
        }
    }
}
