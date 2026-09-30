// Everyday talk with the Farmer, the depth beats trust unlocks, and replies to gifts.

VAR gift_item = ""

=== talk ===
{more_tomorrow:
    {~I've got another idea for you. Tomorrow. One idea a day, or I'll bury you in them.|More tomorrow. I'm pacing myself. It's a new thing I'm trying.}
}
{demeanor():
- "stern": -> stern
- "guarded": -> guarded
- "jovial": -> jovial
}
-> warm

= stern
{shuffle:
- What is it?
- I'm busy with the rows.
- Say what you came to say.
}
-> choices

= guarded
{shuffle:
- Oh. Hello.
- I'm fine. Just working.
- Can it wait? The rows need me.
}
-> choices

= jovial
{shuffle:
- There you are! I had a thought about beans. I always have a thought about beans.
- Is it ready? Nothing's ready. I just like asking.
- I drew a machine last night that picks and plants at the same time. It will never work. It's beautiful.
- Guess what I found in a chest this morning. A seed. I don't know what it is. I love it already.
- {builder()} says I talk to the crops. I don't. I encourage them.
}
-> choices

= warm
{shuffle:
- The soil here has a good smell. You'd think that's a strange thing to say. It isn't, to a farmer.
- I keep a list of every seed I've ever planted. It's a long list. It's mostly beans.
- Waiting is the hardest part of farming. Everyone says it's the digging. It's the waiting.
- I saw a clock in the market that chimes every hour. I want it. I don't need it. I want it.
- {check("hungry"): People are a bit hungry. I've been meaning to plant more of the plain crops. I will.|Everyone's eating well. Somebody should write that down. I did.}
- {check("has_farm"): The farm's looking proper now. Composter, water, rows. It makes me stand up straighter.|You don't have a proper farm yet. Farmland with water and a composter, eight rows or so, and it counts. Just saying.}
- {count("idle_adults") > 0: Some people here have nothing to do. I could use hands in the fields. Just a thought.|Everyone's got somewhere to be. The fields feel it.}
- {builder() != "my friend": I brought {builder()} lunch today. They pretended not to notice. They ate all of it.|I left food by a friend's door today. They'll pretend they didn't notice.}
}
-> choices

= choices
* {rel("trust") >= 20 and done_harvest} [Why do you keep chasing new seeds?]
    -> depth.why -> DONE
* {rel("trust") >= 30 and done_kinds} [What's in the tin you carry?]
    -> depth.tin -> DONE
+ [Anything I should know?]
    {
    - check("hungry"):
        People are hungry. More plain rows, and food where people can reach it. That's my advice, and I'm taking it too.
    - not check("has_farm"):
        We could use a proper farm. Farmland, water, a composter. Then the village can count on it.
    - else:
        Not today. The rows are fine. I'm fine. Everything's fine. I'm going to go and check on the rows.
    }
    -> DONE
+ [Bye.]
    -> DONE

=== depth ===
= why
~ contribute("familiarity", 3, "why_they_chase")
Why? You really want to know? All right.
We had nothing, when I was small. Some winters we had less than nothing.
I swore I'd never be hungry again. And somewhere along the way I decided the way to never be hungry was to be rich, overnight.
One miracle crop, and it would all be fixed forever.
It was never about the money. It was about never counting the bread again.
+ [That makes sense.]
    Does it? It always sounded silly out loud. Thank you.
+ [You're not hungry now.]
    No. I'm not. I keep forgetting to notice that.
- ->->

= tin
~ contribute("familiarity", 3, "the_tin")
This? You noticed. You notice things. I like that about you.
It's one seed. The last one of the crop that never came up. The one I bet everything on.
I don't know why I keep it. To remember, I suppose. Or because part of me still thinks it might grow.
Don't tell {builder()}. They'd be very kind about it, and I couldn't stand that.
+ [I won't.]
    Thank you.
+ [Maybe plant it one day.]
    Maybe. One day. When I'm not betting anything on it.
- ->->

// ---- Gifts (persona.json "gifts") ----

=== gift_seed ===
Oh! {gift_item}! I've never grown this. Where did you find it?
{done_home:
    I'm going to plant it right away. One row, in a corner. I've learned.
- else:
    I'm going to plant it right away. Just a small corner. I promise. Mostly promise.
}
-> DONE

=== gift_seed_again ===
{demeanor():
- "stern": Thank you.
- "guarded": Oh. Seeds. Thanks.
- else: {~More seeds! You know me too well.|I'll find a corner for these. I always find a corner.|You're a terrible influence. Thank you.}
}
-> DONE

=== gift_liked ===
{demeanor():
- "stern": I'll find a use for it.
- else: A {gift_item}? Oh, this is wonderful. I'm going to take it apart. No, I'm not. Maybe a little.
}
-> DONE
