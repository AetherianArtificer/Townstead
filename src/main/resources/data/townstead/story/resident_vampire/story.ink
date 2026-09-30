// A vampire who lives in town, asked the hunters' question by the player: have they fed on anyone
// here? It only attaches while the player is carrying that question (the founder's scene 9). The
// answer is true to their own record: unwilling bites, people who said yes, or animals only.
// Their own voice: tired of being looked at, polite about it, not a pushover.

=== function menu() ===
~ return "The hunter wants to know something."

=== greet ===
# label: Talk
They sent you. Of course they did. The hunter's never once knocked on my door, but here you are.
Go on, then. Ask.
+ [Have you fed on anyone here?]
    {
    - check("bit_more"):
        More than once. I'm not going to lie to you, you'd only find out, and then it'd be worse.
        I was hungry and I was stupid with it. That isn't an excuse. It's just what happened.
    - check("bit_once"):
        ...Once. It was a bad stretch and I didn't stop when I should have. I'm sorry for it. I'd tell them that myself, if they'd stand still long enough.
    - check("fed_on_person"):
        Only from people who said yes. Tell them that. Tell them the word yes, they'll want to hear it twice.
    - else:
        No. Animals. Chickens, mostly, which I'm not proud of.
        Tell them that. And tell them they can ask me themselves next time. I don't bite. Well. You know what I mean.
    }
    ~ act("heard")
    ++ [Thank you for telling me.]
        Don't thank me. Just tell them what I said, the way I said it.
    ++ [I'll tell them.]
        I know you will. That's what I'm afraid of.
    --
    -> DONE
+ [Never mind.]
    Suit yourself. I'll be here. I'm always here, that seems to be the problem.
    -> DONE
