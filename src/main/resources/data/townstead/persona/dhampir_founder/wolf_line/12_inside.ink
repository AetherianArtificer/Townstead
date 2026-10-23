VAR inside_moved = false
VAR done_inside = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function inside_move_ready() ===
~ return done_midpoint and (done_wounded or done_court_note) and not inside_moved

=== function inside_seen_ready() ===
~ return inside_moved and not done_inside and check("wolf_close")

// Beat 12. Early Act 3: its bed is inside now.
=== inside_move ===
~ inside_moved = true
~ act("wolf_rest_inside")
I'm moving its bed. Don't say anything.
-> DONE

=== inside_seen ===
~ done_inside = true
Oh. It's you. Come in, then, mind the wolf.
+ [The wolf sleeps inside now.]
    {check("cold_night"):
        It was cold.
    - else:
        It was cold. # emote:shrug
    }
+ [Good evening to you too, wolf.]
    It doesn't answer to that. It barely answers to its name.
- -> DONE
