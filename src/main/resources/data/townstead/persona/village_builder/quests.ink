// Act 1 quests. Each has an offer (used outside the first meeting) and a quest knot, which the
// meeting enters as a tunnel. A goal already met when the quest starts is handed back through
// "skipped": the Builder checks, sees it done, and moves on.

// ---- It just needs a push (discovered villages) ----

=== push_offer ===
Have you had a chance to make something better? Anything. People notice.
+ [Tell me again.]
    Pick a building everyone walks past and make it better. People notice. Then they stay.
    -> push -> DONE
+ [Not yet.]
    No rush. It'll still need doing tomorrow.
    -> DONE

=== push ===
# quest: It just needs a push
# about: Upgrade a building that everyone walks past. A place grows when people can see it growing.
# goal: upgrade_one
~ lesson_day = today
->->

= waiting
-> checkin ->
{~Anything better yet? Even a little?|Pick the one everyone walks past. That's the trick.|I keep walking the same loop. I'm waiting for something new on it.}
-> DONE

= done
~ daily()
~ done_push = true
~ trust(5)
You did it. I walked past twice to be sure.
People slow down when they walk past it now. That's how it starts.
-> DONE

// ---- Nobody works like that (the mine) ----

=== rest_offer ===
{worry() == "rest" and rest_offer == 1:
    Can I tell you the first thing I look for in a place?
    Where I grew up, the mine ran two shifts, then three. People went down tired and came up worse.
- else:
    Can I ask you something about the work here?
}
+ [Go on.]
    -> rest -> DONE
+ [Not now.]
    Later, then. It'll keep. They won't, but it will.
    -> DONE

=== rest ===
# quest: Nobody works like that
# about: Give every worker a day off in their weekly schedule. People work better rested.
# goal: rest_days
# skip if: rest_days
~ lesson_day = today
Give everyone a day off in the week. One day. They'll work better for it. I'm not being kind, it's just true.
+ [Everyone?]
    Everyone. Me too, probably. Don't hold me to that part.
