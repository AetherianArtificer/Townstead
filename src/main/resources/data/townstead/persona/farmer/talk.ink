// Everyday talk with the Farmer, the depth beats trust unlocks, and replies to gifts. Loud by
// default; they never remark on it. After the year of plenty they stop asking whether it's ready.

VAR gift_item = ""

=== talk ===
{more_tomorrow:
    I've got another idea, but it can wait until tomorrow!
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
- I'm busy!
- Quickly, please!
}
-> choices

= guarded
{shuffle:
- Oh! Hello.
- Can it wait?
- I'm fine! Just busy.
}
-> choices

= jovial
{shuffle:
- {done_year: I'm not in any hurry today. It's nice.|Is it ready yet? Any of it?}
- Look at this! A machine that plants and waters at once. {builder()} says it can't be built. They said that about the last one!
- What if we grew something purple? I've never grown anything purple!
- {builder() != "my friend": {builder()} talks to their tools, did you know? Don't tell them I told you!|Have you ever tried growing something nobody's heard of? I have! Eleven times!}
- {count("idle_adults") > 0: There are people here with nothing to do! I could use them in the fields!|Everyone here's got work! I love a busy place.}
}
-> choices

= warm
{shuffle:
- How are you? I always talk about seeds. Tell me something that isn't seeds.
- {check("hungry"): People are hungry. Can we plant more of what we know grows?|Everyone's eaten! That's my favorite thing about a place.}
- {check("has_farm"): It's a proper farm now! I love a proper farm.|It isn't a proper farm yet! Farmland, water, a composter, and it counts.}
- {builder() != "my friend": I'm leaving bread on {builder()}'s step again tonight. They'll say a bird brought it.|I'm leaving bread on a friend's step again tonight. They'll say a bird brought it.}
- {rel("trust") >= 20: There were seven of us at one table, growing up. You had to shout to get the potatoes.|I've got a new seed in my bag! I'll show you when I know what it is.}
}
-> choices

= choices
* {rel("trust") >= 20 and done_harvest} [Why do you keep chasing new seeds?]
    -> depth.why -> DONE
* {rel("trust") >= 50 and done_kinds} [What's in the tin you carry?]
    -> depth.tin -> DONE
* {rel("trust") >= 20 and here("village_builder")} [What's in the sketchbook today?]
    -> depth.contraption -> DONE
* {rel("trust") >= 20 and origin == "old_town" and here("village_builder")} [What was {builder()} like as a child?]
    -> depth.old_town -> DONE
+ {done_year} [Is it ready yet?]
    Not yet. It'll be ready when it's ready.
    -> DONE
+ [Anything I should know?]
    {
    - check("hungry"):
        People are hungry! More of what we know grows, and food where people can reach it.
    - not check("has_farm"):
        The village needs a proper farm! Farmland, water and a composter.
    - else:
        Nothing! Go and enjoy your day!
    }
    -> DONE
+ [Bye.]
    -> DONE

// ---- Depth beats: once each, when trust allows ----

=== depth ===
= why
~ contribute("familiarity", 3, "why_they_chase")
We had nothing, growing up. Some winters there wasn't enough bread for everyone at the table, and I was the youngest.
I decided I'd find a crop that would fix it. One crop, and nobody I loved would ever go hungry again.
I'm still looking, I suppose.
+ [That makes sense.]
    Does it? Well! Anyway! Did I show you the purple seed?
+ [Nobody here is hungry.]
    {check("hungry"):
        Some are. That's why I'm still looking.
    - else:
        No. They're not.
    }
- ->->

= tin
~ contribute("familiarity", 3, "the_tin")
This? It's a seed. The last one from the field where nothing came up.
I bet the whole farm on that crop. The trader swore it would grow in a week.
I don't know why I keep it. Part of me still wants to plant it.
Don't tell {builder()}. They'd be kind about it.
+ [I won't.]
    Thank you.
+ [Plant it one day.]
    Maybe. When it doesn't matter whether it grows.
- ->->

// ---- With the Builder: only when they're both here ----

= contraption
~ contribute("familiarity", 2, "the_contraption")
Look at this! It plants a row, and waters it on the way back.
{builder()} says it can't be built!
I said it can't be built like that. The wheel's on the wrong side, and it waters whoever's pushing it. # who: village_builder
That's a feature! It's hot work!
+ [Can it be built?]
    Not like that. Something like it, maybe. Give me the book. # who: village_builder
    You're not keeping the book!
+ [It waters whoever's pushing it?]
    Only a little!
    It's a lot. I did the sums. # who: village_builder
+ [I'd use it.]
    SEE? Someone understands!
    {player_name} is being kind to you. # who: village_builder
- I'll draw you a version that works. You won't like it. It'll be very plain. # who: village_builder
Put a little flag on it.
Fine. A little flag. # who: village_builder
- ->->

= old_town
~ contribute("familiarity", 2, "the_old_town")
Do you want to hear what {builder()} was like as a child?
No. # who: village_builder
{builder_home():
- "harbor":
    They used to sit on the harbor wall and mend other people's nets! Nobody asked them to. They were eight!
    Somebody had to. They were full of holes. # who: village_builder
- "mill":
    They've had that starter since they were nine! The same one! They fed it standing on a box because they couldn't reach the counter!
    I could reach the counter. The box was for stirring. # who: village_builder
- "mine":
    They used to knock on every wall in town to hear which ones were hollow! People would open the door and find this tiny child, knocking!
    Two of them were hollow. I told the owners. # who: village_builder
- "roads":
    They drew a map of the whole town when they were ten, and put their own house right in the middle!
    It was roughly in the middle. # who: village_builder
- "forest":
    They carved me a horse for my birthday once! It looked like a duck!
    It was a very good horse. # who: village_builder
- else:
    They used to carry water to the old man at the end of our street every morning, before anyone was up! He thought it was a ghost!
    He left a biscuit out for the ghost. It was a good biscuit. # who: village_builder
}
+ [What were you like?]
    Loud. # who: village_builder
    I was NOT loud!
+ [You two go back a long way.]
    We do. # who: village_builder
    Anyway! Seeds!
- ->->

// ---- Gifts (persona.json "gifts") ----

=== gift_seed ===
Oh! {gift_item}! I've never grown this! Where did you find it?
{done_home:
    It's going in the corner row, after the crops we know.
- else:
    I'm planting it TODAY! In a corner. A very small corner.
}
-> DONE

=== gift_seed_again ===
{demeanor():
- "stern": Thank you.
- "guarded": Seeds! Thanks.
- else: {~More seeds! Thank you!|I'll find a corner for these!|You're a bad influence! Thank you!}
}
-> DONE

=== gift_liked ===
{demeanor():
- "stern": I'll find a use for it.
- else: A {gift_item}! I've always wanted one! Thank you!
}
-> DONE
