VAR tracker_kills = -1
VAR done_tracker = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function tracker_ready() ===
~ return named and not done_tracker and tracker_kills >= 0 and count("founder_kills") > tracker_kills and check("wolf_close")

// Beat 6. After a kill with the wolf close by.
=== tracker ===
~ done_tracker = true
It went in ahead of me tonight. I didn't tell it to. It just went. # emote:ponder
...Good. That's good. I'm going to be insufferable about it for days, I can feel it.
+ [It's learning from you.]
    Poor thing. It's learning from the worst teacher in town.
+ [Don't let it get hurt.]
    ~ toward_oath()
    No. I won't. I'll keep it behind me. It won't like that, but it'll do it.
- -> DONE
