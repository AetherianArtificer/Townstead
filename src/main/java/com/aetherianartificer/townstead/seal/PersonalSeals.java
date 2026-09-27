package com.aetherianartificer.townstead.seal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Every player's chosen seal. A player who never chose one presses {@link PersonalSeal#DEFAULT}. */
public final class PersonalSeals extends SavedData {
    public static final String FILE_ID = "townstead_seals";
    private final Map<UUID, PersonalSeal> seals = new LinkedHashMap<>();

    public static PersonalSeals get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PersonalSeals::new, PersonalSeals::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                PersonalSeals::load, PersonalSeals::new, FILE_ID);
        *///?}
    }

    public static PersonalSeal of(MinecraftServer server, UUID person) {
        return get(server).seals.getOrDefault(person, PersonalSeal.DEFAULT);
    }

    /** Stores a seal the client asked for, and confirms it back. Unknown devices are ignored. */
    public static void choose(ServerPlayer player, String device, int dye) {
        PersonalSeal seal = PersonalSeal.sanitized(device, dye);
        if (seal == null) return;
        PersonalSeals data = get(player.server);
        data.seals.put(player.getUUID(), seal);
        data.setDirty();
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        SealS2CPayload payload = new SealS2CPayload(of(player.server, player.getUUID()));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    //? if >=1.21 {
    public static PersonalSeals load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static PersonalSeals load(CompoundTag tag) {
    *///?}
        PersonalSeals data = new PersonalSeals();
        CompoundTag all = tag.getCompound("seals");
        for (String key : all.getAllKeys()) {
            try {
                data.seals.put(UUID.fromString(key), PersonalSeal.fromTag(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // A malformed key is dropped; that player presses the default seal.
            }
        }
        return data;
    }

    //? if >=1.21 {
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag all = new CompoundTag();
        seals.forEach((person, seal) -> all.put(person.toString(), seal.toTag()));
        tag.put("seals", all);
        return tag;
    }
}
