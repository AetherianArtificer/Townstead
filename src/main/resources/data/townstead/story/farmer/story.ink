// A farmer who is tired of deciding what goes where. Told by the first farmer a player asks.
// Quests live in quests/. Rows and crops are the farmer's worry, never the player's mistake.

VAR village_name = ""

=== greet ===
# label: About the fields
{
- not field_post:
    -> first_talk
- not first_harvest:
    -> field_post.next_job
- else:
    -> idle
}

= first_talk
{who("farmer") != "":
    {who("farmer")} and I split the rows. I get the ones with rocks in them.
- else:
    I've been working these rows on my own.
}
Can I ask you about the fields? It's not urgent. It's a bit urgent.
+ [Go on.]
    -> field_post
+ [Not now.]
    That's fine. They're only weeds. There are a lot of them.
    -> DONE

= idle
{~
- The crows stand on the post now. I think they're reading it too.
- I found a potato I didn't plant. I've decided to be happy about it.
- {who("cook") != "": If {who("cook")} asks, the carrots are coming. They are. Slowly.|Nobody's asked about the carrots yet. I'm ready for when they do.}
- My back says rain. My back is wrong about half the time. # emote:shrug
- {village_name != "": Everyone in {village_name} eats out of this dirt. I try not to think about it too hard.|Everyone here eats out of this dirt. I try not to think about it too hard.}
}
-> DONE
