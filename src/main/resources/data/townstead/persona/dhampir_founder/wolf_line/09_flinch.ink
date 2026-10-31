VAR done_flinch = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function flinch_ready() ===
~ return named and not done_flinch and check("hungry") and check("has_wolf") and not check("wolf_close")

// Beat 9. At high Hunger it keeps away from them.
=== flinch ===
~ done_flinch = true
It won't come near me tonight. # emote:ponder
It's never done that. Not once, not even the first week.
+ [It knows something's changed.]
    ~ toward_oath()
    Yes. Yes, it does. It always knows before I do.
    I'll walk it in the morning. Long way round. Maybe I'll be somebody it wants to walk with by then.
+ [It'll come round.]
    ~ toward_hunger()
    It'll have to, won't it. It hasn't got anybody else.
- -> DONE
