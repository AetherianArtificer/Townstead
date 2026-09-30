// The head of a hunter lodge. Takes on Oathseekers the player brings, and says little else.

=== greet ===
# label: About the lodge
{~The watch starts at dusk.|The oath is short. Keeping it is the long part.|Keep your door shut after dark.}
+ [I know someone who wants to join.]
    Bring them here, close enough that I can see their face.
    ++ [They're here beside me.]
        ~ act("enlist")
        -> DONE
    ++ [Another time.]
        -> DONE
+ {check("sworn")} [Any contracts?]
    -> contracts
+ [Nothing for now.]
    -> DONE

CONST LODGE = "townstead:hunter_lodge"

// The lodge's contracts (sworn hunters only): hand in what's ready, then offer what's going.
=== contracts ===
~ temp handed = contract_turn_in(LODGE)
{handed > 0:
    Done. The lodge pays its debts.
}
- (offer)
~ temp title = contract_offer(LODGE)
{title == "":
    Nothing posted. Come back in a few days.
    -> DONE
}
{title}. {contract_about(LODGE)}
+ [I'll take it.]
    {contract_accept(LODGE):
        It's yours.
    - else:
        Finish what you're carrying first.
    }
+ [Something else.]
    ~ contract_skip(LODGE)
    -> offer
+ [Not now.]
- -> DONE
