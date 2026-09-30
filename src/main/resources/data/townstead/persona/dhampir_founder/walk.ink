// Act 1, scene 7: Night walk. For sworn hunters, in the evening, once the first oath is done.
// The founder asks the player out past the fence and travels with them (travel_with). While
// nothing has died they talk low and short. The scene turns when a vampire dies: theirs
// ("founder_kills" rose) or the player's ("player_kills" rose). If they held back a finishing
// blow ("founder_toyed" rose, the Toying behavior) the player saw them play with it, and the
// choice is about that. Reveals how they hunt, and nothing about themselves.

VAR walking = false
VAR done_walk = false
VAR walk_kills = 0
VAR walk_player_kills = 0
VAR walk_toyed = 0

=== walk_offer ===
I'm walking the edge tonight. Past the fence, out where it's dark. You can come if you want.
Stay behind me, and don't be brave. Brave is how people end up in stories.
+ [I'm coming.]
    ~ walk_kills = count("founder_kills")
    ~ walk_player_kills = count("player_kills")
    ~ walk_toyed = count("founder_toyed")
    ~ walking = true
    ~ act("walk_with")
    Good. Keep up. And if I tell you to run, run toward the lodge, not away from it. People always run the wrong way.
    -> DONE
+ [Not tonight.]
    Another night, then. There's always another night, that's rather the problem.
    -> DONE

// Out on the walk, before anything has died.
=== walk_quiet ===
{~Shh. I'm listening.|Walk where I walk. The ground's easier to read if we're not both on it.|Not yet. It's around. They're always around at this hour, you just have to be dull enough to wait.}
+ [Let's head back.]
    ~ walking = false
    ~ act("walk_end")
    All right. Another night.
    -> DONE
+ [I'll keep quiet.]
    -> DONE

// Something died. Who got it, and how.
=== walk_after ===
~ walking = false
~ done_walk = true
~ act("walk_end")
{
- count("founder_toyed") > walk_toyed:
    -> toyed
- count("founder_kills") > walk_kills:
    -> quick
}
-> yours

= toyed
Did you see it, when I stepped back? It didn't know what to do with that. They never do.
* [You let it go on too long.]
    ~ act("oath_small")
    ...Yes. I did. # emote:ponder
    I'll keep it shorter. I mean that, I'll try.
* [It deserved to be afraid.]
    ~ act("hunger_small")
    It did. Every one of them does, for as long as I can manage.
* [Why step back?]
    ~ trust(1)
    To see what it'd do. They're all the same at the end, but I keep checking.
    In case one of them isn't.
- -> home

= quick
That one went quick. Good. I don't always manage quick.
* [You held back.]
    ~ act("oath_small")
    I did. It's harder than it looks, holding back. Don't tell anyone that.
* [You could have let it run.]
    ~ act("hunger_small")
    I could have. # emote:shrug
    Next time, maybe. It's not as if there's a shortage.
- -> home

= yours
You got it first. Hm. # emote:ponder
That's good. That's very good. I'm not annoyed. I'm a little annoyed.
* [You'd have liked it for yourself.]
    I would, yes. That's not a good thing about me, but it's true.
* [Beginner's luck.]
    ~ trust(1)
    No. It wasn't. Don't do that, don't make yourself smaller. You were good.
- -> home

= home
Come on. Back inside the fence. The walk home is the part people forget to be careful on.
-> DONE
