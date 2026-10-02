// The harbor Builder's first meeting in a new settlement: the reference scene for the voice sheet.
// Objective: a hand with the net, then somewhere to sleep. Turn: they say they're only passing
// through, then ask where the next family would sleep, and end up making room in a place they
// claim they're leaving. Remembered: helped_first, teased_first.


=== harbor_meeting ===
= open
Oh! Could you hold this end? Just there. Thank you. It's been slipping since the coast road, and I've been fighting it the whole way.
+ [Take the end of the net.]
    ~ helped_first = true
    Perfect. Now pull, gently... there! That'll hold for a day or two.
+ [Is that a fishing net?]
    ~ helped_first = true
    ~ teased_first = true
    It's a net, it's for fish, and right now it won't stay knotted. Pull that end, would you? Gently.
    There! Look at that. We make a good crew.
+ [I can't stop right now, sorry.]
    ~ met = 1
    No, no, go on! I'll manage.
    -> DONE
- ~ met = 2
->->

= resume
There you are! I got the knot to hold, by the way. Have you got a minute now?
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
Do you know how rare that is? I've seen town halls you could roll a marble off.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}! Good. Now I know who to thank for the walls.
+ [You measured my walls?]
    ~ teased_first = true
    Only with my eyes! And my thumb. And a bit of string.
+ [What do you want?]
    Honestly? Somewhere to sleep tonight. And I wanted to meet whoever built all this.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
Where does the next family sleep, here? Say someone came up the road tomorrow with a cart and three children. Where would they go?
+ [Nowhere yet.]
    Nowhere. Mm.
    Back home, we'd have had a bed made up before the boat was even tied off.
    Well! That's easy to fix. Easier than nets, anyway.
+ [We'd find room.]
    Would you? Good. Then let's find it before they need it. It's a lot nicer to be expected.
+ [Why, have you got three children?]
    ~ teased_first = true
    Ha! No. Just the net. The net's plenty.
    But someone will come. Someone always does, to a place with walls this straight.
- {check("no_spare_beds"):
    Every bed here's spoken for, as far as I can see. So: a few more. One for the next family, and one left over.
- else:
    There's a bed or two to spare already. Keep one like that, always. Someone turns up, and it's there.
}
~ meeting_asked = true
-> beds ->
~ meeting_asked = false
-> goodbye

= goodbye
Right! I'll stay out of your way. Mostly. If you see me staring at a roof, I'm not up to anything.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay.
+ [See you around.]
    You will! I'm hard to miss. I'm the one with the net.
- -> DONE
