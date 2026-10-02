// Act 3: The Court. After the midpoint, the confession (or a few days without it) and the wolf's
// bed moving inside (wolf beat 12), the founder goes to their father's house and asks the player to
// come. The wolf stays by the lodge fire. The player gets a map (mark_structure); the founder walks
// with them (travel_with). At the marker the father and the quiet one come out to meet them
// (visitors brought up beside the player, roles "father" and "father_thrall"), and the scene is
// four-way. The father knows, and is not sorry: love, to him, is keeping. The pivot is what the
// founder does to the quiet one. The player's push and the balance built so far decide it
// together; when the wolf line is complete, a founder about to fall hesitates once more.
//
// Ending A: they forgive her, the player decides the father's fate (end him with a relic, or spare
// him for his word), and they go home and keep their mother's oath. Ending B: they take his place
// and keep her, as he did; they offer the player a place beside them (turning them, when romanced),
// an accord, or the other side of the door.

VAR court_offered = false
VAR court_walking = false
VAR court_arrived = false
VAR done_court = false
VAR ending = ""
VAR father_fate = ""
VAR b_answer = ""
VAR midpoint_day = -1
VAR done_epilogue = false

=== court_offer ===
~ court_offered = true
I'm going to his house. I said I would, and I've put it off as long as I can stand.
I want you there. Not to fight. I want somebody there who knew me before I walked in.
{who("sworn_hunter") != "":
    The wolf stays here, by the fire. {who("sworn_hunter")} is going to feed it. I've asked, and I've asked again, and I'm going to ask once more before we go.
- else:
    The wolf stays here, by the fire. I'll leave enough food out for a week. It'll eat it in two days, but that's its business.
}
+ [I'll come.]
    ~ court_walking = true
    ~ act("mark_court")
    ~ act("court_travel")
    You've got the map. Same as last time. You lead, I'll follow, and if I stop, don't wait for me to say why.
    -> DONE
+ [Not yet.]
    ~ court_offered = false
    Not yet. Soon, though. I can feel it pulling, like a hook behind my ribs.
    -> DONE

// On the road.
=== court_road ===
{
- check("pull_predator") or check("pull_fallen"):
    {~I'm not tired. I should be. I've been walking all day and I could walk all night.|I keep thinking about what I'll say to him. I keep coming up with nothing. It's a very satisfying nothing.}
- else:
    {~Keep going. I'm all right. I'm mostly all right.|It's further than I thought. Everything is, this year.|Talk to me about something. Anything. Your town. Your crops. I don't care, just talk.}
}
+ [Let's turn back.]
    ~ court_walking = false
    ~ court_offered = false
    ~ act("court_home")
    ...Yes. All right. Not this time. Thank you for not making me say it.
    -> DONE
+ [Keep going.]
    -> DONE

// The marker. They come out to meet them.
=== court_arrive ===
~ court_arrived = true
~ act("court_hosts")
This is it. # emote:ponder
He knows we're here. He's always known where I was, I think. That's the kind of thing he'd know.
Stay by me.
-> DONE

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
-> pivot

= pivot
Step away from her. # emote:point
...Tell me what to do. No. Don't. I want you to, and I don't.
{
- check("midwife_forgiven"):
    I told a frightened woman to come and sit by my fire. She failed my mother too.
- check("midwife_cast_out"):
    I sent a frightened woman home for less than this.
}
{thrall_scorned:
    I sent one like her down the road once. Somebody's pet. I'd do it again. I think I would.
}
{fledgling_end == "curing" and done_cure:
    And there was a way back for one of them. There was a way back, and I never asked.
}
* [She couldn't help it. The bond did this to her.]
    ~ temp lean = pull_tier() - 1 + echoes()
    -> decide(lean)
* [She killed your mother.]
    ~ temp lean2 = pull_tier() + 1 + echoes()
    -> decide(lean2)
* [It's your choice. It always was.]
    ~ temp lean3 = pull_tier() + echoes()
    -> decide(lean3)

= decide(lean)
{lean >= 3:
    {done_inside:
        -> hesitate
    }
    -> fall
}
-> forgive

// The wolf line is complete: one more chance.
= hesitate
... # emote:ponder
There's a wolf asleep by my fire. I didn't want that either.
* [Come home. It's waiting for you.]
    ~ act("oath_small")
    -> forgive
* [Do it.]
    -> fall

// Ending A.
= forgive
~ ending = "A"
You couldn't stop. # emote:ponder
I know what that's like. I know exactly what that's like. There's something in me that wants things, and it doesn't care what I think about it.
It isn't you. It's the thing in you. I've got one too. I've had it all my life, and I've been calling it by other people's names.
{done_inside:
    There's a wolf by my fire. I'd like to get back to it. That's the most I've wanted anything in a long time.
}
I'm not going to kill you. I'm going home.
...Thank you. # who: visitor:father_thrall
Don't. I didn't do it for you.
-> father

= father
And him. # emote:point
That's yours to say. I can't. I've been deciding what to do about him since before I could walk, and I'm tired.
* {check("carries_relic")} [End him.]
    -> end_him
* [Spare him.]
    -> spare_him

= end_him
~ father_fate = "ended"
~ act("end_father")
Ah. # who: visitor:father
...There. # emote:ponder
I thought I'd feel something. I feel like I've put down something heavy. That's all.
~ act("quiet_leave")
Go on. Go wherever you like. Somewhere he isn't.
-> home_a

= spare_him
~ father_fate = "spared"
~ act("father_spared")
Then go home, both of you. Nothing of mine will come to your town, not while I keep this house. You have my word. # who: visitor:father
It is older than your town. # who: visitor:father
Keep it, then. You're good at keeping.
~ act("father_leave")
-> home_a

= home_a
~ act("ending_a")
Let's go home. # emote:nod
I want to stand at that altar in the morning and say all nine. Out loud. With nobody there but the wolf.
~ court_walking = false
~ act("court_home")
-> DONE

// Ending B.
= fall
~ ending = "B"
No. # emote:shake_head
No, I don't think so.
~ act("end_father")
...There you are. # who: visitor:father
Yes. Here I am.
You. # emote:point
This house is mine now. You'll keep it for me. Pour the wine for whoever sits in his chair, and set a cup for me, for when I come.
Yes. # who: visitor:father_thrall
~ act("ending_b")
-> offer

= offer
{romanced:
    -> offer_love
}
You can come with me. Into the house. All the way in.
* [I'll join you.]
    ~ b_answer = "joined"
    -> turn
* [We could have an accord.]
    -> accord
* [No.]
    -> door

= offer_love
Stay with me. Not like this. Like you are. Like I am now.
I can make it so you never get old. So I never have to watch it happen. That's the only thing I've ever been afraid of, did you know that? Watching.
* [Yes.]
    ~ b_answer = "turned"
    -> turn
* [We could have an accord.]
    -> accord
* [No.]
    -> door

= turn
~ act("turn_player")
Good. # emote:nod
It'll hurt for a while. Then it won't. Then nothing will, much.
~ court_walking = false
~ act("court_home")
Come on. We're going home. It's going to be a very different home.
-> DONE

= accord
~ b_answer = "accord"
~ act("b_accord")
An accord. # emote:ponder
...Yes. Your town and mine. You keep to your side of the fence, and I'll keep to mine. Draw it up. I'll sign anything you put in front of me tonight.
~ court_walking = false
~ act("court_home")
-> DONE

= door
~ b_answer = "rival"
~ act("b_rival")
Then you're on the other side of the door. # emote:shrug
Go home. Get your people inside. When I come up the road, decide what you are by then.
~ court_walking = false
~ act("court_home")
-> DONE

// The balance as a number: 0 near the oath, up to 4 fallen.
=== function pull_tier() ===
{
- check("pull_fallen"):
    ~ return 4
- check("pull_predator"):
    ~ return 3
- check("pull_hungry"):
    ~ return 2
- check("pull_steady"):
    ~ return 1
}
~ return 0

// What the founder did with the earlier knocks at the door.
=== function echoes() ===
~ temp e = 0
{check("midwife_forgiven"):
    ~ e = e - 1
}
{thrall_scorned:
    ~ e = e + 1
}
{willing_broke:
    ~ e = e + 1
}
{done_cure:
    ~ e = e - 1
}
~ return e

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
