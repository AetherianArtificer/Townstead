// The meeting, after a kill: they want water, then the open topics.

=== meeting_after_kill ===
It's dead. Give it a minute before you go near it, they twitch. I asked a cleric once why they twitch, and he told me it was the Nether going out of them, and then he tried to sell me a bottle of something that glowed, so.
{check("player_dhampir"):
    ...Huh. You too, then. I've never met another one. I always thought I'd feel something. Mostly I feel like I should have washed first.
}
Is there water somewhere? The last town I came through, I tried their trough and a llama spat at me, which I probably deserved.
* [There's water this way.]
    Good, thanks. # emote:nod
* [You'll live.]
    Probably. I usually do. # emote:shrug
- I'll want to know who lives out at the edge of town, whose doors to try in the morning. Not tonight. Tonight they can sleep, they've earned that much.
- (topics)
* [Who are you?]
    -> meeting_name ->
    -> topics
* [Is it dead?]
    It's dead. I'd been three nights catching up with it. Zombies bang on doors. These ones try the latch, like they've been invited.
    -> topics
* [Are you hurt?]
    -> meeting_hurt ->
    -> topics
* [What was it doing here?]
    Same thing it was doing at the last place.
    -> meeting_shepherd ->
    -> topics
* [It was a person once.]
    It was. It had good boots on, did you see? Somebody made those. I think about that sometimes, who made the boots, whether they know. Then I stop thinking about it, because it doesn't help anybody.
    ** [You're very sure it was too far gone.]
        I have to be. The hunters who aren't sure end up as somebody's supper, and then I'm hunting them too.
    ** [That's kind, thinking about the boots.]
        ~ trust(1)
        I don't know that it's kind. I just notice boots.
    --
    -> topics
* {check("player_dhampir")} [What are we?]
    -> meeting_kin ->
    -> topics
* [You enjoyed that.]
    Yeah, I did. If you want me to pretend otherwise I can do that, I'm fairly good at it. But you asked.
    ** [Don't pretend. It had it coming.]
        ~ toward_hunger()
        It did.
    ** [Maybe pretend, around the others.]
        ~ toward_oath()
        All right. That's fair. People don't need to see that part.
    --
    -> topics
+ [What now? #advance]
    -> meeting_offer
+ [Another time.]
    Sure. I'll be around. I'm not going anywhere tonight.
    -> DONE
