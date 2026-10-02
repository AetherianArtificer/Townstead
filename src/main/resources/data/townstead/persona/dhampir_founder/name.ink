// Act 1, scene 12: The name. A quiet evening once the player has walked or held the watch with
// them, been through the neighbor or the bitten, and earned enough trust. The player asks the old
// question, and this time "some night" is tonight: they give their family name. It was their
// mother's; this is the first time they mention her. They will not talk about their father.
// Romance can open after this ("named").

VAR named = false

=== the_name ===
Sit down, if you want. I'm not doing anything. I'm practicing not doing anything, I'm told it's good for you.
- (sit)
+ [Tell me about yourself.]
    -> tell
+ [How's the practice going?]
    Badly. My hands keep looking for something to sharpen.
    -> sit
+ [I'll let you get on.]
    Go on, then. Another night.
    -> DONE

= tell
...All right. # emote:ponder
You've asked that enough times. I said some night. This is some night, I suppose.
{given_name} {family_name}. That's the whole of it.
~ named = true
~ act("tell_family")
~ trust(3)
It was my mother's name. I use it because she'd have been furious if I didn't, and because nobody ever asks about it, and I'd like to keep it that way.
You're the exception. Don't let it go to your head.
- (after)
* [What was she like?]
    Neat. She was very neat. That's all you're getting tonight.
    ...She'd have liked you, I think. She liked people who ask twice.
    -> after
* [And your father?]
    No. # emote:shake_head
    Ask me about her. Don't ask me about him.
    -> after
* [Thank you for telling me.]
    Don't make it a thing.
    ...It's a bit of a thing. Thank you for asking.
    -> after
+ [Goodnight, {given_name}.]
    {check("night"):
        Goodnight.
    - else:
        See you later, then.
    }
    -> DONE
