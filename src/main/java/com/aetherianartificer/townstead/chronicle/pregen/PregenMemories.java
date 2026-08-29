package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.chronicle.knowledge.AccountLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.DistortionOverlay;
import com.aetherianartificer.townstead.chronicle.knowledge.SpreadChannel;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Writes the belief side of a fabricated past: an account of the story and the
 * memory it left. Impacts are not run through the ledger here, because mood is
 * a state of the present ({@code MOOD_IMPACT_DAILY_DECAY}) and a life remembered
 * from twenty years ago should not land on today's mood. The memory keeps the
 * impact block's valence and strength; the mood does not.
 */
public final class PregenMemories {

    private PregenMemories() {}

    public static void remember(ChronicleWorld world, ChronicleEventTemplate template,
                                ChronicleEvent event, UUID knower, @Nullable String role,
                                float fidelity, long day) {
        AccountLedger.learn(world, template, event, knower, false, role,
                SpreadChannel.WITNESS, ChronicleEvent.NONE, fidelity,
                DistortionOverlay.NONE, day);

        ChronicleEventTemplate.Impact impact = impactFor(template, role);
        float valence = 0.3f;
        float strength = 1.0f;
        if (impact != null && impact.memory() != null) {
            valence = impact.memory().valence();
            strength = Math.max(0.5f, impact.memory().strength());
        }
        world.addOrReinforceMemory(knower, event.templateId().toString(),
                otherParty(event, knower), day, strength, valence, event.params());
    }

    /** Same selection the ledger uses when applying impacts: role, then witness, then on_learn. */
    private static ChronicleEventTemplate.@Nullable Impact impactFor(ChronicleEventTemplate template,
                                                                    @Nullable String role) {
        if (role != null) {
            ChronicleEventTemplate.Impact byRole = template.impacts().get(role);
            if (byRole != null) return byRole;
        }
        return template.impacts().get("on_learn");
    }

    private static @Nullable UUID otherParty(ChronicleEvent event, UUID self) {
        for (Participation participation : event.participations()) {
            UUID uuid = participation.ref().uuid();
            if (uuid != null && !uuid.equals(self)) return uuid;
        }
        return null;
    }
}
