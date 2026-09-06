package com.aetherianartificer.townstead.dialogue.conversation;

//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Social experience belongs to the world, survives reloads, and follows residents across dimensions. */
public final class ConversationSavedData extends SavedData {
    private ConversationMemory memory = new ConversationMemory();
    private boolean relationshipsMigrated;
    public ConversationMemory memory() { return memory; }
    public boolean relationshipsMigrated() { return relationshipsMigrated; }
    public void markRelationshipsMigrated() { relationshipsMigrated = true; setDirty(); }
    public static ConversationSavedData get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ConversationSavedData::new, ConversationSavedData::load), "townstead_conversations");
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                ConversationSavedData::load, ConversationSavedData::new, "townstead_conversations");
        *///?}
    }
    //? if >=1.21 {
    public static ConversationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ConversationSavedData load(CompoundTag tag) {
    *///?}
        ConversationSavedData data = new ConversationSavedData();
        if (tag.contains("version")) {
            com.google.gson.JsonObject document = new com.google.gson.JsonObject();
            document.addProperty("version", tag.getInt("version"));
            com.google.gson.JsonArray pairs = new com.google.gson.JsonArray();
            int count = Math.max(0, Math.min(ConversationMemory.MAX_PAIRS, tag.getInt("count")));
            for (int i = 0; i < count; i++) pairs.add(com.google.gson.JsonParser.parseString(tag.getString("pair_" + i)));
            document.add("pairs", pairs); data.memory = ConversationMemory.load(document);
            data.relationshipsMigrated = tag.getBoolean("relationshipsMigrated");
        }
        return data;
    }
    //? if >=1.21 {
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override public CompoundTag save(CompoundTag tag) {
    *///?}
        // Individual pair documents stay below NBT's UTF string limit, even in a large town.
        var document = memory.save(); var pairs = document.getAsJsonArray("pairs");
        tag.putInt("version", 1); tag.putInt("count", pairs.size());
        tag.putBoolean("relationshipsMigrated", relationshipsMigrated);
        for (int i = 0; i < pairs.size(); i++) tag.putString("pair_" + i, pairs.get(i).toString());
        return tag;
    }
}
