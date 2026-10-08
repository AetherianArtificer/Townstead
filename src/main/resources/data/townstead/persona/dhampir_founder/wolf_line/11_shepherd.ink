VAR shepherd_offered = false
VAR shepherd_walking = false
VAR done_shepherd = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function shepherd_offer_ready() ===
~ return done_midpoint and done_inside and not shepherd_offered

// When this scene plays. The greet in persona.ink checks these in order.
=== function shepherd_road_ready() ===
~ return shepherd_walking and not check("at_shepherd")

=== function shepherd_arrive_ready() ===
~ return shepherd_walking and check("at_shepherd")

// Beat 11. After the midpoint: the shepherd's grave.
=== shepherd_offer ===
~ shepherd_offered = true
I want to take the wolf back to see him. The shepherd.
That's a strange thing to want, isn't it. I don't care. Will you come?
+ [I'll come.]
    ~ start_trip(shepherd_walking, "mark_shepherd", "shepherd_travel")
    The wolf knows the way better than either of us. I'll follow it, you follow me.
    -> DONE
+ [Another day.]
    ~ shepherd_offered = false
    Another day.
    -> DONE

=== shepherd_road ===
{~It's further than I remember. Everything is.|It knows where we're going. Look at it.|Keep up.}
+ [Let's turn back.]
    ~ end_trip(shepherd_walking, "shepherd_home")
    All right. Another time.
    -> DONE
+ [Keep going.]
    -> DONE

=== shepherd_arrive ===
~ done_shepherd = true
~ shepherd_walking = false
~ act("build_shepherd_grave")
~ toward_oath()
~ trust(2)
Here. He's here.
I never did get his name. I put a stone up for him anyway. You don't need a name for a stone. # emote:ponder
He'd want to know it ate well. It eats very well. Somebody keeps spoiling it.
+ [Say something to him.]
    I just did. That's all I've got.
    ...Thank you for the wolf. There. That's the rest of it.
+ [Let's go home.]
    Yes. All three of us.
- ~ act("shepherd_home")
-> DONE
