// The Dhampir founder
//
// A hunter with a vampire father and a living mother. They arrive on the trail of a vampire and
// stay to found a hunter lodge. The full arc is in docs/design/dhampir_founder.md.

// Act 1
INCLUDE act1/01_meeting/01_meeting.ink
INCLUDE act1/01_meeting/02_after_kill.ink
INCLUDE act1/01_meeting/03_cold_trail.ink
INCLUDE act1/01_meeting/04_answers.ink
INCLUDE act1/01_meeting/05_shepherd.ink
INCLUDE act1/01_meeting/06_offer.ink
INCLUDE act1/02_wolf.ink
INCLUDE act1/03_altar.ink
INCLUDE act1/04_garlic.ink
INCLUDE act1/05_volunteers.ink
INCLUDE act1/06_oath.ink
INCLUDE act1/07_walk.ink
INCLUDE act1/08_watch.ink
INCLUDE act1/09_neighbor.ink
INCLUDE act1/09b_bitten.ink
INCLUDE act1/10_thrall.ink
INCLUDE act1/11_counting.ink
INCLUDE act1/12_name.ink

// Act 2 and the midpoint
INCLUDE act2/13_count_talk.ink
INCLUDE act2/14_debrief.ink
INCLUDE act2/15_stakeout/01_offer.ink
INCLUDE act2/15_stakeout/02_night.ink
INCLUDE act2/15_stakeout/03_verdict.ink
INCLUDE act2/16_willing.ink
INCLUDE act2/17_fledgling.ink
INCLUDE act2/m1_slip.ink
INCLUDE act2/m2_midwife_side.ink
INCLUDE act2/m3_grave.ink
INCLUDE act2/m4_book.ink
INCLUDE act2/midpoint.ink
INCLUDE act2/romance.ink

// Act 3 and the endings
INCLUDE act3/01_court_offer.ink
INCLUDE act3/02_court_road.ink
INCLUDE act3/03_court/01_court.ink
INCLUDE act3/03_court/02_pivot.ink
INCLUDE act3/03_court/03_ending_a.ink
INCLUDE act3/03_court/04_ending_b.ink
INCLUDE act3/04_epilogue.ink

// The wolf line
INCLUDE wolf_line/00_wolf_line.ink
INCLUDE wolf_line/02_scraps.ink
INCLUDE wolf_line/03_name.ink
INCLUDE wolf_line/04_mutter.ink
INCLUDE wolf_line/05_collar.ink
INCLUDE wolf_line/06_tracker.ink
INCLUDE wolf_line/07_cold_night.ink
INCLUDE wolf_line/08_bone.ink
INCLUDE wolf_line/09_flinch.ink
INCLUDE wolf_line/10_wounded.ink
INCLUDE wolf_line/11_shepherd.ink
INCLUDE wolf_line/12_inside.ink

// Through the whole story
INCLUDE ongoing/contracts.ink
INCLUDE ongoing/dusk.ink
INCLUDE ongoing/greeting.ink
INCLUDE ongoing/werewolf.ink

// Shared building blocks
INCLUDE lib/balance.ink
INCLUDE lib/trips.ink

// Stand-ins for the game's helpers, for Inky
INCLUDE lib/fallbacks.ink



// Set by the game.
VAR given_name = ""
VAR family_name = ""
VAR house = ""
VAR today = 0
VAR quest_ready = false
VAR quest_open = false
VAR interrupted = false

// 0 before the first meeting, 2 after it.
VAR met = 0
VAR offered = false
VAR founded = false
VAR asked_wolf = false

// The menu line for the next scene. next_scene() sets it.
VAR scene_line = ""

-> inky_start

// Every conversation starts here and goes to the first scene that is ready.
=== greet ===
# label: Talk
~ temp next = next_scene()
-> next

