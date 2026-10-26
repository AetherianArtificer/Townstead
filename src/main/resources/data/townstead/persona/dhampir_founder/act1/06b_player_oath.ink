// Act 1, between scenes 6 and 7: the player's oath. Once they have sworn in a first hunter, the
// founder offers the oath to the player. Saying yes opens a quest; at the altar the founder holds
// the ritual with the player kneeling (swear_player). Scene 7 needs the player sworn.

VAR oath_job = false
VAR oath_put_off = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function player_oath_offer_ready() ===
~ return done_first_oath and not oath_job and today > oath_put_off and not check("player_sworn")

=== player_oath_offer ===
You've watched one now. You could kneel too, if you want it. Nobody's going to make you.
+ [I'll take the oath.]
    -> player_oath -> DONE
+ [Not yet.]
    ~ oath_put_off = today
    It'll keep.
    -> DONE

=== player_oath ===
# quest: The hunter's oath
# about: Kneel at the Oath Altar and let the hunter swear you in.
# goal: swear_oath
# skip if: swear_oath
~ oath_job = true
At the altar, then. When you're ready.
->->

= waiting
+ [I'm ready.]
    {act("swear_player"):
        Then kneel.
    - else:
        Not like this. Come to the altar.
    }
    -> DONE
+ [Not yet.]
    It'll keep.
    -> DONE

= done
There. Now they'll come to you too. Sorry about that.
-> DONE
