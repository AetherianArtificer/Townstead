// Act 2, M2, the founder's side. The midwife reaches them only through the player: the bread she
// sends (her counter says it is hers: "midwife_bread"), and the story of their birth, if the player
// chooses to carry it ("heard_birth"). They know the bread at once. They refuse the story.

VAR bread_known = false
VAR told_father = false

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
