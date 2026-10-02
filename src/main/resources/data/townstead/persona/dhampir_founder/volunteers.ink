// Act 1, scene 5: The volunteers. Once the lodge stands, they go through who in town could do
// this. One of them would sign up tomorrow, and the founder will not have them: someone with a
// young child at home ("parent_volunteer", a real resident). The player can ask, argue, or trust
// the call; asking gets half an answer. The rule is real: the hunters' volunteers never include
// parents of young children, though the player can still put one forward. Reveals their
// judgment, and who they protect.

VAR done_volunteers = false

=== volunteers ===
~ done_volunteers = true
I've got a list. Well, I've got a list in my head, I don't write things down. Everyone in town who could hold a stake without dropping it on their foot.
Most of them I'll wait for. They'll come when they're ready, at dusk usually. The good ones always come at dusk, I don't know why.
{who("parent_volunteer")} would sign up tomorrow if I let them. I'm not going to.
* [Why not?]
    Because I'm not.
    ** [That's not a reason.]
        ~ trust(1)
        No. It isn't. # emote:shrug
        They've got a little one at home. That's all you're getting.
    ** [Fair enough.]
        Thanks.
* [They'd be good at it.]
    They'd be very good at it. That's the problem.
    If you want to put them forward yourself, you can. The altar won't stop you, and I won't either. I just won't be the one who asked.
* [All right. Your call.]
    ~ trust(1)
    Thank you. I mean that. People usually want a reason, and I don't always have one I can say out loud.
- Anyway. The rest will come at dusk, or they won't. You can't talk anybody into this. I've tried. It doesn't take.
-> DONE
