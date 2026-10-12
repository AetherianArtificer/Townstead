// Act 3, The Court, part 3: the meeting. The father and the quiet one are brought up beside the
// player (roles "father" and "father_thrall"), and the scene is four-way. The father knows, and is
// not sorry: love, to him, is keeping. The player takes part: the wine, then one say before the
// founder asks him. Those choices move court_lean, which the pivot adds to its lean. The pivot is
// what the founder does to the quiet one. The player's push and the balance built so far decide it
// together; when the wolf line is complete, a founder about to fall hesitates once more.
//
// Ending A: they forgive her, the player decides the father's fate (end him with a relic, or spare
// him for his word), and they go home and keep their mother's oath. Ending B: they take his place
// and keep her, as he did; they offer the player a place beside them (turning them, when romanced),
// an accord, or the other side of the door.

VAR done_court = false
VAR ending = ""
VAR father_fate = ""
VAR b_answer = ""
VAR court_wine = ""
VAR court_lean = 0

// When this scene plays. The greet in persona.ink checks these in order.
=== function court_ready() ===
~ return court_arrived and not done_court and here("visitor:father")

// The father, the quiet one, the founder and the player.
=== court ===
~ done_court = true
You have her chin. I always wondered which parts of you would be hers. # who: visitor:father
Don't.
{check("hurt"):
    You are hurt. That will not do. See to it, would you? # who: visitor:father
- else:
    And you must be the one who keeps them fed. Thank you. They were always too thin. # who: visitor:father
}
You came all that way. Your mother would have come too, and complained about her shoes the whole time. # who: visitor:father
Wine? # who: visitor:father_thrall
* [Take it.]
    ~ court_wine = "taken"
    ~ court_lean = court_lean + 1
    Good. I like a guest who takes what is offered. I knew a woman who held a cup all evening to be polite and never touched it. # who: visitor:father
* [No, thank you.]
    ~ court_wine = "refused"
    Hold it for them. There is no hurry. # who: visitor:father
- You came about the wine. Somebody told you who pours it. # who: visitor:father
She's here. She is always here. # who: visitor:father
I brought her tea. Every afternoon for a year, I brought her tea, and she thanked me every time. # who: visitor:father_thrall
She was kind to me. I couldn't bear it. # who: visitor:father_thrall
* [You knew. All this time.]
    Of course. I knew the next morning, the way you know it has rained. Would you like me to pretend I did not? I can, if it helps. # who: visitor:father
    -> kept
* {done_book} [Say her words to him. "Turn no one away."]
    ~ court_lean = court_lean - 1
    You let them read her book. # who: visitor:father
    She had lovely handwriting. Very small. She hated wasting paper. # who: visitor:father
* [Ask the quiet one why.]
    ~ court_lean = court_lean - 1
    Go on. Tell them. They came such a long way. # who: visitor:father
    She asked my name. Nobody here had asked it in forty years. # who: visitor:father_thrall
* [Stand by {given_name}. Say nothing.]
    You did not come alone. Good. I never let your mother walk anywhere alone. # who: visitor:father
    Stay there.
- You knew.
The next morning. # who: visitor:father
- (kept)
And you kept her.
{
- predator_or_fallen():
    There it is. That look suits you far better than hers ever did. # who: visitor:father
- pull_tier() == 0:
    You sound exactly like her. It was charming in her too. # who: visitor:father
}
She is mine. You do not throw a thing away because it has a crack in it. # who: visitor:father
{done_wounded:
    You did not throw away your wolf when my people put it on the ground. You sat up with it all night. You see? You understand me better than you would like. # who: visitor:father
}
Your mother never understood keeping. She gave everything away. The door, the tea, the bread. Herself. # who: visitor:father
-> court_pivot
