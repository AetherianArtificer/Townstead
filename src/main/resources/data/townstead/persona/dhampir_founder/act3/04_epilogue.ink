// After the court: home again, as Ending A or Ending B left them.

VAR done_epilogue = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function epilogue_ready() ===
~ return done_court and not done_epilogue and not court_walking and not check("at_court")

// Home again.
=== epilogue ===
~ done_epilogue = true
{ending == "A":
    -> humanity
}
-> insanity

= humanity
I said all nine this morning. At the altar, out loud, nobody there to hear it.
-> recite ->
I still like it. The hunt. I'm not going to stand here and tell you I don't.
I just don't need it to feel good any more, to do it. Do no harm that you can help. I finally know what the "can" is for.
+ [Welcome home.]
    ~ trust(3)
    Thanks. # emote:nod
    It is, isn't it. Home. I didn't think I'd get one.
- -> DONE

= insanity
{b_answer == "rival":
    You came back. Brave. Or you forgot what I told you. # emote:ponder
    Say what you came to say and then go, while I'm still in a mood to let you.
    -> DONE
}
The oath's going to change. They'll swear to me. It's simpler that way, nobody has to remember nine of anything.
{b_answer == "accord":
    Your side of the fence, and mine. I'm keeping to it. You'd be surprised how good I am at keeping things now.
}
{b_answer == "turned" or b_answer == "joined":
    How's the hunger? It gets easier. That's a lie, but it gets familiar, which is nearly the same.
}
The wolf won't come in any more. It sleeps outside again. I don't mind. # emote:shrug
...I don't mind.
-> DONE
