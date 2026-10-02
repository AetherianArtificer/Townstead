// The wolf line, after scene 2 (wolf.ink). The shepherd's wolf runs through the whole story, from
// "it sleeps outside" to its bed inside. At most one beat a day, and never on a day with a main
// scene (the greet only reaches these when nothing else is waiting). Design: "The wolf line" in
// docs/design/dhampir_founder.md.
//
//  2 Scraps      Act 1  the player has given them meat; they "haven't caught" whoever feeds it
//  3 A name      Act 1  they will not name it; once the player puts a name tag on it, "It isn't yours to name either."
//  4 Muttering   Act 1  at night, the player catches them telling it things
//  5 The collar  Act 2  leather and a silver nugget (iron when there's none) for a bell: "So I can hear it coming."
//  6 The tracker Act 2  after a kill with the wolf close by: "It went in ahead of me."
//  7 A cold night any   a really cold night: they let it in "just tonight"
//  8 The bone    Act 2  a bone for it, from the butcher when there is one
//  9 It flinched Act 2  at high Hunger it keeps away from them; the player can pull them back
// 11 The grave   after the midpoint, optional: they take it to the shepherd's grave
// 12 Inside     Act 3  its bed is inside now. "It was cold."

VAR meat_given = 0
VAR wolf_beat_day = -1
VAR done_scraps = false
VAR name_asked = false
VAR done_wolf_name = false
VAR done_mutter = false
VAR collar_asked = false
VAR done_collar = false
VAR tracker_kills = -1
VAR done_tracker = false
VAR let_in = false
VAR done_cold = false
VAR bone_asked = false
VAR done_bone = false
VAR done_flinch = false
VAR shepherd_offered = false
VAR shepherd_walking = false
VAR done_shepherd = false
VAR inside_moved = false
VAR done_inside = false

// Which beat is waiting, or "" for none. The greet calls this last, before the dusk talk.
=== function wolf_beat() ===
{wolf_beat_day == today or not wolf_seen:
    ~ return ""
}
{named and tracker_kills < 0:
    ~ tracker_kills = count("founder_kills")
}
{
- done_midpoint and (done_wounded or done_court_note) and not inside_moved:
    ~ return "inside_move"
- inside_moved and not done_inside and check("wolf_close"):
    ~ return "inside_seen"
- let_in and not check("cold_night"):
    ~ return "cold_out"
- not done_cold and not done_inside and check("cold_night") and check("has_wolf"):
    ~ return "cold_in"
- named and not done_flinch and check("hungry") and check("has_wolf") and not check("wolf_close"):
    ~ return "flinch"
- named and not done_tracker and tracker_kills >= 0 and count("founder_kills") > tracker_kills and check("wolf_close"):
    ~ return "tracker"
- not done_scraps and meat_given > 0 and check("evening"):
    ~ return "scraps"
- done_scraps and not name_asked:
    ~ return "name_ask"
- name_asked and not done_wolf_name and check("wolf_named"):
    ~ return "name_given"
- done_walk and not done_mutter and check("late_night") and check("wolf_close"):
    ~ return "mutter"
- named and not collar_asked:
    ~ return "collar_ask"
- collar_asked and not done_collar and check("carries_collar_things"):
    ~ return "collar_done"
- done_collar and not bone_asked:
    ~ return "bone_ask"
- bone_asked and not done_bone and check("carries_bone"):
    ~ return "bone_done"
- done_midpoint and done_inside and not shepherd_offered:
    ~ return "shepherd_offer"
}
~ return ""

=== wolf_beat_run ===
~ temp beat = wolf_beat()
~ wolf_beat_day = today
{
- beat == "inside_move":
    -> inside_move
- beat == "inside_seen":
    -> inside_seen
- beat == "cold_out":
    -> cold_out
- beat == "cold_in":
    -> cold_in
- beat == "flinch":
    -> flinch
- beat == "tracker":
    -> tracker
- beat == "scraps":
    -> scraps
- beat == "name_ask":
    -> name_ask
- beat == "name_given":
    -> name_given
- beat == "mutter":
    -> mutter
- beat == "collar_ask":
    -> collar_ask
- beat == "collar_done":
    -> collar_done
- beat == "bone_ask":
    -> bone_ask
- beat == "bone_done":
    -> bone_done
- beat == "shepherd_offer":
    -> shepherd_offer
}
-> dusk

