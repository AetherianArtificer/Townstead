// Everyday talk once nothing is waiting: one line, picked by demeanor. The object they carry from
// home is a small running thread; the walls gag shows up out of delight; "we" once they trust
// the player. Nothing claims a detail about the village without checking it.

VAR said_glad = false

=== talk ===
{rel("trust") >= 20 and not said_glad and demeanor() != "stern" and demeanor() != "guarded":
    ~ said_glad = true
    I'm glad I came here. I mean that.
    -> choices
}
{demeanor():
- "stern": -> stern
- "guarded": -> guarded
- "jovial": -> jovial
}
-> warm

// How the thing they carry from home is doing, today.
= keepsake
{hometown:
- "harbor": {~The net's holding, for now.|I mended the net again. It won't be the last time.}
- "mill": {~The starter's doing well. He's always hungry.|Want to smell the starter? Go on. That's bread, before it's bread.}
- "mine": {~I still haven't fixed the lamp. It's become a sort of tradition.|I should fix the lamp. I keep saying that.}
- "roads": {~I've made you a bigger dot on the map. Don't let it go to your head.|The map's getting crowded around here. That's a good sign, on a map.}
- "forest": {~The heron has a second leg now. It's still a bit of a duck.|I carved a little more of the heron. Want to see?}
- else: {~I still haven't opened the other bottle. I won't. It's not for drinking.|Have a sip? Just the one, mind. Habit.}
}
->->

= stern
{shuffle:
- Is there something you need?
- I'm working.
- Say what you came to say.
}
-> choices

= guarded
{shuffle:
- Oh. Hello.
- I'm fine. Busy.
- Can it wait? I'm in the middle of something.
}
-> choices

= jovial
{shuffle:
- There you are! I've been checking corners again. Yours are still square. I'm almost disappointed.
- -> keepsake ->
- I had an idea just now, and I've lost it. It'll come back.
- {we() == "we": We're doing well, you know. Don't tell anyone I said so.|You're doing well here, you know. Don't let me go on about it.}
- If I start humming, just let me.
- {village_name != "": I've started saying I live in {village_name}. Out loud. It feels good.|I've started saying I live here. Out loud. It feels good.}
}
-> choices

= warm
{shuffle:
- Do you ever stand somewhere and think about where a door should go? No? Just me, then.
- If you ever need a second opinion on anything, I've got a first one ready.
- I keep fixing things nobody asked me to. I'm working on that.
- I keep a list of things to fix. It's a long list.
- -> keepsake ->
- {done_carry: I took my day off this week. The whole day.|I tried to take a break earlier. I lasted about a minute.}
- {count("newcomer") > 0: Have you met the new arrival properly? I have.|I keep wondering who'll turn up next. Someone always does.}
- {building("raised") != "": I went and stood in the {building("raised")} for a bit. Square. Properly square. Sorry, I'll stop.|I like watching a place go up.}
- {check("empty_houses"): There's room for more people here. I like that. It means we're expecting someone.|Every bed's spoken for. We could use a few more.}
}
-> choices

= choices
+ [Anything I should know?]
    {
    - check("hungry"):
        People are a bit hungry. I'm not nagging. I'm noticing out loud.
    - check("no_spare_beds"):
        Every bed's taken. If anyone new turns up, they'll be sleeping on your floor. Or mine.
    - count("idle_adults") > 0:
        A few people have nothing to do. Not their fault. Nobody's asked them yet.
    - else:
        {~Not right now. That's a good sign.|Nothing that can't wait. I'll find you.|No! Enjoy it.}
    }
    -> DONE
+ [Bye.]
    -> DONE
