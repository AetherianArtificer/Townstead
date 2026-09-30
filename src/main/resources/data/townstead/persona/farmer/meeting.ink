// The Farmer's first meeting. They walk in a day or two after the Builder writes to them, and
// find the player near the fields. Topics in any order; asking about the fields starts the first
// quest, and leaving early still gets one quick look at them.

=== meeting ===
You're the one with the farmland, aren't you? {builder()} wrote to me about it.
Well. They wrote to me about you, mostly. The farmland was a footnote, but it was a very good footnote.
I'm {villager_name}. I've been walking since before dawn, and I planted three things on the way here. Don't tell {builder()}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}! Good. Now I have a name to go with the fields.
+ [You planted things on the way?]
    Only three. Well, four. There was a nice bit of riverbank, and I had a seed I'd never tried, and it seemed rude not to.
+ [I'm a bit busy right now.]
    ~ met = 1
    Of course! I'll be out looking at your soil. Find me when you've got a minute. I'll be the one kneeling in it.
    -> DONE
- ~ met = 2
-> topics

= resume
Oh, you came back! Good. Have you got a minute now? I've been looking at your soil. It's lovely soil.
+ [Sure.]
    ~ met = 2
    -> topics
+ [Not yet.]
    That's all right. The soil and I will be here.
    -> DONE

= topics
- (hub)
* [How do you know {builder()}?]
    -> how_we_met ->
    -> hub
* [What do you grow?]
    Everything, once. That's the problem, or so I'm told.
    I'm always looking for the one crop that changes everything. The one that grows twice as fast and sells for ten times as much.
    I haven't found it yet. But I've found a lot of very interesting beans.
    -> hub
* [What do you make of the fields?]
    -> fields ->
    -> hub
* [What's that you're carrying?]
    Oh, this? It's a sketch. A machine that plants and waters at the same time, in theory.
    I can't build it. I can barely build a fence. But {builder()} can build almost anything, so I keep drawing them.
    They build a sensible version, and then we argue about it for a week. It's my favorite part.
    -> hub
+ [I should get back to it.]
    {not fields:
        Oh, before you go! Can I tell you one thing about the fields? It's quick. Mostly quick.
        -> fields ->
    }
    -> close

= how_we_met
{origin == "old_town":
    We grew up two doors apart, in {home_town()}. When everyone left, our family's plot was the first to go wild.
    Years later I lost everything again. My own farm, this time. And {builder()} found me and took me in.
- else:
    Years ago, I bet my whole farm on a seed a trader swore would change everything. I planted every field with it.
    Not one row came up. Not one.
    {builder()} found me standing in the middle of it with nothing left. Fed me, found me a roof, got me back on my feet.
}
I owe them. They won't hear a word about it, which only makes it worse.
->->

= fields
{check("has_farm"):
    The ground here is good. Better than good. Whoever chose it has a good eye.
- else:
    You've made a start. It's not a proper farm yet, but it wants to be. I can tell.
}
{check("hungry"):
    People look a little hungry, though. Fields are the slow way to fix that, but they're the way that lasts.
}
-> plan ->
->->

= close
I'd like to stay, if you'll have me. I'll be in your fields either way, if I'm honest.
+ [Stay. You're welcome here.]
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Wonderful! I'll try very hard not to plant anything in your house.
+ [We'll see.]
    That's fair. Watch me work for a week, and then decide.
- Right. I'm going to go and look at your soil, up close. It was good to meet you, {player_name}.
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
