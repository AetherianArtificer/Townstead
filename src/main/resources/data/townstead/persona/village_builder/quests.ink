// Act 1 quests. Each has an offer (used outside the first meeting) and a quest knot, which the
// meeting enters as a tunnel. A goal already met when the quest starts is handed back through
// "skipped". Progress talks are short exchanges with a practical tip; hand-backs are small
// conversations. Lines only claim what a goal or check confirmed (voice sheet, "Never").

// "we" once they trust the player, "you" before.
=== function we() ===
{rel("trust") >= 20:
    ~ return "we"
}
~ return "you"

// ---- It just needs a push (discovered villages) ----

=== push_offer ===
Have you picked one yet? A building to make better? I've got opinions, if you want them. I've always got opinions.
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
{~Anything better yet? Even a little? I'm not rushing you. I'm very excited, which is different.|Most buildings are only a few things away from the next step.}
+ [Any advice?]
    The Catalog shows what a building needs for its next step up. Usually it's only a few things.
    Start with the one people pass on their way to work. That's the one they'll talk about.
+ [Working on it.]
    I know. I can tell. Sorry, I'll stop hovering.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_push = true
~ trust(5)
You did it! I have to go and look at it properly.
+ [It was your idea.]
    It was your work. I just talked a lot. I'm very good at the talking part.
