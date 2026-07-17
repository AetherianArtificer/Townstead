package com.aetherianartificer.townstead.chronicle.concept;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of abstract chronicle participants: ancestors, roads, landmarks,
 * and later institutions and factions. Ids are namespaced strings
 * ({@code ancestor:<uuid>}, {@code road:riverside}) so future arcs can attach
 * real state to an id and old events retro-link for free.
 */
public class ConceptLedger extends SavedData {
    public static final String FILE_ID = "townstead_concepts";

    public record ConceptEntry(String id, String kind, String displayNameLiteral,
                               String displayNameLangKey, long foundingDay,
                               @Nullable VillageKey village) {}

    private final Map<String, ConceptEntry> entries = new HashMap<>();

    public ConceptLedger() {}

    public static ConceptLedger get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ConceptLedger::new, ConceptLedger::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                ConceptLedger::load,
                ConceptLedger::new,
                FILE_ID);
        *///?}
    }

    public @Nullable ConceptEntry byId(String id) {
        return entries.get(id);
    }

    public void put(ConceptEntry entry) {
        entries.put(entry.id(), entry);
        setDirty();
    }

    public List<ConceptEntry> byVillage(VillageKey key) {
        List<ConceptEntry> result = new ArrayList<>();
        for (ConceptEntry entry : entries.values()) {
            if (key.equals(entry.village())) result.add(entry);
        }
        return result;
    }

    public int size() {
        return entries.size();
    }

    //? if >=1.21 {
    public static ConceptLedger load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ConceptLedger load(CompoundTag tag) {
    *///?}
        ConceptLedger ledger = new ConceptLedger();
        ListTag list = tag.getList("concepts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            VillageKey village = null;
            if (e.contains("dim")) {
                try {
                    village = new VillageKey(parseRl(e.getString("dim")), e.getInt("village"));
                } catch (Exception ignored) {
                }
            }
            ConceptEntry entry = new ConceptEntry(e.getString("id"), e.getString("kind"),
                    e.getString("lit"), e.getString("lang"), e.getLong("founded"), village);
            ledger.entries.put(entry.id(), entry);
        }
        return ledger;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (ConceptEntry entry : entries.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", entry.id());
            e.putString("kind", entry.kind());
            e.putString("lit", entry.displayNameLiteral());
            e.putString("lang", entry.displayNameLangKey());
            e.putLong("founded", entry.foundingDay());
            if (entry.village() != null) {
                e.putString("dim", entry.village().dimension().toString());
                e.putInt("village", entry.village().villageId());
            }
            list.add(e);
        }
        tag.put("concepts", list);
        return tag;
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
