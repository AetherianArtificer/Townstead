// The dhampir founder. A hunter with a vampire father and a living mother, who arrives on the trail
// of a vampire and stays to found a hunter lodge. The whole arc and the voice rules are in
// docs/design/dhampir_founder.md. Act 1 lives in meeting.ink (scene 1), wolf.ink (scene 2), altar.ink (scene 3), garlic.ink (scene 4), volunteers.ink (scene 5), oath.ink (scene 6), walk.ink (scene 7), watch.ink (scene 8), neighbor.ink (scene 9), bitten.ink (scene 9b), thrall.ink (scene 10), counting.ink (scene 11) and name.ink (scene 12). Act 2: count_talk.ink (scene 13), debrief.ink (14), stakeout.ink (15), willing.ink (16), fledgling.ink (17), slip.ink (M1), midwife_side.ink (M2, the founder's side), grave.ink (M3), wounded.ink (wolf beat 10), book.ink (M4), midpoint.ink (the Truth), romance.ink. The wolf line: wolf_line.ink. Werewolves: werewolf.ink. Act 3 and the endings: court.ink. Contracts: contracts.ink.
//
// Voice: guarded, dry, rage held in. The hunt delights them; that delight is for their quarry,
// never the player. They respect the player and show care through what they do. Reveals are slow.

VAR given_name = ""
VAR family_name = ""
VAR house = ""
VAR today = 0

// 0: not met. 2: met.
VAR met = 0
VAR offered = false
VAR founded = false
VAR asked_wolf = false

VAR quest_ready = false
VAR quest_open = false
VAR interrupted = false

// What the player says to open a conversation.
=== function menu() ===
{
- interrupted:
    ~ return "You were saying?"
- court_arrived and not done_court and here("visitor:father"):
    ~ return "They're here."
- court_walking and not court_arrived and check("at_court"):
    ~ return "Is this the place?"
- court_walking and not court_arrived:
    ~ return "How far now?"
- done_court and not done_epilogue and not court_walking:
    ~ return "Home again."
- shepherd_walking and check("at_shepherd"):
    ~ return "Is this where he is?"
- shepherd_walking:
    ~ return "Still with me?"
- staking and check("late_night") and stake_last < today:
    ~ return "Anything moving?"
- couple_called and not done_willing and here("visitor:couple_thrall"):
    ~ return "Who are they?"
- fledgling_called and not done_fledgling and here("visitor:fledgling"):
    ~ return "They're asking for you."
- curing and not done_cure and is(fledgling_name, "cured_villager"):
    ~ return "Did it work?"
- met == 0:
    ~ return "Who are you?"
- met == 2 and not offered:
    ~ return "About earlier..."
- offered and not founded:
    ~ return "About staying..."
- founded and not wolf_seen and check("has_wolf"):
    ~ return "Who's this?"
- founded and not altar_started and not done_lodge:
    ~ return "Still measuring?"
- done_lodge and not garlic_started and not done_garlic and check("second_kill"):
    ~ return "Another one?"
- done_lodge and not done_first_oath and check("first_oath"):
    ~ return "That went well."
- walking:
    ~ return "Anything?"
- watching and check("raid_near"):
    ~ return "Where do you want me?"
- watching:
    ~ return "Is that all of them?"
- done_walk and not done_watch and who("sworn_hunter") != "" and check("late_night"):
    ~ return "Quiet tonight."
- asking:
    ~ return "About your neighbor..."
- bitten_asked and not done_bitten and not is(bitten, "turning_villager"):
    ~ return "About " + bitten + "..."
- thrall_called and not thrall_seen and check("thrall_near"):
    ~ return "Who's that?"
- thrall_seen and not done_thrall and not check("thrall_near"):
    ~ return "It's decided."
- done_count_talk and not done_slip and count("founder_oaths") > slip_oaths:
    ~ return "About the oath..."
- grave_walking and not grave_seen and check("at_grave"):
    ~ return "Is this it?"
- grave_walking:
    ~ return "How are you doing?"
- bringer_called and not done_midpoint and here("visitor:candle_bringer"):
    ~ return "They came for me."
- done_book and not bringer_called and today > book_day:
    ~ return "Did you hear something?"
- done_midpoint and not done_confession_after and (check("midwife_forgiven") or check("midwife_cast_out")):
    ~ return "About her..."
- bread_known and not done_grave:
    ~ return "You're quiet today."
- court_came and not done_wounded and count("wolf_downs") > court_downs and check("wolf_down"):
    ~ return "Is it breathing?"
- court_came and not done_wounded and count("wolf_downs") > court_downs:
    ~ return "It's up."
- done_grave and not court_came and check("late_night"):
    ~ return "Something wrong?"
- done_counting and not named and (done_walk or done_watch) and (done_neighbor or done_bitten) and rel("trust") >= 15 and check("evening"):
    ~ return "Mind if I sit?"
- done_watch and not done_neighbor and not bitten_asked and who("turning_villager") != "":
    ~ return "You look worried."
- done_watch and not done_neighbor and who("resident_vampire") != "":
    ~ return "Something on your mind?"
- done_first_oath and not done_walk and check("player_sworn") and check("evening"):
    ~ return "Going out tonight?"
- named and count("founder_kills") > debrief_kills and debrief_day < today:
    ~ return "Good hunting?"
- romance_ready():
    ~ return "Mind the company?"
- done_midpoint and done_inside and not court_offered and (done_confession_after or today > midpoint_day + 5):
    ~ return "You've been quiet."
- quest_ready:
    ~ return "Come and look."
- quest_open:
    ~ return "About that job..."
}
~ return "Got a moment?"

