// Act 1, scene 9: The neighbor with curtains. A vampire lives in the town ("resident_vampire";
// one who has bitten someone unwillingly comes first). The lodge's rule is to talk first, and the
// founder asks the player to do the talking: one question, whether they have fed on anyone here.
// The player carries it to the neighbor (their answer is the resident_vampire story, true to their
// own record) and brings it back, or reports without asking. The founder never harms them; the
// outcome sets their mood for a few days. Reveals their certainty that "they all hurt someone
// eventually".

VAR neighbor = ""
VAR asking = false
VAR done_neighbor = false
VAR neighbor_mood = ""
VAR neighbor_mood_until = -1

// When this scene plays. The greet in persona.ink checks these in order.
=== function neighbor_offer_ready() ===
~ return done_watch and not done_neighbor and who("resident_vampire") != ""

=== function neighbor_back_ready() ===
~ return asking

=== neighbor_offer ===
~ neighbor = who("neighbor_biter")
{neighbor == "":
    ~ neighbor = who("resident_vampire")
}
{neighbor} is one of them. I knew the first day. I can smell it on them, like a cellar somebody shut up too long.
The lodge has a rule, and it's a good rule, and I hate it. We talk first. Before anybody does anything, somebody talks to them.
{check("hungry"):
    I'm going to keep to it. I want you to know I'm doing that for you, not for them.
}
I'm not the one to do the talking. Everything I say to one of them comes out sounding like a threat, because it usually is.
Would you ask them something for me? Just one thing. Whether they've fed on anyone here.
- (ask)
+ [I'll ask.]
    ~ asking = true
    ~ act("carry_question")
    Thanks. Go in daylight, somewhere with people around. I'm not saying they'd try anything. I'm saying don't give them the choice.
    -> DONE
+ [Why not leave them alone?]
    Because I don't know yet. That's all talking first is. Finding out.
    -> ask
+ [Not now.]
    All right. It'll wait. I'm good at waiting.
    -> DONE

// The player is back, whether or not they asked.
=== neighbor_back ===
Well? What did {neighbor} say?
* {check("heard_neighbor") and is(neighbor, "neighbor_clean")} [They keep to animals.]
    ...Animals.
    All right. All right, I'll leave them be. I don't have to like it.
    ** [Maybe they're decent.]
        ~ toward_oath()
        ~ neighbor_mood = "easy"
        Maybe. They all are, until they aren't. ...That's not fair. I know it's not fair.
    ** [Keep an eye on them anyway.]
        ~ toward_hunger()
        ~ neighbor_mood = "sour"
        I will. I always do.
* {check("heard_neighbor") and is(neighbor, "neighbor_willing")} [Only from people who said yes.]
    Yes. That's what they always say.
    ** [It's true.]
        ~ toward_oath()
        ~ neighbor_mood = "easy"
        ...Fine. It's true. I'll leave it there.
    ** [You don't believe them.]
        ~ toward_hunger()
        ~ neighbor_mood = "sour"
        No. I don't believe any of them. I'll believe it when I'm dead, and that's a long way off, for me.
* {check("heard_neighbor") and is(neighbor, "neighbor_biter")} [They bit someone. They say they're sorry.]
    There it is. # emote:ponder
    They all hurt someone eventually. I keep saying it and nobody likes hearing it.
    ** [They told the truth. That counts.]
        ~ toward_oath()
        ~ neighbor_mood = "easy"
        ...It counts for something. Not much. Something.
    ** [Then you were right.]
        ~ toward_hunger()
        ~ neighbor_mood = "sour"
        I usually am. I'd like, once, not to be.
* {check("heard_neighbor")} [They're harmless. Leave them be.]
    If you say so. You're the one who talked to them.
    ~ neighbor_mood = "easy"
* {not check("heard_neighbor")} [They're harmless. Leave them be.]
    Did you ask them, or are you being kind?
    ** [I asked.]
        ...All right. I'll take your word for it.
        ~ neighbor_mood = "easy"
    ** [I'm being kind.]
        Kind's fine. Kind just isn't the same as knowing.
        ~ neighbor_mood = "sour"
    --
+ [I haven't talked to them yet.]
    Take your time. Daylight, remember.
    -> DONE
- ~ asking = false
~ done_neighbor = true
~ neighbor_mood_until = today + 3
-> DONE

// Between-quest openings while the talk is still on their mind.
=== function neighbor_mood_line() ===
{
- neighbor_mood_until < today or neighbor == "":
    ~ return ""
- neighbor_mood == "easy":
    ~ return "I've been thinking about " + neighbor + ". Not the bad kind of thinking, for once."
- neighbor_mood == "sour":
    ~ return "I keep thinking about " + neighbor + ". I'm not going to do anything. I'm just thinking about it."
}
~ return ""
