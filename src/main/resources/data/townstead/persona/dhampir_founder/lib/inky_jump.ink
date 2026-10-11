// Generated from the checkpoints in persona.json by tools/inky_jump.py. Do not edit.
// Inky only: pick a scene to start at. The game never runs this; it always starts at greet.
// Counters, acts and world checks in a checkpoint do not apply here. Use the stand-ins in
// lib/fallbacks.ink for those.

=== inky_jump ===
+ [From the start] -> greet
+ [Act 1] -> g_act_1
+ [Act 2] -> g_act_2
+ [Act 3] -> g_act_3
+ [Wolf line] -> g_wolf_line

= g_act_1
+ [scene01_meeting]
    ~ met = 0
    -> meeting
+ [scene02_wolf]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    -> wolf
+ [scene03_altar]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    -> altar_offer
+ [scene04_garlic]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    -> garlic_offer
+ [scene05_volunteers]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    -> volunteers
+ [scene06_first_oath]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    -> first_oath
+ [scene07_walk]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    -> walk_offer
+ [scene08_watch]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    -> watch_offer
+ [scene09_neighbor]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    -> neighbor_offer
+ [scene09b_bitten]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    -> bitten_offer
+ [scene10_thrall]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    -> thrall_arrival
+ [scene11_counting]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    -> counting
+ [scene12_name]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    -> the_name
+ [Back] -> inky_jump

= g_act_2
+ [scene13_count]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    -> the_count
+ [m1_slip]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    -> slip_after
+ [m2_midwife]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    -> greet
+ [m3_grave]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    -> grave_offer
+ [m4_book]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    -> the_book
+ [midpoint]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    ~ done_book = true
    ~ book_day = -1
    -> bringer_arrival
+ [confession_after]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    ~ done_book = true
    ~ book_day = -1
    ~ bringer_called = true
    ~ done_midpoint = true
    -> confession_after
+ [scene14_debrief]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    -> debrief
+ [scene15_stakeout]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    -> stakeout_offer
+ [scene16_willing]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ stake_offered = true
    ~ done_stakeout = true
    -> couple_arrival
+ [scene17_fledgling]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ stake_offered = true
    ~ done_stakeout = true
    ~ couple_called = true
    ~ done_willing = true
    ~ done_slip = true
    ~ bread_known = true
    -> fledgling_arrival
+ [Back] -> inky_jump

= g_act_3
+ [act3_court_offer]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    ~ done_book = true
    ~ book_day = -1
    ~ bringer_called = true
    ~ done_midpoint = true
    ~ done_confession_after = true
    ~ inside_moved = true
    ~ done_inside = true
    -> court_offer
+ [act3_court]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    ~ done_book = true
    ~ book_day = -1
    ~ bringer_called = true
    ~ done_midpoint = true
    ~ done_confession_after = true
    ~ inside_moved = true
    ~ done_inside = true
    ~ court_offered = true
    ~ court_walking = true
    ~ court_arrived = true
    -> court_arrive
+ [Back] -> inky_jump

= g_wolf_line
+ [wounded]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    -> court_move
+ [act3_inside]
    ~ met = 2
    ~ offered = true
    ~ founded = true
    ~ asked_wolf = true
    ~ wolf_seen = true
    ~ altar_started = true
    ~ done_lodge = true
    ~ garlic_started = true
    ~ done_garlic = true
    ~ done_volunteers = true
    ~ done_first_oath = true
    ~ done_walk = true
    ~ done_watch = true
    ~ done_neighbor = true
    ~ thrall_called = true
    ~ thrall_seen = true
    ~ done_thrall = true
    ~ done_counting = true
    ~ named = true
    ~ done_count_talk = true
    ~ done_slip = true
    ~ bread_known = true
    ~ grave_seen = true
    ~ done_grave = true
    ~ court_came = true
    ~ done_wounded = true
    ~ done_book = true
    ~ book_day = -1
    ~ bringer_called = true
    ~ done_midpoint = true
    ~ done_confession_after = true
    -> inside_move
+ [Back] -> inky_jump