// What they call the player over with when a scene is waiting ("" for nothing): the game shows a
// mark over their head and, once you come close, they wave and say it. A scene is waiting when
// menu() has something other than the everyday label; walks and trips in progress never call.
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

=== greet ===
# label: Talk
{
- met == 0:
    -> meeting
- met == 2 and not offered:
    -> meeting.resume
- offered and not founded:
    -> reoffer
- court_arrived and not done_court and here("visitor:father"):
    -> court
- court_walking and not court_arrived and check("at_court"):
    -> court_arrive
- court_walking and not court_arrived:
    -> court_road
- done_court and not done_epilogue and not court_walking and not check("at_court"):
    -> epilogue
- shepherd_walking and check("at_shepherd"):
    -> shepherd_arrive
- shepherd_walking:
    -> shepherd_road
- staking and check("late_night") and stake_last < today:
    -> stakeout_night
- couple_called and not done_willing and here("visitor:couple_thrall"):
    -> couple_meet
- fledgling_called and not done_fledgling and here("visitor:fledgling"):
    -> fledgling_meet
- curing and not done_cure and is(fledgling_name, "cured_villager"):
    -> cure_after
- founded and not wolf_seen and check("has_wolf"):
    -> wolf
- founded and not altar_started and not done_lodge:
    -> altar_offer
- walking and (count("founder_kills") > walk_kills or count("player_kills") > walk_player_kills):
    -> walk_after
- walking:
    -> walk_quiet
- watching and check("raid_near"):
    -> watch_during
- watching:
    -> watch_after
- asking:
    -> neighbor_back
- bitten_asked and not done_bitten and not is(bitten, "turning_villager"):
    -> bitten_after
- thrall_called and not thrall_seen and check("thrall_near"):
    -> thrall_cold
- thrall_seen and not done_thrall and not check("thrall_near"):
    -> thrall_after
- done_count_talk and not done_slip and count("founder_oaths") > slip_oaths:
    -> slip_after
- grave_walking and not grave_seen and check("at_grave"):
    -> grave_arrive
- grave_walking and grave_seen:
    -> grave_arrive.stay
- grave_walking:
    -> grave_road
- bringer_called and not done_midpoint and here("visitor:candle_bringer"):
    -> midpoint
- done_book and not bringer_called and today > book_day:
    -> bringer_arrival
- done_midpoint and not done_confession_after and (check("midwife_forgiven") or check("midwife_cast_out")):
    -> confession_after
- court_came and not done_wounded and count("wolf_downs") > court_downs and check("wolf_down"):
    -> wounded_down
- court_came and not done_wounded and count("wolf_downs") > court_downs:
    -> wounded_up
- court_came and not done_wounded and not done_court_note and not check("raid_near") and count("wolf_downs") <= court_downs:
    -> court_note
- done_grave and not court_came and check("late_night"):
    -> court_move
- bread_known and not done_grave and not grave_walking:
    -> grave_offer
- done_lodge and not done_first_oath and check("first_oath"):
    -> first_oath
- done_lodge and not done_volunteers and who("parent_volunteer") != "":
    -> volunteers
- done_first_oath and not done_walk and check("player_sworn") and check("evening"):
    -> walk_offer
- done_walk and not done_watch and who("sworn_hunter") != "" and check("late_night"):
    -> watch_offer
- done_watch and not done_neighbor and who("resident_vampire") != "":
    -> neighbor_offer
- done_watch and not done_neighbor and not bitten_asked and who("turning_villager") != "":
    -> bitten_offer
- (done_neighbor or done_bitten) and not thrall_called:
    -> thrall_arrival
- done_thrall and not done_counting and check("night"):
    -> counting
- done_counting and not named and (done_walk or done_watch) and (done_neighbor or done_bitten) and rel("trust") >= 15 and check("evening"):
    -> the_name
- done_lodge and not garlic_started and not done_garlic and check("second_kill"):
    -> garlic_offer
- done_midpoint and done_inside and not court_offered and (done_confession_after or today > midpoint_day + 5):
    -> court_offer
- named and count("founder_kills") > debrief_kills and debrief_day < today:
    -> debrief
- romance_ready():
    -> romance_beat
- done_count_talk and not stake_offered and not done_stakeout:
    -> stakeout_offer
- done_stakeout and not couple_called:
    -> couple_arrival
- done_willing and bread_known and not fledgling_called:
    -> fledgling_arrival
- done_fledgling and fledgling_end != "" and not done_fledgling_after and not check("oath_restored") and check("night") and today > fledgling_day:
    -> fledgling_after
- mod("werewolves") and done_lodge and not done_w1 and check("werewolf_near"):
    -> werewolf_signs
- w1_asked and not done_w1_items and check("carries_werewolf_things"):
    -> werewolf_items
- done_w1 and not done_w2 and check("werewolf_near") and check("has_wolf") and today > w1_day:
    -> werewolf_wolf
- wolf_beat() != "":
    -> wolf_beat_run
}
-> dusk

