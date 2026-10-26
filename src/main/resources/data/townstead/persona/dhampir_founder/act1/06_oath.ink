// Act 1, scene 6: First oath. After the founder swears in the lodge's first hunter (the
// "first_oath" count of rituals they have officiated), the player finds them still at the altar.
// The player can ask where the words come from: "somebody who wrote things down". They slip once
// and say "she", and take it back. The oath they use is short because they don't remember all of
// it, which they won't say (it sets up the count, scene 11). Reveals: someone who wrote things
// down; the mother is hinted, never named.

VAR done_first_oath = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function first_oath_ready() ===
~ return done_lodge and not done_first_oath and check("first_oath")

=== first_oath ===
~ done_first_oath = true
{who("sworn_hunter") != "":
    That's one. {who("sworn_hunter")} looked like they'd swallowed a bell. They'll be fine by morning. Mostly by morning.
- else:
    That's one. They looked like they'd swallowed a bell. They'll be fine by morning. Mostly by morning.
}
I'm going to sit here a bit longer, if that's all right. Nothing's wrong. I just want to sit.
- (topics)
* [Where do the words come from?]
    Somebody who wrote things down.
    ** [Who?]
        Somebody I knew. Very neat handwriting. No patience at all for people who didn't mean what they said.
    ** [Did you write any of it?]
        Some. The first version wasn't about hunting. I made it about hunting.
        I'm not sure she'd have. # emote:ponder
        Never mind.
    --
    -> topics
* [Is that all of it?]
    No. There's more. It goes on for a while, the whole thing. I keep it short for the ceremony. Nobody wants to kneel that long.
    -> topics
* [How does it feel, swearing someone in?]
    Heavier than I thought it would. You say the words, and they say them back, and then they're yours to keep alive.
    I hadn't thought about that part. I should have.
    -> topics
+ [I'll leave you to it.]
    Go on. I'll be here a while.
    -> DONE

// The first recruit. Opens with the lodge: the player can bring someone who wants to join
// (enlist_recruit), and volunteers still come at dusk. Whoever swears first hands it back, and
// the hand-back is the scene above.
=== recruit_job ===
# quest: The first recruit
# about: Bring a villager who wants to join to the hunter. They train at the altar for three nights, then swear the oath.
# goal: founder_oaths
# skip if: founder_oaths
If you know somebody who'd do it, bring them to me. Somebody who wants it, not somebody you talked into it. I'll know the difference.
->->

= waiting
{~Found anyone? Don't drag them here. If they need dragging, they're not ready.|Still looking? Me too.}
- (asks)
+ [I've brought someone.]
    {act("enlist_recruit"):
        All right. They train by the altar after dark, three nights. If they're still here after that, they kneel.
    - else:
        Not this one.
    }
    -> DONE
+ [Who are you looking for?]
    Grown, steady on their feet, nobody at home who'd be left with nothing. That's most of it.
    -> asks
+ [I'll let you get on.]
    Go on.
    -> DONE

= done
// Scene 6 may have played already, when the oath was sworn before the quest caught up.
{done_first_oath:
    -> greet
}
-> first_oath
