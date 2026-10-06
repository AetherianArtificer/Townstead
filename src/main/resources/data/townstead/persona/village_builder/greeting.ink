// What the Builder says when the player walks up (in place of MCA's greeting), and what they call
// the player over with when a new lesson is waiting. Same voice as the talk: hopeful, a little
// sorry for snooping, never bitter. Follows their mood toward the player like talk.ink does.

// A fresh 1 to 100 each time the conversation opens (set by the game).
VAR chance = 0

=== function greeting() ===
{met == 0:
    ~ return "Oh! Hello. Sorry, I didn't see you there."
}
{
- check("hurt"):
    ~ return "It's nothing. It'll mend. Hello!"
- check("wet"):
    ~ return "I keep forgetting to come in out of the rain. Hello!"
- check("night"):
    ~ return "You're up late too? I won't tell if you don't."
}
{demeanor():
- "stern":
    ~ return "Yes?"
- "guarded":
    {chance % 2 == 0:
        ~ return "Oh. Hello."
    }
    ~ return "Hi. Sorry, I'm a bit busy."
- "jovial":
    {chance % 2 == 0:
        ~ return "There you are! I was hoping you'd come by."
    }
    ~ return "Hello, hello. Good timing, I've run out of things to fix."
}
~ temp p = chance % 3
{
- p == 0:
    ~ return "Oh, hello! Give me a second, I was counting something."
- p == 1:
    ~ return "There you are."
}
~ return "Hello! Don't mind me, I'm just looking at your walls. Admiringly."

// "" for nothing. A hand-in has its own mark, and a talk left half-way waits to be picked up.
=== function calling() ===
{
- interrupted or quest_ready or quest_open:
    ~ return ""
- met == 0:
    ~ return "Hello! Sorry, could I borrow you for a moment?"
- met == 1 or (not done_all() and lesson_day != today):
    {demeanor() == "stern" or demeanor() == "guarded":
        ~ return "When you've got a moment. No rush."
    }
    ~ return "Have you got a minute? I've been thinking about something."
}
~ return ""
