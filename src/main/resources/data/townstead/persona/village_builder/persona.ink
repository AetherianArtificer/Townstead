// The Village Builder. Grew up in a town that emptied when its work dried up; hopeful about it,
// not bitter. Each world rolls one hometown (persona.json "rolls"), which sets their job, their
// first worry and how they talk about home. Act 1 lives here: meeting.ink and quests.ink.
//
// Voice rules: the first line is about something the player did. They apologize for snooping.
// Home gets one light mention, then a deflection. Problems belong to the village, not the player.

VAR hometown = ""
VAR line = ""
VAR gift = ""
VAR villager_name = ""
VAR village_name = ""
VAR today = 0

// 0: not met. 1: introduced, the player left early. 2: met.
VAR met = 0
VAR stayed = false

VAR done_push = false
VAR done_rest = false
VAR done_beds = false
VAR done_pot = false
VAR done_work = false
VAR done_water = false
VAR done_carry = false

// Pacing. The first talk each day earns a little trust (capped), and they bring up at most one
// new lesson a day, so visiting them is a habit, not a queue.
VAR last_talk_day = -1
VAR daily_trust = 0
VAR lesson_day = -1
VAR more_tomorrow = false

=== greet ===
# label: Talk about the village
{
- met == 0:
    -> meeting
- met == 1:
    -> meeting.resume
}
-> checkin -> next

// The next Act 1 step: a discovered village's push first, then the first worry from home, then
// the other lessons in order.
=== next ===
~ more_tomorrow = false
{
- lesson_day == today:
    ~ more_tomorrow = true
    -> idle
- check("discovered") and not done_push:
    -> push_offer
- worry() == "rest" and not done_rest:
    -> rest_offer
- worry() == "pot" and not done_pot:
    -> pot_offer
- worry() == "work" and not done_work:
    -> work_offer
- worry() == "water" and not done_water:
    -> water_offer
- not done_beds:
    -> beds_offer
- not done_pot:
    -> pot_offer
- not done_work:
    -> work_offer
- not done_water and check("thirst_on"):
    -> water_offer
}
-> idle

=== function daily() ===
{today != last_talk_day:
    ~ last_talk_day = today
    {daily_trust < 8:
        ~ daily_trust = daily_trust + 1
        ~ trust(1)
    }
}

=== function done_all() ===
~ return (done_push or not check("discovered")) and done_beds and done_pot and done_work and (done_water or not check("thirst_on"))

// The first thing they look for, from what killed their town.
=== function worry() ===
{ hometown:
- "mine": ~ return "rest"
- "mill": ~ return "pot"
- "harbor": ~ return "beds"
- "roads": ~ return "work"
- "forest": ~ return "beds"
- "well": ~ return "water"
}
~ return "beds"

// Start of every later talk: the day's trust, then their collapse if it has happened.
=== checkin ===
~ daily()
{count("collapsed") > 0 and not carry: -> carry_offer}
->->

=== idle ===
{more_tomorrow and not done_all():
    {~There's another thing I want to show you. Tomorrow. One thing a day, or you'll stop listening to me.|I've got more for you, but it can wait for tomorrow. Some things should.}
    -> DONE
}
-> talk

