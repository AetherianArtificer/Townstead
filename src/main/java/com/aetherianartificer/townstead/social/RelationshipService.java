package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.dialogue.conversation.ConversationMemory;
import com.aetherianartificer.townstead.dialogue.conversation.ConversationSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Shared transition/query boundary for live relationship state and its repeat-safe legacy import. */
public final class RelationshipService {
    private static final Set<MinecraftServer> RECONCILED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private RelationshipService() {}

    public static ChronicleSavedData data(MinecraftServer server) {
        ChronicleSavedData social = ChronicleSavedData.get(server);
        ConversationSavedData conversations = ConversationSavedData.get(server);
        synchronized (RECONCILED) { if (!RECONCILED.add(server)) return social; }
        long today = TownsteadCalendar.worldDay(server);
        // Replay is intentional: operation ids make it harmless when both files saved, and it
        // repairs the case where the conversation file committed before Chronicle SavedData.
        for (ConversationMemory.LegacyOpinion legacy : conversations.memory().legacyOpinions()) {
            social.applyRelationship(legacy.from(), legacy.toward(), new RelationshipLedger.Contribution(
                    "legacy_conversation_v1:" + legacy.from() + ":" + legacy.toward(), RelationshipQualities.AFFECTION,
                    legacy.opinion(), today, 0, "townstead:legacy_conversation_v1"));
        }
        Set<UUID> people = new LinkedHashSet<>();
        conversations.memory().legacyOpinions().forEach(value -> people.add(value.from()));
        for (UUID person : people) for (ConversationMemory.Companion companion : conversations.memory().companions(person))
            recognizeFriendship(social, person, "", companion.other(), companion.name(), companion.meetings(), today);
        if (!conversations.relationshipsMigrated()) conversations.markRelationshipsMigrated();
        return social;
    }

    public static void clear(MinecraftServer server) { synchronized (RECONCILED) { RECONCILED.remove(server); } }

    public static boolean apply(MinecraftServer server, UUID from, UUID toward, String operation,
                                String quality, float amount, long day, int authoredHalfLifeDays, String source) {
        if (amount == 0) return false;
        RelationshipQuality definition = RelationshipQualities.byId(quality);
        int halfLife = authoredHalfLifeDays < 0 ? definition.defaultHalfLifeDays() : authoredHalfLifeDays;
        return data(server).applyRelationship(from, toward, new RelationshipLedger.Contribution(
                operation, quality, amount, day, halfLife, source));
    }

    public static boolean recognizeFriendship(ChronicleSavedData data, UUID first, String firstName,
                                              UUID second, String secondName, int meetings, long day) {
        if (meetings < 4
                || data.relationships().value(first, second, RelationshipQualities.AFFECTION, day) < 12
                || data.relationships().value(second, first, RelationshipQualities.AFFECTION, day) < 12) return false;
        String pair = first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
        return data.formBond("friendship:" + pair, "townstead:friendship", first, firstName,
                second, secondName, day, "townstead:earned_friendship");
    }
}
