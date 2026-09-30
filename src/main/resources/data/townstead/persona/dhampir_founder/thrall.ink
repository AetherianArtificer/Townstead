// Act 1, scene 10: The grieving thrall. After scene 9 or 9b, a thrall whose master was killed
// comes up the road (a walk-in visitor, role "grieving_thrall") to ask the town for shelter. The
// founder's contempt is instant and cold. The player hears the thrall out in their own story
// (story/grieving_thrall) and decides: stay or go. The founder answers the decision. How they
// treated this thrall ("thrall_scorned") returns at the court, when they face the thrall who
// killed their mother.

VAR thrall_called = false
VAR thrall_name = ""
VAR thrall_seen = false
VAR done_thrall = false
VAR thrall_scorned = false

=== thrall_arrival ===
~ thrall_called = true
~ act("thrall_arrives")
Somebody's coming up the road. Don't turn round yet.
I don't like how they walk. Like they're used to walking a step behind someone.
-> DONE

// The founder sees what they are.
=== thrall_cold ===
~ thrall_seen = true
~ thrall_name = who("visitor:grieving_thrall")
That's a thrall. Somebody's pet. You can tell by the way they wait to be told where to stand.
On their own, so their master's dead. Good.
Send them back down the road.
* [They're a person.]
    They were. Then they handed themselves over. What's left is whatever a vampire wanted them to be.
* [I'll hear them out.]
    It's your town. # emote:shrug
    Don't let them near the children. That's not cruelty, that's arithmetic.
* [Why do you hate them so much?]
    I don't hate them. I just don't think about them. # emote:shrug
    ...That isn't true. Ask me something else.
- -> DONE

// The player decided. The thrall stayed, or went.
=== thrall_after ===
~ done_thrall = true
{is(thrall_name, ""):
    -> stayed
}
-> went

= stayed
You let them stay.
* [They're grieving.]
    ~ act("oath_small")
    So am I. Nobody gave me a roof for it.
    ...Forget I said that.
* [Keep an eye on them if you like.]
    ~ act("hunger_small")
    ~ thrall_scorned = true
    Every day. They'll feel it, too. I'll make sure of that.
* [Be kind to them.]
    ~ trust(1)
    I'll be polite. That's the most I've got, for one of those. Don't ask me for more yet.
- -> DONE

= went
Good. # emote:nod
* [That was cruel.]
    ~ act("oath_small")
    Maybe. It was also right. I don't need it to be both, but it usually is.
* [You're welcome.]
    ~ act("hunger_small")
    ~ thrall_scorned = true
    Thank you. You did the right thing. Nobody's going to thank you for it but me.
- -> DONE
