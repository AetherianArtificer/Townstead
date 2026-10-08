VAR collar_asked = false
VAR done_collar = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function collar_ask_ready() ===
~ return named and not collar_asked

=== function collar_done_ready() ===
~ return collar_asked and not done_collar and check("carries_collar_things")

// Beat 5. A bell for its collar.
=== collar_ask ===
~ collar_asked = true
I want to put a bell on that wolf's collar. A proper one, with a leather strap to hang it from.
Bring me a bit of leather and a nugget of silver, if there's any about. Iron'll do if there isn't. Silver rings better.
+ [What's the bell for?]
    So I can hear it coming. It walks quieter than I do, and I don't like being the second-quietest thing in a room.
+ [I'll find some.]
    Thanks. No hurry. It's not like it's going anywhere. It never goes anywhere I'm not.
- -> DONE

=== collar_done ===
~ done_collar = true
~ act("take_collar_things")
That'll do nicely. # emote:nod
I'll file the bell down tonight, so it doesn't clang. Just enough to hear.
So I can hear it coming.
-> DONE
