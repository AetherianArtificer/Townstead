// Act 1, scene 9b: The bitten. For a town with no resident vampire: someone has Sanguinare and is
// turning ("turning_villager"). The founder has always ended it before the turn, and says so.
// The player can ask for another way (a garlic injection, before it takes, is real) or tell them
// to do what they have to; either way the founder will not touch someone who has not turned.
// Later the scene closes on what really happened to them: cured, turned (then scene 9 can follow
// with them as the neighbor), or gone. Reveals the same certainty as scene 9, tested.

VAR bitten = ""
VAR bitten_asked = false
VAR done_bitten = false

=== bitten_offer ===
~ bitten = who("turning_villager")
~ bitten_asked = true
{bitten} has it. The bite. I can smell it on them, faint still. It's early.
They didn't choose it. Somebody did it to them. I want that said before we talk about the rest.
Where I learned this, you do it now, before they turn. People say it's kinder. It's certainly easier. I've done it.
{check("hungry"):
    More than once. I didn't lose any sleep over it. I'd like that to worry me more than it does.
}
- (choice)
* [There has to be another way.]
    ~ act("oath_small")
    There is, this early. Garlic, the right way, in the blood. A garlic injection. Get it into them before it takes and it's over.
    I'll be honest, I've never done it. Nobody ever asked me to try.
    ** [Then we try.]
        ~ trust(1)
        All right. We try.
    ** [I'll see to it.]
        Good. Quickly, if you can.
    --
    -> DONE
* [Do what you have to.]
    ~ act("hunger_small")
    ...No. # emote:ponder
    Not yet. Not until they've turned. That's the rule, and it's a good rule, and I hate it.
    And if they turn and they're like the rest of them, I'll be standing right there.
    -> DONE
* [Why tell me?]
    Because it's your town. And because if I decide this on my own, I'll decide it the way I always have.
    -> choice

// It is over for them, one way or another.
=== bitten_after ===
~ done_bitten = true
{
- is(bitten, "cured_villager"):
    -> cured
- is(bitten, "resident_vampire"):
    -> turned
- is(bitten, ""):
    It's gone off {bitten}. I don't know how, and I'm not going to ask.
    -> DONE
}
{bitten}'s gone. I don't know more than that, and I don't want to guess.
-> DONE

= cured
{bitten}'s clear. I can't smell it on them any more. I keep sniffing the air like a dog, it's embarrassing.
* [You were wrong about doing it early.]
    ~ trust(2)
    I was. Put that somewhere safe, you won't hear it often.
* [You helped.]
    I told you a word. You did the rest.
- -> DONE

= turned
It took. {bitten}'s one of them now.
* [They didn't choose it.]
    I know. I know that. It doesn't change what they are. It changes how much I hate it.
* [What now?]
    Now they're a neighbor. And we talk first. Every time.
- -> DONE
