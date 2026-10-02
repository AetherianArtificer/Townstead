// Act 2, scene 17: The fledgling. After scene 16, once the midwife is in town, someone newly
// turned walks in from the road on their own because they heard there was a lodge here (a visitor,
// role "fledgling"). They ask the lodge to end them before they hurt someone, in the founder's own
// words. For the first time before the midpoint, the founder hesitates. The choices: grant it
// (gently; it is care, and ambiguous for the balance), let them stay (a neighbor the founder must
// live beside), or look for a cure. The founder believes there is no way back; the midwife knows
// the way (Vampirism's cure, on the vampire state in place: weaken them, then a golden apple).
// Afterwards they recite the oath and stop at the gap: a "do not" article would have told them
// what to do.

VAR fledgling_called = false
VAR fledgling_name = ""
VAR done_fledgling = false
VAR fledgling_end = ""
VAR curing = false
VAR done_cure = false
VAR done_fledgling_after = false
VAR fledgling_day = -1

=== fledgling_arrival ===
~ fledgling_called = true
~ act("fledgling_arrives")
Somebody's coming in on their own, asking for the lodge. Asking for it by name.
Nobody asks for us by name. People find us, or we find them. # emote:ponder
-> DONE

=== fledgling_meet ===
~ done_fledgling = true
~ fledgling_day = today
~ fledgling_name = who("visitor:fledgling")
You're the hunters. I was bitten on the road, a long way back. I've been walking since. # who: visitor:fledgling
I heard what you say about them. That they all hurt somebody eventually. # who: visitor:fledgling
I don't want to be eventually. Will you do it? Before I do something. # who: visitor:fledgling
... # emote:ponder
Give me a minute.
I've said yes to this a hundred times. In my head, on the road, I've had the whole speech ready. I can't find it.
- (choose)
* [Do what they ask.]
    -> grant
* [They haven't hurt anyone. Let them stay.]
    -> stay
* [There might be a way back.]
    -> cure

= grant
~ fledgling_end = "granted"
Tonight, then. Not here. Out past the houses, where it's quiet. # emote:nod
Thank you. # who: visitor:fledgling
Don't thank me. Please don't.
~ act("fledgling_leave")
-> DONE

= stay
~ fledgling_end = "stayed"
~ act("oath_small")
~ act("fledgling_stay")
Then I'll be living next door to one I could have stopped. # emote:shrug
...All right. If you hurt anybody, I'll know before you do. That isn't a threat. It's the only kindness I've got.
-> DONE

= cure
There isn't one. There's no way back from it, everybody knows that.
* [Someone who's sat with a lot of sick people might know.]
    ...Her.
    Ask her, then. And if she says there's nothing, you're the one who tells them. Not me. I couldn't.
    ~ curing = true
    ~ fledgling_end = "curing"
    ~ act("asked_cure")
    ~ act("fledgling_stay")
    You can stay while we ask. That's all I'm promising.
    -> DONE
* [You're right. There isn't.]
    -> choose

// They are cured: there was a way back.
=== cure_after ===
~ done_cure = true
~ act("oath_small")
~ act("oath_small")
~ trust(2)
They're not one any more. I checked. I checked three times, and I'd check a fourth if they'd let me.
There was a way back. All this time, there was a way back, and she knew.
* [What does that change?]
    I don't know yet. Everything I've put down, maybe. Some of them.
    No. Not all of them. Some of them were never coming back from anything. But some of them.
* [You couldn't have known.]
    I could have asked. I never asked anybody anything. I just went.
- -> DONE

// A night or two later, at the recitation (only while the oath is still broken).
=== fledgling_after ===
~ done_fledgling_after = true
-> recite ->
That's where it stops. Every time, right about there.
I think there was one after that, telling you what not to do. I could have done with it this week.
-> DONE