+ [What's next?]
    Oh, I've got a list. But enjoy this one first. Properly. Stand there and look at it.
- -> DONE

// ---- Nobody works like that (the mine) ----

=== rest_offer ===
{worry() == "rest" and rest_offer == 1: -> first_time}
Can I ask you about the work here? How people spend their weeks, I mean. Humor me.
+ [Go on.]
    -> rest -> DONE
+ [Not now.]
    Later, then. It'll keep.
    -> DONE

= first_time
Have you got a minute? There's something I keep thinking about.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    Later, then.
    -> DONE

=== rest ===
# quest: Nobody works like that
# about: Give every worker a day off in their weekly schedule. People work better rested.
# goal: rest_days
# skip if: rest_days
~ lesson_day = today
{meeting_asked: ->->}
One day off in the week, for everyone. Just one. They'll work better on the other six, I promise.
I'm not saying it to be kind. Where I grew up, nobody stopped, and it didn't end well.
+ [Everyone?]
    Everyone. Me too, probably. Don't hold me to that part.
+ [We can't spare anyone.]
    You can't spare them worn out, either. Try it for a week. If I'm wrong, I'll haul their stone myself.
- ->->

= waiting
-> checkin ->
{~Somebody's still working every day of the week. I checked. I always check.|The schedules are getting better. Not there yet, but better.}
+ [Any advice?]
    Open the shift planner and give each of them a day with no work in it. It doesn't have to be the same day for everyone.
    A week plan does it for you, if you'd rather set it once and forget it.
+ [Working on it.]
    I know. Thank you. It matters more than it looks.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_rest = true
~ trust(5)
Everyone has a day of their own now. Every one of them.
Some of them won't know what to do with it, the first time. They'll work it out.
+ [And you?]
    Me? I'm working on it. Don't look at me like that.
+ [Good.]
    Good. Yes.
- -> DONE

= skipped
~ daily()
~ done_rest = true
~ trust(5)
Oh! Everyone already has a day off. You did that before I even asked. Look at you.
-> DONE

// ---- A bed for everyone ----

=== beds_offer ===
{worry() == "beds" and beds_offer == 1: -> first_time}
Can I talk to you about beds? I know how that sounds. Stay with me.
+ [Go on.]
    -> beds -> DONE
+ [Not now.]
    That's fair. Later, then.
    -> DONE

= first_time
Have you got a minute? There's something I keep thinking about.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    That's fair. Later, then.
    -> DONE

=== beds ===
# quest: A bed for everyone
# about: Build more beds than you need. Then someone new moves in, and there's still room.
# goal: newcomer
# goal: spare_bed
~ lesson_day = today
{not meeting_asked:
    A few more beds than {we()} need. Room for the next person, and then a little more.
    Someone always turns up, sooner or later. When they do, I'd like there to be a bed waiting, and one left over.
}
->->

= waiting
-> checkin ->
{
- check("spare_bed"):
    {~There's room now! We just have to wait for someone to notice it.|There's a bed waiting. Now someone just has to come.}
- else:
    {~Every bed's spoken for, as far as I can see. We could use a few more.|Two more, at least, so there's always one to spare.}
}
+ [Any advice?]
    A bed only counts inside a house. A small house with two beds does more than you'd think.
    Newcomers come on their own when there's room. You don't have to go looking for them.
+ [Working on it.]
    I know. I'll keep counting beds.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_beds = true
~ trust(5)
Someone new moved in! And there's still a bed to spare.
{worry() == "beds": Back home, at the end, nobody expected anyone. Anyway! This is good. This is really good.}
+ [It was a good idea.]
    It was a good bed. I just pointed at it.
+ [Have you met them?]
    Not properly yet. I'm going to try very hard not to check their corners.
- -> DONE

// ---- Something in the pot ----

=== pot_offer ===
{worry() == "pot" and pot_offer == 1: -> first_time}
Can I ask you about food? Not mine. Everyone's.
+ [Go on.]
    -> pot -> DONE
+ [Not now.]
    All right. Later, then.
    -> DONE

= first_time
Have you got a minute? There's something I keep thinking about.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    All right. Later, then.
    -> DONE

=== pot ===
# quest: Something in the pot
# about: Get the village's hunger to a good level. Put food where people can reach it.
# goal: fed
# skip if: fed
~ lesson_day = today
{not meeting_asked:
    Keep food close to where people live. A kitchen, a chest in a house, anywhere that isn't locked away.
    {can_build("kitchen"): And if someone here can cook, let them. A warm meal goes further than a cold one.}
    Enough that nobody goes without. That's all I'm asking.
}
->->

= waiting
-> checkin ->
{
- check("hungry"):
    {~People are still a bit hungry. I peeked in a chest. Sorry. Habit.|Not quite there yet. Keep going.}
- else:
    {~It's getting better. Keep it coming.|Nearly there. Don't stop now.}
}
+ [Any advice?]
    Put food in a chest inside a house, or in a kitchen. People eat from what they can reach, not from what you're carrying.
    Bread, and anything cooked, goes the furthest.
+ [Working on it.]
    I know. Thank you. This one matters to me.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_pot = true
~ trust(5)
Nobody here's going hungry now. Nobody!
{worry() == "pot": That's the one thing I always look for first. And it's here. I'm going to go and stand near a pot and feel good about it.}
+ [Go on, then.]
    I will! Don't wait up.
+ [You worry a lot, don't you?]
    About food? Always. It's the one thing I can't stop checking.
- -> DONE

= skipped
~ daily()
~ done_pot = true
~ trust(5)
Oh! Everyone's already eating well. You were ahead of me. Good. I'll find something else to fuss about.
-> DONE

// ---- Everybody does something ----

=== work_offer ===
{worry() == "work" and work_offer == 1: -> first_time}
Can I ask about work? Who's doing what, I mean. Humor me.
+ [Go on.]
    -> work -> DONE
+ [Not now.]
    Sure. Later, then.
    -> DONE

= first_time
Have you got a minute? There's something I keep thinking about.
+ [Of course.]
    -> meeting.first_worry -> DONE
+ [Not now.]
    Sure. Later, then.
    -> DONE

=== work ===
# quest: Everybody does something
# about: Get residents working. A workstation each, and a job worth doing.
# goal: three_working
# skip if: three_working
~ lesson_day = today
{not meeting_asked:
    People stay where there's work. A workstation each, and something worth doing at it.
    Three people working is a good start. After that it gets easier, I promise.
}
->->

= waiting
-> checkin ->
{count("idle_adults") > 0:
    {~A few people still have nothing to do. Not their fault. Nobody's asked them yet.|Almost everyone's got somewhere to be. Almost.}
- else:
    Nearly there. Give it a little time to settle.
}
+ [Any advice?]
    Put a workstation near the houses, and someone without a job will usually take it. The Career screen shows who's doing what.
+ [Working on it.]
    I know. It shows.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_work = true
~ trust(5)
Everyone has somewhere to be now. Everyone!
{worry() == "work": Back home, the mornings went quiet first. This is the opposite of that. I didn't know how much I'd missed it.}
+ [It's a good feeling.]
    It is!
+ [What now?]
    Now we let it settle, and then I find something new to fuss about. You know me by now.
- -> DONE

= skipped
~ daily()
~ done_work = true
~ trust(5)
Oh! Everyone's already working. Somebody here knows what they're doing, and I think it's you.
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
Have you got a minute? There's something I keep thinking about.
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
{not meeting_asked:
    Put water where people live. Anything that isn't a long walk with a heavy bucket.
}
->->

= waiting
-> checkin ->
{~Water's still a walk for some people.|It's closer than it was! Closer still would be lovely.}
+ [Any advice?]
    Water near the houses is the whole trick. A water source by the front doors does more than a lake past the fields.
+ [Working on it.]
    I know. Thank you.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_water = true
~ trust(5)
Nobody's short of water now. Not one person.
{worry() == "water": I used to carry buckets for an hour every morning, as a child. I don't miss it. Anyway!}
+ [Thank you for pushing.]
    Thank you for listening. Not everyone does.
+ [You can stop counting steps.]
    I can, can't I? I probably won't.
- -> DONE

= skipped
~ daily()
~ done_water = true
~ trust(5)
Oh! There's already water close by. Good. That's one thing I don't have to worry about.
-> DONE

// ---- Let someone else carry it (after their first collapse) ----

=== carry_offer ===
{demeanor() == "stern": -> stern}
Oh. You heard.
I'm fine, honestly. I sat down. Quite hard, and not on purpose, but I sat down.
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
I don't know how to stop, that's the truth of it. Where I grew up, nobody stopped until the work did.
And then it did, all at once, and nobody knew what to do with their hands. I suppose I never learned.
{here("farmer"):
    I saw it coming. I meant to say something, and then I got busy with a new seed, and I didn't. # who: farmer
    It's not your job to watch me.
    It's exactly my job. # who: farmer
}
+ [Then let someone else carry some of it.]
    -> carry -> DONE
+ [Take a day off. That's not a request.]
    -> carry -> DONE

=== carry ===
# quest: Let someone else carry it
# about: Change their schedule: a day off each week, and no day longer than ten hours.
# goal: own_rest_day
# goal: shorter_days
Fine. Change my schedule, if you have to. A day off, and shorter days.
I'll hate it. Then I'll probably thank you.
->->

= waiting
~ daily()
{~I looked at my schedule. It's still long. I'm not complaining. I'm reporting.|I'd change it myself, but I'd only make it longer. You know I would.}
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
    Strange and quiet, and good, I think. Nothing fell down without me. I checked twice.
+ [I'm glad.]
    So am I. Don't tell anyone.
- Thank you. For not letting me talk you out of it.
-> DONE
