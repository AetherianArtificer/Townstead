// Act 2, scene 13: The count. After the name (scene 12), the player can ask about the recitation
// they overheard (scene 11). The founder does not explain: they start again from the first article
// and stop in the same place, which still follows the pull of the blood. This arms the slip: at the
// next oath they swear someone in, their invocation slips into her wording (the hunter_oath ritual's
// line variant), which is M1 (act2/m1_slip.ink).

VAR done_count_talk = false
VAR slip_oaths = 0

// When this scene plays. The greet in persona.ink checks these in order.
=== function count_talk_ready() ===
~ return named and done_counting and not done_count_talk

=== the_count ===
~ done_count_talk = true
// Armed now, so the slip still comes if the talk is cut short.
~ slip_oaths = count("founder_oaths")
~ act("prime_slip")
You heard that. # emote:ponder
It's nothing. It's a list. I say it over sometimes, so I don't lose it.
-> recite ->
- (after)
* [What comes after?]
    Something. # emote:shrug
    It'll come back. It usually comes back if I don't chase it.
    -> after
* [Whose words are those?]
    Hers.
    Not tonight. I've given you her name, that's plenty for one month.
    -> after
* [You stopped in the same place.]
    I know where I stopped.
    -> after
+ [I'll leave it.]
    Thank you.
    -> another_hunter -> DONE

// Optional: the slip (M1) comes at the next oath, so the lodge asks for another hunter. Done once
// the slip has happened; the hand-back is M1 itself.
=== another_hunter ===
# quest: Another hunter
# about: Bring the hunter another villager who wants to join. They train at the altar for three nights, then swear the oath.
# goal: second_hunter
# skip if: second_hunter
One more thing. The lodge could stand another pair of hands. If anybody asks you about it, send them my way.
->->

= waiting
{~Found anyone? Don't drag them here. If they need dragging, they're not ready.|Still looking? Me too.}
+ [I've brought someone.]
    {act("enlist_recruit"):
        All right. They train by the altar after dark, three nights. If they're still here after that, they kneel.
    - else:
        Not this one.
    }
    -> DONE
+ [I'll let you get on.]
    Go on.
    -> DONE

= done
-> greet
