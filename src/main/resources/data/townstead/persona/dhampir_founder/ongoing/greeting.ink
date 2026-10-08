// What the founder says when the player walks up, in place of MCA's greeting. Short: the talk
// itself opens with its own line. The same habits run through every phase (the whetstone, the
// torches, the wolf, the dry asides) and only the warmth around them moves, so the change is
// gradual: guarded, warmer once they have given their name, shaken after the book, unsettled in
// Act 3, settled after Ending A, and after Ending B still themselves, with the warmth aimed wrong.
// High Hunger nudges the same lines toward the hunt. Layers, first match wins: something that just
// happened, then hurt, wet and night, then the phase's own pool.

// A fresh 1 to 100 each time the conversation opens (set by the game).
VAR chance = 0

=== function greeting() ===
{
- met == 0:
    ~ return greet_stranger()
- ending == "B":
    ~ return greet_fallen()
- ending == "A":
    ~ return greet_home()
- done_midpoint:
    ~ return greet_court()
- done_book:
    ~ return greet_shaken()
- named:
    ~ return greet_warm()
}
~ return greet_guarded()

// One of {count} lines, by the roll.
=== function pick(n) ===
~ return chance % n

// ---- before they have met ----

=== function greet_stranger() ===
{
- check("hurt"):
    ~ return "Don't mind this. It looks worse than it is."
- check("night"):
    ~ return "You shouldn't be out at this hour. Neither should I, but here we are."
}
{pick(2) == 0:
    ~ return "Do I know you?"
}
~ return "Can I help you with something?"

// ---- Act 1: guarded, dry ----

=== function greet_guarded() ===
{
- check("hurt"):
    ~ return "It's nothing. It's closing."
- check("wet"):
    ~ return "Don't ask."
- check("night"):
    ~ return "You're up late. Stay where the torches are."
- check("hungry"):
    {pick(2) == 0:
        ~ return "Oh. It's you. I'm only sharpening this. I'd rather be using it."
    }
    ~ return "You again. Make it quick, would you. I'm itching to be out."
}
~ temp p = pick(4)
{
- p == 0:
    ~ return "Oh. It's you. I'm only sharpening this, go ahead."
- p == 1:
    ~ return "You again. Go on, I'm listening."
- p == 2:
    ~ return "Oh, it's you. I was about to go and count your torches."
}
~ return "Need something? Ask. I'm never busy until dark."

// ---- after the name: warmer ----

=== function greet_warm() ===
{count("founder_kills") > debrief_kills:
    ~ return "Oh, it's you. Got another one. I'll tell you about it, if you ask nicely."
}
{
- check("hurt"):
    ~ return "It's nothing. Don't look at it like that, it's closing."
- check("wet"):
    ~ return "Don't ask. I'll dry. Eventually."
- check("night"):
    ~ return "You're up late. Me too. I counted your torches again, I can't help it."
- check("hungry"):
    {pick(2) == 0:
        ~ return "Oh, it's you. Sit, if you want. I'm sharpening this. I'd rather be using it."
    }
    ~ return "There you are. I've been sitting on my hands all evening, come and keep me company."
}
~ temp p = pick(5)
{
- p == 0:
    ~ return "Oh, it's you. Sit, if you want. I'm only sharpening this."
- p == 1:
    ~ return "There you are. I was about to come and find you."
- p == 2 and check("wolf_close"):
    ~ return "Oh, it's you. Sit. The wolf won't mind. I might, but I'll get over it."
- p == 3:
    ~ return "You look tired. Have you eaten? Properly, I mean, off a plate."
- p == 4 and romanced:
    ~ return "There you are. I kept a seat for you. Don't make it a thing."
}
~ return "Oh, it's you. Good."

// ---- Act 2, after the book: shaken ----

=== function greet_shaken() ===
{count("founder_kills") > debrief_kills:
    ~ return "It's you. Got another one. It helped. It always helps, for about an hour."
}
{
- check("hurt"):
    ~ return "It'll close. They always close. I used to find that a comfort."
- check("wet"):
    ~ return "Don't ask. I didn't notice I was getting wet, that's the kind of week it is."
- check("night"):
    ~ return "Up late. Me too. I keep reading the same page."
- check("hungry"):
    ~ return "It's you. Sit. I've sharpened this twice already. I keep wanting to go out and use it."
}
~ temp p = pick(4)
{
- p == 0:
    ~ return "It's you. Sit. I've sharpened this twice already, I keep forgetting I did it."
- p == 1:
    ~ return "Oh, it's you. Sorry. I was miles away."
- p == 2 and romanced:
    ~ return "There you are. Good. Sit close, would you. Just for a bit."
}
~ return "There you are. Talk to me about something ordinary, would you. Anything."

// ---- Act 3: unsettled ----

=== function greet_court() ===
{
- check("hurt"):
    ~ return "It'll close. Don't fuss. Fuss a little."
- check("wet"):
    ~ return "Don't ask. Sit anyway."
- check("night"):
    ~ return "Can't sleep either? Sit. It's easier with two."
- check("hungry"):
    ~ return "You. Good. I can't settle. I keep thinking about his door."
}
~ temp p = pick(4)
{
- p == 0:
    ~ return "You. Good. Sit with me a minute, I can't settle."
- p == 1:
    ~ return "Oh, it's you. I'm sharpening this. Out of habit, mostly."
- p == 2 and romanced:
    ~ return "There you are. Stay close today. Closer than that."
}
~ return "There you are. Don't go far today, would you."

// ---- after Ending A: settled ----

=== function greet_home() ===
{count("founder_kills") > debrief_kills:
    ~ return "Oh, it's you. Got one. Quick, too. I didn't drag it out."
}
{
- check("hurt"):
    ~ return "Got a piece of me. It'll close. I'll even wash it, you'll be proud."
- check("wet"):
    ~ return "Don't ask. I'm going to sit by the fire like a normal person."
- check("night"):
    ~ return "Up late? Me too. I'm not going out, though. Funny, that."
}
~ temp p = pick(5)
{
- p == 0:
    ~ return "Oh, it's you. Sit. I'm not even sharpening anything, look. Just sitting."
- p == 1 and check("wolf_close"):
    ~ return "There you are. Pull up a stool, the wolf's got the warm spot."
- p == 2:
    ~ return "Oh, it's you. Good. I was hoping you'd come by."
- p == 3 and romanced:
    ~ return "There you are. I was hoping. I'm allowed to say that now, apparently."
- p == 4:
    ~ return "Oh, it's you. I've stopped keeping track of the hours. It's nice. Don't tell anyone."
}
~ return "Oh, it's you. Sit."

// ---- after Ending B: still themselves, the warmth aimed wrong ----

=== function greet_fallen() ===
{
- check("hurt"):
    ~ return "It'll close. They always close now. Faster than they used to."
- check("wet"):
    ~ return "Don't ask. I don't feel the cold much any more."
- check("night"):
    ~ return "Up late. Good. I like the town at this hour. Everybody's where I can find them."
- b_answer == "rival":
    ~ return "Oh, it's you. You shouldn't be here. Sit anyway, I'm in a good mood."
- b_answer == "turned" or b_answer == "joined":
    {pick(2) == 0:
        ~ return "Oh, it's you. How's the thirst? It gets familiar. That's nearly the same as easier."
    }
}
~ temp p = pick(3)
{
- p == 0:
    ~ return "Oh, it's you. Sit. I'm sharpening this. I don't need to any more, I just like the sound."
- p == 1:
    ~ return "There you are. I wondered when you'd come by. I always know, roughly."
}
~ return "Oh, it's you. Good. Sit where I can see you."
