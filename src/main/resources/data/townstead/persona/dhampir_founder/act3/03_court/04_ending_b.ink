// Ending B.
=== court_ending_b ===
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
~ end_trip(court_walking, "court_home")
Come on. We're going home. It's going to be a very different home.
-> DONE

= accord
~ b_answer = "accord"
~ act("b_accord")
An accord. # emote:ponder
...Yes. Your town and mine. You keep to your side of the fence, and I'll keep to mine. Draw it up. I'll sign anything you put in front of me tonight.
~ end_trip(court_walking, "court_home")
-> DONE

= door
~ b_answer = "rival"
~ act("b_rival")
Then you're on the other side of the door. # emote:shrug
Go home. Get your people inside. When I come up the road, decide what you are by then.
~ end_trip(court_walking, "court_home")
-> DONE
