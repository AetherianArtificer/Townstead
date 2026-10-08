// Act 2, M4: The book of oaths, into the midpoint. The midwife has kept the mother's book for
// twenty years and gives it to the player (her visit 2). The player brings it to the founder
// ("carries_book"), who takes it and reads: all nine articles in her words, and her last pages,
// about "the quiet one" who brought his wine, and a father who doubled the men at the gate and
// sat up at night. The oath is whole again: the recitation runs to the end, and the ritual is
// sworn in her wording from now on ("oath_restored"). The truth comes in her own words.

VAR done_book = false

=== the_book ===
~ done_book = true
~ book_day = today
~ act("take_book")
~ act("restore_oath")
That's her hand. # emote:ponder
Where did you... No. I know where. Of course she had it.
Give me a minute.
First. Come when you are called, whatever the hour. Second. Wash your hands before, and after. Third. Speak plainly to the frightened. Fourth. Carry the lamp yourself. Fifth. Keep what you are told.
Sixth. Turn no one away. Seventh. Do no harm that you can help. Eighth. Take nothing from the dying. Ninth. Do not leave before it is finished.
...Nine. There were nine.
- (read)
* [There's more, at the back.]
    -> pages
* [Are you all right?]
    No. Yes. Ask me in an hour.
    -> read
+ [I'll leave you with it.]
    Don't. Not yet. There's more, at the back. I can see her writing going smaller. Stay while I read it.
    -> pages

= pages
"The quiet one brought his wine again tonight and stood behind my chair until I moved it. She does not like me. I do not think she likes anyone he looks at."
"He says I am safe while he is in the house. He has doubled the men at the gate. He sits up when he thinks I am asleep."
"If this book comes to anyone but me, give it to my child. Tell them the fourth one is the hardest. Carry the lamp yourself."
"Raining. He is away until the new moon. The quiet one has been kind to me all day, which frightens me more than anything she has done."
That's the last one.
She wrote it like she was writing about the weather.
- (after)
* [Your father tried to protect her.]
    Don't.
    ...He doubled the gate. He sat up. She wrote that, and she never wrote anything down that wasn't true.
    Then where was he. # emote:shake_head
    -> after
* [Who is the quiet one?]
    I don't know. Somebody who brought him wine.
    Somebody who might still be bringing him wine.
    -> after
* [Carry the lamp yourself.]
    That's the one I said. At the altar, out of nowhere, in front of everyone.
    She told me which one was the hardest, and I said it by accident.
    -> after
+ [What will you do?]
    I don't know yet. That's new. I've always known.
    I need to be on my own for a while. Take the wolf for a walk, would you. It likes you.
    -> DONE
