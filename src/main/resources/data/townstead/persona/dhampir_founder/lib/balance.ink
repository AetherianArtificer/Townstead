// The Oath and Hunger balance: the founder's pull between their mother's oath and the hunt.

// Nudge the balance a little toward the oath.
=== function toward_oath() ===
~ act("oath_small")

// Nudge the balance a little toward the hunger.
=== function toward_hunger() ===
~ act("hunger_small")

// The balance as a number: 0 near the oath, up to 4 fallen.
=== function pull_tier() ===
{
- check("pull_fallen"):
    ~ return 4
- check("pull_predator"):
    ~ return 3
- check("pull_hungry"):
    ~ return 2
- check("pull_steady"):
    ~ return 1
}
~ return 0

// At the predator pull or past it.
=== function predator_or_fallen() ===
~ return pull_tier() >= 3
