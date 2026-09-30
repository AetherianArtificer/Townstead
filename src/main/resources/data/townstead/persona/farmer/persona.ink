// The Farmer. Brilliant, cheerful, and strangely impatient for a farmer: always looking for the
// one crop that will make them rich overnight. The Builder took them in when they bet their farm
// on a miracle seed and lost everything; they owe the Builder more than the Builder will let them
// repay. Their arc ends in a year of plenty, and the realization that the fortune was the harvest
// that comes in every year. The world rolls whether they met the Builder on the road or grew up
// in the Builder's hometown.
//
// Voice: warm and quick, a little breathless when excited, funny about their own habits and never
// about the village. They see what others miss, love inventions they cannot build, and care about
// people even when an idea makes them forget everyone for a while.

VAR origin = ""
VAR villager_name = ""
VAR village_name = ""
VAR player_name = ""
VAR today = 0
VAR quest_ready = false
VAR quest_open = false
VAR interrupted = false

// 0: not met. 1: met, the player was busy. 2: met.
VAR met = 0
VAR done_plan = false
VAR done_harvest = false
VAR done_kinds = false
VAR done_home = false
VAR done_frost = false
VAR done_year = false

// Pacing, as with the Builder: the first talk each day earns a little trust, capped, and they
// raise at most one new thing a day.
VAR last_talk_day = -1
VAR daily_trust = 0
VAR quest_day = -1
VAR more_tomorrow = false

=== function builder() ===
~ temp name = persona_name("townstead:village_builder")
{name == "":
    ~ return "my friend"
}
~ return name

// The Builder's hometown, for the Farmer who grew up there too.
=== function builder_home() ===
~ return roll("townstead:village_builder", "hometown")

=== function menu() ===
{
- interrupted:
    ~ return "Where were we?"
- met == 0:
    ~ return "Hello there."
- met == 1:
    ~ return "Got a minute now?"
- quest_ready:
    ~ return "I think it's ready."
- quest_open:
    ~ return "How's it growing?"
- not done_year and quest_day != today:
    ~ return "Planted anything new?"
}
{demeanor():
- "stern": ~ return "Hello."
- "guarded": ~ return "Got a moment?"
}
~ temp pick = RANDOM(1, 3)
{pick:
- 1: ~ return "Is it ready yet?"
- 2: ~ return "What are you growing?"
}
~ return "How are the fields?"

=== function resumed() ===
{demeanor():
- "stern": ~ return "As I was saying."
- "guarded": ~ return "Right. Where was I."
}
~ temp pick = RANDOM(1, 3)
{pick:
- 1: ~ return "There you are! Sorry, where was I? Oh, yes."
- 2: ~ return "Oh, good, you're back. As I was saying..."
}
~ return "Right, right. As I was saying..."

=== function daily() ===
{today != last_talk_day:
    ~ last_talk_day = today
    {daily_trust < 8:
        ~ daily_trust = daily_trust + 1
        ~ trust(1)
    }
}

=== greet ===
# label: Talk
{
- met == 0:
    -> meeting
- met == 1:
    -> meeting.resume
}
~ daily()
-> next

// The next step of their story: the plan, the first harvest, the search for something new, then
// the fall and the long year. One new thing a day.
=== next ===
~ more_tomorrow = false
{
- quest_day == today and not done_year:
    ~ more_tomorrow = true
    -> talk
- not done_plan:
    -> plan_offer
- not done_harvest:
    -> harvest_offer
- not done_kinds:
    -> kinds_offer
- not done_home and check("hungry"):
    -> home_offer
- not done_frost and check("autumn") and done_kinds:
    -> frost_offer
- not done_year and done_kinds and (done_home or rel("trust") >= 30):
    -> year_offer
}
-> talk
