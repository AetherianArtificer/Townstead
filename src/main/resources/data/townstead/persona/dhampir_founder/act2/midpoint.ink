// Midpoint: The Truth. A day or more after the book (M4), someone comes to the lodge asking for the
// player: the candle-bringer, a court thrall who kept the mother's house and still tends her grave in
// secret (a walk-in visitor). A three-way scene (# who:) once they are near: they want the signet
// back before it is missed, and they tell what the father never would. He knew the next morning. The
// quiet one still pours his wine, and he keeps her, because he keeps everything. The player decides
// the signet: give it back, offer them a place here (her open door), or keep it and send them off.
// Then the founder asks the arc's question: was she wrong?

VAR book_day = -1
VAR bringer_called = false
VAR bringer_name = ""
VAR done_midpoint = false
VAR bringer_stayed = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function bringer_arrival_ready() ===
~ return done_book and not bringer_called and today > book_day

=== function midpoint_ready() ===
~ return bringer_called and not done_midpoint and here("visitor:candle_bringer")

=== function confession_after_ready() ===
~ return done_midpoint and not done_confession_after and (check("midwife_forgiven") or check("midwife_cast_out"))

=== bringer_arrival ===
~ bringer_called = true
~ act("bringer_arrives")
Somebody's come into town asking for you. Not me. You.
I don't know them. They walk like they're used to waiting in doorways.
-> DONE

=== midpoint ===
~ done_midpoint = true
~ midpoint_day = today
~ act("mark_midpoint")
~ bringer_name = who("visitor:candle_bringer")
Forgive me. I shouldn't have come up to the lodge. I only need the ring back. The signet. I dropped it, at her grave. # who: visitor:candle_bringer
At her grave.
I go when I can. When I'm sent to market and there's time. Nobody knows. # who: visitor:candle_bringer
Who are you?
I kept her house. Your mother's. I did her hair on market days, and she taught me my letters when she found out I couldn't read. # who: visitor:candle_bringer
You stand the way she did. I'm sorry. I'm staring. # who: visitor:candle_bringer
You serve him. # emote:ponder
Yes. After, he took in the whole household. There wasn't anywhere else to go. # who: visitor:candle_bringer
- (ask)
* [Why the candles?]
    Because nobody else would. # who: visitor:candle_bringer
    He never goes. Every year on the day, he has a candle put on his table, and he sits with it until it's out. That's all. # who: visitor:candle_bringer
    He sits with it. # emote:ponder
    -> ask
* [You've read her book. You know about the quiet one.]
    -> truth
* [Tell them what you know.]
    -> truth

= truth
She still pours his wine. Every night. He watches her do it. # who: visitor:candle_bringer
He's always known. He knew the next morning. # who: visitor:candle_bringer
And he kept her.
He keeps everything. That's what he is. # who: visitor:candle_bringer
Please. The ring. If it's missed they'll know I've been coming here. I don't mind what they'd do so much. But I wouldn't be able to come back to her. # who: visitor:candle_bringer
- (signet)
* {check("carries_signet")} [Give it back.]
    ~ act("take_signet")
    ~ act("bringer_leave")
    Thank you. Thank you. # who: visitor:candle_bringer
    Go on, then. Before I change my mind about you.
* [Stay here. You'd be safe here.]
    Here? # emote:ponder
    I couldn't. Could I? # who: visitor:candle_bringer
    ** [You could.]
        ~ bringer_stayed = true
        ~ act("bringer_stay")
        ...All right. All right. I'll stay. # who: visitor:candle_bringer
        You're doing what she'd have done. You know that, don't you. She let everybody in.
    ** [It's up to you.]
        Then I'd better go back. Before I'm missed. # who: visitor:candle_bringer
        ~ act("bringer_leave")
    --
* {check("carries_signet")} [Keep it. Go.]
    ~ act("bringer_leave")
    Then I won't be coming again. # who: visitor:candle_bringer
    Good. # emote:nod
* {not check("carries_signet")} [I don't have it any more.]
    ~ act("bringer_leave")
    Oh. # who: visitor:candle_bringer
    Then I'd better go back and hope nobody asks. # who: visitor:candle_bringer
- -> question

// The founder, after. The arc's question.
= question
Somebody she was kind to. That's who it was. Not the dark. Not one of them out there.
Somebody she let in and poured tea for and was kind to.
She let everybody in. She let him in. Was she wrong?
Tell me she was wrong. It'd be easier.
* [She was wrong.]
    ~ toward_hunger()
    ~ toward_hunger()
    Then I've been right all along. Every door I shut. # emote:nod
    That's something. That's a lot, actually.
* [She wasn't.]
    ~ toward_oath()
    ~ toward_oath()
    Then I don't know what I've been doing. # emote:shake_head
    ...That isn't true. I know exactly what I've been doing. I just don't know what it's for.
* [I don't know.]
    ~ trust(2)
    No. Me neither.
    That's the first honest thing anybody's said about her in years.
- I'm going there. To his house. Not yet. But I'm going.
-> DONE

// After the midwife's confession (visit 3, her story): the founder, alone with the player.
VAR done_confession_after = false

=== confession_after ===
~ done_confession_after = true
{check("midwife_forgiven"):
    ~ toward_oath()
    I told her to come and sit by the fire. I meant it.
    I think I meant it. Ask me again when she's sitting there.
- else:
    ~ toward_hunger()
    I sent her home. # emote:shrug
    ...Don't look at me like that. You'd have done the same. You'd have wanted to.
}
-> DONE
