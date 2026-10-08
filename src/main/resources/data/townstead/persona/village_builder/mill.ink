// The mill Builder's first meeting in a new settlement. Object: a jar of sourdough starter from
// home, kept alive for years. Objective: a pinch of flour, then who feeds the hungry here. Turn:
// only passing through, yet they start on the village's supper.

=== mill_meeting ===
= open
Oh, good, a person! You wouldn't have a pinch of flour on you? No? That's all right, he's not fussy.
This jar. He's a starter, for bread. He needs feeding twice a day, and I've been rationing him since the river road.
+ [Hold the jar steady for them.]
    ~ helped_first = true
    Thank you! There we go, a pinch from my own bag after all, and a splash of water... look at him bubble. He likes you.
+ [You named your bread?]
    ~ helped_first = true
    ~ teased_first = true
    He's not bread yet! He's the bit before bread. And yes. You would too, if you'd carried him this far.
+ [I can't stop right now, sorry.]
    ~ met = 1
    No, no, go on! He'll keep.
    -> DONE
- ~ met = 2
->->

= resume
There you are! He's fed, by the way. Grumpy, but fed. Have you got a minute now?
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
Somebody took their time with your walls. You can tell.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. Good. That's a name you could put on a bakery sign.
+ [Are you always this cheerful?]
    ~ teased_first = true
    Only before breakfast. After breakfast I'm worse.
+ [What do you want?]
    Honestly? An oven, eventually. For now, somewhere to set this jar down where it won't get kicked.
- -> passing

= passing
I'm only passing through, mind. A season, maybe.
Where does a hungry person eat, here? Say someone came in off the road with nothing in their bag. Where would they go?
+ [Nowhere yet.]
    Nowhere. Mm.
    Back home, you could smell the ovens from the ferry. Nobody came in hungry and left that way.
    Well! That's easy to fix. Food's the easy part. People just have to be able to reach it.
+ [They'd eat with us.]
    Would they? Good. Then let's have enough in the pot that nobody has to ask.
+ [Is this about the jar again?]
    ~ teased_first = true
    It is always a little bit about the jar. But it's mostly about supper.
- {check("hungry"):
    People here look a bit hungry, if I'm honest. I peeked in a chest. Sorry. Old habit.
- else:
    Everyone looks fed, which is lovely. Let's keep it that way on purpose, not by luck.
}
~ meeting_asked = true
-> pot ->
~ meeting_asked = false
-> goodbye

= goodbye
Right! I'll stay out of your way. Mostly. If something smells good, that's me.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay. And then there's bread.
+ [See you around.]
    You will! I'm the one with the jar.
- -> DONE