// The scene that plays next, and what the player says to start it ("" for the everyday line).
// The first one that is ready wins, so the order matters.
=== function next_scene() ===
{
- meeting_ready():
    ~ return scene(-> meeting, "Who are you?")
- meeting_resume_ready():
    ~ return scene(-> meeting.resume, "About earlier...")
- reoffer_ready():
    ~ return scene(-> reoffer, "About staying...")
- court_ready():
    ~ return scene(-> court, "They're here.")
- court_arrive_ready():
    ~ return scene(-> court_arrive, "Is this the place?")
- court_road_ready():
    ~ return scene(-> court_road, "How far now?")
- epilogue_ready():
    ~ return scene(-> epilogue, "Home again.")
- shepherd_arrive_ready():
    ~ return scene(-> shepherd_arrive, "Is this where he is?")
- shepherd_road_ready():
    ~ return scene(-> shepherd_road, "Still with me?")
- stakeout_night_ready():
    ~ return scene(-> stakeout_night, "Anything moving?")
- couple_meet_ready():
    ~ return scene(-> couple_meet, "Who are they?")
- fledgling_meet_ready():
    ~ return scene(-> fledgling_meet, "They're asking for you.")
- cure_after_ready():
    ~ return scene(-> cure_after, "Did it work?")
- wolf_ready():
    ~ return scene(-> wolf, "Who's this?")
- wolf_fetch_ready():
    ~ return scene(-> wolf_fetch, "")
- altar_offer_ready():
    ~ return scene(-> altar_offer, "Still measuring?")
- walk_after_ready():
    ~ return scene(-> walk_after, "Anything?")
- walk_quiet_ready():
    ~ return scene(-> walk_quiet, "Anything?")
- watch_during_ready():
    ~ return scene(-> watch_during, "Where do you want me?")
- watch_after_ready():
    ~ return scene(-> watch_after, "Is that all of them?")
- neighbor_back_ready():
    ~ return scene(-> neighbor_back, "About your neighbor...")
- bitten_after_ready():
    ~ return scene(-> bitten_after, "About " + bitten + "...")
- thrall_cold_ready():
    ~ return scene(-> thrall_cold, "Who's that?")
- thrall_after_ready():
    ~ return scene(-> thrall_after, "It's decided.")
- slip_after_ready():
    ~ return scene(-> slip_after, "About the oath...")
- grave_arrive_ready():
    ~ return scene(-> grave_arrive, "Is this it?")
- grave_stay_ready():
    ~ return scene(-> grave_arrive.stay, "How are you doing?")
- grave_road_ready():
    ~ return scene(-> grave_road, "How are you doing?")
- midpoint_ready():
    ~ return scene(-> midpoint, "They came for me.")
- bringer_arrival_ready():
    ~ return scene(-> bringer_arrival, "Did you hear something?")
- confession_after_ready():
    ~ return scene(-> confession_after, "About her...")
- wounded_down_ready():
    ~ return scene(-> wounded_down, "Is it breathing?")
- wounded_up_ready():
    ~ return scene(-> wounded_up, "It's up.")
- court_note_ready():
    ~ return scene(-> court_note, "")
- court_move_ready():
    ~ return scene(-> court_move, "Something wrong?")
- grave_offer_ready():
    ~ return scene(-> grave_offer, "You're quiet today.")
- first_oath_ready():
    ~ return scene(-> first_oath, "That went well.")
- volunteers_ready():
    ~ return scene(-> volunteers, "")
- walk_offer_ready():
    ~ return scene(-> walk_offer, "Going out tonight?")
- watch_offer_ready():
    ~ return scene(-> watch_offer, "Quiet tonight.")
- neighbor_offer_ready():
    ~ return scene(-> neighbor_offer, "Something on your mind?")
- bitten_offer_ready():
    ~ return scene(-> bitten_offer, "You look worried.")
- thrall_arrival_ready():
    ~ return scene(-> thrall_arrival, "")
- counting_ready():
    ~ return scene(-> counting, "")
- the_name_ready():
    ~ return scene(-> the_name, "Mind if I sit?")
- garlic_offer_ready():
    ~ return scene(-> garlic_offer, "Another one?")
- court_offer_ready():
    ~ return scene(-> court_offer, "You've been quiet.")
- debrief_ready():
    ~ return scene(-> debrief, "Good hunting?")
- romance_ready():
    ~ return scene(-> romance_beat, "Mind the company?")
- stakeout_offer_ready():
    ~ return scene(-> stakeout_offer, "")
- couple_arrival_ready():
    ~ return scene(-> couple_arrival, "")
- fledgling_arrival_ready():
    ~ return scene(-> fledgling_arrival, "")
- fledgling_after_ready():
    ~ return scene(-> fledgling_after, "")
- werewolf_signs_ready():
    ~ return scene(-> werewolf_signs, "")
- werewolf_items_ready():
    ~ return scene(-> werewolf_items, "")
- werewolf_wolf_ready():
    ~ return scene(-> werewolf_wolf, "")
- wolf_beat_ready():
    ~ return scene(-> wolf_beat_run, "")
}
{
- quest_ready:
    ~ return scene(-> dusk, "Come and look.")
- quest_open:
    ~ return scene(-> dusk, "About that job...")
}
~ return scene(-> dusk, "")

// Sets the menu line for the scene that next_scene() picks.
=== function scene(target, line) ===
~ scene_line = line
~ return target

// What the player says to start the next scene.
=== function menu() ===
{interrupted:
    ~ return "You were saying?"
}
~ next_scene()
{scene_line != "":
    ~ return scene_line
}
~ return "Got a moment?"

// What they call out to bring the player over when a scene is waiting, or "" for nothing.
// They do not call during walks and trips, or for everyday talk.
=== function calling() ===
{
- menu() == "Got a moment?" or interrupted:
    ~ return ""
- walking or watching or grave_walking or court_walking or shepherd_walking:
    ~ return ""
- met == 0:
    ~ return "You. Hold up a moment."
- ending == "B":
    ~ return "There you are. Come here."
}
~ return "Have you got a minute? I want a word."
