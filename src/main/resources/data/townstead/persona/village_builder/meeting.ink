// The first meeting, as a real conversation: an arrival beat, how the player receives them, then
// topics the player can ask about in any order before saying goodbye. A new settlement ends in
// the first worry from home; a village the player did not found ends in "It just needs a push".
// Deliberately left out: the secret, hobbies, the line, and any stranger's greeting.

VAR player_name = ""

=== meeting ===
{check("discovered"):
    -> discovered
}
-> settlement

// ---- Scene A: a new settlement ----

= settlement
~ temp raised = building("raised")
{raised != "":
    You put up the {raised}, right?
- else:
    You put all this up, right?
}
{count("residents") > 0:
    I asked around, and everyone pointed at you.
- else:
    There's nobody else around, so I guessed it was you.
}
Sorry, I've been wandering around your place for about an hour. I do that when I find somewhere new. I hope you don't mind.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. Good. It's nice to meet you properly, instead of just your walls.
+ [Can I help you?]
    No, no. Well, maybe. Mostly I wanted to say hello to whoever built this.
+ [Were you snooping?]
    A little. Only the outside. And maybe one chest. I'll confess to the chest.
+ [I'm a bit busy right now.]
    ~ met = 1
    Of course, sorry. I'll be around. Come and find me when you've got a minute.
    -> DONE
- ~ met = 2
-> settlement_topics

// When the player was busy last time.
= resume
{check("discovered"):
    Oh, you came back. Good. Have you got a minute now?
- else:
    Oh, you came back. Good. Have you got a minute now? I've been looking at your roofs again.
}
+ [Sure.]
    ~ met = 2
    {check("discovered"): -> discovered_topics}
    -> settlement_topics
+ [Not yet.]
    Fair enough. I'm not going anywhere.
    -> DONE

= settlement_topics
- (topics)
* [Where are you from?]
    -> from ->
    -> topics
* [What do you do?]
    -> job ->
    -> topics
* [What brings you out here?]
    I was on the road and saw roofs where there weren't any last time I came through. You don't see that often.
    Most places I pass through are getting smaller. It's nice to find one that's getting bigger.
    So I wanted to meet whoever was doing it. And here you are.
    -> topics
* [What do you think of the place?]
    -> thoughts ->
    -> topics
+ [I should get back to it.]
    {not thoughts:
        Oh, before you go. Can I tell you one thing? It won't take long, I promise.
        -> thoughts ->
    }
    -> settlement_close

= thoughts
~ temp raised = building("raised")
{raised != "":
    Honestly? I like it. The {raised} is good work. Somebody measured that, and it shows.
- else:
    Honestly? I like it. The walls are straight, and somebody clearly measured. It shows.
}
{check("no_spare_beds"):
    You've got {count("residents")} people and exactly that many beds, which is fine, right up until somebody has a baby.
}
{check("hungry"):
    People look a bit hungry, though. I peeked in a chest or two, I'm afraid. Old habit.
}
-> first_worry ->
->->

// The first thing they look for, from what happened at home. Starts the first worry quest.
= first_worry
Can I tell you the first thing I always look for in a new place?
{hometown:
- "mill":
    Food, somewhere people can reach it. Where I grew up, the mill fed half the valley, until one year it didn't.
    I still remember how quiet a supper table gets. So I look for full pots before anything else.
    -> pot ->
- "mine":
    Whether anyone ever gets a day off. Where I grew up, the mine ran two shifts, then three.
    People went down tired and came up worse. So I watch who's still working after dark.
    -> rest ->
- "harbor":
    Beds. It sounds strange, I know. Where I grew up, the sand filled the harbor, the boats stopped coming, and then so did the people.
    A town that stops making room for new people has already started shrinking. So I count beds.
    -> beds ->
- "roads":
    Who's got work, and who doesn't. Where I grew up, the roads got too dangerous for traders, and the market closed.
    After that there was nothing to do, and people drift when there's nothing to do. So I look for idle hands.
    -> work ->
- "forest":
    Beds. It sounds strange, I know. Where I grew up, they cut the forest to the last tree, then pulled the houses apart for the timber.
    A town that stops making room for new people has already started shrinking. So I count beds.
    -> beds ->
- "well":
    Water, close to where people live. Where I grew up, the well went dry one summer and never came back.
    You'd be surprised how fast a town empties when every bucket is a long walk. So I look for water first.
    -> water ->
}
->->

= settlement_close
{hometown:
- "mill": I'd like to stay a while, if that's all right. I bake, so I won't just be eating your bread. I'll be making it.
- "mine": I'd like to stay a while, if that's all right. I work stone, so I won't just be sleeping under your roofs. I'll be fixing them.
- "harbor": I'd like to stay a while, if that's all right. I fish, so I won't just be eating your food. I'll be catching some of it.
- "roads": I'd like to stay a while, if that's all right. I make maps, so at least one of us will always know where everything is.
- "forest": I'd like to stay a while, if that's all right. I make arrows, so the next thing that wanders in at night will be sorry.
- else: I'd like to stay a while, if that's all right. I farm, so I won't just be eating your food. I'll be growing some of it.
}
+ [Stay. You're welcome here.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Thank you. Really. I'll find somewhere to sleep that isn't your hay pile.
+ [We'll see.]
    That's fair. I'd say the same about me. Give me a few days, and I'll try to earn it.
- I've kept you long enough. It was good to meet you, {player_name}.
+ [Good to meet you too.]
    -> DONE
+ [See you around.]
    -> DONE

// ---- Scene B: a village the player did not found ----

= discovered
~ temp upgraded = building("upgraded")
{upgraded != "":
    Hello. People here say you're the one who got the {upgraded} going again.
- else:
    Hello. People here say you've done a lot for this place.
}
{hometown:
- "mill": I've just come in on the river road, so forgive the state of me. I'm {villager_name}.
- "mine": I've just come down from the hills, so forgive the state of me. I'm {villager_name}.
- "harbor": I've just come up the coast road, so forgive the sand. It gets everywhere. I'm {villager_name}.
- "roads": I've just come in on the east road, so forgive the dust. I'm {villager_name}.
- "forest": I've just come through the woods, so forgive the leaves. I'm {villager_name}.
- else: I've just come across the dry flats, so forgive the dust. I'm {villager_name}.
}
+ {village_name != ""} [Welcome to {village_name}.]
    ~ contribute("affection", 1, "welcomed")
    Thank you. It's a lovely name. It sounds like somewhere people mean to stay.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. It's good to put a face to all the talk.
+ [I'm a bit busy right now.]
    ~ met = 1
    Of course, sorry. I'll be around. Come and find me when you've got a minute.
    -> DONE
- ~ met = 2
-> discovered_topics

= discovered_topics
- (topics)
* [Where are you from?]
    -> from ->
    -> topics
* [What do you do?]
    -> job ->
    -> topics
* [What brings you here?]
    I heard {village_name != "":{village_name}|this place} was doing well, and I wanted to see it for myself.
    Most places I pass through are getting smaller. It's nice to find one that isn't.
    -> topics
* [What do you make of the place?]
    -> noticed ->
    -> topics
+ [I should get back to it.]
    {not noticed:
        Oh, before you go. Can I tell you one thing I noticed? It won't take long.
        -> noticed ->
    }
    -> discovered_close

= noticed
Honestly? I like it. The houses are cared for, and people say hello when you pass.
{
- check("empty_houses"):
    I walked around a bit before I found you, and a few of the houses are standing empty. That surprised me.
- count("idle_adults") > 0:
    I walked around a bit before I found you, and a few people seem to have nothing to do all day. That surprised me.
- else:
    I walked around a bit before I found you. It's quieter than a place this good ought to be.
}
It reminds me of home a little. Not how it ended. How it was, before anyone noticed anything was wrong.
The good news is that nothing's falling down. It just needs a push.
{building("upgraded") != "":
    What you did with the {building("upgraded")} is the right idea. Do it again, somewhere everyone walks past.
- else:
    Pick a building everyone walks past, and make it better. People notice. Then they stay.
}
-> push ->
->->

= discovered_close
I've kept you long enough.
{hometown:
- "mill": I'll be near the ovens, if anyone will lend me one. It was good to meet you, {player_name}.
- "mine": I'll be around, probably admiring someone's wall. It was good to meet you, {player_name}.
- "harbor": I'll be off looking for somewhere to fish. I hear the fishing around here is terrible, and I'd like to find out for myself. It was good to meet you, {player_name}.
- "roads": I want to walk the edges and see how far this place really goes. It was good to meet you, {player_name}.
- "forest": I'll go and see where the trees are. I always like to know. It was good to meet you, {player_name}.
- else: I'll go and look at the fields, and try not to have too many opinions about them. It was good to meet you, {player_name}.
}
+ [Good to meet you too.]
    -> DONE
+ [See you around.]
    -> DONE

// ---- Shared topics ----

// Where they're from: one light mention of home, then a deflection.
= from
{hometown:
- "mill":
    A mill town, a few days up the river. Everything smelled of flour. You'd have liked it, once.
    There's not much of it left now.
- "mine":
    A mining town, up in the hills. Grey stone, grey sky, and the friendliest people you'd ever meet.
    It's mostly empty now.
- "harbor":
    A harbor town, down the coast. Boats everywhere, gulls everywhere, fish in everything.
    The sand took the harbor, in the end.
- "roads":
    A market town on the old east road. Traders came through from everywhere, and I grew up on other people's stories.
    Nobody uses the road much anymore.
- "forest":
    A timber town, in what used to be a forest. You could smell the sawdust from a mile off.
    It's mostly stumps now.
- else:
    A farming town out in the dry country. Tough ground, and tougher people.
    The well gave out, in the end.
}
It was a long time ago. Ask me something more cheerful.
->->

// What they do: their job from home, and the habit that comes with them everywhere.
= job
{hometown:
- "mill": I bake. Bread, mostly, and anything else that will sit still long enough to go in an oven.
- "mine": I work stone. Walls, steps, the odd well. Anything that should still be standing in a hundred years.
- "harbor": I fish, mostly, when the water lets me. I mend nets, too, and I have strong opinions about boats.
- "roads": I make maps. Somebody has to write down where everything is, or people keep getting lost.
- "forest": I make arrows, and bows when someone asks nicely. It's quieter work than it sounds.
- else: I farm. Dry ground, mostly, so anywhere with water nearby feels like a holiday.
}
And I can't walk past a half-built wall without wanting to finish it. That one's less of a job and more of a problem.
->->
