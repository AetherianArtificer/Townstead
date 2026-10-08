// Act 2, scene 14: Debriefs. After the name (scene 12), the first talk on a day the founder has
// put a vampire down ("founder_kills" rose since the last debrief) is a short talk about it. The
// player praises or tempers their enjoyment: this is the main source of Hunger and Oath steps.
// Their opening follows the pull of the blood, not the kill: the game knows a vampire died, not how.

VAR debrief_kills = 0
VAR debrief_day = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function debrief_ready() ===
~ return named and count("founder_kills") > debrief_kills and debrief_day < today

=== debrief ===
~ debrief_kills = count("founder_kills")
~ debrief_day = today
{
- check("pull_fallen"):
    {~Another one. I could have made that last all night. I nearly did, and I don't see why I shouldn't have.|That's another. Don't look at me like that. Somebody has to enjoy the work, or it doesn't get done.}
- check("pull_predator"):
    {~I got another one. I'd go back out right now if it wasn't getting light. I'm not even tired. I'm the opposite of tired.|Another one. Ask me how it felt. Go on. Nobody ever asks the interesting question.}
- check("pull_hungry"):
    {~Another one down. I'm keeping count, you know. Not for any reason. I just like watching the number go up.|That's another. I'll say this for them, they keep me in work.}
- check("pull_steady"):
    {~That's another one that won't be trying anybody's latch.|One more. It's done, that's the main thing.}
- else:
    {~One more. I didn't drag it out. I'm trying not to, these days.|It's done. I'll be glad when there aren't any left. I think I'd be glad. I'd like to find out.}
}
{check("hurt"):
    It got a piece of me first. It'll close.
}
+ [Good. You're allowed to enjoy it.]
    ~ toward_hunger()
    {~I do. That's the trouble with telling me I'm allowed.|Thanks. It's nice to hear it from somebody who isn't me.}
+ [Don't get to like it too much.]
    ~ toward_oath()
    {~I know. I'm working on it. Working on it is about as far as I've got.|Fair. You'd tell me if I was getting worse, wouldn't you? Somebody should.}
+ [Did anyone get hurt?]
    ~ trust(1)
    {check("hurt"):
        -> hurt
    }
    No. Not this time.
+ [I'll let you clean up.]
    Thanks. I smell like a cellar.
- -> DONE

= hurt
Only me, and I don't count.
+ [You count.]
    ~ toward_oath()
    ...All right. I'll go and wash it, then. Happy?
+ [Get it seen to.]
    In the morning.
- -> DONE
