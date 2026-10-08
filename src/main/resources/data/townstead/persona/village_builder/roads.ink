// The roads Builder's first meeting in a new settlement. Object: a folded map with one road inked
// out. Objective: getting your village onto their map, then who here has nothing to do. Voice:
// talkative, travel sayings. Turn: only passing through, yet they start finding everyone work.

=== roads_meeting ===
= open
Hold on, hold on, don't move, I've nearly got you. ...There! Sorry. I'm putting you on my map.
Well, not you. Your village. You're more of a dot.
+ [Hold the corner of the map down.]
    ~ helped_first = true
    Thank you! It keeps trying to roll itself up. Right... there. You're official.
+ [Can I be a bigger dot?]
    ~ helped_first = true
    ~ teased_first = true
    Ha! Build a few more roofs and I'll give you a circle. That's a promise.
+ [I can't stop right now, sorry.]
    ~ met = 1
    Go on! I'll draw you from memory. I'm very good at memory.
    -> DONE
- ~ met = 2
->->

= resume
There you are! You're on the map now, by the way. Small, but on it. Got a minute?
+ [Sure.]
    ~ met = 2
    ->->
+ [Not yet.]
    That's all right. I'll be here.
    -> DONE

= walls
~ temp raised = building("raised")
{raised != "":
    You put up the {raised}, didn't you? I've been round it twice. Square! Properly square!
- else:
    You put all this up, didn't you? I've been round your walls twice. Square! Properly square!
}
I've seen a hundred villages from the road, and I could count the square ones on one hand.
{village_name != "":
    I'm {villager_name}. And this is {village_name}? Good. How many letters? I don't want to spell it wrong on the map.
- else:
    I'm {villager_name}. Has this place got a name yet? I'll leave a gap on the map for it.
}
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. I'll write that down too. Small, in the corner. Mapmaker's privilege.
+ [What's the road that's inked out?]
    Oh, that one. It doesn't go anywhere anymore. I keep meaning to redraw it.
+ [What do you want?]
    A bed, a table I can spread the map on, and to hear about every road you know. Mostly the bed.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
Who here's got nothing to do all day? There's always someone. Standing about, looking at their hands.
+ [Nobody, I think.]
    {count("idle_adults") > 0:
        Hm. I counted a few on the way in. Sorry. You get in the habit, watching crossroads.
    - else:
        Then you're doing well! Let's keep it that way.
    }
+ [There might be a few.]
    Where I grew up, the market closed when the roads went bad, and after that everyone stood about. Nobody knew what to do with their hands.
    Well! That's easy to fix, as these things go. A workstation each, and something worth doing at it.
+ [You, soon.]
    ~ teased_first = true
    Ha! Not a chance. I've got a map to finish. But everyone else should have somewhere to be in the morning.
- ~ meeting_asked = true
-> work ->
~ meeting_asked = false
-> goodbye

= goodbye
Right! I'll stay out of your way. Mostly. If you see someone walking your edges counting steps, it's me.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay. And then I draw you a proper circle.
+ [See you around.]
    You will! I'm the one with the map.
- -> DONE
