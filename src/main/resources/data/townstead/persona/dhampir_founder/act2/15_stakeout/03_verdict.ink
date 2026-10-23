// The verdict, after the nights of watching.

=== stakeout_verdict ===
* [Say it. They're not dangerous.]
    ~ toward_oath()
    ~ trust(1)
    ~ not_them = true
    You want me to say it out loud. # emote:shake_head
    ...Not them. All right? Not them. I've said it.
    -> settle
* [You don't have to say anything.]
    Thanks. I'd rather not, tonight. I'll think it, though. Don't tell anyone I thought it.
    -> settle
* [Not yet, you mean.]
    ~ toward_hunger()
    Not yet. That's it exactly. Not yet.
    {stake_visitor:
        ~ act("settler_leave")
    }
    -> DONE

= settle
{stake_visitor:
    ~ act("settler_stay")
    They can stay. I'll tell them myself, and I'll try to make it sound like good news.
}
-> DONE

// Between-quest line once the founder has said it.
=== function not_them_line() ===
{not_them and stake_target != "":
    ~ return "If " + stake_target + " nods at me, I've decided I'm going to nod back. That's as far as I've got."
}
~ return ""
