// The wolf line. The shepherd's wolf runs through the whole story, from "it sleeps outside" to its
// bed inside. It starts in Act 1, scene 2 (act1/02_wolf.ink). At most one beat plays a day, and
// never on a day with a main scene. Each beat has its own file, numbered as below. Design: "The
// wolf line" in docs/design/dhampir_founder.md.
//
//  2 Scraps       Act 1  the player has given them meat; they "haven't caught" whoever feeds it
//  3 A name       Act 1  they will not name it; once the player puts a name tag on it, "It isn't yours to name either."
//  4 Muttering    Act 1  at night, the player catches them telling it things
//  5 The collar   Act 2  leather and a silver nugget (iron when there's none) for a bell: "So I can hear it coming."
//  6 The tracker  Act 2  after a kill with the wolf close by: "It went in ahead of me."
//  7 A cold night any    a really cold night: they let it in "just tonight"
//  8 The bone     Act 2  a bone for it, from the butcher when there is one
//  9 It flinched  Act 2  at high Hunger it keeps away from them; the player can pull them back
// 10 Wounded      Act 2  the court's first move; if the wolf goes down, they sit up with it
// 11 The grave    after the midpoint, optional: they take it to the shepherd's grave
// 12 Inside       Act 3  its bed is inside now. "It was cold."

VAR meat_given = 0
VAR wolf_beat_day = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function wolf_beat_ready() ===
~ temp beat = next_wolf_beat()
~ return not (beat == -> dusk)

// The beat that plays next, or dusk for none. The first one that is ready wins, so the order
// matters.
=== function next_wolf_beat() ===
{wolf_beat_day == today or not wolf_seen:
    ~ return -> dusk
}
// The tracker beat counts kills from the day they gave their name.
{named and tracker_kills < 0:
    ~ tracker_kills = count("founder_kills")
}
{
- inside_move_ready():
    ~ return -> inside_move
- inside_seen_ready():
    ~ return -> inside_seen
- cold_out_ready():
    ~ return -> cold_out
- cold_in_ready():
    ~ return -> cold_in
- flinch_ready():
    ~ return -> flinch
- tracker_ready():
    ~ return -> tracker
- scraps_ready():
    ~ return -> scraps
- name_ask_ready():
    ~ return -> name_ask
- name_given_ready():
    ~ return -> name_given
- mutter_ready():
    ~ return -> mutter
- collar_ask_ready():
    ~ return -> collar_ask
- collar_done_ready():
    ~ return -> collar_done
- bone_ask_ready():
    ~ return -> bone_ask
- bone_done_ready():
    ~ return -> bone_done
- shepherd_offer_ready():
    ~ return -> shepherd_offer
}
~ return -> dusk

=== wolf_beat_run ===
~ temp beat = next_wolf_beat()
~ wolf_beat_day = today
-> beat
