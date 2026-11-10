package com.aetherianartificer.townstead.rebirth;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.aspect.AspectPicks;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;
import com.aetherianartificer.townstead.politics.charter.CharterMemberships;
import com.aetherianartificer.townstead.politics.standing.StandingService;
import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.ServerInteractionManager;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import com.aetherianartificer.townstead.item.JournalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player who dies may come back as a new person. The life that ended is kept as a memorial: dead
 * in the family tree, with its own Chronicle history. The new person keeps the account's land,
 * buildings, recipes and advancements, but starts as a stranger: no hearts, standing or
 * memberships, a new name, and a fresh pass through Destiny.
 */
public final class Rebirth {
    private Rebirth() {}

    public static final int MAX_NAME_LENGTH = 32;

    /** The Destiny location that takes a reborn player back to where the last life ended. */
    public static final String DESTINY_LOCATION = "townstead:where_you_died";

    /** Players who asked, from the death screen, to be reborn on their next respawn; true for a hand-off to MCA Descendants. */
    private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();

    /** Where each just-reborn player's last life ended, until Destiny closes or they go there. */
    private static final Map<UUID, GlobalPos> LAST_DEATH = new ConcurrentHashMap<>();

    /** A request written on the dead player, so a disconnect before respawning does not lose it. */
    private static final String REQUEST = "townstead:rebirth_request";
    /** Written on a reborn player until their new character is made, so a rejoin picks Destiny back up. */
    private static final String DESTINY = "townstead:rebirth_destiny";

    public static RebirthMode mode() {
        return Switchboard.get(TownsteadConfig.REBIRTH_MODE);
    }

    public static boolean available(Player player) {
        return mode() != RebirthMode.OFF && !player.level().getLevelData().isHardcore()
                && !com.aetherianartificer.townstead.compat.mcadescendants.DescendantsBridge.handlesDeaths(player);
    }

    /** The name a player goes by now: their character name, or the account name when they have none. */
    public static String nameOf(Player player) {
        String name = PlayerLives.nameOf(player.getUUID());
        return name != null ? name : player.getGameProfile().getName();
    }

    /**
     * The character name a player shows under in chat, death messages and nameplates, or null when
     * they go by their account name. Either side: the server reads its record, a client the names it was sent.
     */
    public static @Nullable String characterName(Player player) {
        return player.level().isClientSide()
                ? com.aetherianartificer.townstead.client.rebirth.CharacterNameClient.get(player.getUUID())
                : PlayerLives.nameOf(player.getUUID());
    }

