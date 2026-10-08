// Act 2, scene 16: The willing thrall (the rescue nobody asked for). After the stakeout (15), a
// vampire and their thrall walk into town together (visitors: role "couple", and "couple_thrall"
// for the thrall). In scene 10 the master was dead; here they love each other. A three-way scene
// once they are near: the founder offers to break the bond, and the thrall refuses, because they
// chose it. "That's what it does. It makes you say that." Both are right; it is the Ending A truth,
// said too early and for the wrong reason. The thrall minds, a little, who else the master talks
// to: a small detail that points at the quiet one. Choice: respect the choice (Oath), side with
// the founder (Hunger), or ask the master. At high Hunger the founder breaks the bond for real
// (release_thrall). The thrall is hurt by it, and the master remembers.

VAR couple_called = false
VAR done_willing = false
VAR willing_broke = false
VAR asked_master = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function couple_arrival_ready() ===
~ return done_stakeout and not couple_called

=== function couple_meet_ready() ===
~ return couple_called and not done_willing and here("visitor:couple_thrall")

=== couple_arrival ===
~ couple_called = true
~ act("couple_arrives")
Two of them coming up the road. One of them, and one of theirs.
{argued_consent:
    You'll like this. You can tell me all about how it's fine because somebody said yes.
- else:
    Stay close when they get here. I want you to hear this.
}
-> DONE

=== couple_meet ===
~ done_willing = true
You. Not them. You, the one standing a step behind. # emote:point
I can cut that. Whatever it is that ties you to them. It isn't even hard, if you know where to put the knife, so to speak.
I don't want it cut. # who: visitor:couple_thrall
That's what it does. It makes you say that.
He asked me. Three times, over a whole winter. I said no twice. I said yes the third time because I wanted to, and I'd say it again tomorrow. # who: visitor:couple_thrall
He talks to other people. Half the night, some nights. I don't mind. # who: visitor:couple_thrall
...I mind a little. That isn't the bond. That's just me. # who: visitor:couple_thrall
- (choose)
* [It's their choice. Leave it.]
    -> respect
* {not check("hungry")} [Cut it.]
    -> refuse
* {check("hungry")} [Cut it.]
    -> cut
* [What does he say?]
    ~ asked_master = true
    I'd let them go any night they asked. They've never asked. # who: visitor:couple
    And I'd know, would I? If you were making them not ask.
    You would not. That's the trouble with it, hunter. Nobody would. # who: visitor:couple
    -> choose

= respect
~ toward_oath()
...Fine. Their choice. # emote:shrug
I'm going to keep thinking you're wrong. Both of you. I'll just do it somewhere else.
~ act("couple_stay")
They can stay, if they want. I'm not going to stand in the road about it.
-> DONE

// Siding with the founder, below the hungry tier: they want to, and do not.
= refuse
~ toward_hunger()
I'd like to. You've no idea how much. # emote:ponder
But they're holding on to it with both hands, and I'd have to break their fingers to get it off them.
Go on. Both of you. Find somewhere else.
~ act("couple_leave")
-> DONE

// At high Hunger they break it for real.
= cut
~ willing_broke = true
~ toward_hunger()
~ act("break_bond")
There. # emote:nod
Oh. Oh, no. No, no. # who: visitor:couple_thrall
You'll be remembered for that, hunter. I keep a long list and a longer memory. # who: visitor:couple
Good. Put me at the top.
~ act("couple_leave")
...They'll thank me. Not this year. But they will.
-> DONE
