// Act 2, M3: The grave. After the bread (M2), the founder asks the player to walk with them to
// their mother's grave, where the house was (mark_structure gives the player the map; travel_with
// has the founder follow, and the wolf stays at the lodge). At the marker the grave is there, and
// someone has been: a candle still lit, and a signet dropped with a crest on it (both placed by
// the story when they arrive). Whoever it was is gone. Choice: go after them (Hunger), or let them
// go and look at the signet (Oath). Reveals that someone still visits her. The court's first move
// (Wounded, wolf beat 10) comes for the signet.

VAR grave_walking = false
VAR done_grave = false
VAR grave_seen = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function grave_offer_ready() ===
~ return bread_known and not done_grave and not grave_walking

=== function grave_road_ready() ===
~ return grave_walking and not grave_seen and not check("at_grave")

=== function grave_arrive_ready() ===
~ return grave_walking and not grave_seen and check("at_grave")

=== function grave_stay_ready() ===
~ return grave_walking and grave_seen

=== grave_offer ===
I want to go and see her. My mother.
It's a long walk, where the house was. There's nothing there worth the walk except her, and the walk's the part I've been putting off.
Will you come? The wolf stays here. It doesn't like long walks, whatever it tells you.
+ [I'll come.]
    ~ start_trip(grave_walking, "mark_grave", "grave_travel")
    You've got the map. You go first. I know the way, I just don't want to be the one leading.
    -> DONE
+ [Not yet.]
    No. Not yet. Another day.
    -> DONE

// On the road, before the grave.
=== grave_road ===
{~Keep going.|It's further than I remember. Everything is.|Don't talk for a while, would you? It's not you.|We're close. I can tell by how much I want to turn round.}
+ [Let's turn back.]
    ~ end_trip(grave_walking, "grave_home")
    ...All right. Another time. Thank you for getting this far.
    -> DONE
+ [Keep going.]
    -> DONE

// They are there.
=== grave_arrive ===
~ grave_seen = true
~ act("build_grave")
~ act("drop_signet")
Here. This is her.
Somebody's been here. That candle's still going.
And that. On the ground. Somebody dropped that, and not long ago.
* [Whoever it was can't be far. Go after them.]
    ~ toward_hunger()
    Yes. # emote:nod
    ...No. They've gone. Whoever it was knows this ground better than I do, and they had a start on us.
    I wanted to, though. I want you to know how much I wanted to.
* [Let them go. Look at this.]
    ~ toward_oath()
    ~ trust(1)
    ...All right.
    I know this crest. I've seen it once. On a door I was carried out of. # emote:ponder
    Don't ask me whose. Not here.
- Somebody still comes here. Somebody who brings her candles.
Why would anybody from there bring her candles?
- (stay)
* [Tell me about her.]
    She hummed when she worked. She didn't know she did it. If you told her, she'd stop, and then five minutes later she'd be at it again.
    I haven't thought about that in years.
    -> stay
* [Do you want a minute?]
    Yes.
    ...Stay, though. Just stay where I can see you.
    -> stay
+ [Take the signet. It's evidence.]
    Keep it. I don't want it in my pocket. # emote:shake_head
    -> stay
+ [Let's go home.]
    ~ done_grave = true
    ~ end_trip(grave_walking, "grave_home")
    Yes. Home. # emote:nod
    -> DONE
