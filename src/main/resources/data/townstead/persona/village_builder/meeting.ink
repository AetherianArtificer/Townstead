// The first meeting. Every hometown opens with the object the Builder carries (see the hometown
// files), then a new settlement gets that hometown's scene and a village the player did not found
// gets the discovered scene. Left out on purpose: the secret, hobbies, the line, and any
// stranger's greeting.

=== meeting ===
-> opening ->
{check("discovered"): -> discovered_meeting}
-> home_scene

= resume
{hometown:
- "harbor": -> harbor_meeting.resume ->
- "mill": -> mill_meeting.resume ->
- "mine": -> mine_meeting.resume ->
- "roads": -> roads_meeting.resume ->
- "forest": -> forest_meeting.resume ->
- else: -> well_meeting.resume ->
}
{check("discovered"): -> discovered_meeting}
-> home_scene

= opening
{hometown:
- "harbor": -> harbor_meeting.open ->
- "mill": -> mill_meeting.open ->
- "mine": -> mine_meeting.open ->
- "roads": -> roads_meeting.open ->
- "forest": -> forest_meeting.open ->
- else: -> well_meeting.open ->
}
->->

= home_scene
{hometown:
- "harbor": -> harbor_meeting.walls
- "mill": -> mill_meeting.walls
- "mine": -> mine_meeting.walls
- "roads": -> roads_meeting.walls
- "forest": -> forest_meeting.walls
}
-> well_meeting.walls

// Their first worry, raised outside the first meeting (after a discovered village's push, or
// when the player put them off). The same question each hometown asks in its own scene.
= first_worry
{hometown:
- "mill":
    Where does a hungry person eat, here? Say someone came in off the road with nothing in their bag. Where would they go?
    Back home, nobody came in hungry and left that way. I'd like it to be true here too.
    -> pot ->
- "mine":
    Who's still working here after dark? Tell me honestly. Somebody always is.
    Where I grew up, the late shift came up grey. One day in the week with no work in it, for everyone. That's all.
    -> rest ->
- "harbor":
    Where does the next family sleep, here? Say someone came up the road tomorrow with a cart and three children.
    Back home, we'd have had a bed made up before the boat was even tied off. Let's do that here.
    -> beds ->
- "roads":
    Who here's got nothing to do all day? There's always someone, standing about, looking at their hands.
    Where I grew up, that's how it started. Let's find everyone somewhere to be in the morning.
    -> work ->
- "forest":
    When somebody new comes, is there a roof waiting for them? A bed that's theirs, from the first night?
    Let's make a bit more room than you need. It's the easy part, building.
    -> beds ->
- "well":
    How far is water from your nearest door? In steps. Guess.
    We measured water in cups, back home. Let's put water where people live.
    -> water ->
}
->->
