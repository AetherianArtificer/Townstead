// Act 1, scene 11: Counting. At night, after the thrall, the player catches the founder reciting
// their mother's oath under their breath, article by article. They stop where their memory fails,
// and where they stop follows the pull of the blood: six articles near their oath, down to two if
// they have fallen. The lost articles are the ones that say "do not". They say nothing about it.
// After this, some nights open with the same recitation. Scene 13 (Act 2) is the player asking.
//
// The nine articles, for reference: 1 Come when you are called, whatever the hour. 2 Wash your
// hands before, and after. 3 Speak plainly to the frightened. 4 Carry the lamp yourself. 5 Keep
// what you are told. 6 Turn no one away. 7 Do no harm that you can help. 8 Take nothing from the
// dying. 9 Do not leave before it is finished.

VAR done_counting = false

=== counting ===
~ done_counting = true
-> recite ->
# emote:ponder
Oh. It's you.
Did you want something?
-> dusk.talk

// The recitation, as far as they get tonight.
=== recite ===
First. Come when you are called, whatever the hour.
Second. Wash your hands before, and after.
{check("pull_fallen"):
    Third...
    ->->
}
Third. Speak plainly to the frightened.
{check("pull_predator"):
    Fourth...
    ->->
}
Fourth. Carry the lamp yourself.
{check("pull_hungry"):
    Fifth...
    ->->
}
Fifth. Keep what you are told.
{check("pull_steady"):
    Sixth...
    ->->
}
Sixth. Turn no one away.
Seventh...
->->
