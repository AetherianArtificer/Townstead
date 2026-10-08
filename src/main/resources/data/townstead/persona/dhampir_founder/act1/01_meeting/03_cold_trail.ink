// The trail went cold: the vampire went on, or someone else ended it.
=== meeting_cold_trail ===
{check("player_dhampir"):
    Hang on. You too? Huh. I've never met another one. Give me a minute, I don't know what I'm supposed to say to that.
}

You're not... Did you notice anything strange last night? Wolves howling, strangers walking around...
* [Yeah, now that you mention it...]
    Show me in the morning, then. Tonight, everybody keeps their doors shut. Say it's for phantoms if people want a reason. Nobody argues with phantoms.
* [You mean, like you?]
    ... Yeah, actually. There's a predator loose around here. I lost it at the edge of your town. It's probably hiding in a cave somewhere. If it's a cave, I'll get it. If it's somebody's house, that's... not ideal.
- (cold)
* [Who are you?]
    -> meeting_name ->
    -> cold
* [What are you hunting?]
    Something that used to be a man.
    -> meeting_shepherd ->
    -> cold
* {check("player_dhampir")} [What are we?]
    -> meeting_kin ->
    -> cold
* {check("player_killed")} [I've killed one.]
    Have you? Huh. # emote:ponder
    Then either you're luckier than most, or better than you look. No offense.
    ** [Better.]
        ~ toward_hunger()
        We'll see. I'd like that to be true.
    ** [Luckier.]
        ~ trust(1)
        That's an honest answer. You'd be surprised how few people give me one.
    --
    -> cold
* [Are you hurt?]
    -> meeting_hurt ->
    -> cold
+ [What now? #advance]
    -> meeting_offer
+ [Another time.]
    Sure. Keep your door shut tonight.
    -> DONE
    -> DONE
.
    -> DONE
    -> DONE
 Sure. Keep your door shut tonight.
    -> DONE
    -> DONE
