// Replies to gifts (persona.json "gifts"). The gift from home plays a memory the first time,
// one light mention and no more. Later gifts get a short thanks that follows their demeanor.

=== gift_from_home ===
{hometown:
- "mill":
    Oh. Bread.
    The mill town smelled like this every morning. You could find your way home with your eyes shut.
- "mine":
    Copper. Raw, even.
    My father brought a piece home in his pocket every payday. For luck. It didn't work, but I liked it.
- "harbor":
    Cod. Cooked right, too.
    Every house on the harbor cooked this on the last day of the week. The whole street smelled of it.
- "roads":
    A compass.
    The traders who came through carried these. I used to follow them to the edge of town to see which way they went.
- "forest":
    A sapling. Oak.
    There isn't a tree left where I grew up. I'm going to plant this somewhere nobody will cut it down.
- else:
    Water. Clean, too.
    We used to line up at the well with bottles like this. Then one summer there was nothing to line up for.
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
