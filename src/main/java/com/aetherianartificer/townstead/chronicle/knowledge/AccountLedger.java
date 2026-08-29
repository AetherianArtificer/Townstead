package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import com.aetherianartificer.townstead.chronicle.world.ServerChronicleWorld;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates accounts (belief) and applies their on-learn impacts. This is the
 * ONLY place impacts touch mood/sentiment/memories, and impacts always route
 * through an account — the truth firewall's belief side.
 *
 * <p>Impacts scale by fidelity and honor the account's distortion overlay:
 * a substituted participant takes the sentiment hit, believed (not true)
 * params seed the memory.</p>
 */
public final class AccountLedger {

    private AccountLedger() {}

    /**
     * Record-time handoff from the emitter: participants and witnesses learn
     * first-hand (fidelity 1.0, no overlay), with their role's impacts.
     */
    public static void onRecorded(MinecraftServer server, ChronicleEventTemplate template,
                                  ChronicleEvent event, List<LivingEntity> knownBy) {
        if (!event.newsworthy()) return;
        for (LivingEntity entity : knownBy) {
            String role = roleOf(event, entity.getUUID());
            learn(server, template, event, entity.getUUID(),
                    entity instanceof VillagerEntityMCA, role,
                    SpreadChannel.WITNESS, ChronicleEvent.NONE, 1.0f, DistortionOverlay.NONE,
                    event.worldDay());
        }
    }

    public static Account learn(MinecraftServer server, ChronicleEventTemplate template,
                                ChronicleEvent event, UUID knower, boolean applyImpacts,
                                @Nullable String role, SpreadChannel channel,
                                long sourceAccountId, float fidelity, DistortionOverlay overlay,
                                long learnedDay) {
        return learn(new ServerChronicleWorld(server), template, event, knower, applyImpacts,
                role, channel, sourceAccountId, fidelity, overlay, learnedDay);
    }

    /**
     * Creates the account, updates the known-stories cache, queues the archive
     * row, and (for villagers) applies impacts. Returns the account.
     */
    public static Account learn(ChronicleWorld world, ChronicleEventTemplate template,
                                ChronicleEvent event, UUID knower, boolean applyImpacts,
                                @Nullable String role, SpreadChannel channel,
                                long sourceAccountId, float fidelity, DistortionOverlay overlay,
                                long learnedDay) {
        long accountId = world.assignAccountId();
        Account account = new Account(accountId, event.eventId(), knower, channel.id(),
                sourceAccountId, fidelity, learnedDay, overlay.toJson());

        world.noteKnownStory(knower, new KnownStoriesCache.Entry(
                event.eventId(), accountId, fidelity, learnedDay, event.templateId(),
                event.worldDay(), event.villageId(), event.magnitude(), event.reach(), overlay));
        world.appendAccount(account);

        if (applyImpacts) {
            applyImpacts(world, template, event, knower, role, fidelity, overlay, accountId, learnedDay);
        }
        return account;
    }

    private static void applyImpacts(ChronicleWorld world, ChronicleEventTemplate template,
                                     ChronicleEvent event, UUID knower, @Nullable String role,
                                     float fidelity, DistortionOverlay overlay,
                                     long accountId, long today) {
        ChronicleEventTemplate.Impact impact = null;
        if (role != null && !Participation.ROLE_WITNESS.equals(role)) {
            impact = template.impacts().get(role);
        } else if (Participation.ROLE_WITNESS.equals(role)) {
            impact = template.impacts().get(Participation.ROLE_WITNESS);
        }
        if (impact == null) impact = template.impacts().get("on_learn");
        if (impact == null) return;

        if (impact.mood() != 0f) {
            world.addMoodImpact(knower, impact.mood() * fidelity * overlay.magnitudeMult());
        }

        ChronicleEventTemplate.SentimentImpact sentiment = impact.sentiment();
        if (sentiment != null) {
            UUID toward = overlay.believedUuid(sentiment.towardRole(),
                    uuidOfRole(event, sentiment.towardRole()));
            if (toward != null && !toward.equals(knower)) {
                world.adjustSentiment(knower, toward, sentiment.delta() * fidelity, today, accountId);
            }
        }

        ChronicleEventTemplate.MemoryImpact memory = impact.memory();
        if (memory != null) {
            float valence = Math.max(-1f, Math.min(memory.valence() + overlay.valenceSkew(), 1f));
            UUID otherParty = overlay.believedUuid(template.primaryRole().id(),
                    uuidOfRole(event, template.primaryRole().id()));
            if (knower.equals(otherParty)) otherParty = null;
            Map<String, String> params = overlay.applyToParams(event.params());
            world.addOrReinforceMemory(knower, event.templateId().toString(), otherParty,
                    today, memory.strength() * fidelity * overlay.magnitudeMult(), valence, params);
        }
    }

    private static @Nullable String roleOf(ChronicleEvent event, UUID uuid) {
        for (Participation participation : event.participations()) {
            if (uuid.equals(participation.ref().uuid())) return participation.role();
        }
        return null;
    }

    private static @Nullable UUID uuidOfRole(ChronicleEvent event, String role) {
        for (Participation participation : event.participations()) {
            if (role.equals(participation.role())) return participation.ref().uuid();
        }
        return null;
    }
}
