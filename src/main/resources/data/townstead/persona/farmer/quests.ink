// The Farmer's quests. Each one tests their patience in a different way: they want to skip the
// plan, dig up the crop to check it, find the miracle seed, start over, squeeze in one more
// planting. The year of plenty is where it all comes home.

=== checkin ===
~ daily()
->->

// ---- A plan for the fields ----

=== plan_offer ===
~ quest_day = today
Have you got a minute? I've been walking the rows, and I've got ideas. Too many ideas, probably.
+ [Go on.]
    -> plan -> DONE
+ [Not now.]
    That's fine. The ideas will keep. They always do. That's half the trouble.
    -> DONE

=== plan ===
# quest: A plan for the fields
# about: Place a Field Post over the fields and paint a plan: what grows where, row by row.
# goal: field_plan
# skip if: field_plan
~ quest_day = today
Have you tried painting a plan for the fields? A Field Post. You mark what goes where, and the farmers stop guessing.
I know. You'd think I'd be the last person to want a plan. I'd rather plant the exciting thing everywhere and see what happens.
That's exactly why I need one.
->->

= waiting
-> checkin ->
{~I keep wanting to plant something new in the middle of the plan. I haven't. Yet.|The plan's the boring part. I know it's the part that matters.}
+ [Any advice?]
    Put a Field Post near the farmland, then open it and paint the rows. Whatever you paint, the farmers plant.
    And please don't let me paint the whole thing with something I've never grown.
+ [I'm working on it.]
    I know. I'll try to be patient. I'm not good at it, but I'll try.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_plan = true
~ trust(5)
You did it. Every row has a name now.
I'll be honest, I wanted to fill the whole plan with something I've never grown. I didn't. I'm very proud of me.
+ [I'm proud of you too.]
    Stop it. I'll get ideas.
+ [What's next?]
    Next, we wait. Which is the part I'm worst at.
- -> DONE

= skipped
~ daily()
~ done_plan = true
~ trust(5)
Oh, you already have a plan. And it's a good one. Better than I'd have made, if I'm honest.
-> DONE

// ---- First harvest ----

=== harvest_offer ===
~ quest_day = today
Now comes the hard part. Can I tell you about it?
+ [Go on.]
    -> harvest -> DONE
+ [Not now.]
    That's fine. It won't be ready yet anyway. It's never ready yet.
    -> DONE

=== harvest ===
# quest: First harvest
# about: Wait for the fields to ripen and bring in the first harvest. No digging it up to check.
# goal: first_harvest
~ quest_day = today
We wait. That's the whole plan. We wait for everything to ripen, and then we bring it in.
I'm terrible at waiting. I'll check on the rows every hour. I'll want to dig one up just to look.
If you see me doing it, stop me.
->->

= waiting
-> checkin ->
{~Is it ready? It isn't ready. I know it isn't ready.|I didn't dig one up. I thought about it very hard, though.|I've been counting the days. You don't want to know how many times.}
+ [Any advice?]
    Crops grow on their own. Light and water nearby help. When they're ripe, the farmer brings them in. That's me.
+ [Leave them alone.]
    I am. I'm leaving them alone very loudly.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_harvest = true
~ trust(5)
It's in! The first harvest. Look at it.
I know it's just an ordinary crop. It's the best ordinary crop I've ever seen.
+ [You didn't dig any up.]
    Not one. Well. Not one that you saw.
+ [It looks good.]
    It does, doesn't it? And there'll be more. That's the trick of it. There's always more.
- -> DONE

// ---- Something new in the ground ----

=== kinds_offer ===
~ quest_day = today
Can I tell you about the fun part? The part I'm actually good at?
+ [Go on.]
    -> kinds -> DONE
+ [Not now.]
    Later, then. I'll try not to plant anything while you're gone.
    -> DONE

=== kinds ===
# quest: Something new in the ground
# about: Grow and harvest five different kinds of crops. Try each one properly and see what it really gives.
# goal: five_kinds
~ quest_day = today
Every seed is a little bet. Most of them are small. One of them might be the one.
Let's grow five different kinds, properly. A row each, and we see what each one really gives.
Properly. That's the word I'm going to keep saying to myself.
->->

= waiting
-> checkin ->
{~How many kinds so far? I've lost count. I haven't. I've written it down twice.|I've got a chart. You don't have to look at the chart.}
+ [Any advice?]
    New seeds come from trading, from exploring, and from other villagers. Give each kind its own row on the Field Post.
+ [Found the one yet?]
    Not yet. But I have a very good feeling about the next one. I always do.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_kinds = true
~ trust(5)
Five kinds! Five. I've written down what each one gave. It's all in the chart.
None of them was the one. But one of them was very nearly the one. I'm almost sure of it.
+ [Which one?]
    I'll tell you when I'm sure. I'm never sure. That's the fun of it.
+ [You did it properly.]
    I did, didn't I? It nearly killed me.
- -> DONE

// ---- Bring it home (their flaw, when the village goes hungry) ----

=== home_offer ===
~ quest_day = today
Can I tell you something? I've done it again.
Everything in the ground is new, and nobody's eating properly. I was so busy with the next thing that I forgot who this is all for.
That's what happened last time. The field where nothing came up.
+ [It's not the same.]
    It's the beginning of the same. I know what the beginning looks like.
+ [Then let's fix it.]
- -> home -> DONE

=== home ===
# quest: Bring it home
# about: Feed the village before trying anything new. The Farmer asked you to hold them to it.
# goal: fed
# goal: harvest_32
~ quest_day = today
No new seeds until everyone's fed. Plain crops, full stores, everyone eating.
Help me hold myself to that. I'm asking you, because I don't trust me.
->->

= waiting
-> checkin ->
{~I haven't planted anything new. I bought a seed. I haven't planted it.|Plain rows. Full stores. I'm saying it every morning.}
+ [Any advice?]
    Put food where people can reach it: a kitchen, a chest in a house. And keep the plain crops going. They feed people.
+ [You're doing well.]
    I'm doing it. Well is a strong word.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_home = true
~ trust(10)
Everyone's eaten. And I haven't planted a single new thing in days. It was agony.
Thank you. For not letting me do it again.
+ [You did it yourself.]
    With you standing next to me. That's the part that worked.
+ [Now the new seeds?]
    One row. In a corner. After supper. I've learned. A little.
- -> DONE

// ---- Before the frost (only while a seasons mod runs, in autumn) ----

=== frost_offer ===
~ quest_day = today
Autumn's here. I can feel it in the soil. It goes quiet.
Last year I'd have planted one more thing that might finish in time. This year, let's bring in what we have.
+ [Let's do it.]
    -> frost -> DONE
+ [Not now.]
    Soon, though. Winter doesn't wait.
    -> DONE

=== frost ===
# quest: Before the frost
# about: Bring in the harvest and fill the stores before the first day of winter.
# goal: harvest_48
# goal: fed
~ quest_day = today
Everything that's ripe comes in. Everything that isn't, we let go.
->->

= waiting
-> checkin ->
{~The rows are coming in. The sky's getting grey.|No new plantings. I know. I know.}
+ [Any advice?]
    Harvest everything that's ripe, and store it near the houses. Nothing new goes in the ground now.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_frost = true
~ trust(5)
The stores are full. Winter can come. Let it try.
-> DONE

// ---- A year of plenty (the end of their arc) ----

=== year_offer ===
~ quest_day = today
I want to try something. Not a crop. A year.
+ [Go on.]
    -> year -> DONE
+ [Not now.]
    That's all right. A year will still be there.
    -> DONE

=== year ===
# quest: A year of plenty
# about: Four good seasons: everyone fed and the fields full, with a farm and a granary to keep it all.
# goal: good_seasons
# goal: farm
# goal: granary
~ quest_day = today
Four good seasons, or near enough. Everyone fed, the fields full, a farm and a granary to keep it all.
No miracles. No new seed that changes everything. Just a year.
I've never done that. Not once. I've always left before the year was up.
->->

= waiting
-> checkin ->
{~Another season. Don't jinx it.|The granary smells like bread. Did you know granaries smell like bread? They do.|Some days I still want to plant something strange. Then I look at the rows, and I don't.}
+ [Any advice?]
    Keep everyone fed, season after season, and keep the harvest coming. A recognized farm and a granary hold it together.
+ [How are we doing?]
    Well, I think. I'm trying very hard not to count. I'm counting.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_year = true
~ trust(10)
~ contribute("affection", 5, "year_of_plenty")
It's been a year. A whole one. Every season, everyone fed.
I cooked supper. From our own fields, all of it. I asked {builder()} to come.
{builder()} tried to say it wasn't necessary. I didn't let them. It isn't repayment. It's just dinner.
Come and sit with us. Please.
+ [Of course.]
+ [Wouldn't miss it.]
- Before we go in, can I show you something?
I spent years looking for the crop that would change everything. Twice as fast. Ten times the price.
~ temp crop = most_harvested()
{crop != "":
    It was the {crop}. It was here every year.
- else:
    It was all of it. Every row, every year.
}
It was right in front of me the whole time. I just never stayed long enough to see it.
{depth.tin:
    I think I'll plant that seed now. The one in the tin. In a corner, just to see. I'm not betting anything on it.
}
+ [Let's go in.]
    Yes. Let's. Before {builder()} starts fixing my table.
    -> DONE
