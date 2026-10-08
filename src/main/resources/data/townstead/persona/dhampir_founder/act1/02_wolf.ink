// Act 1, scene 2: The wolf. After the lodge is founded they leave for a day, and come back with the
// shepherd's wolf at their heel. They are busy with it without admitting it: they want scraps, and
// somewhere out of the wind by the lodge for it to sleep. They insist it is not theirs. Unlocks
// once they keep a wolf ("has_wolf").
//
// The wolf is a quiet Hunger meter: at "hungry" or worse it keeps a little distance from them.

VAR wolf_seen = false
VAR asked_scraps = false
// The day they left to fetch it; they are back the day after.
VAR wolf_day = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function wolf_ready() ===
~ return founded and not wolf_seen and check("has_wolf")

=== function wolf_fetch_ready() ===
~ return founded and asked_wolf and not wolf_seen and today > wolf_day and not check("has_wolf_anywhere")

=== wolf ===
~ wolf_seen = true
// It sleeps outside the lodge for now. Later beats bring it in.
~ act("wolf_rest_outside")
{who("butcher") != "":
    Does {who("butcher")} sell offcuts? Bones, fat, whatever's going. I can pay, I've got emeralds. I'm not asking for charity.
- else:
    Where does this town throw its scraps? Not rotten flesh, before you say it. It has to be the real thing.
}
~ asked_scraps = true
- (topics)
* [What do you need scraps for?]
    No reason.
    ** [Is it for the wolf?]
        What wolf. # emote:shrug
        ...All right, yes. It followed me back. I went to cut it loose and it just came along. I didn't ask it to.
        *** [You went back for it.]
            I was passing.
            **** [Two valleys isn't passing.]
                ~ trust(1)
                It is if you walk fast. Which I do.
            **** [Of course you were.]
                I was, though.
            ----
        *** [Wolves do that.]
            Apparently. Nobody told me.
        ---
    ** [I'll find you some.]
        Thanks. Don't make it sound like it's for anything, if anybody asks. I've got a reputation, I'd like to keep it a while.
    --
    -> topics
* [Where's it going to sleep?]
    Outside somewhere, round the lodge, out of the wind. Not underfoot. Definitely not on my bed, I've seen what they do to wool.
    ** [By the door.]
        The door's fine. It'll hear people coming from there.
    ** [Round the back.]
        The back, then. It's quieter.
    ** [Inside?]
        It's a wolf. It sleeps outside. That's how wolves work, I'm fairly sure.
    --
    -> topics
* [Does it have a name?]
    Probably. Ask the shepherd.
    ** [Where is he?]
        Under the tree by his gate. I put him there yesterday, on the way back.
        *** [You buried him?]
            Somebody had to. The ground was soft, so it didn't take long. The wolf sat and watched the whole time. I don't know what it thought I was doing.
        *** [On your own?]
            Me and a shovel I borrowed off his neighbor. I should bring it back one of these days.
        ---
    ** [So it isn't yours.]
        No. It isn't anybody's, now.
    --
    -> topics
* {not check("hungry")} [It stays close to you.]
    I've noticed. It'll get bored of me eventually. Most things do.
    -> topics
* {check("hungry")} [It keeps its distance from you.]
    Yeah, I've noticed that too. Smart animal.
    -> topics
+ [I'll leave you to it.]
    Offcuts. Don't forget, please.
    -> DONE

// The player gave them raw meat. They take it for the wolf.
=== gift_meat ===
{wolf_seen:
    ~ meat_given++
    {asked_scraps:
        {~Thanks. I'll make sure it gets it. Nobody needs to know.|It eats better than I do now. I'm not sure how that happened.|You're spoiling it. It's going to start expecting this.}
    - else:
        {~I'll see it gets it.|It eats better than I do now.|You're spoiling it.}
    }
- else:
    I've eaten, thanks. # emote:shrug
}
-> DONE

// They went to fetch the wolf and came back without it. The game brings the wolf now.
=== wolf_fetch ===
~ act("adopt_wolf")
-> wolf
