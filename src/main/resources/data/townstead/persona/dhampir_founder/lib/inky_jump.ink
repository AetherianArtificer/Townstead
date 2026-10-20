// Generated from the checkpoints in persona.json by tools/inky_jump.py. Do not edit.
// Inky only: pick a scene to start at. The game never runs this; it always starts at greet.
// Counters, acts and world checks in a checkpoint do not apply here. Use the stand-ins in
// lib/fallbacks.ink for those. Later parts of a scene play on later talks in game.

=== inky_jump ===
+ [From the start] -> greet
+ [Act 1] -> g_act_1
+ [Act 2] -> g_act_2
+ [Act 3] -> g_act_3
+ [Wolf line] -> g_wolf_line

= g_act_1
+ [scene01_meeting] -> cp_scene01_meeting
+ [scene02_wolf] -> cp_scene02_wolf
+ [scene03_altar] -> cp_scene03_altar
+ [scene04_garlic] -> cp_scene04_garlic
+ [scene05_volunteers] -> cp_scene05_volunteers
+ [scene06_first_oath] -> cp_scene06_first_oath
+ [scene06b_oath] -> cp_scene06b_oath
+ [scene07_walk] -> cp_scene07_walk
+ [scene08_watch] -> cp_scene08_watch
+ [scene09_neighbor] -> cp_scene09_neighbor
+ [scene09b_bitten] -> cp_scene09b_bitten
+ [scene10_thrall] -> cp_scene10_thrall
+ [scene11_counting] -> cp_scene11_counting
+ [scene12_name] -> cp_scene12_name
+ [Back] -> inky_jump

= g_act_2
+ [scene13_count] -> cp_scene13_count
+ [m1_slip] -> cp_m1_slip
+ [m2_midwife] -> cp_m2_midwife
+ [m3_grave] -> cp_m3_grave
+ [m4_book] -> cp_m4_book
+ [midpoint] -> cp_midpoint
+ [confession_after] -> cp_confession_after
+ [scene14_debrief] -> cp_scene14_debrief
+ [scene15_stakeout] -> cp_scene15_stakeout
+ [scene16_willing] -> cp_scene16_willing
+ [scene17_fledgling] -> cp_scene17_fledgling
+ [Back] -> inky_jump

= g_act_3
+ [act3_court_offer] -> cp_act3_court_offer
+ [act3_court] -> cp_act3_court
+ [Back] -> inky_jump

= g_wolf_line
+ [wounded] -> cp_wounded
+ [act3_inside] -> cp_act3_inside
+ [Back] -> inky_jump

= cp_scene01_meeting
~ met = 0
+ [meeting] -> meeting
+ [meeting_after_kill] -> meeting_after_kill
+ [meeting_cold_trail] -> meeting_cold_trail
+ [meeting_offer] -> meeting_offer
+ [meeting_stay] -> meeting_stay
+ [reoffer] -> reoffer
+ [Back] -> g_act_1

= cp_scene02_wolf
~ met = 2
~ offered = true
~ founded = true
~ asked_wolf = true
+ [wolf] -> wolf
+ [gift_meat] -> gift_meat
+ [wolf_fetch] -> wolf_fetch
+ [Back] -> g_act_1

= cp_scene03_altar
~ met = 2
~ offered = true
~ founded = true
~ asked_wolf = true
~ wolf_seen = true
-> altar_offer

= cp_scene04_garlic
~ met = 2
~ offered = true
~ founded = true
~ asked_wolf = true
~ wolf_seen = true
~ altar_started = true
~ done_lodge = true
-> garlic_offer

= cp_scene05_volunteers
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

= cp_scene06_first_oath
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

= cp_scene06b_oath
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
-> player_oath_offer

= cp_scene07_walk
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
+ [walk_offer] -> walk_offer
+ [walk_quiet] -> walk_quiet
+ [walk_after] -> walk_after
+ [Back] -> g_act_1

= cp_scene08_watch
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
~ oath_job = true
+ [watch_offer] -> watch_offer
+ [watch_during] -> watch_during
+ [watch_after] -> watch_after
+ [Back] -> g_act_1

= cp_scene09_neighbor
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
~ oath_job = true
+ [neighbor_offer] -> neighbor_offer
+ [neighbor_back] -> neighbor_back
+ [Back] -> g_act_1

= cp_scene09b_bitten
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
~ oath_job = true
+ [bitten_offer] -> bitten_offer
+ [bitten_after] -> bitten_after
+ [Back] -> g_act_1

= cp_scene10_thrall
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
~ oath_job = true
+ [thrall_arrival] -> thrall_arrival
+ [thrall_cold] -> thrall_cold
+ [thrall_after] -> thrall_after
+ [Back] -> g_act_1

= cp_scene11_counting
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
~ oath_job = true
-> counting

= cp_scene12_name
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
~ oath_job = true
-> the_name

= cp_scene13_count
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
~ oath_job = true
-> the_count

= cp_m1_slip
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
~ oath_job = true
+ [slip_after] -> slip_after
+ [gift_bread] -> gift_bread
+ [father_at_birth] -> father_at_birth
+ [Back] -> g_act_2

= cp_m2_midwife
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
~ oath_job = true
-> greet

= cp_m3_grave
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
~ oath_job = true
+ [grave_offer] -> grave_offer
+ [grave_road] -> grave_road
+ [grave_arrive] -> grave_arrive
+ [Back] -> g_act_2

= cp_m4_book
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
~ oath_job = true
-> the_book

= cp_midpoint
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
~ oath_job = true
+ [bringer_arrival] -> bringer_arrival
+ [midpoint] -> midpoint
+ [Back] -> g_act_2

= cp_confession_after
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
~ oath_job = true
+ [confession_after] -> confession_after
+ [romance_beat] -> romance_beat
+ [Back] -> g_act_2

= cp_scene14_debrief
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
~ oath_job = true
-> debrief

= cp_scene15_stakeout
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
~ oath_job = true
+ [stakeout_offer] -> stakeout_offer
+ [stakeout_night] -> stakeout_night
+ [stakeout_verdict] -> stakeout_verdict
+ [Back] -> g_act_2

= cp_scene16_willing
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
~ oath_job = true
+ [couple_arrival] -> couple_arrival
+ [couple_meet] -> couple_meet
+ [Back] -> g_act_2

= cp_scene17_fledgling
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
~ oath_job = true
+ [fledgling_arrival] -> fledgling_arrival
+ [fledgling_meet] -> fledgling_meet
+ [cure_after] -> cure_after
+ [fledgling_after] -> fledgling_after
+ [Back] -> g_act_2

= cp_act3_court_offer
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
~ oath_job = true
+ [court_offer] -> court_offer
+ [court_road] -> court_road
+ [Back] -> g_act_3

= cp_act3_court
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
~ oath_job = true
+ [court_arrive] -> court_arrive
+ [court] -> court
+ [court_pivot] -> court_pivot
+ [court_ending_a] -> court_ending_a
+ [court_ending_b] -> court_ending_b
+ [epilogue] -> epilogue
+ [Back] -> g_act_3

= cp_wounded
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
~ oath_job = true
+ [court_move] -> court_move
+ [wounded_down] -> wounded_down
+ [wounded_up] -> wounded_up
+ [court_note] -> court_note
+ [shepherd_offer] -> shepherd_offer
+ [shepherd_road] -> shepherd_road
+ [shepherd_arrive] -> shepherd_arrive
+ [Back] -> g_wolf_line

= cp_act3_inside
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
~ oath_job = true
+ [inside_move] -> inside_move
+ [inside_seen] -> inside_seen
+ [Back] -> g_wolf_line
