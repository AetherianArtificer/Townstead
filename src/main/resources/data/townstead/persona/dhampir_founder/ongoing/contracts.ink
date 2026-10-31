// The lodge's contracts, from the founder (sworn hunters only). Hands in whatever is ready, then
// offers what's going: the player can take it, ask for something else, or leave it. The contracts
// themselves are data (data/townstead/contract/hunter/), rolled per player every few days.

CONST LODGE = "townstead:hunter_lodge"

=== contracts ===
~ temp handed = contract_turn_in(LODGE)
{handed > 0:
    {handed == 1:
        That's done, then. Good. # emote:nod
    - else:
        That's {handed} done. You've been busy.
    }
}
- (offer)
~ temp title = contract_offer(LODGE)
{title == "":
    Nothing right now. Give it a few days, something always turns up. That's the trouble with this work.
    ->->
}
{title}. {contract_about(LODGE)}
+ [I'll take it.]
    {contract_accept(LODGE):
        Good. Don't be brave about it.
    - else:
        You've got enough on already. Finish something first.
    }
+ [Anything else?]
    ~ contract_skip(LODGE)
    Let me think.
    -> offer
+ [Not now.]
    All right. It'll keep a day or two.
- ->->
