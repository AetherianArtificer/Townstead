// Act 1, scene 3: The altar. Once the lodge is founded they walk the town working out where the
// altar should go, and hand the choice to the player. The lodge becomes a quest: the altar, two
// workbenches, one room. While it waits they react to where the altar really is (order_altar
// checks), and when the lodge is recognized they stand in it for a while. Reveals only that the
// altar is for promises made on your knees, and that they did not write the words.

VAR altar_started = false
VAR done_lodge = false

=== altar_offer ===
I've been walking round your town trying to work out where the altar should go. If anybody asks, I'm not lost. I'm measuring.
+ [Where do you want it?]
    -> altar -> DONE
+ [Not now.]
    That's fine. It'll still be an altar tomorrow.
    -> DONE

=== altar ===
# quest: The lodge
# about: Place the Oath Altar, then build the lodge around it: the altar and two workbenches in one room.
# goal: altar_placed
# goal: lodge_built
# skip if: lodge_built
~ altar_started = true
Somewhere with a door that shuts. Somewhere people have to mean it to walk into, not somewhere they pass on the way to somewhere else.
And not too close to anyone's bed. People say things on their knees that they don't want the whole house hearing.
- (topics)
* [What does the altar do?]
    You kneel at it and say some words and mean them. That's the whole of it. The words are the hard part.
    I didn't write them, before you ask. I'm not that good with words.
    -> topics
* [What else does a lodge need?]
    Two workbenches. A fletching table, a smithing table, whatever you've got. Stakes don't sharpen themselves, and neither do I.
    And a room round the lot of it, all in one place. I don't want to be running between buildings with a crossbow in the dark.
    -> topics
+ [Where's the altar now?]
    You've got it. I gave it to you, remember? Put it somewhere, and I'll tell you if I hate it.
->->

= waiting
{check("altar_placed"):
    -> placed
}
{~Have you found a spot for it yet? Don't overthink it. Or do, it's an altar.|The altar's still in your pack, I'm guessing. It's heavier than it looks, I know.}
-> advice

= placed
{
- check("altar_graveyard"):
    You put it in the graveyard. I'm not complaining. It's quiet, and nobody in there is going to interrupt. It's a bit on the nose, that's all.
- check("altar_house"):
    You put it in somebody's house. I'm not kneeling in somebody's kitchen. Well, I will, but I won't enjoy it.
- check("altar_inn"):
    In the inn. So people will be swearing oaths after three ciders, and meaning about half of them. All right.
- check("altar_infirmary"):
    In with the sick. I suppose if anybody's going to make a promise on their knees, it's somebody in there.
- check("altar_arms"):
    Next to the weapons. Good. That's practical. I like practical.
- check("altar_open_sky"):
    Out in the open, under the sky. It'll get rained on, and so will whoever's kneeling at it. Maybe that's good for them.
- check("altar_underground"):
    Down in the dark. I don't mind the dark, obviously. But the ones swearing in should be able to see where they're going.
- else:
    It'll do. I'd have picked somewhere else, but it'll do.
}
It still wants the workbenches, and a room round the lot.
-> advice

= advice
+ [How do I finish it?]
    The altar and two workbenches, in the same room, with walls and a roof. Once it's a real room, it's a lodge.
    If it doesn't count, it's usually the roof. It's always the roof.
+ [I'm working on it.]
    I know. I'm not in a hurry. They are, but I'm not.
+ [I'll let you get on.]
    Go on.
- -> DONE

= done
~ done_lodge = true
~ trust(3)
// The lodge exists now, so the wolf's spot outside it can be found.
~ act("wolf_rest_outside")
It's a lodge. I stood in it for a while earlier, just stood there. I don't know what I was waiting for.
* [Are you?]
    Yes. I think so. I'm not used to things being finished. Usually I leave before anything's finished.
* [It's yours.]
    It's the town's. I just sleep in it. I'll have to get used to saying that.
- Now I need people. That's the next part, and it's harder than carpentry.
-> DONE

= skipped
~ done_lodge = true
~ act("wolf_rest_outside")
Somebody's already built a lodge. You, I'm guessing. I was going to have opinions about where the altar went, and now I don't get to.
That's fine. I'll find something else to have opinions about.
-> DONE
