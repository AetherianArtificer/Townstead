// The forest Builder's first meeting in a new settlement. Object: a whittling knife and a
// half-carved figure. Objective: an opinion on the carving, then whether there's a roof waiting
// for the next person. Voice: warm and unhurried, wood words. Turn: only passing through, yet
// they start making room.

=== forest_meeting ===
= open
Oh, hello! Don't mind the shavings, they're only shavings. Here, what do you think? Be honest.
It's going to be a heron. It's a heron from the neck up, anyway. The rest is still deciding.
+ [It's lovely.]
    ~ helped_first = true
    You're very kind, and very wrong, and I like you already. Give it a week.
+ [Is that a duck?]
    ~ helped_first = true
    ~ teased_first = true
    A duck! It's a heron! Look at the neck! ...All right, it's a bit of a duck. The grain's against me.
+ [I can't stop right now, sorry.]
    ~ met = 1
    Go on! The heron will wait. Herons are good at waiting.
    -> DONE
- ~ met = 2
->->

= resume
There you are! The heron's got a leg now. One. Got a minute?
+ [Sure.]
    ~ met = 2
    ->->
+ [Not yet.]
    That's all right. The heron and I will be here.
    -> DONE

= walls
~ temp raised = building("raised")
{raised != "":
    You put up the {raised}, didn't you? I've been round it twice. Square! Properly square!
- else:
    You put all this up, didn't you? I've been round your walls twice. Square! Properly square!
}
Somebody here knows how timber wants to sit. You can't fake that.
I'm {villager_name}.
+ [Welcome. I'm {player_name}.]
    ~ contribute("affection", 1, "welcomed")
    {player_name}. Good. That's a name with a bit of grain to it.
+ [Do you carve a lot?]
    Whenever my hands are empty. Which is too often, lately. That's partly why I'm here.
+ [What do you want?]
    A bed under a roof that isn't mine to worry about, for once. And somewhere to put the shavings.
- -> passing

= passing
I'm only passing through, mind. A season, maybe. Then I'll see which way the wind is.
When somebody new comes, is there a roof waiting for them? A bed that's theirs, from the first night?
+ [Not yet.]
    Not yet. Mm.
    Where I grew up, at the end, they took the houses apart for the timber. Board by board. You could smell the sawdust a mile off.
    Well! That's the easy part, building. Let's make a bit more room than you need.
+ [There'd be room.]
    Would there? Good. Then let's make sure there's a bed standing empty, waiting, before anyone asks.
+ [For a duck-heron?]
    ~ teased_first = true
    Ha! For the heron, a shelf. For people, a bed. A few more than you need.
- {check("no_spare_beds"):
    Every bed here's spoken for, as far as I can see. So: a few more, and one left over.
- else:
    There's a bed or two spare already. Lovely. Keep one like that, always.
}
~ meeting_asked = true
-> beds ->
~ meeting_asked = false
-> goodbye

= goodbye
Right! I'll stay out of your way. Mostly. If you find shavings, I was there.
+ [Stay as long as you like.]
    ~ stayed = true
    ~ contribute("affection", 2, "welcomed")
    ~ trust(2)
    Careful. People say that, and then I stay. And then you get a heron.
+ [See you around.]
    You will! I'm the one with the duck. Heron. Heron.
- -> DONE
