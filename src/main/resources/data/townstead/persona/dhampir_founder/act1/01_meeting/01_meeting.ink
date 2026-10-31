// Act 1, scene 1: First blood. The first conversation, soon after the founder arrives on the trail
// of a vampire. They are in the middle of their own work: after a kill (the "made_kill" counter)
// they want to get clean and find out who lives near the edge of town; when the trail went cold
// they are asking who slept badly. The player answers their question first; then the open topics.
// The offer to stay is their own plan: they need a base near the ridge. The player can leave at
// any point and pick up where they were (meeting.resume). Reveals only their given name, their
// hatred of vampires, and the shepherd's wolf (for scene 2).

// When this scene plays. The greet in persona.ink checks these in order.
=== function meeting_ready() ===
~ return met == 0

=== function meeting_resume_ready() ===
~ return met == 2 and not offered

=== meeting ===
{check("vampire_near"):
    -> not_now
}
~ met = 2
{check("made_kill"):
    -> meeting_after_kill
}
-> meeting_cold_trail

// A vampire is still close. They will not talk with one at their back.
= not_now
Not now. Get behind me. # emote:point
-> DONE

// Back after leaving mid-conversation.
= resume
Where were we.
{check("made_kill"):
    -> meeting_after_kill.topics
}
-> meeting_cold_trail.cold
