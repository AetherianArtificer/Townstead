// Everyday talk, when no scene is waiting. They are in the middle of something, and the first
// line follows what is going on: hurt, wet, out at night, or just working.
=== dusk ===
~ temp mood = neighbor_mood_line()
{mood == "":
    ~ mood = not_them_line()
}
{done_counting and check("night") and RANDOM(1, 4) == 1:
    -> recite ->
    Oh. It's you.
    -> talk
}
{
- ending == "B":
    {~You again. Sit, if you like. I don't bite. Not you, anyway.|The lodge is quiet tonight. I like it quiet. I like a lot of things quiet now.}
- ending == "A" and RANDOM(1, 3) == 1:
    {~I said all nine again this morning. I'm getting faster. I'm trying not to.|It's a good night. I'm not going out. I don't have to, and I'm finding out what that's like.}
- mood != "":
    {mood}
- check("hurt"):
    Don't fuss, it's already closing up. I'd take some food, though, if you've got any. Not bread.
- check("wet"):
    Everything I own is wet, including some things I didn't know could get wet.
- check("night"):
    You're up late. Don't walk the edge on your own at this hour, I mean it. Stay where the torches are.
- else:
    {~Oh, it's you. Sit down if you want, I'm only sharpening this.|Afternoon. Or morning, I lose track. I sleep in the day more than I should.|It's you. Good. I was about to go and count your torches again.}
}
- (talk)
* {who("farmer") != ""} [How are the people here?]
    {who("farmer")} leaves the door on the latch most nights. Too tired to check it, I think. I close it on my way past.
    Don't tell them. They'd only start locking it, and then lose the key, and then I'd be the one getting them in through a window.
    -> talk
* {who("farmer") == ""} [How are the people here?]
    Quieter than they think they are. They talk about me when I walk past, which is fine. I'd talk about me too.
    -> talk
* {founded} [How's the lodge?]
    It's a roof. I haven't had one I could call mine in a while, so I keep walking round it checking the corners. Force of habit.
    -> talk
* {wolf_seen and check("has_wolf")} [How's the wolf?]
    {check("hungry"):
        Keeping its distance lately. I think it's got more sense than I have.
    - else:
        Fat. Somebody keeps feeding it, and it isn't me, whatever it tells you.
    }
    -> talk
* {check("carries_book") and not done_book} [I have something of your mother's.]
    -> the_book
* {check("heard_birth") and not told_father} [She says your father was at your birth.]
    -> father_at_birth
* {named and done_counting and not done_count_talk} [What were you reciting, that night?]
    -> the_count
+ {check("player_sworn")} [Any work?]
    -> contracts ->
    -> talk
+ [Need anything?]
    {~Torches. Always torches. You can't have too many, whatever your builders tell you.|Arrows, if anyone's making them. I go through them faster than I'd like.|Sleep, mostly. You can't get me any of that, but thanks for asking.}
    -> talk
+ [What do you do when you're not hunting?]
    Sharpen things, mostly. Walk the edge of town. I tried fishing once, a whole afternoon, and I caught a boot and a saddle. I still don't know what a saddle was doing in a river. # emote:shrug
    -> talk
+ [Tell me about yourself.]
    Not now. I'm not being difficult, I'm just tired, and it isn't a short story.
    ++ [Some other time, then.]
        Some other time. Sure.
    ++ [Suit yourself.]
        Sorry. Ask me when I've slept.
    --
    -> talk
+ [I'll let you get on.]
    {check("night"):
        Goodnight. Stay in, if you can.
    - else:
        See you around.
    }
    -> DONE
