VAR done_mutter = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function mutter_ready() ===
~ return done_walk and not done_mutter and check("late_night") and check("wolf_close")

// Beat 4. At night, talking to it. They stop when they see the player.
=== mutter ===
~ done_mutter = true
...no, you're right, I should have waited. You're always right, that's the annoying thing about you.
Oh. It's you. # emote:shrug
I was telling it about the night. It listens better than most people. It doesn't interrupt and it never asks follow-up questions.
+ [What did it say?]
    That I should have waited. Weren't you listening?
+ [I'll leave you two alone.]
    Don't make it sound like that.
- -> DONE
