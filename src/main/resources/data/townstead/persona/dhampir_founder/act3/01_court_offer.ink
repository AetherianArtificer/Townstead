// Act 3, The Court, part 1: the offer. After the midpoint, the confession (or a few days without
// it) and the wolf's bed moving inside (wolf beat 12), the founder asks the player to come to
// their father's house. The wolf stays by the lodge fire.

VAR court_offered = false
VAR midpoint_day = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function court_offer_ready() ===
~ return done_midpoint and done_inside and not court_offered and (done_confession_after or today > midpoint_day + 5)

=== court_offer ===
~ court_offered = true
I'm going to his house. I said I would, and I've put it off as long as I can stand.
I want you there. Not to fight. I want somebody there who knew me before I walked in.
{who("sworn_hunter") != "":
    The wolf stays here, by the fire. {who("sworn_hunter")} is going to feed it. I've asked, and I've asked again, and I'm going to ask once more before we go.
- else:
    The wolf stays here, by the fire. I'll leave enough food out for a week. It'll eat it in two days, but that's its business.
}
+ [I'll come.]
    ~ start_trip(court_walking, "mark_court", "court_travel")
    You've got the map. Same as last time. You lead, I'll follow, and if I stop, don't wait for me to say why.
    -> DONE
+ [Not yet.]
    ~ court_offered = false
    Not yet. Soon, though. I can feel it pulling, like a hook behind my ribs.
    -> DONE