// They offered to stay, and the player asked for time.
=== reoffer ===
Have you thought about it? I'm not trying to rush you. They are, though.
+ [Stay. Build your lodge.]
    -> meeting.stay
+ [Not yet.]
    All right. Keep your shutters barred in the meantime. I'll be around.
    -> DONE

// Between-quest talk, before the later scenes are written. They are in the middle of something,
// and the opening follows what is really going on: hurt, soaked, out at night, or just working.
=== dusk ===
~ temp mood = neighbor_mood_line()
{mood == "":
    ~ mood = not_them_line()
}
{done_counting and check("night") and RANDOM(1, 4) == 1:
    -> recite ->
    Oh. It's you.
    -> talk
}
{
- ending == "B":
    {~You again. Sit, if you like. I don't bite. Not you, anyway.|The lodge is quiet tonight. I like it quiet. I like a lot of things quiet now.}
- ending == "A" and RANDOM(1, 3) == 1:
    {~I said all nine again this morning. I'm getting faster. I'm trying not to.|It's a good night. I'm not going out. I don't have to, and I'm finding out what that's like.}
- mood != "":
    {mood}
- check("hurt"):
    Don't fuss, it's already closing up. I'd take some food, though, if you've got any. Not bread.
- check("wet"):
    Everything I own is wet, including some things I didn't know could get wet.
- check("night"):
    You're up late. Don't walk the edge on your own at this hour, I mean it. Stay where the torches are.
- else:
    {~Oh, it's you. Sit down if you want, I'm only sharpening this.|Afternoon. Or morning, I lose track. I sleep in the day more than I should.|It's you. Good. I was about to go and count your torches again.}
}
- (talk)
* {who("farmer") != ""} [How are the people here?]
    {who("farmer")} leaves the door on the latch most nights. Too tired to check it, I think. I close it on my way past.
    Don't tell them. They'd only start locking it, and then lose the key, and then I'd be the one getting them in through a window.
    -> talk
* {who("farmer") == ""} [How are the people here?]
    Quieter than they think they are. They talk about me when I walk past, which is fine. I'd talk about me too.
    -> talk
* {founded} [How's the lodge?]
    It's a roof. I haven't had one I could call mine in a while, so I keep walking round it checking the corners. Force of habit.
    -> talk
* {wolf_seen and check("has_wolf")} [How's the wolf?]
    {check("hungry"):
        Keeping its distance lately. I think it's got more sense than I have.
    - else:
        Fat. Somebody keeps feeding it, and it isn't me, whatever it tells you.
    }
    -> talk
* {check("carries_book") and not done_book} [I have something of your mother's.]
    -> the_book
* {check("heard_birth") and not told_father} [She says your father was at your birth.]
    -> father_at_birth
* {named and done_counting and not done_count_talk} [What were you reciting, that night?]
    -> the_count
+ {check("player_sworn")} [Any work?]
    -> contracts ->
    -> talk
+ [Need anything?]
    {~Torches. Always torches. You can't have too many, whatever your builders tell you.|Arrows, if anyone's making them. I go through them faster than I'd like.|Sleep, mostly. You can't get me any of that, but thanks for asking.}
    -> talk
+ [What do you do when you're not hunting?]
    Sharpen things, mostly. Walk the edge of town. I tried fishing once, a whole afternoon, and I caught a boot and a saddle. I still don't know what a saddle was doing in a river. # emote:shrug
    -> talk
+ [Tell me about yourself.]
    Not now. I'm not being difficult, I'm just tired, and it isn't a short story.
    ++ [Some other time, then.]
        Some other time. Sure.
    ++ [Suit yourself.]
        Sorry. Ask me when I've slept.
    --
    -> talk
+ [I'll let you get on.]
    {check("night"):
        Goodnight. Stay in, if you can.
    - else:
        See you around.
    }
    -> DONE
