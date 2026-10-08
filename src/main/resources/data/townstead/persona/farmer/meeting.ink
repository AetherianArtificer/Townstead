// The Farmer's first meeting. They walk in a day or two after the Builder writes to them.
// Objective: somewhere to plant, and a yes to a plan. Turn: they ask the player to paint the plan,
// because they know what they'd paint. Topics in any order; leaving early still reaches the plan.

=== meeting ===
Hello! You're {player_name}, aren't you? {builder()} wrote to me about you.
I'm {villager_name}. Hold on, I'm counting. Eleven kinds of seed, and one in my boot I'm not sure about.
+ [Welcome! I'm glad you came.]
    ~ contribute("affection", 1, "welcomed")
    Oh! Well, thank you! Thank you. Do you want to see the one from my boot?
+ [Why is there a seed in your boot?]
    It got in somewhere on the road. I'm keeping it! It came all this way.
+ [Maybe a little quieter?]
    ~ asked_quiet = true
    Oh! Sorry.
    So! Where's the FARMLAND?
+ [I'm a bit busy right now.]
    ~ met = 1
    Of course! I'll be out looking at your fields. Come and find me!
    -> DONE
- ~ met = 2
-> topics

= resume
There you are! Have you got a minute now? I've got about a hundred questions about your farmland.
+ [Sure.]
    ~ met = 2
    -> topics
+ [Not yet.]
    All right! I'll save them up.
    -> DONE

= topics
- (hub)
* [How do you know {builder()}?]
    -> how_we_met ->
    -> hub
* [What's in the book?]
    Machines! Drawings of machines. This one plants and waters at the same time.
    I can't build any of them. {builder()} builds something close, and then we argue for a week about what I meant.
    The last one worked twice. That's a record!
    -> hub
* [What do you think of the fields?]
    -> fields ->
    -> hub
+ [I should get back to it.]
    {not fields:
        Wait, before you go! Can I show you one thing? It won't take long.
        -> fields ->
    }
    -> close

= how_we_met
{origin == "old_town":
    We grew up on the same street, in {home_town()}! They were the one fixing the fences. I was the one climbing them.
    They were scared of the dark until they were twelve! Don't tell them I said.
    Years later I had a very bad year, and they found me and took me in.
- else:
    On the road, years ago. I was having a very bad year.
    They fed me, found me somewhere to sleep, and wouldn't hear a word of thanks.
    They sing when they think nobody's listening, you know. Terribly! Don't tell them I said.
}
{here("village_builder"):
    {villager_name}. # who: village_builder
}
They still won't let me thank them. I've tried everything!
->->

= fields
{check("has_farm"):
    I've been all over your farmland! Can I show you something?
- else:
    You've got farmland started! Can I show you something?
}
{check("hungry"):
    People here look hungry. Let's grow what we know grows first, and plenty of it.
}
-> plan ->
->->

= close
Can I stay? I've got nowhere I need to be, and eleven kinds of seed.
+ [Stay. You're welcome here.]
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Oh! Good! Right. I'll go and look at the fields, then. Thank you!
+ [We'll see how you do.]
    Fair! Watch me for a week.
- It was good to meet you, {player_name}!
+ [Good to meet you too.]
    -> DONE
+ [See you around.]
    -> DONE

=== function home_town() ===
{builder_home():
- "mill": ~ return "the mill town up the river"
- "mine": ~ return "the mining town in the hills"
- "harbor": ~ return "the harbor town down the coast"
- "roads": ~ return "the market town on the old east road"
- "forest": ~ return "the old timber town"
- "well": ~ return "the farming town out in the dry country"
}
~ return "the town we came from"
