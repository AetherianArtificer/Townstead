package com.aetherianartificer.townstead.compat.mcadescendants;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.rebirth.Rebirth;
import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.root.RootServerLogic;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works with MCA Descendants, which lets a player continue as one of their descendants. Townstead
 * never reimplements that: when Descendants handles a death, Townstead's own rebirth stands aside;
 * otherwise the death screen can hand off to it. Either way, Townstead carries its own parts across
 * Descendants' swap: the old life becomes a memorial with a journal, and the player takes on the
 * descendant's Root and name.
 *
 * <p>Nothing here links against Descendants. Its classes are reached by name, so Townstead builds and
 * runs without it, and a Descendants update that moves something only disables the hand-off.</p>
 */
public final class DescendantsBridge {
    private DescendantsBridge() {}

    private static final String MOD_ID = "mca_descendants";
    private static final String PKG = "net.dannyfather.mca_descendants.";

    private static final Map<UUID, DamageSource> LAST_SOURCE = new ConcurrentHashMap<>();
    private static final Map<UUID, Before> BEFORE = new ConcurrentHashMap<>();

    /** The player's name, where the old life ended, and the Root of the villager about to be taken over. */
    private record Before(String name, @Nullable GlobalPos journalAt, @Nullable String targetRoot) {}

    public static boolean installed() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /** True when Descendants takes this player's deaths itself: in hardcore, or when set for all worlds. */
    public static boolean handlesDeaths(Player player) {
        return installed() && (player.level().getLevelData().isHardcore() || !hardcoreOnly());
    }

    /** True when the death screen may hand this player's respawn to Descendants. */
    public static boolean canHandOff(Player player) {
        return installed() && !handlesDeaths(player);
    }

    public static void rememberDeath(ServerPlayer player, DamageSource source) {
        if (!installed()) return;
        LAST_SOURCE.put(player.getUUID(), source);
    }

    /** Set while Townstead runs Descendants' handlers for a hand-off, so their hardcore-only check passes. */
    private static final ThreadLocal<Boolean> HANDING_OFF = ThreadLocal.withInitial(() -> false);

    /**
     * Hands a player who chose "Continue as a descendant" to MCA Descendants by running its own death
     * and respawn handlers for them, exactly as if Descendants had taken the death itself. Whatever
     * those handlers do in a given Descendants version happens here too.
     */
    public static void handOff(ServerPlayer player) {
        UUID id = player.getUUID();
        try {
            ServerLevel level = player.serverLevel();
            // Descendants names players through the family tree and reads that name at death.
            if (!player.hasCustomName()) {
                player.setCustomName(Component.literal(FamilyTree.get(level).getOrCreate(player).getName()));
            }
            DamageSource source = LAST_SOURCE.getOrDefault(id, level.damageSources().generic());
            Class<?> events = Class.forName(PKG + "events.MCADescendantsEvents");
            Method onDeath = events.getMethod("onLivingDeath", deathEventClass());
            Method onRespawn = events.getMethod("onPlayerRespawn", respawnEventClass());
            HANDING_OFF.set(true);
            try {
                onDeath.invoke(null, newDeathEvent(player, source));
                onRespawn.invoke(null, newRespawnEvent(player));
            } finally {
                HANDING_OFF.set(false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Townstead.LOGGER.warn("[Rebirth] Could not hand {} to MCA Descendants", player.getGameProfile().getName(), e);
            player.sendSystemMessage(Component.translatable("townstead.rebirth.descendant.failed"));
        } finally {
            LAST_SOURCE.remove(id);
        }
    }

    /** Their "hardcore only" setting, read as off while a hand-off runs their handlers. */
    public static Object gateValue(Object configValue, Object value) {
        if (!HANDING_OFF.get() || !(value instanceof Boolean)) return value;
        return configValue == hardcoreOnlySetting() ? Boolean.FALSE : value;
    }

    //? if neoforge {
    private static Class<?> deathEventClass() {
        return net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class;
    }

    private static Class<?> respawnEventClass() {
        return net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class;
    }

    private static Object newDeathEvent(ServerPlayer player, DamageSource source) {
        return new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, source);
    }

    private static Object newRespawnEvent(ServerPlayer player) {
        return new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(player, false);
    }
    //?} else {
    /*private static Class<?> deathEventClass() {
        return net.minecraftforge.event.entity.living.LivingDeathEvent.class;
    }

    private static Class<?> respawnEventClass() {
        return net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class;
    }

    private static Object newDeathEvent(ServerPlayer player, DamageSource source) {
        return new net.minecraftforge.event.entity.living.LivingDeathEvent(player, source);
    }

    private static Object newRespawnEvent(ServerPlayer player) {
        return new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(player, false);
    }
    *///?}

    // Around Descendants' swaps (see DescendantsMixin).

    public static void beforeDeathSwap(ServerPlayer player) {
        GlobalPos here = GlobalPos.of(player.level().dimension(), player.blockPosition());
        GlobalPos at = player.isDeadOrDying() ? here : player.getLastDeathLocation().orElse(here);
        BEFORE.put(player.getUUID(), new Before(Rebirth.nameOf(player), at, null));
    }

    /** The old self is now the soul Descendants left behind: keep it as this player's past life. */
    public static void afterDeathSwap(LivingEntity soul, ServerPlayer player) {
        Before before = BEFORE.remove(player.getUUID());
        if (before == null) return;
        String now = nodeName(player);
        Rebirth.endLife(player, soul.getUUID(), before.name(), now != null ? now : before.name(), false,
                before.journalAt());
    }

    public static void beforeChosenSwap(LivingEntity target, ServerPlayer player) {
        BEFORE.put(player.getUUID(), new Before(Rebirth.nameOf(player), null, RootAssignment.currentRoot(target)));
    }

    /** The player is now the descendant: their Root and name come along. */
    public static void afterChosenSwap(ServerPlayer player) {
        Before before = BEFORE.remove(player.getUUID());
        if (before != null && before.targetRoot() != null) {
            ResourceLocation root = RootServerLogic.resolveKnown(before.targetRoot());
            if (root != null) RootAssignment.adoptPlayerRoot(player, root);
        }
        reconcileName(player);
    }

    /** Descendants names players through the family tree; keep Townstead's shown name in step. */
    public static void tick(MinecraftServer server) {
        if (!installed() || server.getTickCount() % 40 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) reconcileName(player);
    }

    private static void reconcileName(ServerPlayer player) {
        String name = nodeName(player);
        if (name != null && !name.isBlank() && !name.equals(Rebirth.nameOf(player))) Rebirth.rename(player, name);
    }

    private static @Nullable String nodeName(ServerPlayer player) {
        return FamilyTree.get(player.serverLevel()).getOrEmpty(player.getUUID()).map(FamilyTreeNode::getName).orElse(null);
    }

    private static boolean hardcoreOnly() {
        Object setting = hardcoreOnlySetting();
        if (setting == null) return true;
        try {
            Object on = setting.getClass().getMethod("get").invoke(setting);
            return !(on instanceof Boolean b) || b;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }

    private static volatile Object hardcoreOnlySetting;

    /** Descendants' "Hardcore Only" config value, or null when it cannot be found. */
    private static @Nullable Object hardcoreOnlySetting() {
        Object setting = hardcoreOnlySetting;
        if (setting != null) return setting;
        try {
            setting = Class.forName(PKG + "config.MCADescendantsCommonConfig").getField("HARDCORE_ONLY").get(null);
            hardcoreOnlySetting = setting;
            return setting;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
