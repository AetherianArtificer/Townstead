// The Farmer's quests. Each one tests their patience in a different way: they want to skip the
// plan, dig up the crop to check it, find the miracle seed, start over, squeeze in one more
// planting. The fall (home) is in their quiet voice: no exclamation marks. The year of plenty is
// where it all comes home.

=== checkin ===
~ daily()
->->

// ---- A plan for the fields ----

=== plan_offer ===
~ quest_day = today
Have you got a minute? I've got an idea for your fields!
+ [Go on.]
    -> plan -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== plan ===
# quest: A plan for the fields
# about: Place a Field Post over the fields and paint a plan: what grows where, row by row.
# goal: field_plan
# skip if: field_plan
~ quest_day = today
Have you seen a Field Post? You set one up by the farmland and paint which crop goes in which row. The farmers plant whatever you paint.
You paint it, though, not me. The last plan I painted, every row was something I'd never grown before.
->->

= waiting
-> checkin ->
{~Have you painted it yet? I'm not hurrying you! I just want to see it.|When it's done, every farmer here will know exactly what to plant. Imagine that!}
+ [Any advice?]
    Set a Field Post down near the farmland, open it, and paint the rows. Whatever you paint, the farmers plant.
+ [I'm working on it.]
    Good! I'll stop asking.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_plan = true
~ trust(5)
You painted it! Every row has a name.
And not one strange seed in the whole plan. Good!
+ [You wanted to add one, didn't you?]
    Only one! A small one. In the corner.
+ [What's next?]
    Next, water! I'll show you tomorrow!
- -> DONE

= skipped
~ daily()
~ done_plan = true
~ trust(5)
You've already got a plan painted! Every row. That's more than I've ever managed.
-> DONE

// ---- Water for the rows ----

=== water_offer ===
~ quest_day = today
The plan's missing something! Can I show you?
+ [Go on.]
    -> water -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== water ===
# quest: Water for the rows
# about: Paint a cell as Water in the Field Post plan, and put a bucket in a chest near the fields. The farmers fetch the water and fill the cell.
# goal: watered
~ quest_day = today
Farmland near water stays wet, and crops on wet farmland grow faster.
You can paint a cell as Water in the plan. Put a bucket in a chest near the fields, and the farmers will fetch the water and fill it in!
Or we could just wait for rain!
+ [Rain won't come when we need it.]
    No. Fine! Water!
+ [Water. Today.]
    Water today! Yes!
+ [You'd really rather wait for rain?]
    I like surprises! Rain is a surprise!
- ->->

= waiting
-> checkin ->
{~Is there water in the plan yet?|Remember the bucket! Nobody can carry water in their hands!}
+ [Any advice?]
    Open the Field Post and paint a cell as Water. Then put a bucket in a chest near the fields. The farmers do the rest.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_water = true
~ trust(5)
There's water in the rows! Everything around it is going to grow faster now!
+ [It's just water.]
    It's water in the right place! That's the best kind!
+ [What's next?]
    The harvest! Is anything ready yet?
- -> DONE

// ---- First harvest ----

=== harvest_offer ===
~ quest_day = today
Is anything ready yet? No? Then can we talk about the harvest?
+ [Go on.]
    -> harvest -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== harvest ===
# quest: First harvest
# about: Wait for the fields to ripen and bring in the first harvest. No digging it up to check.
# goal: first_harvest
~ quest_day = today
Here's the plan for the first harvest: we let the whole field ripen, and we bring it in together.
No picking early, and no digging one up to see how it's doing.
If you catch me near the rows with a trowel, take it off me.
->->

= waiting
-> checkin ->
{~Is it ready yet?|My trowel's staying in my bag. You can check!}
+ [Not yet.]
    Right! Not yet.
+ [Any advice?]
    Crops need light and water close by. When they're ripe, the farmers bring them in. Until then, we leave them alone.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_harvest = true
~ trust(5)
It's in! The first harvest! Someone's going to eat that!
+ [You didn't dig any up.]
    Not one! I had the trowel out once. I put it back.
+ [It looks good.]
    It does, doesn't it! You know, next time we could try something new in one of the rows...
- -> DONE

// ---- Somewhere to keep it ----

=== store_offer ===
~ quest_day = today
Where does the harvest go, once it's in? Can we talk about that?
+ [Go on.]
    -> store -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== store ===
# quest: Somewhere to keep it
# about: Build a granary, so the village has somewhere to keep its harvest. Food in chests inside the houses is food people can reach.
# goal: granary
# skip if: granary
~ quest_day = today
A harvest needs somewhere to go. A granary keeps it all together, so the village can live on it.
Where I grew up, we kept ours under the bed!
+ [Under the bed?]
    It was the only place my brothers and sisters wouldn't look!
+ [Why not plant it all again?]
    Because then nobody eats it! I know. I KNOW.
+ [A granary it is.]
    A granary! With a door that shuts!
- ->->

= waiting
-> checkin ->
{~Have you looked at what a granary needs? The Catalog shows it!|When the granary's up, I'm going to stand in it for a while.}
+ [Any advice?]
    Open the Catalog and find the granary. It shows what the building needs to count.
    And keep some food in chests inside the houses. People eat what they can reach!
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_store = true
~ trust(5)
A granary! A real one! Everything we grow has somewhere to go now!
+ [Go and stand in it.]
    I will! Don't wait for me!
+ [What's next?]
    Something new in the ground! I've been waiting to tell you!
- -> DONE

= skipped
~ daily()
~ done_store = true
~ trust(5)
You've already got a granary! Everything we grow has somewhere to go!
-> DONE

// ---- Something new in the ground ----

=== kinds_offer ===
~ quest_day = today
I've got an idea! A good one, I think.
+ [Go on.]
    -> kinds -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== kinds ===
# quest: Something new in the ground
# about: Grow and harvest five different kinds of crops. Try each one properly and see what it really gives.
# goal: five_kinds
~ quest_day = today
Let's grow five different crops. A row of each, grown properly and harvested properly, and we see what each one really gives.
One of them might be the one! Twice as fast, ten times the price. What if it's here already, and nobody's tried it?
->->

= waiting
-> checkin ->
{~Which one do you think it'll be? I've got a feeling about one of them. I'm not saying which!|When we find it, I'm going to write it down in big letters.}
+ [Any advice?]
    New seeds come from trading, from exploring, and from other villagers. Give each kind its own row on the Field Post.
+ [Found the one yet?]
    Not yet! But we're not done.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_kinds = true
~ trust(5)
Five kinds! And I wrote down what every one of them gave.
None of them was the one. But the fourth one... I've got a feeling about the fourth one.
+ [Maybe there isn't a "one".]
    There's always a one! There has to be.
+ [You did it properly.]
    Thank you! Now, about the fourth one.
- -> DONE

// ---- Better ground (the great experiment) ----

=== ground_offer ===
~ quest_day = today
I've got an idea. It's the best one yet!
+ [Go on.]
    {
    - check("nutrient_soil"):
        -> ground_soil -> DONE
    - check("big_crops"):
        -> ground_big -> DONE
    - check("rich_soil"):
        -> ground_rich -> DONE
    - else:
        -> ground_giant -> DONE
    }
+ [Not now.]
    It'll still be the best one later!
    -> DONE

=== ground_soil ===
# quest: Feed the soil
# about: Paint cells as Fertilized (Nutrients) in the Field Post plan, and keep compost or bone meal in a chest near the fields. The farmers put back what the crops take.
# goal: fed_soil
~ quest_day = today
Every crop takes something out of the soil, and it doesn't come back on its own. Beans put a little back.
So we put it back ourselves! Paint some cells as Fertilized, and keep compost or bone meal in a chest. The farmers do the rest.
And with the soil fed just right, what if we grow something bigger than anyone's ever seen?
-> experiment_choices ->
->->

= waiting
-> checkin ->
{~Is it enormous yet?|When it's done, I'm going to need a very big cart!}
+ [Any advice?]
    Open the Field Post and paint some cells as Fertilized. Keep compost or bone meal in a chest near the fields. The farmers top up the soil.
+ [See you later.]
- -> DONE

= done
~ daily()
-> experiment_done ->
-> DONE

=== ground_big ===
# quest: A giant crop
# about: Paint cells as Fertilized (Bone Meal) and plant lettuce or onions there, near water. Keep Compost in a chest near the fields. The farmers feed the grown crops, and every so often one grows big, then giant.
# goal: giant_crop
~ quest_day = today
Lettuce and onions can grow BIG. Some of them, near water, grow enormous!
Plant them near water, and when they're grown, feed them Compost. Every so often one grows big, and then giant!
What if we grow the biggest one anyone's ever seen?
-> experiment_choices ->
->->

= waiting
-> checkin ->
{~Any giants yet?|When it's done, I'm going to need a very big cart!}
+ [Any advice?]
    Paint some cells as Fertilized, a few steps from water, and plant lettuce or onions there. Keep Compost in a chest, and the farmers feed the grown ones. You can feed them yourself too. It's mostly luck!
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_ground = true
~ trust(5)
Look at it! It's ENORMOUS! It really worked!
It's still a vegetable, though. A very big one.
We're going to be eating it for a week!
+ [You did it.]
    WE did it! Now, what else can we make giant?
+ [What will you do with it?]
    Eat it! Everyone gets some!
- -> DONE

=== ground_rich ===
# quest: Better ground
# about: Paint rich soil into the Field Post plan. The farmers till it themselves. Then bring in crops grown on it.
# goal: rich_harvest
~ quest_day = today
Rich soil! Crops grow faster on it. What if we grow something on it bigger than anyone's ever seen?
People will come from all over just to look at it!
-> experiment_choices ->
->->

= waiting
-> checkin ->
{~Is it enormous yet?|When it's done, I'm going to need a very big cart!}
+ [Any advice?]
    Open the Field Post and paint a few cells as rich soil. The farmers till it themselves. Then we wait!
+ [See you later.]
- -> DONE

= done
~ daily()
-> experiment_done ->
-> DONE

=== ground_giant ===
# quest: The great experiment
# about: Grow pumpkins or melons, with room beside each vine for the fruit, and bring them in.
# goal: giant
~ quest_day = today
Pumpkins! Or melons! The vine grows along the ground, and the fruit comes up beside it.
What if we grew one bigger than anyone's ever seen? People will come from all over just to look at it!
-> experiment_choices ->
->->

= waiting
-> checkin ->
{~Is it enormous yet?|When it's done, I'm going to need a very big cart!}
+ [Any advice?]
    Plant pumpkin or melon seeds with an empty spot beside each one. The fruit needs somewhere to grow!
+ [See you later.]
- -> DONE

= done
~ daily()
-> experiment_done ->
-> DONE

=== experiment_choices ===
+ [How big is enormous?]
    Bigger than the granary! I'll need a ladder!
+ [Crops don't grow that big.]
    Not YET they don't!
+ [I'll help.]
    You will? Oh, this is going to be wonderful!
- ->->

=== experiment_done ===
~ done_ground = true
~ trust(5)
Look at it! It's...
It's a normal size.
It's a perfectly normal size, and I grew it, and it's lovely!
+ [It's a good one.]
    It IS a good one! Anyway! More of these!
+ [Enormous, you said.]
    It's enormous on the inside!
- ->->

// ---- Rice in the water: only where paddy rice grows ----

=== rice_offer ===
~ quest_day = today
Have you ever seen rice grow? It grows standing in water! In WATER!
+ [Go on.]
    -> rice -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== rice ===
# quest: Rice in the water
# about: Paint cells as Paddy in the Field Post plan, and keep rice seeds in a chest near the fields. The farmers flood the cells and plant the rice.
# goal: rice
~ quest_day = today
You paint a Paddy in the plan, and the farmers flood it and plant the rice right in the water!
I tried it once in a dry field. It didn't work.
+ [Why a dry field?]
    I didn't have a pond! I had a feeling!
+ [Paddies it is.]
    Paddies! Yes!
- ->->

= waiting
-> checkin ->
{~Is the paddy flooded yet?|Rice in water! I still can't believe it works!}
+ [Any advice?]
    Open the Field Post and paint some cells as Paddy. Keep rice seeds in a chest near the fields. The farmers flood the cells and plant.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_rice = true
~ trust(5)
Rice! Out of the water! Look at it!
+ [It worked.]
    It did! It needed water all along. Who knew!
+ [What's next?]
    More rice!
- -> DONE

// ---- Bring it home (the fall: their quiet voice) ----

=== home_offer ===
~ quest_day = today
Can I talk to you about something?
Almost everything in the ground right now is new. And people here are hungry. I didn't see it until today.
This is how it went last time. I planted every field with one new seed, and not one row came up.
+ [It's not the same.]
    It started the same way.
+ [Then let's fix it.]
- -> home -> DONE

=== home ===
# quest: Bring it home
# about: Feed the village before trying anything new. The Farmer asked you to hold them to it.
# goal: fed
# goal: harvest_32
~ quest_day = today
No new seeds until everyone here is fed. Only what we know grows, and enough of it to fill the stores.
Will you hold me to that? If I start talking about a new seed, tell me no.
->->

= waiting
-> checkin ->
{
- check("hungry"):
    People are still hungry. I'm going to plant more of what we know.
- done_trader and not trader_refused:
    Nobody's hungry today. There's a seed in my bag from that trader. I haven't planted it.
- else:
    Nobody's hungry today. I haven't planted anything new.
}
+ [Any advice?]
    Put food where people can reach it, like a kitchen or a chest in a house. And keep the crops we know going.
+ [You're doing well.]
    Thank you. Let's see how the stores look first.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_home = true
~ trust(10)
Everyone's eaten, the stores are full, and I haven't planted one new thing.
Thank you.
+ [You did it yourself.]
    You were there. It helped.
+ [Now the new seeds?]
    One row! In the corner! After supper!
- -> DONE

// ---- Before the frost ----

=== frost_offer ===
~ quest_day = today
It's autumn! Somewhere else, I'd have planted one more thing now and hoped it came in before the frost.
This year, let's bring in what we've got.
+ [Let's do it.]
    -> frost -> DONE
+ [Not now.]
    Soon, though!
    -> DONE

=== frost ===
# quest: Before the frost
# about: Bring in the harvest and fill the stores before the first day of winter.
# goal: harvest_48
# goal: fed
~ quest_day = today
Everything that's ripe comes in, and all of it goes into the stores. No new plantings until spring.
->->

= waiting
-> checkin ->
{~I want the stores full before winter! How are they looking?|No new plantings. I haven't even opened my seed bag.}
+ [Any advice?]
    Harvest everything that's ripe, and store it near the houses. Nothing new goes in the ground now.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_frost = true
~ trust(5)
The stores are full! That's the first winter I've ever been ready for.
-> DONE

// ---- A year of plenty ----

=== year_offer ===
~ quest_day = today
Can I ask you something big?
+ [Go on.]
    -> year -> DONE
+ [Not now.]
    Later, then!
    -> DONE

=== year ===
# quest: A year of plenty
# about: Four good seasons: everyone fed and the fields full, with a farm and a granary to keep it all.
# goal: good_seasons
# goal: farm
# goal: granary
~ quest_day = today
I want to do a whole year here. Four good seasons: everyone fed, the harvest in, and a farm and a granary to hold it.
I've never stayed anywhere a whole year. There was always a new seed somewhere else.
->->

= waiting
-> checkin ->
{check("hungry"):
    People are hungry again. Let's fix that before anything else.
- else:
    {~Everyone's fed! Let's keep it that way.|Another season! What if this one's the best yet?}
}
+ [Any advice?]
    Keep everyone fed, season after season, and keep the harvest coming. A recognized farm and a granary hold it together.
+ [How are we doing?]
    Good, I think! Ask me at the end of the season.
+ [See you later.]
- -> DONE

= done
~ daily()
~ done_year = true
~ trust(10)
~ contribute("affection", 5, "year_of_plenty")
A whole year! Every season, everyone fed.
I cooked supper. Everything on the table came out of our fields. I asked {builder()} to come.
{here("village_builder"):
    I told them they didn't have to. They told me to sit down. # who: village_builder
    And you sat!
- else:
    {builder()} said I didn't have to.
}
It isn't for anything. It's just supper.
Will you eat with us?
+ [Of course.]
+ [Wouldn't miss it.]
- Before we go in, can I show you something?
~ temp crop = most_harvested()
{crop != "":
    This is what we grew the most of, all year. The {crop}.
    I spent years looking for the crop that would change everything. We've been eating it all year.
- else:
    I spent years looking for the crop that would change everything. We've been eating from these fields all year.
}
{depth.tin:
    I'm going to plant the seed from the tin. Just in a corner, to see what comes up.
}
{done_letter and not letter_sent:
    And I'm sending the letter tonight. There's good news in it now.
}
+ [Let's go in.]
    Yes! Before {builder()} starts fixing my table.
    -> DONE