+ [We can't spare anyone.]
    You can't spare them tired, either. Try it for a week. If I'm wrong, I'll haul their stone myself.
    I'd probably do that anyway.
- ->->

= waiting
-> checkin ->
{~The schedules are on the board. One day. That's all I'm asking.|Somebody's still working every day of the week. I checked. Sorry. I check.|One day off each. It doesn't have to be the same day.}
-> DONE

= done
~ daily()
~ done_rest = true
~ trust(5)
Everyone gets a day now. I watched them on it. They didn't know what to do with themselves at first.
Then they figured it out.
-> DONE

= skipped
~ daily()
~ done_rest = true
~ trust(5)
Oh. Everyone already has a day off. You did that before I asked.
I'll stop explaining things to you. No, I won't. But I'll try.
-> DONE

// ---- A bed for everyone ----

=== beds_offer ===
{worry() == "beds" and beds_offer == 1:
    Can I tell you the first thing I look for in a place?
    {hometown == "harbor":
        Where I grew up, the harbor filled with sand. The boats stopped coming, and then so did the people.
    - else:
        Where I grew up, they cut the forest to the last tree. Then they pulled the houses apart for the timber.
    }
- else:
    Can I talk to you about beds? I know. Stay with me.
}
+ [Go on.]
    -> beds -> DONE
+ [Not now.]
    Fair. Beds aren't going anywhere. That's sort of the point.
    -> DONE

=== beds ===
# quest: A bed for everyone
# about: Build more beds than you need. Then someone new moves in, and there's still room.
# goal: newcomer
# goal: spare_bed
~ lesson_day = today
Build a few more beds than you need. Room for the next person, and then some.
Someone always turns up. When they do, I want there to be a bed left over.
->->

= waiting
-> checkin ->
{
- check("spare_bed"):
    {~There's room. Now we wait for someone to notice.|A spare bed is a kind of invitation. Somebody will take it.}
- else:
    {~Every bed's taken. I counted. Sorry. I count things.|We need a few more. Two, at least.}
}
-> DONE

= done
~ daily()
~ done_beds = true
~ trust(5)
Someone new moved in. And there's still a bed to spare. That's a place expecting company.
{worry() == "beds": Where I grew up, nobody expected anyone. Anyway. It's good. This is good.}
-> DONE

// ---- Something in the pot ----

=== pot_offer ===
{worry() == "pot" and pot_offer == 1:
    Can I tell you the first thing I look for in a place?
    Where I grew up, the mill fed half the valley. Then one year it didn't, and people went to bed hungry.
- else:
    Can I ask about food? Not mine. Everyone's.
}
+ [Go on.]
    -> pot -> DONE
+ [Not now.]
    All right. Nobody's starving. I'll keep an eye on it.
    -> DONE

=== pot ===
# quest: Something in the pot
# about: Get the village's hunger to a good level. Put food where people can reach it.
# goal: fed
# skip if: fed
~ lesson_day = today
Keep food somewhere close. A kitchen, a chest in a house, anywhere that isn't locked away.
{can_build("kitchen"): And if someone here can cook, let them. A warm meal goes further than a cold one.}
Enough that nobody goes without. That's all.
->->

= waiting
-> checkin ->
{
- check("hungry"):
    {~People are still hungry. I can tell. They get quiet.|The chests near the houses are nearly empty. I looked. Sorry. I look.}
- else:
    {~It's getting better. Keep it coming.|Nearly there. People have stopped checking the chests twice.}
}
-> DONE

= done
~ daily()
~ done_pot = true
~ trust(5)
Everyone's eating. I walked past at supper and nobody was waiting on anyone.
{worry() == "pot": That's the sound I remember from before it went wrong. Plates. It's a good sound.}
-> DONE

= skipped
~ daily()
~ done_pot = true
~ trust(5)
Oh. Everyone's already eating well. You were ahead of me.
I'll find something else to worry about. I always do.
-> DONE

// ---- Everybody does something ----

=== work_offer ===
{worry() == "work" and work_offer == 1:
    Can I tell you the first thing I look for in a place?
    Where I grew up, the roads got too dangerous for traders. The market closed, and then there was nothing to do.
- else:
    Can I ask about work? Who's doing what, I mean.
}
+ [Go on.]
    -> work -> DONE
+ [Not now.]
    Sure. It can wait a day. Not a season. A day.
    -> DONE

=== work ===
# quest: Everybody does something
# about: Get residents working. A workstation each, and a job worth doing.
# goal: three_working
# skip if: three_working
~ lesson_day = today
People stay where there's work. Give them a workstation each, and something worth doing at it.
Three people working is a good start. After that it gets easier. It really does.
->->

= waiting
-> checkin ->
{count("idle_adults") > 0:
    {~A few people are still standing around. Not their fault. Nobody's asked them.|Everyone wants something to do. Most people, anyway.}
- else:
    Nearly there. Give it time to settle.
}
-> DONE

= done
~ daily()
~ done_work = true
~ trust(5)
Everyone's got somewhere to be in the morning. You can hear it. It's louder, in a good way.
{worry() == "work": Where I grew up, the mornings went quiet first. This is the opposite of that.}
-> DONE

= skipped
~ daily()
~ done_work = true
~ trust(5)
Oh. Everyone's already working. Somebody here knows what they're doing. I think it's you.
-> DONE

// ---- Water you can reach (only while a thirst system is on) ----

=== water_offer ===
{worry() == "water" and water_offer == 1:
    Can I tell you the first thing I look for in a place?
    Where I grew up, the well went dry one summer and never came back.
- else:
    Can I ask about water? It's not exciting. It's important, though.
}
+ [Go on.]
    -> water -> DONE
+ [Not now.]
    All right. I'll drink slowly.
    -> DONE

=== water ===
# quest: Water you can reach
# about: Get the village's thirst to a good level. Put water where people live.
# goal: watered
# skip if: watered
~ lesson_day = today
Put water where people live. A well, a stream, a barrel. Anything that isn't a long walk.
->->

= waiting
-> checkin ->
{~People are still walking a long way for water. I followed one. Sorry. I follow people.|Closer to the houses. That's the whole trick.}
-> DONE

= done
~ daily()
~ done_water = true
~ trust(5)
Nobody's walking for water anymore. I timed it. It's about nine steps from the nearest door.
{worry() == "water": I used to carry buckets for an hour every morning. I don't miss it. Anyway.}
-> DONE

= skipped
~ daily()
~ done_water = true
~ trust(5)
Oh. There's already water close by. Good. That's one thing I don't have to worry about.
-> DONE

// ---- Let someone else carry it (after their first collapse) ----

=== carry_offer ===
{demeanor() == "stern": -> stern}
Oh. You heard.
I'm fine. I sat down. Quite hard, and not on purpose, but I sat down.
+ [You collapsed.]
    I know. I was there. Briefly.
+ [You're working too much.]
    Everyone's working. I'm just also working. A lot. I see the problem.
- -> admit

= stern
You heard. I'm fine. Leave it.
+ [You collapsed.]
    I sat down. Quite hard. It happens.
    -> admit
+ [All right.]
    -> DONE

= admit
I don't know how to stop. Where I grew up, nobody stopped until the work did.
+ [Then let someone else carry some of it.]
    -> carry -> DONE
+ [Take a day off. That's not a request.]
    -> carry -> DONE

=== carry ===
# quest: Let someone else carry it
# about: Change their schedule: a day off each week, and no day longer than ten hours. Nobody carries a village alone.
# goal: own_rest_day
# goal: shorter_days
Fine. Change my schedule, if you have to. A day off. Shorter days.
I'll hate it. Then I'll probably thank you. Don't tell me which one happens first.
->->

= waiting
~ daily()
{~I looked at my schedule. It's still long. I'm not complaining. I'm reporting.|I'd change it myself, but I'd only make it longer.|Somebody else can haul that stone. I keep telling myself that.}
-> DONE

= done
~ daily()
~ done_carry = true
~ trust(10)
I took the day off. I sat down on purpose this time, and felt guilty for about an hour.
Then I didn't. That was new.
Thank you. For not letting me.
-> DONE
