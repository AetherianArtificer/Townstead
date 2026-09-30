// Replies to gifts (persona.json "gifts"). The gift from home plays a memory the first time,
// one light mention and no more. Later gifts get a short thanks that follows their demeanor.

=== gift_from_home ===
{hometown:
- "mill":
    Bread! Oh, and a good one. Look at that crust.
    Back home, the whole street smelled like this before sunrise. You could find your way to the mill with your eyes shut.
    I'm going to give the starter a crumb. Don't tell him it isn't mine.
- "mine":
    Copper! Raw, even.
    My father brought a piece home in his pocket every payday. For luck. It never worked, but I loved it.
    It's going next to the lamp. They'll get on.
- "harbor":
    Cod! Cooked right, too.
    Every house on the harbor cooked this on the last day of the week. The whole street smelled of it.
    I'm going to eat this very slowly, and nobody's allowed to talk to me while I do.
- "roads":
    A compass! Oh, and it doesn't even wobble.
    The traders who came through used to carry these. I followed them to the edge of town, just to see which way they'd go.
    Now I'll always know which way home is. Well. Which way here is.
- "forest":
    A sapling! An oak, even.
    There isn't a tree left where I grew up. Not one.
    I'm going to plant it somewhere nobody will ever cut it down. You'll help me pick the spot?
- else:
    Water. Clean water.
    We used to line up at the well with bottles like this. Then one summer there was nothing to line up for.
    I'm going to drink this one. The other bottle, I'll keep carrying.
}
Thank you. I mean it.
-> DONE

=== gift_loved ===
{demeanor():
- "stern": ...Thank you.
- "guarded": Oh. Thanks. That's kind.
- "jovial": {~You remembered! You keep remembering.|Again? You're going to spoil me. Keep going.|This is the best part of my day. Don't tell anyone.}
- else: {~You remembered. Thank you.|That's the one. Thanks.|I'll keep this one somewhere safe.}
}
-> DONE

=== gift_liked ===
{demeanor():
- "stern": I'll find a use for it.
- "guarded": Thanks. I can use this.
- else: {~Oh, I can use this. Thank you.|Good. I know exactly where this goes.|You know me too well already.}
}
-> DONE

=== gift_disliked ===
{demeanor():
- "jovial": I'm going to pretend you didn't just hand me that. Here. Have it back.
- "warm": That's... no. Thank you, but no. Here.
- else: No. Take it back, please.
}
-> DONE
