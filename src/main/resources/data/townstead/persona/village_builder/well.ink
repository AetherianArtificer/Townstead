// The dry-well Builder's first meeting in a new settlement. Object: a stoppered bottle of water
// from the last good day at the well, never drunk, and a flask they ration. Voice: sparing words,
// warm under them. Objective: a drink shared, then how far water is from the nearest door. Turn:
// only passing through, yet they start on the village's water.

=== well_meeting ===
= open
Here. Have a sip. Just the one, mind. It's a long way between wells, where I've come from.
+ [Take a sip.]
    ~ helped_first = true
    There. Now we've shared water. Where I grew up, that's as good as a handshake.
+ [What's in the other bottle?]
    ~ helped_first = true
    That one's not for drinking. It's just for carrying. Here, the flask. Go on.
+ [I can't stop right now, sorry.]
    ~ met = 1
    Go on. The water will keep. Water's patient. People less so.
    -> DONE
- ~ met = 2
->->

= resume
There you are. I saved you a sip. Got a minute now?
+ [Sure.]
    ~ met = 2
    ->->
+ [Not yet.]
    Fine. I'll be here, in the shade if there's any.
    -> DONE

= walls
~ temp raised = building("raised")
{raised != "":
    You put up the {raised}, didn't you? I've been round it twice. Square! Properly square!
- else:
    You put all this up, didn't you? I've been round your walls twice. Square! Properly square!
}
I don't waste words. That one I meant.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. Good. Easy to say with a dry mouth. That matters, where I'm from.
+ [You don't talk much, do you?]
    ~ teased_first = true
    Only when it's worth the water. You're worth a bit.
+ [What do you want?]
    A bed. Some shade. And to know where you get your water.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
How far is water from your nearest door? In steps. Guess.
+ [Not far.]
    Good. Closer is better. It's always better.
+ [I've never counted.]
    Never counted. Mm.
    We measured water in cups, back home. Then in spoonfuls. Then we stopped measuring.
    Well! Water's the easy part, if you plan for it. Put it where people live.
+ [Are you going to count?]
    ~ teased_first = true
    Already did, on the way in. I'll tell you if it gets better.
- ~ meeting_asked = true
-> water ->
~ meeting_asked = false
-> goodbye

= goodbye
Right. I'll stay out of your way. If you see someone pacing about, counting steps, that's me.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay.
+ [See you around.]
    You will. I'm the one with two bottles.
- -> DONE
