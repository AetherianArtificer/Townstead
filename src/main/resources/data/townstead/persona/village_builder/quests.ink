// Act 1 quests. Each has an offer (used outside the first meeting) and a quest knot, which the
// meeting enters as a tunnel. A goal already met when the quest starts is handed back through
// "skipped": the Builder checks, sees it done, and moves on. Progress talks are short exchanges
// with a practical tip; hand-backs are small conversations, not a single line.

// ---- It just needs a push (discovered villages) ----

=== push_offer ===
Have you had a chance to make something better yet? Anything at all. People notice more than you'd think.
+ [Remind me what you meant.]
    Pick a building everyone walks past, and make it better. People notice. Then they stay.
    -> push -> DONE
+ [Not yet.]
    That's all right. It'll still be there tomorrow, and so will I.
    -> DONE

=== push ===
# quest: It just needs a push
# about: Upgrade a building that everyone walks past. A place grows when people can see it growing.
# goal: upgrade_one
~ lesson_day = today
->->

= waiting
-> checkin ->
{~I keep walking the same loop through town, waiting for something new on it.|I've been looking at the buildings again. There's always one that's nearly ready for more.}
+ [Any advice?]
    The Catalog shows what a building needs for its next step up. Usually it's only a few things.
    Start with the one people walk past on their way to work. That's the one they'll talk about.
+ [I'm working on it.]
    I know you are. I'm not rushing you. I'm just excited.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_push = true
~ trust(5)
You did it. I walked past twice, just to be sure I wasn't imagining it.
People slow down when they pass it now. Somebody stopped to point it out to their neighbor.
That's how it starts, you know. One thing gets better, and everyone starts looking for the next.
+ [It was your idea.]
    It was your work. I just talked a lot. I'm good at that part.
+ [What's next?]
    Oh, I've got a list. I always have a list. But enjoy this one first.
- -> DONE

// ---- Nobody works like that (the mine) ----

=== rest_offer ===
{worry() == "rest" and rest_offer == 1: -> first_time}
Can I ask you something about the work here? How people spend their weeks.
+ [Go on.]
    -> rest -> DONE
+ [Not now.]
    Later, then. It'll keep.
    -> DONE

= first_time
Have you got a minute? There's something I've been meaning to say.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    Later, then. It'll keep. The people working too hard won't, but it will.
    -> DONE

=== rest ===
# quest: Nobody works like that
# about: Give every worker a day off in their weekly schedule. People work better rested.
# goal: rest_days
# skip if: rest_days
~ lesson_day = today
Give everyone one day off in the week. Just one. They'll work better for it on the other days.
I'm not saying it to be kind. I've watched what happens when nobody stops. It's slower than you'd think, and then it's very fast.
+ [Everyone?]
    Everyone. Me too, probably. Don't hold me to that part.
+ [We can't spare anyone.]
    You can't spare them tired, either. Try it for a week. If I'm wrong, I'll haul their stone myself.
    I'd probably do that anyway, if I'm honest.
- ->->

= waiting
-> checkin ->
{~Somebody's still working every day of the week. I checked, I'm afraid. I always check.|The schedules are getting better. Not there yet, but better.}
+ [Any advice?]
    Open the shift planner and give each of them a day with no work in it. It doesn't have to be the same day for everyone.
    A week plan does it for you, if you'd rather set it once and forget about it.
+ [I'm working on it.]
    I know. Thank you. It matters more than it looks.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_rest = true
~ trust(5)
Everyone has a day of their own now. I watched a few of them on theirs.
They didn't know what to do with themselves at first. They stood around like they'd forgotten something.
Then they figured it out. Somebody went fishing. Somebody slept until noon. It was lovely.
+ [And you?]
    Me? I'm working on it. Don't look at me like that.
+ [Good.]
    Good. Yes. It really is.
- -> DONE

= skipped
~ daily()
~ done_rest = true
~ trust(5)
Oh. Everyone already has a day off. You did that before I even asked.
I'll try to stop explaining things you already know. No promises, but I'll try.
-> DONE

// ---- A bed for everyone ----

=== beds_offer ===
{worry() == "beds" and beds_offer == 1: -> first_time}
Can I talk to you about beds? I know how that sounds. Stay with me.
+ [Go on.]
    -> beds -> DONE
+ [Not now.]
    That's fair. The beds aren't going anywhere. That's sort of the point.
    -> DONE

= first_time
Have you got a minute? There's something I've been meaning to say.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    That's fair. The beds aren't going anywhere. That's sort of the point.
    -> DONE

=== beds ===
# quest: A bed for everyone
# about: Build more beds than you need. Then someone new moves in, and there's still room.
# goal: newcomer
# goal: spare_bed
~ lesson_day = today
Build a few more beds than you need. Room for the next person, and then a little more.
Someone always turns up, sooner or later. And when they do, I'd like there to be a bed waiting, and one left over.
->->

= waiting
-> checkin ->
{
- check("spare_bed"):
    {~There's room now. We just have to wait for someone to notice it.|A spare bed is a kind of invitation, you know. Somebody will take it.}
- else:
    {~Every bed's taken at the moment. I counted. I always count.|We could use a few more beds. Two at least, so there's one to spare.}
}
+ [Any advice?]
    A bed counts once it's inside a house. A small house with two beds does more than you'd think.
    Newcomers come on their own when there's room. You don't have to go looking for them.
+ [I'm working on it.]
    I know. I can hear the hammering from here.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_beds = true
~ trust(5)
Someone new moved in. Did you see? And there's still a bed to spare.
That's what a place expecting company looks like.
{worry() == "beds": Where I grew up, nobody expected anyone, at the end. Anyway. This is good. This is really good.}
+ [It was a good idea.]
    It was a good bed. I just pointed at it.
+ [Who moved in?]
    I haven't met them properly yet. I'm going to try very hard not to count their things.
- -> DONE

// ---- Something in the pot ----

=== pot_offer ===
{worry() == "pot" and pot_offer == 1: -> first_time}
Can I ask you about food? Not mine. Everyone's.
+ [Go on.]
    -> pot -> DONE
+ [Not now.]
    All right. Nobody's going hungry today. I'll keep an eye on it.
    -> DONE

= first_time
Have you got a minute? There's something I've been meaning to say.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    All right. Nobody's going hungry today. I'll keep an eye on it.
    -> DONE

=== pot ===
# quest: Something in the pot
# about: Get the village's hunger to a good level. Put food where people can reach it.
# goal: fed
# skip if: fed
~ lesson_day = today
Keep food somewhere close to where people live. A kitchen, a chest in a house, anywhere that isn't locked away.
{can_build("kitchen"): And if someone here can cook, let them. A warm meal goes further than a cold one, and it makes a house feel lived in.}
Enough that nobody goes without. That's all I'm asking.
->->

= waiting
-> checkin ->
{
- check("hungry"):
    {~People are still a little hungry. You can tell. They go quiet.|The chests near the houses are nearly empty. I looked, I'm afraid.}
- else:
    {~It's getting better. Keep it coming.|Nearly there. People have stopped checking the chests twice.}
}
+ [Any advice?]
    Put food in a chest inside a house, or in a kitchen. People eat from what they can reach, not from what you're carrying.
    Bread and anything cooked go the furthest.
+ [I'm working on it.]
    I know. I can smell it, some days.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_pot = true
~ trust(5)
Everyone's eating. I walked past at supper, and nobody was waiting on anyone.
{worry() == "pot": That's the sound I remember from before it all went wrong. Plates, and people talking over each other. It's a good sound.}
+ [It's a good sound.]
    It is. Thank you for making it.
+ [You worry a lot, don't you?]
    About food? Always. It's the one thing I can't make myself stop checking.
- -> DONE

= skipped
~ daily()
~ done_pot = true
~ trust(5)
Oh. Everyone's already eating well. You were ahead of me.
Good. I'll find something else to worry about. I always do.
-> DONE

// ---- Everybody does something ----

=== work_offer ===
{worry() == "work" and work_offer == 1: -> first_time}
Can I ask about work? Who's doing what, I mean. Humor me.
+ [Go on.]
    -> work -> DONE
+ [Not now.]
    Sure. It can wait a day. Not a season, but a day.
    -> DONE

= first_time
Have you got a minute? There's something I've been meaning to say.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    Sure. It can wait a day. Not a season, but a day.
    -> DONE

=== work ===
# quest: Everybody does something
# about: Get residents working. A workstation each, and a job worth doing.
# goal: three_working
# skip if: three_working
~ lesson_day = today
People stay where there's work. Give them a workstation each, and something worth doing at it.
Three people working is a good start. After that it gets easier, I promise. Work makes more work.
->->

= waiting
-> checkin ->
{count("idle_adults") > 0:
    {~A few people are still standing around. It's not their fault. Nobody's asked them yet.|Everyone wants something to do. Most people, anyway.}
- else:
    Nearly there. Give it a little time to settle.
}
+ [Any advice?]
    Put a workstation near the houses, and someone without a job will usually take it. The Career screen shows who's doing what.
    It helps if it's something the village needs. Nobody stays for a job that doesn't matter.
+ [I'm working on it.]
    I know. It shows.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_work = true
~ trust(5)
Everyone has somewhere to be in the morning now. You can hear it, can't you? It's louder, in a good way.
{worry() == "work": Where I grew up, the mornings went quiet first. This is the opposite of that. I didn't know how much I'd missed it.}
+ [It's a good noise.]
    The best one. Tools, and people calling to each other. Nothing sounds more like a town.
+ [What now?]
    Now we let it settle, and then I find something new to fuss about. You know me by now.
- -> DONE

= skipped
~ daily()
~ done_work = true
~ trust(5)
Oh. Everyone's already working. Somebody here knows what they're doing, and I think it's you.
-> DONE

// ---- Water you can reach (only while a thirst system is on) ----

=== water_offer ===
{worry() == "water" and water_offer == 1: -> first_time}
Can I ask about water? It's not exciting, I know. It's important, though.
+ [Go on.]
    -> water -> DONE
+ [Not now.]
    All right. I'll drink slowly.
    -> DONE

= first_time
Have you got a minute? There's something I've been meaning to say.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    All right. I'll drink slowly.
    -> DONE

=== water ===
# quest: Water you can reach
# about: Get the village's thirst to a good level. Put water where people live.
# goal: watered
# skip if: watered
~ lesson_day = today
Put water where people live. A well, a stream nearby, anything that isn't a long walk with a heavy bucket.
It sounds small. It isn't. Nobody stays long in a place where every drink is a trip.
->->

= waiting
-> checkin ->
{~People are still walking a long way for water. I followed one, I'm afraid.|It's closer than it was. Closer is good. Closer still is better.}
+ [Any advice?]
    Water near the houses is the whole trick. A water source by the front doors does more than a lake past the fields.
+ [I'm working on it.]
    I know. Thank you. It matters to me more than I can say.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_water = true
~ trust(5)
Nobody's walking for water anymore. I timed it, and it's about nine steps from the nearest door.
{worry() == "water": I used to carry buckets for an hour every morning, as a child. I don't miss it. Anyway.}
+ [Nine steps.]
    Nine. I checked twice. I'm very proud of those nine steps.
+ [Thank you for pushing.]
    Thank you for listening. Not everyone does.
- -> DONE

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
I'm fine, really. I sat down. Quite hard, and not on purpose, but I sat down.
+ [You collapsed.]
    I know. I was there. Briefly.
+ [You're working too much.]
    Everyone's working. I'm just also working. A lot. All right, I see the problem.
- -> admit

= stern
You heard. I'm fine. Leave it.
+ [You collapsed.]
    I sat down. Quite hard. It happens.
    -> admit
+ [All right.]
    -> DONE

= admit
I don't know how to stop. That's the truth of it. Where I grew up, nobody stopped until the work did.
And then it did, all at once, and nobody knew what to do with their hands. I suppose I never learned.
+ [Then let someone else carry some of it.]
    -> carry -> DONE
+ [Take a day off. That's not a request.]
    -> carry -> DONE

=== carry ===
# quest: Let someone else carry it
# about: Change their schedule: a day off each week, and no day longer than ten hours. Nobody carries a village alone.
# goal: own_rest_day
# goal: shorter_days
Fine. Change my schedule, if you have to. A day off, and shorter days.
I'll hate it. Then I'll probably thank you. Don't tell me which one happens first.
->->

= waiting
~ daily()
{~I looked at my schedule. It's still long. I'm not complaining. I'm reporting.|I'd change it myself, but I'd only make it longer. You know I would.|Somebody else can haul that stone. I keep telling myself that.}
+ [Any advice?]
    You're asking me? All right. Open my week in the shift planner. One day with no work, and no day past ten hours.
    Please don't let me talk you out of it.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_carry = true
~ trust(10)
I took the day off. I sat down on purpose this time, and felt guilty for about an hour.
Then I didn't. That was new.
+ [How did it feel?]
    Strange. Quiet. Good, I think. I watched other people carry things, and nothing fell down.
+ [I'm glad.]
    So am I. Don't tell anyone.
- Thank you. For not letting me talk you out of it.
-> DONE
