// The first meeting. A new settlement gets the "What I noticed" walk-through and the first worry
// from home. A village the player did not found gets "It just needs a push".
// Deliberately left out: the secret, hobbies, the line, and any stranger's greeting.

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
    I asked around and everyone pointed at you.
- else:
    Nobody else is around, so I figured it had to be you.
}
Sorry, I've been walking around your place for about an hour. I do that.
I'm {villager_name}. I saw roofs from the road and I had to come and look.
-> settlement_choice

= settlement_choice
+ [What did you think?]
    ~ met = 2
    -> thoughts
+ [Who are you, exactly?]
    ~ met = 2
    -> who_are_you
+ [I've got work to do.]
    ~ met = 1
    {hometown:
    - "harbor": Right, of course. I'll be around. Probably looking for somewhere to fish, having opinions about it.
    - "mine": Right, of course. I'll be around. Probably looking at your walls, having opinions about them.
    - "roads": Right, of course. I'll be around. Probably walking the edges, having opinions about them.
    - "forest": Right, of course. I'll be around. Probably counting trees, having opinions about them.
    - else: Right, of course. I'll be around. Probably looking for good soil, having opinions about it.
    }
    -> DONE

// When the player left early last time.
= resume
Oh, good. I was hoping you'd come back.
+ [What did you think of the place?]
    ~ met = 2
    -> thoughts
+ [Not now.]
    Fair enough. I'm not going anywhere.
    -> DONE

= who_are_you
{hometown:
- "mill": Nobody much. I bake. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
- "mine": Nobody much. I work stone. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
- "harbor": Nobody much. I fish. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
- "roads": Nobody much. I make maps. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
- "forest": Nobody much. I make arrows. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
- else: Nobody much. I farm. I also can't walk past a half-built wall without wanting to finish it, which is its own problem.
}
I heard someone was starting something out here. I wanted to see if it was true.
-> thoughts

= thoughts
~ temp raised = building("raised")
{raised != "":
    Honestly? The {raised} is good. Somebody measured that.
- else:
    Honestly? It's good. The walls are straight. Somebody measured.
}
{check("no_spare_beds"):
    You've got {count("residents")} people and exactly that many beds. That's fine until somebody has a baby.
}
{check("hungry"):
    And nobody's cooking. I looked in your chests. I know. I'm sorry. I looked in your chests.
}
{hometown:
- "mill":
    Where I grew up, the mill fed half the valley. Then one year it didn't, and people went to bed hungry.
    So that's the first thing I look for. Food somewhere people can reach it.
    -> pot -> close
- "mine":
    Where I grew up, the mine ran two shifts, then three. People went down tired and came up worse.
    So that's the first thing I look for. Whether anyone here ever gets a day off.
    -> rest -> close
- "harbor":
    Where I grew up, the harbor filled with sand. The boats stopped coming, and then so did the people.
    So I count beds. It's a strange thing to count, I know. It tells you if a place expects anyone new.
    -> beds -> close
- "roads":
    Where I grew up, the roads got too dangerous for traders. The market closed, and then there was nothing to do.
    So that's the first thing I look for. Who's got work, and who doesn't.
    -> work -> close
- "forest":
    Where I grew up, they cut the forest to the last tree. Then they pulled the houses apart for the timber.
    So I count beds. It's a strange thing to count, I know. It tells you if a place expects anyone new.
    -> beds -> close
- "well":
    Where I grew up, the well went dry one summer and never came back.
    So that's the first thing I look for. Water people can reach without a long walk.
    -> water -> close
}
-> beds -> close

= close
{hometown:
- "mill": I'd like to stay a while, if that's all right. I bake, so I won't just be eating your bread. I'll be making it.
- "mine": I'd like to stay a while, if that's all right. I work stone, so I won't just be sleeping under your roofs. I'll be fixing them.
- "harbor": I'd like to stay a while, if that's all right. I fish, so I won't just be eating your food. I'll be catching it.
- "roads": I'd like to stay a while, if that's all right. I make maps, so at least one of us will know where everything is.
- "forest": I'd like to stay a while, if that's all right. I make arrows, so the next thing that wanders in at night will be sorry.
- else: I'd like to stay a while, if that's all right. I farm, so I won't just be eating your food. I'll be growing it.
}
+ [Stay.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Good. I'll find somewhere to sleep that isn't your hay pile.
+ [We'll see.]
    Fair. I'd say the same about me.
- -> DONE

// ---- Scene B: a village the player did not found ----

= discovered
~ met = 2
~ temp upgraded = building("upgraded")
{upgraded != "":
    People here say you're the one who got the {upgraded} going again.
- else:
    People here say you've done a lot for this place. I wanted to see who that was.
}
{hometown:
- "mill": I'm {villager_name}. I came in on the river road this morning. I bake, mostly, when someone lends me an oven.
- "mine": I'm {villager_name}. I came down from the hills this morning. I work stone, mostly, when there's stone that needs it.
- "harbor": I'm {villager_name}. I came up the coast road this morning. I fish, mostly, when the water lets me.
- "roads": I'm {villager_name}. I came in on the east road this morning. I make maps, mostly, of places people want to find.
- "forest": I'm {villager_name}. I came through the woods this morning. I make arrows, mostly, and bows when someone asks nicely.
- else: I'm {villager_name}. I came across the dry flats this morning. I farm, mostly, wherever the ground lets me.
}
{village_name != "": {village_name} is a good place. You can tell people like living here.|This is a good place. You can tell people like living here.}
{
- check("empty_houses"):
    Some of the houses have nobody in them, though. I counted on the way in. Sorry. I count things.
- count("idle_adults") > 0:
    Some people here have nothing to do all day, though. I counted on the way in. Sorry. I count things.
- else:
    It's quieter than it looks, though. I walked around on the way in. Sorry. I do that.
}
+ [It looks fine to me.]
    It does. It looked fine where I grew up, too, for years.
    Busy on market days. Quiet the rest of the week. Then quiet on market days too.
+ [You've seen this before?]
    {hometown:
    - "mill": Home. A mill town, up the river. The grain went somewhere cheaper, and after a while so did everyone else.
    - "mine": Home. A mining town in the hills. The seam ran out, and after a while so did everyone else.
    - "harbor": Home. A harbor town, down the coast. The sand came in, the boats stopped coming, and after a while so did everyone else.
    - "roads": Home. A market town on the old east road. The road went bad, the traders stopped, and after a while so did everyone else.
    - "forest": Home. A timber town. They cut until there was nothing left to cut, and after a while everyone left too.
    - else: Home. A farming town out in the dry country. The well gave out, and after a while so did everyone else.
    }
    I was about as tall as a barrel. It was a long time ago.
+ [What would you do?]
- The good news is nothing's falling down. It just needs a push.
{building("upgraded") != "":
    What you did with the {building("upgraded")} is the right idea. Do it again, somewhere everyone walks past.
- else:
    Pick a building everyone walks past and make it better. People notice. Then they stay.
}
-> push -> discovered_close

= discovered_close
{hometown:
- "mill": I'll be near the ovens if you need me. Or near wherever the ovens should be.
- "mine": I'll be around if you need me. Probably staring at a wall. It's a good wall. I'll tell it so.
- "harbor": I'll be off looking for somewhere to fish if you need me. I hear the fishing around here is terrible. I'd like to find out for myself.
- "roads": I'll be around if you need me. I want to walk the edges and see how far this place goes.
- "forest": I'll be around if you need me. I like to know where the trees are, so I'll go and look.
- else: I'll be around if you need me. Probably kneeling in the dirt, having opinions about it.
}
-> DONE
