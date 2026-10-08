// The mine Builder's first meeting in a new settlement. Object: a miner's lamp with a cracked
// glass. Objective: a hand with the lamp, then who's still working after dark. Voice: the driest
// of the six, fond understatement. Turn: only passing through, yet they start on rest days.

=== mine_meeting ===
= open
Hold this a moment? Mind the glass, it's cracked. I'm trying to get the wick to sit right.
+ [Hold the lamp steady.]
    ~ helped_first = true
    There. Thank you. It won't sit right with the crack, but that's close.
+ [Why not fix the glass?]
    ~ helped_first = true
    ~ teased_first = true
    I will. I've been meaning to for about four years.
+ [I can't stop right now, sorry.]
    ~ met = 1
    Go on, then.
    -> DONE
- ~ met = 2
->->

= resume
There you are. The wick's behaving now, more or less. Got a minute?
+ [Sure.]
    ~ met = 2
    ->->
+ [Not yet.]
    Fine by me. I'll be here.
    -> DONE

= walls
~ temp raised = building("raised")
{raised != "":
    You put up the {raised}, didn't you? I've been round it twice. Square! Properly square!
- else:
    You put all this up, didn't you? I've been round your walls twice. Square! Properly square!
}
I knocked on one, too, sorry. It rang solid all the way through. I've seen chapels that lean like they've had a long night.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. Good.
+ [You knocked on my walls?]
    ~ teased_first = true
    Only one. It sounded solid. That's all I wanted to know.
+ [What do you want?]
    A bed, a wall to work on, and nobody asking me to go underground. In that order.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
Who's still working here after dark? Tell me honestly. Somebody always is.
+ [Everyone works hard.]
    Everyone. Mm.
    Where I grew up, the late shift came up grey, and went back down the same night.
    Well! That's an easy one to fix, as these things go. One day in the week with no work in it. For everyone.
+ [Nobody, I hope.]
    Then let's make sure of it. One day a week with no work in it, for everyone.
+ [You, probably.]
    ~ teased_first = true
    Ha! Probably. Don't hold me to that part. But everyone else gets one, at least.
- ~ meeting_asked = true
-> rest ->
~ meeting_asked = false
-> goodbye

= goodbye
Right. I'll stay out of your way. If you hear knocking on your walls, it's only me.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay.
+ [See you around.]
    You will. I'm the one with the cracked lamp.
- -> DONE
