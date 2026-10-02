// Act 2, M1: The missing lines. At the first oath after scene 13, the founder's invocation slipped
// into her wording ("carry the lamp yourself", her fourth article) in front of everyone, before
// they caught it (the ritual's line variant, armed by "prime_slip"). Afterwards they admit they
// know her oath only from memory; she wrote it down, and the book is gone. Reveals that the oath
// is incomplete, and opens the search that leads to the midwife (M2).

VAR done_slip = false

=== slip_after ===
~ done_slip = true
~ act("slipped")
~ act("mark_slipped")
You heard it. Don't pretend you didn't, everybody heard it.
I said her line. The lamp. In the middle of ours, in front of all of them, I said hers.
I don't know it, you see. Not all of it. I know it the way you know a song you heard through a wall.
She wrote it down, every word of it, and I never did, because she was always going to be there to ask.
* [Where's the book now?]
    I don't know. I looked, after. I didn't look very well. I was young and I was angry, and angry people are bad at finding things.
* [Then let's find it.]
    ~ trust(2)
    ...You'd do that? It isn't a job. There's no garlic in it.
    ** [I know.]
        All right. # emote:nod
        All right. I don't know where you'd even start. But all right.
    ** [I'll think of something.]
        You usually do. It's one of the more annoying things about you.
    --
* [The hunters don't know the difference.]
    ~ act("hunger_small")
    No. They don't. # emote:shrug
    That's the problem, isn't it. Nobody knows the difference but me, and I'm losing it.
* [Say it with me. The parts you know.]
    ~ act("oath_small")
    ...All right.
    -> recite ->
    Thank you. That helped. Don't tell anybody that helped.
- -> DONE
