// Act 2, M2, the founder's side. The midwife reaches them only through the player: the bread she
// sends (her counter says it is hers: "midwife_bread"), and the story of their birth, if the player
// chooses to carry it ("heard_birth"). They know the bread at once. They refuse the story.

VAR bread_known = false
VAR told_father = false
VAR seeing_midwife = false

// The player gave them bread.
=== gift_bread ===
{check("midwife_bread") and not bread_known:
    -> hers
}
{~Bread. Thanks. I'll eat it later, probably.|You're trying to feed me. Everybody's trying to feed me lately.}
-> DONE

= hers
~ bread_known = true
~ act("bread_delivered")
Where did you get this?
No, don't tell me. I know where.
She puts the seeds in the crust. Nobody else does that, I've looked.
* [She's in town.]
    ...Of course she is. # emote:ponder
    I'm going to eat this later. Not now.
* [Who's "she"?]
    Somebody who used to make sure I ate. # emote:shrug
    I'm going to eat this later. Not now.
- -> DONE

// The player carries the midwife's story of the night they were born.
=== father_at_birth ===
~ told_father = true
She's old. She's done a thousand of those, she's mixing it up with some other one.
He wasn't there. I know he wasn't, because if he was, then I don't know anything, and I know some things.
* [She seemed sure.]
    She would. She was always sure. It was one of the things I liked about her.
    ...Leave it. Please.
* [All right.]
    Thank you.
- -> DONE

// The midwife asked the player to bring them to her (her visit 3). They go, following the player,
// and the trip ends at her door: her confession sends them home either way.
=== function midwife_road_ready() ===
~ return seeing_midwife and not check("midwife_forgiven") and not check("midwife_cast_out")

=== function midwife_waiting() ===
~ return check("midwife_asked") and not seeing_midwife and not check("midwife_forgiven") and not check("midwife_cast_out")

=== see_midwife ===
She wants to see me. # emote:ponder
She came two valleys and sent me bread instead of knocking. That's her all over.
+ [I'll take you.]
    Fine. Walk me there. If I turn round, don't let me.
+ [Only if you want to.]
    I don't. That's never stopped me before. Lead the way.
- ~ seeing_midwife = true
~ act("walk_with")
-> DONE

=== midwife_road ===
{~Is it much further?|She'll fuss. She always fussed.|I'm walking. Look, I'm walking.}
+ [She's just over here.]
    Then let's get it over with.
    -> DONE
+ [Let's go back.]
    ~ seeing_midwife = false
    ~ act("walk_end")
    Good. Another day.
    -> DONE
