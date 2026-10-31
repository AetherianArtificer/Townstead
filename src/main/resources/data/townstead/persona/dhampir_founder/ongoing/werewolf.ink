// The werewolf thread in Act 1 (Werewolves only). W1: something near the village that is not a
// vampire ("werewolf_near"), and for the first time the founder does not know their quarry. They ask
// for silver and wolfsbane. W2: the shepherd's wolf hackles up at the same scent, and the founder
// trusts it over their own senses. W3, the stolen kill: after W2, a werewolf puts down a vampire
// near the player ("werewolf_kills_seen"), and the founder is furious, and fascinated.

VAR done_w1 = false
VAR w1_asked = false
VAR done_w1_items = false
VAR done_w2 = false
VAR w1_day = -1
VAR done_w3 = false
VAR w2_kills = 0

// When this scene plays. The greet in persona.ink checks these in order.
=== function werewolf_signs_ready() ===
~ return mod("werewolves") and done_lodge and not done_w1 and check("werewolf_near")

=== function werewolf_kill_ready() ===
~ return mod("werewolves") and done_w2 and not done_w3 and count("werewolf_kills_seen") > w2_kills

=== function werewolf_wolf_ready() ===
~ return done_w1 and not done_w2 and check("werewolf_near") and check("has_wolf") and today > w1_day

=== werewolf_signs ===
# quest: Silver and wolfsbane
# about: Bring the hunter silver and wolfsbane for whatever is out there.
# goal: werewolf_things
~ done_w1 = true
~ w1_asked = true
~ w1_day = today
There's something out there tonight that isn't one of mine. # emote:ponder
I know how they move. I know what they smell like, like a cellar with the door shut. This isn't that. This is wet fur and something under it I can't name.
I don't like not knowing what I'm hunting. I've always known.
Bring me silver if you can find any, and wolfsbane. Somebody told me once that's what you want for this kind of thing. I didn't listen properly, because it wasn't my kind of thing.
+ [What is it?]
    If I knew, I'd tell you. That's the part that's bothering me.
+ [I'll look.]
    Thanks. Don't go looking for it, though. Look for the silver. There's a difference and it matters.
- -> DONE

= done
~ done_w1_items = true
~ act("take_werewolf_things")
~ trust(1)
Good. That's good. # emote:nod
I'll keep them on me. I don't know if they work. I don't know if anything works, with this one.
-> DONE

// The wolf knows first: the founder tells the player to watch it, not the dark.
=== werewolf_wolf ===
~ done_w2 = true
~ w2_kills = count("werewolf_kills_seen")
It's close again. The thing that isn't one of mine. # emote:ponder
Watch the wolf, not the dark. If it goes stiff, that's where it is. It'll know before I do.
I can't smell it yet. It can. I'm going to trust the wolf. It's never once been wrong about who's coming.
+ [Since when do you trust anything?]
    Since about now, apparently. Don't spread it around.
+ [What do we do?]
    Stay inside the light. Let it come to us, if it's coming. I'd rather it was on my ground than its own.
- -> DONE

// W3: a werewolf got to a vampire first, close enough that the player saw it.
=== werewolf_kill ===
~ done_w3 = true
You saw that. Tell me you saw that.
I'd been three nights behind that one. Three. And something got to it first, and didn't even finish the job properly.
It went for the throat and then it just left. Like it was making a point.
+ [It did your job for you.]
    ~ toward_hunger()
    It did. I should be grateful. I'm not. I wanted that one.
    ...It was good, though. I'll give it that. It was very good.
+ [Let it be. It isn't yours to hunt.]
    ~ toward_oath()
    No. It isn't. It's somebody else's problem, and I'd like it to stay that way.
+ [Are you all right?]
    ~ trust(1)
    I'm angry. That's different from not all right. ...Mostly different.
- -> DONE
