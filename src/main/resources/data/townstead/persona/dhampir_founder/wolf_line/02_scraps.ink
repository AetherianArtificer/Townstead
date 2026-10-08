VAR done_scraps = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function scraps_ready() ===
~ return not done_scraps and meat_given > 0 and check("evening")

// Beat 2. They give the player's meat to the wolf, and pretend they don't.
=== scraps ===
~ done_scraps = true
Somebody's been feeding that wolf off the end of my plate. I've been keeping watch. I haven't caught them yet.
+ [It's you.]
    Prove it.
    ...You can't, can you. Good.
+ [I'll keep an eye out.]
    Do. It's a serious matter. It's going to be too fat to walk at this rate.
- -> DONE
