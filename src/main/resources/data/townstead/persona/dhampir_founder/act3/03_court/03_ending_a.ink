// Ending A.
=== court_ending_a ===
~ ending = "A"
You couldn't stop. # emote:ponder
I know what that's like. I know exactly what that's like. There's something in me that wants things, and it doesn't care what I think about it.
It isn't you. It's the thing in you. I've got one too. I've had it all my life, and I've been calling it by other people's names.
{done_inside:
    There's a wolf by my fire. I'd like to get back to it. That's the most I've wanted anything in a long time.
}
I'm not going to kill you. I'm going home.
...Thank you. # who: visitor:father_thrall
Don't. I didn't do it for you.
-> father

= father
And him. # emote:point
That's yours to say. I can't. I've been deciding what to do about him since before I could walk, and I'm tired.
* {check("carries_relic")} [End him.]
    -> end_him
* [Spare him.]
    -> spare_him

= end_him
~ father_fate = "ended"
~ act("end_father")
Ah. # who: visitor:father
...There. # emote:ponder
I thought I'd feel something. I feel like I've put down something heavy. That's all.
~ act("quiet_leave")
Go on. Go wherever you like. Somewhere he isn't.
-> home_a

= spare_him
~ father_fate = "spared"
~ act("father_spared")
Then go home, both of you. Nothing of mine will come to your town, not while I keep this house. You have my word. # who: visitor:father
It is older than your town. # who: visitor:father
Keep it, then. You're good at keeping.
~ act("father_leave")
-> home_a

= home_a
~ act("ending_a")
Let's go home. # emote:nod
I want to stand at that altar in the morning and say all nine. Out loud. With nobody there but the wolf.
~ end_trip(court_walking, "court_home")
-> DONE
