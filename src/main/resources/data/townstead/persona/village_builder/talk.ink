// Everyday talk once nothing is waiting: one line, picked by demeanor, sometimes about something
// that really happened. Nothing here claims a detail about the village without checking it.

=== talk ===
{demeanor():
- "stern": -> stern
- "guarded": -> guarded
- "jovial": -> jovial
}
-> warm

= stern
{shuffle:
- Is there something you need?
- I'm working.
- Say what you came to say.
- We're fine. Don't push it.
- I've got nothing to say to you right now.
}
-> choices

= guarded
{shuffle:
- Oh. It's you.
- I'm fine. Busy.
- Can it wait? I'm in the middle of something.
- Mm. Hello.
- I'll let you know if something's wrong.
}
-> choices

= jovial
{shuffle:
- There you are! I was just thinking about roofs. I'm always thinking about roofs.
- Good day for it, isn't it? For anything, really.
- I walked the whole place this morning. It's getting good. Don't let it go to your head.
- Have I told you this place is good? I'm telling you again. It's good.
- I'm in a mood today. A good one. You couldn't ruin it if you tried. Please don't try.
- Somebody laughed at one of my jokes today. On purpose, I think.
- I'd like it on the record that I'm happy. Write that down somewhere.
- If I start humming, just let me. It's a whole thing.
- {village_name != "": I told someone I live in {village_name} today. Out loud. It felt good.|I told someone I live here today. Out loud. It felt good.}
}
-> choices

= warm
{shuffle:
- I walked the whole place this morning. I do that every morning. I'm not going to stop.
- Do you ever stand somewhere and think about where a door should go? No? Just me, then.
- If you ever need a second opinion on anything, I have a first one ready.
- Somebody asked me where I'm from today. I said down the road. It's true. It's just a long road.
- I fixed something earlier. Nobody asked me to. I'm working on that.
- Every place has a sound when it's working. This one's getting louder.
- Don't tell anyone, but I talk to the walls I build. Encouragingly.
- I had an idea earlier. I've lost it. It'll come back. They always come back at night.
- You look busy. That's good. Busy is good. Too busy is a different conversation.
- People keep asking me for advice. I keep giving it. One of us should stop.
- I keep a list of things to fix. It's a long list. It's a happy list, somehow.
- My hands don't know what to do when they're not holding something. It's a problem.
- {done_carry: I took my day off this week. The whole thing. I'm very proud of myself.|I tried to take a break earlier. I lasted about a minute.}
- {count("newcomer") > 0: Did you meet the new one? I did. Twice. I'm told that's once too many.|I keep wondering who'll turn up next. Someone always does.}
- {building("raised") != "": I stood in the {building("raised")} for a while today. It's a good room. Somebody measured.|I like watching a place go up. Even slowly. Especially slowly.}
- {village_name != "": {village_name}. I like saying it. It sounds like a place that's going to be around a while.|This place needs a name people say with their chest. Just a thought.}
- {check("empty_houses"): There's room for more people here. I like that. It means we're expecting someone.|Every bed's spoken for. That's a good problem. Still a problem.}
}
-> choices

= choices
+ [Anything I should know?]
    {
    - check("hungry"):
        People are a bit hungry. I'm not nagging. I'm noticing out loud.
    - check("no_spare_beds"):
        Every bed's taken. If anyone new turns up, they'll be sleeping on your floor.
    - count("idle_adults") > 0:
        A few people have nothing to do. Not their fault. Nobody's asked them yet.
    - else:
        {~Not right now. That's a good sign.|Nothing that can't wait. I'll find you.|No. Enjoy it. That doesn't last.}
    }
    -> DONE
+ [Bye.]
    -> DONE
