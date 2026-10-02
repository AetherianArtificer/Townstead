// Act 2, wolf beat 10: Wounded, the court's first move. At night after the grave (M3), the court
// sends someone for the signet: an advanced vampire and two more walk in from the edge
// (spawn_at_edge). If the wolf goes down in the fighting ("wolf_downs" rose), the founder stays with
// it; a keeper beside a downed pet brings it round, so sitting up with it is what really happens.
// When it is up again, for the first time: "It's mine." If it never went down, a shorter scene.

VAR court_came = false
VAR court_downs = 0
VAR done_wounded = false
VAR done_court_note = false

=== court_move ===
~ court_came = true
~ court_downs = count("wolf_downs")
~ act("court_arrives")
Stay close tonight. Closer than that.
Something's coming in, and it isn't hungry. It walks like it's got an errand.
{check("has_wolf"):
    Wolf. Here. # emote:point
}
-> DONE

// The wolf is down.
=== wounded_down ===
{wounded_down == 1:
    -> first
}
{~Still breathing. That's all I'm counting now.|Don't go. Please.|It twitched. That's good. I think that's good.}
-> DONE

= first
Don't. Don't touch it. # emote:shake_head
...Sorry. Sorry. It's breathing. Help me keep it warm.
* [I'll get help.]
    {who("cleric") != "":
        Get {who("cleric")}. Run.
    - else:
        There's nobody. It's us. Sit down.
    }
* [I'm staying.]
    ~ trust(1)
    Good. Sit. Here, where it can smell you.
- -> DONE

// It got up again.
=== wounded_up ===
~ done_wounded = true
~ act("mark_wounded_seen")
~ trust(3)
It got up. It looked at me like I'd been gone a week.
Of everything in this town, it had to be the one thing that follows me around.
It's mine. # emote:ponder
The wolf. It's mine.
* [I know.]
    Don't say anything else. I heard it too.
* [They came for the signet.]
    Yes. And they found the wolf in the way. So now it's personal. It already was, but now it's personal in a way I can do something about.
* [You sat with it all night.]
    Somebody had to. # emote:shrug
    ...That isn't why. You know that isn't why.
- -> DONE

// They came, and the wolf never went down.
=== court_note ===
~ done_court_note = true
~ act("mark_wounded_seen")
They came for the signet, I'd bet my life on it. They didn't get it.
They'll try again. They're patient like that. It's the only thing about them I'd call a virtue.
Keep it somewhere I'd never think to look. That way nobody can make me tell them.
-> DONE
