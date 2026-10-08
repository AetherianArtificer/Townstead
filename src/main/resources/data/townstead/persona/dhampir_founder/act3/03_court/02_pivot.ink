// The pivot: what the founder does to the quiet one. The player's push and the balance decide
// it together.

=== court_pivot ===
Step away from her. # emote:point
...Tell me what to do. No. Don't. I want you to, and I don't.
{
- check("midwife_forgiven"):
    I told a frightened woman to come and sit by my fire. She failed my mother too.
- check("midwife_cast_out"):
    I sent a frightened woman home for less than this.
}
{thrall_scorned:
    I sent one like her down the road once. Somebody's pet. I'd do it again. I think I would.
}
{fledgling_end == "curing" and done_cure:
    And there was a way back for one of them. There was a way back, and I never asked.
}
* [She couldn't help it. The bond did this to her.]
    ~ temp lean = pull_tier() - 1 + echoes()
    -> decide(lean)
* [She killed your mother.]
    ~ temp lean2 = pull_tier() + 1 + echoes()
    -> decide(lean2)
* [It's your choice. It always was.]
    ~ temp lean3 = pull_tier() + echoes()
    -> decide(lean3)

= decide(lean)
{lean >= 3:
    {done_inside:
        -> hesitate
    }
    -> court_ending_b
}
-> court_ending_a

// The wolf line is complete: one more chance.
= hesitate
... # emote:ponder
There's a wolf asleep by my fire. I didn't want that either.
* [Come home. It's waiting for you.]
    ~ toward_oath()
    -> court_ending_a
* [Do it.]
    -> court_ending_b

// What the founder did with the earlier knocks at the door.
=== function echoes() ===
~ temp e = 0
{check("midwife_forgiven"):
    ~ e = e - 1
}
{thrall_scorned:
    ~ e = e + 1
}
{willing_broke:
    ~ e = e + 1
}
{done_cure:
    ~ e = e - 1
}
~ return e
