// Act 3, The Court, part 3: the meeting. The father and the quiet one are brought up beside the
// player (roles "father" and "father_thrall"), and the scene is four-way. The father knows, and is
// not sorry: love, to him, is keeping. The pivot is what the founder does to the quiet one. The
// player's push and the balance built so far decide it together; when the wolf line is complete,
// a founder about to fall hesitates once more.
//
// Ending A: they forgive her, the player decides the father's fate (end him with a relic, or spare
// him for his word), and they go home and keep their mother's oath. Ending B: they take his place
// and keep her, as he did; they offer the player a place beside them (turning them, when romanced),
// an accord, or the other side of the door.

VAR done_court = false
VAR ending = ""
VAR father_fate = ""
VAR b_answer = ""

// When this scene plays. The greet in persona.ink checks these in order.
=== function court_ready() ===
~ return court_arrived and not done_court and here("visitor:father")

// The father, the quiet one, the founder and the player.
=== court ===
~ done_court = true
You have her chin. I always wondered which parts of you would be hers. # who: visitor:father
Don't.
You came about the wine. Somebody told you who pours it. # who: visitor:father
She's here. She is always here. # who: visitor:father
I brought her tea. Every afternoon for a year, I brought her tea, and she thanked me every time. # who: visitor:father_thrall
She was kind to me. I couldn't bear it. # who: visitor:father_thrall
You knew.
The next morning. # who: visitor:father
And you kept her.
She is mine. You do not throw a thing away because it has a crack in it. # who: visitor:father
{done_wounded:
    You did not throw away your wolf when my people put it on the ground. You sat up with it all night. You see? You understand me better than you would like. # who: visitor:father
}
Your mother never understood keeping. She gave everything away. The door, the tea, the bread. Herself. # who: visitor:father
-> court_pivot