// Beat 2. They give the player's meat to the wolf, and pretend they don't.
=== scraps ===
~ done_scraps = true
Somebody's been feeding that wolf off the end of my plate. I've been keeping watch. I haven't caught them yet.
+ [It's you.]
    Prove it.
    ...You can't, can you. Good.
+ [I'll keep an eye out.]
    Do. It's a serious matter. It's going to be too fat to walk at this rate.
- -> DONE

// Beat 3. They will not name it.
=== name_ask ===
~ name_asked = true
People keep asking me what it's called. I tell them it's called "the wolf". They look at me like I've kicked it.
I'm not naming it. If I name it, then it's mine, and I've told everybody it isn't.
+ [Somebody should.]
    Somebody can. Not me.
+ [Fair enough.]
    Thank you. Finally, somebody sensible.
- -> DONE

=== name_given ===
~ done_wolf_name = true
You put a name on it. # emote:ponder
It isn't yours to name either, you know.
...It answers to it, though. I tried it when nobody was about.
+ [Do you like it?]
    It's fine. It's a name. Don't look so pleased.
+ [It suits it.]
    ~ trust(1)
    It does a bit. I hate that it does.
- -> DONE

// Beat 4. At night, talking to it. They stop when they see the player.
=== mutter ===
~ done_mutter = true
...no, you're right, I should have waited. You're always right, that's the annoying thing about you.
Oh. It's you. # emote:shrug
I was telling it about the night. It listens better than most people. It doesn't interrupt and it never asks follow-up questions.
+ [What did it say?]
    That I should have waited. Weren't you listening?
+ [I'll leave you two alone.]
    Don't make it sound like that.
- -> DONE

// Beat 5. A bell for its collar.
=== collar_ask ===
~ collar_asked = true
I want to put a bell on that wolf's collar. A proper one, with a leather strap to hang it from.
Bring me a bit of leather and a nugget of silver, if there's any about. Iron'll do if there isn't. Silver rings better.
+ [What's the bell for?]
    So I can hear it coming. It walks quieter than I do, and I don't like being the second-quietest thing in a room.
+ [I'll find some.]
    Thanks. No hurry. It's not like it's going anywhere. It never goes anywhere I'm not.
- -> DONE

=== collar_done ===
~ done_collar = true
~ act("take_collar_things")
That'll do nicely. # emote:nod
I'll file the bell down tonight, so it doesn't clang. Just enough to hear.
So I can hear it coming.
-> DONE

// Beat 6. After a kill with the wolf close by.
=== tracker ===
~ done_tracker = true
It went in ahead of me tonight. I didn't tell it to. It just went. # emote:ponder
...Good. That's good. I'm going to be insufferable about it for days, I can feel it.
+ [It's learning from you.]
    Poor thing. It's learning from the worst teacher in town.
+ [Don't let it get hurt.]
    ~ act("oath_small")
    No. I won't. I'll keep it behind me. It won't like that, but it'll do it.
- -> DONE

// Beat 7. A really cold night. Just tonight.
=== cold_in ===
~ let_in = true
~ done_cold = true
~ act("wolf_rest_inside")
I'm letting it in tonight. Just tonight. It's cold out, even for a wolf.
Don't look at me like that. Just tonight.
-> DONE

=== cold_out ===
~ let_in = false
{not done_wounded:
    ~ act("wolf_rest_outside")
}
Back out it goes. It was one night. We agreed on one night, didn't we.
-> DONE

// Beat 8. A bone, from the butcher when there is one.
=== bone_ask ===
~ bone_asked = true
{who("butcher") != "":
    Would you ask {who("butcher")} for a bone? A proper one, something with a bit of work in it. I'd go myself but I'd have to explain who it's for.
- else:
    If you find a bone anywhere, a proper one, bring it to me. Don't ask what for.
}
-> DONE

=== bone_done ===
~ done_bone = true
~ act("take_bone")
Give it here.
I'll leave it where it'll find it later. When nobody's looking. I'm not going to stand there watching it eat, I'm not that far gone.
-> DONE

// Beat 9. At high Hunger it keeps away from them.
=== flinch ===
~ done_flinch = true
It won't come near me tonight. # emote:ponder
It's never done that. Not once, not even the first week.
+ [It knows something's changed.]
    ~ act("oath_small")
    Yes. Yes, it does. It always knows before I do.
    I'll walk it in the morning. Long way round. Maybe I'll be somebody it wants to walk with by then.
+ [It'll come round.]
    ~ act("hunger_small")
    It'll have to, won't it. It hasn't got anybody else.
- -> DONE

// Beat 11. After the midpoint: the shepherd's grave.
=== shepherd_offer ===
~ shepherd_offered = true
I want to take the wolf back to see him. The shepherd.
That's a strange thing to want, isn't it. I don't care. Will you come?
+ [I'll come.]
    ~ act("mark_shepherd")
    ~ act("shepherd_travel")
    ~ shepherd_walking = true
    The wolf knows the way better than either of us. I'll follow it, you follow me.
    -> DONE
+ [Another day.]
    ~ shepherd_offered = false
    Another day.
    -> DONE

=== shepherd_road ===
{~It's further than I remember. Everything is.|It knows where we're going. Look at it.|Keep up.}
+ [Let's turn back.]
    ~ shepherd_walking = false
    ~ act("shepherd_home")
    All right. Another time.
    -> DONE
+ [Keep going.]
    -> DONE

=== shepherd_arrive ===
~ done_shepherd = true
~ shepherd_walking = false
~ act("build_shepherd_grave")
~ act("oath_small")
~ trust(2)
Here. He's here.
I never did get his name. I put a stone up for him anyway. You don't need a name for a stone. # emote:ponder
He'd want to know it ate well. It eats very well. Somebody keeps spoiling it.
+ [Say something to him.]
    I just did. That's all I've got.
    ...Thank you for the wolf. There. That's the rest of it.
+ [Let's go home.]
    Yes. All three of us.
- ~ act("shepherd_home")
-> DONE

// Beat 12. Early Act 3: its bed is inside now.
=== inside_move ===
~ inside_moved = true
~ act("wolf_rest_inside")
I'm moving its bed. Don't say anything.
-> DONE

=== inside_seen ===
~ done_inside = true
Oh. It's you. Come in, then, mind the wolf.
+ [The wolf sleeps inside now.]
    {check("cold_night"):
        It was cold.
    - else:
        It was cold. # emote:shrug
    }
+ [Good evening to you too, wolf.]
    It doesn't answer to that. It barely answers to its name.
- -> DONE
