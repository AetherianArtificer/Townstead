VAR name_asked = false
VAR done_wolf_name = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function name_ask_ready() ===
~ return done_scraps and not name_asked

=== function name_given_ready() ===
~ return name_asked and not done_wolf_name and check("wolf_named")

// Beat 3. They will not name it.
=== name_ask ===
~ name_asked = true
People keep asking me what it's called. I tell them it's called "the wolf". They look at me like I've kicked it.
I'm not naming it. If I name it, then it's mine, and I've told everybody it isn't.
+ [Somebody should.]
    Somebody can. Not me.
+ [Fair enough.]
    Thank you. Finally, somebody sensible.
- -> DONE

=== name_given ===
~ done_wolf_name = true
You put a name on it. # emote:ponder
It isn't yours to name either, you know.
...It answers to it, though. I tried it when nobody was about.
+ [Do you like it?]
    It's fine. It's a name. Don't look so pleased.
+ [It suits it.]
    ~ trust(1)
    It does a bit. I hate that it does.
- -> DONE
