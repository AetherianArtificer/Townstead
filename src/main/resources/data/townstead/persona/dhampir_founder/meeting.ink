// Act 1, scene 1: First blood. The first conversation, soon after the founder arrives on the trail
// of a vampire. They are in the middle of their own work: after a kill (the "made_kill" counter)
// they want to get clean and find out who lives near the edge of town; when the trail went cold
// they are asking who slept badly. The player answers their question first; then the open topics.
// The offer to stay is their own plan: they need a base near the ridge. The player can leave at
// any point and pick up where they were (meeting.resume). Reveals only their given name, their
// hatred of vampires, and the shepherd's wolf (for scene 2).

=== meeting ===
{check("vampire_near"):
    -> not_now
}
~ met = 2
{check("made_kill"):
    -> after_kill
}
-> cold_trail

// A vampire is still close. They will not talk with one at their back.
= not_now
Not now. Get behind me. # emote:point
-> DONE

// Back after leaving mid-conversation.
= resume
Where were we.
{check("made_kill"):
    -> after_kill.topics
}
-> cold_trail.cold

= after_kill
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
    -> name ->
    -> topics
* [Is it dead?]
    It's dead. I'd been three nights catching up with it. Zombies bang on doors. These ones try the latch, like they've been invited.
    -> topics
* [Are you hurt?]
    -> hurt ->
    -> topics
* [What was it doing here?]
    Same thing it was doing at the last place.
    -> shepherd ->
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
    -> kin ->
    -> topics
* [You enjoyed that.]
    Yeah, I did. If you want me to pretend otherwise I can do that, I'm fairly good at it. But you asked.
    ** [Don't pretend. It had it coming.]
        ~ act("hunger_small")
        It did.
    ** [Maybe pretend, around the others.]
        ~ act("oath_small")
        All right. That's fair. People don't need to see that part.
    --
    -> topics
+ [What now? #advance]
    -> offer
+ [Another time.]
    Sure. I'll be around. I'm not going anywhere tonight.
    -> DONE

// The trail went cold: the vampire went on, or someone else ended it.
= cold_trail
{check("player_dhampir"):
    Hang on. You too? Huh. I've never met another one. Give me a minute, I don't know what I'm supposed to say to that.
}
Did anybody here sleep badly last night? Dogs going off at nothing, or somebody finding their door open in the morning and blaming the wind. It's always the wind. I've never met the wind, but it gets blamed for a lot.
* [Now that you mention it...]
    Show me in the morning, then. Tonight, everybody keeps their doors shut. Say it's for phantoms if people want a reason. Nobody argues with phantoms.
* [No. Why?]
    Because something came this way and I lost it at the edge of your town. Either it went on over the hills, or it found a cave to lie up in until dark.
    If it's a cave, I'll get it. If it's somebody's cellar, that's worse.
- (cold)
* [Who are you?]
    -> name ->
    -> cold
* [What are you hunting?]
    Something that used to be a man.
    -> shepherd ->
    -> cold
* {check("player_dhampir")} [What are we?]
    -> kin ->
    -> cold
* {check("player_killed")} [I've killed one.]
    Have you? Huh. # emote:ponder
    Then either you're luckier than most, or better than you look. No offense.
    ** [Better.]
        ~ act("hunger_small")
        We'll see. I'd like that to be true.
    ** [Luckier.]
        ~ trust(1)
        That's an honest answer. You'd be surprised how few people give me one.
    --
    -> cold
* [Are you hurt?]
    -> hurt ->
    -> cold
+ [What now? #advance]
    -> offer
+ [Another time.]
    Sure. Keep your door shut tonight.
    -> DONE

// ---- shared pieces ----

= name
{given_name}.
* [Just {given_name}?]
    For now. I've got a family name, I just don't give it to people I met over a body. Nothing personal.
* [Where are you from?]
    East, originally. Then a lot of places. I've slept in more haystacks than beds this year. # emote:shrug
- ->->

= kin
Tired, mostly. Hungry at the wrong times. Hard to kill.
I don't know much more than that, if I'm honest, and I've had longer to work it out than you have.
->->

= hurt
{check("hurt"):
    -> hurt_some
}
Not by that one.
* [By what, then?]
    That's a longer story than I've got in me tonight.
* [Good.]
    Yeah. # emote:nod
- ->->

= hurt_some
A bit. It'll close up by morning, it always does. Don't waste a potion on it.
* [Let someone look at it.]
    ~ trust(1)
    In the morning, maybe. If it's still open, which it won't be.
* [Always?]
    Always. I heal fast. Don't ask me why, I'd rather not get into it tonight.
- ->->

// Why it came, and the shepherd. The wolf comes up because it is still on their mind.
= shepherd
Does anyone here keep sheep?
* [Some of us. Why?]
    Tell them to sleep inside for a while. The sheep will be fine. It doesn't want sheep.
* [No. Why?]
    Good. One less thing.
- There was a shepherd past the second ridge. I never got his name. Nobody I asked knew it either, which says something about him, or about them.
It walked him out of his own door in the middle of the night, and he went, the way you go when you think a fox is at the pen.
~ asked_wolf = true
He had a wolf, a big grey thing, tied up by the gate. It was still there when I came through. I keep thinking about that. I should have cut the rope. # emote:ponder
->->

// The offer. Their plan as much as the town's: a roof near the ridge, and people to hold it.
= offer
There's more of them. There's a nest somewhere up between here and the pass, I'd put emeralds on it. Once one of them feeds near a town, the others find out. I don't know how. They just do.
I can't walk that whole ridge by myself. I've been trying for a month and I'm tired. I need a roof that isn't a haystack, and some people who'll hold a door shut while I'm out.
You'd get the same out of it. I'd set up an altar and swear in anyone who's got the stomach for it. Not many do. The ones who do are usually worth it.
~ offered = true
- (offer_topics)
* [What's the altar for?]
    Promises. The kind you make on your knees and actually mean.
    I'll show you when somebody's ready. It's easier to watch than to explain.
    -> offer_topics
* [What's in it for you?]
    A roof. A bed that's mine. And they'd come to me for once, instead of me walking three nights after them.
    ** [That's all?]
        That's plenty, from where I'm standing.
    ** [There's more to it than that.]
        ~ trust(1)
        Maybe. Ask me again in a year, if we're both still around.
    --
    -> offer_topics
* [Why us?]
    {check("made_kill"):
        You came toward the noise. That's rarer than you'd think.
    - else:
        Honestly? Your people seem decent, and decent people are the ones they like.
    }
    -> offer_topics
+ [Stay. #advance]
    -> stay
+ [I'll think about it.]
    Take your time. I'd just rather you thought faster than they eat.
    -> DONE

= stay
~ act("found")
~ founded = true
~ trust(2)
Good. Thank you. I'll start looking for the ones who don't mind the dark. They usually find me first, if I'm honest. # emote:nod
{asked_wolf:
    I'll be gone a day first. There's a rope I need to go and cut.
}
// They walk out once no one is watching and come back in a day with the wolf (scene 2).
~ act("fetch_wolf")
-> DONE
