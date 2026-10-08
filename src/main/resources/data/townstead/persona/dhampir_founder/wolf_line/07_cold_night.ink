VAR let_in = false
VAR done_cold = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function cold_out_ready() ===
~ return let_in and not check("cold_night")

=== function cold_in_ready() ===
~ return not done_cold and not done_inside and check("cold_night") and check("has_wolf")

// Beat 7. A really cold night. Just tonight.
=== cold_in ===
~ let_in = true
~ done_cold = true
~ act("wolf_rest_inside")
I'm letting it in tonight. Just tonight. It's cold out, even for a wolf.
Don't look at me like that. Just tonight.
-> DONE

=== cold_out ===
~ let_in = false
{not done_wounded:
    ~ act("wolf_rest_outside")
}
Back out it goes. It was one night. We agreed on one night, didn't we.
-> DONE
