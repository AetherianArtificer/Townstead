// Act 2, romance. Optional, after the name (scene 12), gated on hearts with the player. Three beats,
// a few days apart: "Not tonight" becomes "Stay." Turning one down ends it gently for now; the beat
// comes back after a few days. At the predator tier and past it, their lines turn possessive (veiled).
// "romanced" is read by the court (Ending B offers to turn the player).

VAR romance = 0
VAR romance_day = -10
VAR romanced = false

=== function romance_ready() ===
{named and not romanced and today >= romance_day + 3:
    {
    - romance == 0 and check("hearts_close") and check("evening"):
        ~ return true
    - romance == 1 and check("hearts_dear") and check("night"):
        ~ return true
    - romance == 2 and check("hearts_love") and check("evening"):
        ~ return true
    }
}
~ return false

=== romance_beat ===
~ romance_day = today
{
- romance == 0:
    -> first
- romance == 1:
    -> second
}
-> third

// "Not tonight", for the last time.
= first
You keep finding me at this hour. I've noticed. I'm not complaining. I'm just noticing.
+ [I like your company.]
    ~ romance = 1
    ~ contribute("attraction", 3, "romance")
    ...All right. # emote:ponder
    Nobody's said that to me in a long time. Don't wait for me to say it back. I'm slow at it. I'm slow at most things that aren't killing.
+ [I'm checking on the lodge.]
    Sure. The lodge is fine. It's a roof. It's very good at being a roof.
- -> DONE

= second
Sit with me a while. Not for anything. Just sit.
I usually say not tonight. To everything. Not tonight, some night, ask me later.
+ [What would you say tonight?]
    ~ romance = 2
    ~ contribute("attraction", 3, "romance")
    {predator_or_fallen():
        I'd say stay where I can see you. I like knowing where you are. I like it a lot.
    - else:
        I'd say stay a bit longer than you meant to. That's all. That's a lot, for me.
    }
+ [I should get some sleep.]
    Go on, then. Some night.
- -> DONE

= third
I keep thinking about her door. My mother's. She left it open, and look what came in.
I've kept every door I've got shut since. It's the one thing I've been good at.
Stay.
+ [I'll stay.]
    ~ romance = 3
    ~ romanced = true
    ~ contribute("attraction", 5, "romance")
    ~ trust(3)
    {predator_or_fallen():
        Good. Then you're mine to keep safe. Nobody else gets to. # emote:nod
    - else:
        Good. # emote:nod
        ...I don't know what happens now. I've never got this far. You'll have to show me.
    }
+ [I can't.]
    ~ romance = 2
    All right. # emote:shrug
    No, that's all right. I asked. That's further than I've ever got. Let's leave it there, for now.
- -> DONE
