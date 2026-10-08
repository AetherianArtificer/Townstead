// The game's helpers. Inky cannot run them, so each one has a stand-in below. In game the real
// helper always runs instead. Change a return value here to steer a playthrough in Inky.

EXTERNAL check(what)
EXTERNAL count(what)
EXTERNAL who(role)
EXTERNAL here(role)
EXTERNAL is(name, what)
EXTERNAL rel(quality)
EXTERNAL trust(amount)
EXTERNAL contribute(quality, amount, reason)
EXTERNAL act(id)
EXTERNAL mod(id)
EXTERNAL contract_offer(pool)
EXTERNAL contract_about(pool)
EXTERNAL contract_skip(pool)
EXTERNAL contract_accept(pool)
EXTERNAL contract_turn_in(pool)

=== function check(what) ===
~ return false

=== function count(what) ===
~ return 0

=== function who(role) ===
~ return false

=== function here(role) ===
~ return false

=== function is(name, what) ===
~ return false

=== function rel(quality) ===
~ return 0

=== function trust(amount) ===
~ return

=== function contribute(quality, amount, reason) ===
~ return

=== function act(id) ===
~ return

=== function mod(id) ===
~ return false

=== function contract_offer(pool) ===
~ return ""

=== function contract_about(pool) ===
~ return ""

=== function contract_skip(pool) ===
~ return

=== function contract_accept(pool) ===
~ return false

=== function contract_turn_in(pool) ===
~ return 0

// Test values for Inky. The game fills these in itself, and never runs this part: it always
// starts at greet. Change them here to try other names or days.
=== inky_start ===
~ given_name = "Mira"
~ family_name = "Kessler"
~ house = "von Aschenau"
~ today = 1
~ chance = 50
~ quest_ready = false
~ quest_open = false
~ interrupted = false

// Story progress, by the file that declares it. Every line is at its starting value, so this
// changes nothing until you edit one. To try a later scene, set what the earlier scenes would
// have set, and change "-> greet" at the bottom to that scene's knot if you want to jump straight
// in. The checkpoints in persona.json list the values for each scene.

// persona.ink
~ met = 0
~ offered = false
~ founded = false
~ asked_wolf = false

// act1/02_wolf.ink
~ wolf_seen = false
~ asked_scraps = false
~ wolf_day = -1

// act1/03_altar.ink
~ altar_started = false
~ done_lodge = false

// act1/04_garlic.ink
~ garlic_started = false
~ done_garlic = false

// act1/05_volunteers.ink
~ done_volunteers = false

// act1/06_oath.ink
~ done_first_oath = false

// act1/07_walk.ink
~ walking = false
~ done_walk = false
~ walk_kills = 0
~ walk_player_kills = 0
~ walk_toyed = 0

// act1/08_watch.ink
~ watching = false
~ done_watch = false

// act1/09_neighbor.ink
~ neighbor = ""
~ asking = false
~ done_neighbor = false
~ neighbor_mood = ""
~ neighbor_mood_until = -1

// act1/09b_bitten.ink
~ bitten = ""
~ bitten_asked = false
~ done_bitten = false

// act1/10_thrall.ink
~ thrall_called = false
~ thrall_name = ""
~ thrall_seen = false
~ done_thrall = false
~ thrall_scorned = false

// act1/11_counting.ink
~ done_counting = false

// act1/12_name.ink
~ named = false

// act2/13_count_talk.ink
~ done_count_talk = false
~ slip_oaths = 0

// act2/14_debrief.ink
~ debrief_kills = 0
~ debrief_day = -1

// act2/15_stakeout/01_offer.ink
~ stake_target = ""
~ stake_offered = false
~ staking = false
~ stake_visitor = false
~ stake_nights = 0
~ stake_last = -1
~ done_stakeout = false
~ not_them = false
~ argued_consent = false

// act2/16_willing.ink
~ couple_called = false
~ done_willing = false
~ willing_broke = false
~ asked_master = false

// act2/17_fledgling.ink
~ fledgling_called = false
~ fledgling_name = ""
~ done_fledgling = false
~ fledgling_end = ""
~ curing = false
~ done_cure = false
~ done_fledgling_after = false
~ fledgling_day = -1

// act2/m1_slip.ink
~ done_slip = false

// act2/m2_midwife_side.ink
~ bread_known = false
~ told_father = false

// act2/m3_grave.ink
~ grave_walking = false
~ done_grave = false
~ grave_seen = false

// act2/m4_book.ink
~ done_book = false

// act2/midpoint.ink
~ book_day = -1
~ bringer_called = false
~ bringer_name = ""
~ done_midpoint = false
~ bringer_stayed = false
~ done_confession_after = false

// act2/romance.ink
~ romance = 0
~ romance_day = -10
~ romanced = false

// act3/01_court_offer.ink
~ court_offered = false
~ midpoint_day = -1

// act3/02_court_road.ink
~ court_walking = false
~ court_arrived = false

// act3/03_court/01_court.ink
~ done_court = false
~ ending = ""
~ father_fate = ""
~ b_answer = ""

// act3/04_epilogue.ink
~ done_epilogue = false

// wolf_line/00_wolf_line.ink
~ meat_given = 0
~ wolf_beat_day = -1

// wolf_line/02_scraps.ink
~ done_scraps = false

// wolf_line/03_name.ink
~ name_asked = false
~ done_wolf_name = false

// wolf_line/04_mutter.ink
~ done_mutter = false

// wolf_line/05_collar.ink
~ collar_asked = false
~ done_collar = false

// wolf_line/06_tracker.ink
~ tracker_kills = -1
~ done_tracker = false

// wolf_line/07_cold_night.ink
~ let_in = false
~ done_cold = false

// wolf_line/08_bone.ink
~ bone_asked = false
~ done_bone = false

// wolf_line/09_flinch.ink
~ done_flinch = false

// wolf_line/10_wounded.ink
~ court_came = false
~ court_downs = 0
~ done_wounded = false
~ done_court_note = false

// wolf_line/11_shepherd.ink
~ shepherd_offered = false
~ shepherd_walking = false
~ done_shepherd = false

// wolf_line/12_inside.ink
~ inside_moved = false
~ done_inside = false

// ongoing/werewolf.ink
~ done_w1 = false
~ w1_asked = false
~ done_w1_items = false
~ done_w2 = false
~ w1_day = -1

-> greet
