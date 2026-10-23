// Act 1, scene 4: Garlic for the frightened. Once they have put down a second vampire here
// ("second_kill"), they expect the town to start hanging garlic. They call garlic on a door next
// to useless against vampires, then ask for a proper patch anyway, because it lets people sleep.
// The quest: bring them a good pile of garlic (any mod's, by the townstead:garlic tag). Reveals
// contempt for comfort they still provide, and that they would rather the kindness looked like
// the player's.

VAR garlic_started = false
VAR done_garlic = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function garlic_offer_ready() ===
~ return done_lodge and not garlic_started and not done_garlic and check("second_kill")

=== garlic_offer ===
That's two I've put down since I got here. People notice that sort of thing, even when you do it quietly.
Somebody's going to start hanging garlic over their door any day now, you watch.
+ [Does garlic work?]
    -> garlic -> DONE
+ [Not now.]
    Sure. It'll keep. Garlic keeps, that's about the one thing it's reliably good for.
    -> DONE

=== garlic ===
# quest: Garlic for the frightened
# about: Grow garlic and bring the hunter a good pile of it, enough for every door in town.
# goal: garlic_pile
~ garlic_started = true
On a door? Not really. I've watched them walk right past it. It's like hanging up a sign that says please don't. They can read, they just don't care.
In the air it's different. There are ways of burning it so it hangs in a room, and that they do mind. That's later, though. That's a whole other job.
But I'll tell you what garlic on a door does do. It lets people sleep. And people who sleep don't open the door at three in the morning to see what the noise was.
So grow some. A proper patch, not three sad heads in a pot. Bring me a good pile and I'll see it gets round the houses.
- (topics)
* [You just said it was useless.]
    I said it was useless against them. I didn't say it was useless. Those are different things, and I'd like it on record that I know the difference.
    -> topics
* [Where do I find it?]
    -> where ->
    -> topics
+ [I'll grow some.]
    Good. Thanks.
->->

= where
It grows wild if you look, out in the grass, little white heads. Or somebody's selling it somewhere. Plant a few and it'll do the rest, it isn't fussy. It's garlic.
->->

= waiting
{~Any garlic yet? No rush. Well, a bit of a rush.|How's the patch coming? You don't have to tell me, I'll smell it from here when it's going well.}
+ [Where does it grow?]
    -> where ->
+ [I'm working on it.]
    I know. Thank you.
+ [I'll let you get on.]
    Go on, then.
- -> DONE

= done
~ act("take_garlic")
~ done_garlic = true
~ trust(2)
That's a lot of garlic. The whole lodge is going to smell like a kitchen for a month.
I'll take it round tonight, a bunch for every door. If anybody asks, it was your idea. They'll like you more for it than they'd like me.
* [Why not say it was you?]
    Because then they'd wonder why the hunter thinks they need it, and they'd lie awake wondering. If it's from you, it's just a neighbor being kind.
* [They should know it was you.]
    ~ trust(1)
    No, they shouldn't. It works better this way. I've done this in other towns, trust me.
- -> DONE