    /** Trims a requested name to something printable, or null when nothing usable is left. */
    public static @Nullable String cleanName(@Nullable String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == ChatFormatting.PREFIX_CODE || !allowed(c)) continue;
            out.append(Character.isWhitespace(c) ? ' ' : c);
        }
        String name = out.toString().trim().replaceAll(" {2,}", " ");
        if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH).trim();
        return name.isEmpty() ? null : name;
    }

    private static boolean allowed(char c) {
        //? if >=1.21 {
        return net.minecraft.util.StringUtil.isAllowedChatCharacter(c);
        //?} else {
        /*return SharedConstants.isAllowedChatCharacter(c);
        *///?}
    }

    public static void handleRequest(ServerPlayer player, RebirthRequestC2SPayload payload) {
        if (!player.isDeadOrDying()) return;
        if (payload.descendant()) {
            if (com.aetherianartificer.townstead.compat.mcadescendants.DescendantsBridge.canHandOff(player)) {
                request(player, true);
            }
            return;
        }
        if (available(player)) request(player, false);
    }

    private static void request(ServerPlayer player, boolean descendant) {
        PENDING.put(player.getUUID(), descendant);
        player.getPersistentData().putBoolean(REQUEST, descendant);
    }

    public static void onRespawn(ServerPlayer player, boolean endConquered) {
        Boolean requested = PENDING.remove(player.getUUID());
        if (endConquered) return;
        if (Boolean.TRUE.equals(requested)) {
            com.aetherianartificer.townstead.compat.mcadescendants.DescendantsBridge.handOff(player);
            return;
        }
        if (!available(player)) return;
        if (requested == null && mode() != RebirthMode.FORCED) return;
        try {
            begin(player);
        } catch (RuntimeException e) {
            Townstead.LOGGER.error("[Rebirth] Could not start a new life for {}", player.getGameProfile().getName(), e);
        }
    }

    public static void onLogin(ServerPlayer player) {
        send(player, new CharacterNamesS2CPayload(PlayerLives.names()));
        // Respawning makes a fresh player without this tag, so it is only ever on someone still dead.
        if (player.isDeadOrDying() && player.getPersistentData().contains(REQUEST)) {
            PENDING.put(player.getUUID(), player.getPersistentData().getBoolean(REQUEST));
        }
        resumeDestiny(player);
    }

    /** Someone who left mid-Destiny gets it back, with "Where you died" still on offer. */
    private static void resumeDestiny(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(DESTINY)) return;
        if (PlayerSaveData.get(player).isEntityDataSet()) {
            data.remove(DESTINY);
            return;
        }
        net.minecraft.nbt.CompoundTag saved = data.getCompound(DESTINY);
        net.minecraft.resources.ResourceLocation dimension = net.minecraft.resources.ResourceLocation.tryParse(saved.getString("dimension"));
        if (saved.contains("dimension") && dimension != null) {
            LAST_DEATH.put(player.getUUID(), GlobalPos.of(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimension), BlockPos.of(saved.getLong("pos"))));
        }
        // After MCA's own join handling, which may open a Destiny of its own without our location.
        player.server.tell(new net.minecraft.server.TickTask(player.server.getTickCount() + 5, () -> {
            if (!player.hasDisconnected()) openDestiny(player);
        }));
    }

    private static void rememberDestiny(ServerPlayer player, @Nullable GlobalPos died) {
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        if (died != null) {
            saved.putString("dimension", died.dimension().location().toString());
            saved.putLong("pos", died.pos().asLong());
        }
        player.getPersistentData().put(DESTINY, saved);
    }

    /** Runs one step of changing lives. No step can be undone, so one failing must not stop the rest. */
    private static void step(ServerPlayer player, String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Townstead.LOGGER.error("[Rebirth] Step {} failed for {}", name, player.getGameProfile().getName(), e);
        }
    }

    public static void onLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        LAST_DEATH.remove(player.getUUID());
        DestinyShield.closed(player.getUUID());
    }

    private static void begin(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        Gender gender = FamilyTree.get(level).getOrEmpty(id).map(FamilyTreeNode::gender).orElse(Gender.NEUTRAL);
        String newName = Names.pickCitizenName(gender);
        GlobalPos died = player.getLastDeathLocation().orElse(null);
        endLife(player, UUID.randomUUID(), nameOf(player), newName, true, died);
        // Destiny's name field starts from the player's custom name: offer the new name, not the old one.
        step(player, "custom_name", () -> player.setCustomName(Component.literal(newName)));

        PlayerSaveData data = PlayerSaveData.get(player);
        Optional<Village> home = data.getLastSeenVillage(VillageManager.get(level));
        step(player, "reset_character", () -> {
            data.setEntityDataSet(false);
            data.setDirty();
        });
        step(player, "root", () -> RootAssignment.assign(player, RootRegistry.DEFAULT_ID));

        step(player, "arrive", () -> home.ifPresent(village -> arrive(player, level, village)));
        if (died != null) LAST_DEATH.put(id, died);
        rememberDestiny(player, died);
        openDestiny(player);
    }

    /**
     * Ends the player's current life: the passing goes in the Chronicles, the old careers become a
     * journal left at {@code journalAt}, standing and memberships are cleared, and the life is kept
     * under {@code memorial}. {@code splitFamily} is false when another mod already moved the old
     * self in the family tree.
     */
    public static void endLife(ServerPlayer player, UUID memorial, String oldName, String newName,
                               boolean splitFamily, @Nullable GlobalPos journalAt) {
        MinecraftServer server = player.server;
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();

        // The passing is the last event of the old life, so it is recorded before the boundary.
        step(player, "chronicle", () -> ChronicleEmitter.emit(level, new TriggerKey("lifecycle", "townstead:passed_on"), player, 1.0f,
                Map.of("deceased", oldName, "next", newName)));
        long lastEvent = ChronicleSavedData.get(server).lastEventId();

        if (splitFamily) step(player, "family", () -> FamilySplit.split(level, id, memorial, oldName, newName));
        step(player, "careers", () -> {
            if (Relearning.capture(player, memorial, oldName)) leaveJournal(player, memorial, oldName, journalAt);
        });
        step(player, "standing", () -> StandingService.forget(server, id));
        step(player, "memberships", () -> CharterMemberships.endAll(server, id));
        // Vampirism keeps a faction through death; the new life starts without the old one's aspect.
        step(player, "aspect", () -> AspectPicks.become(player, null, 0, false));
        step(player, "lives", () -> PlayerLives.get(server).endLife(id,
                new PlayerLives.Life(memorial, oldName, level.getGameTime(), lastEvent), newName));
        step(player, "names", () -> com.aetherianartificer.townstead.naming.PlayerNames.startNewLife(player));
        step(player, "sync", () -> syncNames(player));
    }

    /** Changes the name a reborn player goes by, without ending a life. */
    public static void rename(ServerPlayer player, String name) {
        PlayerLives.get(player.server).rename(player.getUUID(), name);
        syncNames(player);
    }

    /**
     * Destiny and MCA's editor name a player through the family tree; the shown name follows it.
     * In a first life only while naming is on, since then the account name stood in.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        boolean naming = com.aetherianartificer.townstead.switchboard.Systems.on(
                com.aetherianartificer.townstead.switchboard.Systems.NAMING);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (naming || !PlayerLives.livesOf(player.getUUID()).isEmpty()) adoptFamilyTreeName(player);
        }
    }

    /** Takes the player's family-tree name as the name they go by, when it differs. */
    public static void adoptFamilyTreeName(ServerPlayer player) {
        String name = cleanName(FamilyTree.get(player.serverLevel()).getOrEmpty(player.getUUID())
                .map(FamilyTreeNode::getName).orElse(null));
        if (name != null && !name.equals(nameOf(player))) rename(player, name);
    }

    private static void syncNames(ServerPlayer player) {
        CharacterNamesS2CPayload names = new CharacterNamesS2CPayload(PlayerLives.names());
        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) send(online, names);
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    /** Destiny, with "Where you died" offered first when the last life's death spot is known. */
    private static void openDestiny(ServerPlayer player) {
        boolean whereYouDied = LAST_DEATH.containsKey(player.getUUID());
        DestinyShield.opened(player);
        //? if neoforge {
        net.conczin.mca.network.s2c.OpenDestinyGuiRequest base = new net.conczin.mca.network.s2c.OpenDestinyGuiRequest(player);
        if (!whereYouDied) {
            net.conczin.mca.network.Network.sendToPlayer(base, player);
            return;
        }
        java.util.List<net.conczin.mca.destiny.DestinyDestination> destinations = new java.util.ArrayList<>();
        destinations.add(new net.conczin.mca.destiny.DestinyDestination(DESTINY_LOCATION,
                Optional.of(LAST_DEATH.get(player.getUUID()).dimension())));
        destinations.addAll(base.destinations());
        net.conczin.mca.network.Network.sendToPlayer(
                new net.conczin.mca.network.s2c.OpenDestinyGuiRequest(base.allowTeleportation(), destinations), player);
        //?} else {
        /*if (whereYouDied) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, new RebirthDestinyS2CPayload());
        ServerInteractionManager.launchDestiny(player);
        *///?}
    }

    /** Destiny's "Where you died": go there, or say why not. */
    public static void chooseWhereYouDied(ServerPlayer player) {
        if (!goToLastDeath(player)) {
            player.sendSystemMessage(Component.translatable("townstead.rebirth.where_you_died.failed")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Takes a reborn player to the surface above where their last life ended. False when unknown. */
    public static boolean goToLastDeath(ServerPlayer player) {
        GlobalPos death = LAST_DEATH.remove(player.getUUID());
        // Used up, so a rejoin before finishing does not offer it again.
        if (player.getPersistentData().contains(DESTINY)) rememberDestiny(player, null);
        ServerLevel level = death == null ? null : player.server.getLevel(death.dimension());
        if (level == null) return false;
        BlockPos pos = death.pos();
        level.getChunkAt(pos);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
        if (!level.getWorldBorder().isWithinBounds(ground) || ground.getY() <= level.getMinBuildHeight()) {
            // No ground above the spot, as over the void: stay where Destiny found you.
            return false;
        }
        player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        return true;
    }

    /** The old life's journal lies where it died, for whoever finds it. */
    private static void leaveJournal(ServerPlayer player, UUID memorial, String name, @Nullable GlobalPos at) {
        ItemStack journal = JournalItem.create(Townstead.JOURNAL.get(), memorial, name);
        ServerLevel level = at == null ? null : player.server.getLevel(at.dimension());
        if (level == null) {
            if (!player.getInventory().add(journal)) player.drop(journal, false);
            return;
        }
        BlockPos pos = at.pos();
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, journal);
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
    }

    /** The new person walks into the old home town rather than waking in the old bed. */
    private static void arrive(ServerPlayer player, ServerLevel level, Village village) {
        BlockPos center = new BlockPos(village.getCenter());
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        if (ground.getY() <= level.getMinBuildHeight()) return;
        player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * Called whenever a villager looks up what it remembers of a player. The first time a villager
     * meets a reborn player, it forgets the person who came before.
     */
    public static void onMemoriesLookup(Entity villager, Player player, Memories memories) {
        MinecraftServer server = villager.level().getServer();
        if (server == null || PlayerLives.livesOf(player.getUUID()).isEmpty()) return;
        if (!PlayerLives.get(server).forgetOnce(player.getUUID(), villager.getUUID())) return;
        memories.setHearts(0);
    }

    //? if neoforge {
    private static void send(ServerPlayer player, CharacterNamesS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
    //?} else {
    /*private static void send(ServerPlayer player, CharacterNamesS2CPayload payload) {
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
    }
    *///?}
}
