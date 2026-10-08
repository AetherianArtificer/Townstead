// Answers the player can ask for in either branch of the meeting.

=== meeting_name ===
{given_name}.
* [Just {given_name}?]
    It's enough to call me by. You don't need more than that.
* [Where are you from?]
    Here and there. I don't really have a somewhere I'm from. I just go where I'm needed. # emote:shrug
- ->->

=== meeting_kin ===
Tired, mostly. Hungry at the wrong times. Hard to kill.
I don't know much more than that, if I'm honest, and I've had longer to work it out than you have.
->->

=== meeting_hurt ===
{check("hurt"):
    -> hurt_some
}
Not by that one.
* [By what, then?]
    That's a longer story than I've got in me tonight.
* [Good.]
    Yeah. # emote:nod
- ->->

= hurt_some
A bit. It'll close up by morning, it always does. Don't waste a potion on it.
* [Let someone look at it.]
    ~ trust(1)
    In the morning, maybe. If it's still open, which it won't be.
* [Always?]
    Always. I heal fast. Don't ask me why, I'd rather not get into it tonight.
- ->->
, I'd rather not get into it tonight.
- ->->

