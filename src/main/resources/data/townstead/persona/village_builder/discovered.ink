// The first meeting in a village the player did not found, after the hometown's opening. They
// came because they heard what the player did here. Objective: to be told they can look around,
// then to share what they noticed. Turn: the place reminds them of home before it went wrong,
// and they turn that into "It just needs a push".

=== discovered_meeting ===
~ temp upgraded = building("upgraded")
{upgraded != "":
    You're the one who got the {upgraded} going again, aren't you? I heard about it on the road. I had to come and see.
- else:
    You're the one people here talk about, aren't you? I heard your name on the road. I had to come and see.
}
{village_name != "":
    {village_name}. I've been round it twice already. Some of these walls are square! Properly square!
- else:
    I've been round the whole place twice already. Some of these walls are square! Properly square!
}
I'm {villager_name}.
+ [Welcome{village_name != "": to {village_name}}.]
    ~ contribute("affection", 1, "welcomed")
    Thank you! It's a lovely place to be welcomed to.
+ [Heard about me, did you?]
    ~ teased_first = true
    Only good things! Well. Mostly good things. Somebody thought you were taller.
+ [What brings you here?]
    You, mostly. I wanted to see what you'd done here.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
Can I tell you what I noticed, walking round? You might not like it.
+ [Tell me.]
+ [Is it bad?]
    No! It's easy to fix. That's why I'm telling you.
+ [I probably know already.]
    You might. Let's see if we noticed the same thing.
- {
- check("empty_houses"):
    A few of the houses are standing empty. A place this good ought to be filling up, not the other way round.
- count("idle_adults") > 0:
    A few people here have nothing to do all day. Not their fault. Nobody's asked them yet.
- else:
    Nothing's wrong, exactly. I just think it could be more.
}
{hometown:
- "harbor": Home looked fine for years, too. Then the boats stopped coming, one at a time.
- "mill": Home looked fine for years, too. Then the mill wheel started turning a little slower each autumn.
- "mine": Home looked fine for years, too. Then the seams got thinner, and nobody said so out loud.
- "roads": Home looked fine for years, too. Then the traders came less, and then not at all.
- "forest": Home looked fine for years, too. Then the tree line got further away every spring.
- else: Home looked fine for years, too. Then the bucket came up a little lighter every summer.
}
Well! Nothing's falling down. It just needs a push.
{building("upgraded") != "":
    What you did with the {building("upgraded")} is the right idea. Do it again, somewhere everyone walks past.
- else:
    Pick a building everyone walks past, and make it better. People notice that kind of thing.
}
+ [Will you help?]
    Help? I'll be under your feet the whole time. You'll have to stop me.
+ [Show me where.]
    Oh, I've got opinions. I've got a list. Walk with me some time and I'll point.
- -> push ->
-> goodbye

= goodbye
Right! I'll stay out of your way. Mostly.
{hometown:
- "harbor": If you need me, I'm the one with the net.
- "mill": If something smells good, that's me.
- "mine": If you hear knocking on your walls, it's only me.
- "roads": If you see someone counting steps along your edges, that's me, finishing the map.
- "forest": If you find shavings, I was there.
- else: If you see someone pacing about, counting steps, that's me.
}
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay.
+ [See you around.]
    You will! I'm hard to miss.
- -> DONE
