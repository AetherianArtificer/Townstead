// Act 3, The Court, part 2: the road. The player gets a map (mark_structure) and the founder walks
// with them (travel_with). At the marker, the father and the quiet one come out to meet them.

VAR court_walking = false
VAR court_arrived = false

// When this scene plays. The greet in persona.ink checks these in order.
=== function court_road_ready() ===
~ return court_walking and not court_arrived and not check("at_court")

=== function court_arrive_ready() ===
~ return court_walking and not court_arrived and check("at_court")

// On the road.
=== court_road ===
{
- predator_or_fallen():
    {~I'm not tired. I should be. I've been walking all day and I could walk all night.|I keep thinking about what I'll say to him. I keep coming up with nothing. It's a very satisfying nothing.}
- else:
    {~Keep going. I'm all right. I'm mostly all right.|It's further than I thought. Everything is, this year.|Talk to me about something. Anything. Your town. Your crops. I don't care, just talk.}
}
+ [Let's turn back.]
    ~ court_offered = false
    ~ end_trip(court_walking, "court_home")
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
