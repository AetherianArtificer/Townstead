// When this scene plays. The greet in persona.ink checks these in order.

=== function reoffer_ready() ===
~ return offered and not founded

// The offer. Their plan as much as the town's: a roof near the ridge, and people to hold it.
=== meeting_offer ===
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
    -> meeting_stay
+ [I'll think about it.]
    Take your time. I'd just rather you thought faster than they eat.
    -> DONE

=== meeting_stay ===
~ act("found")
~ founded = true
~ trust(2)
Good. Thank you. I'll start looking for the ones who don't mind the dark. They usually find me first, if I'm honest. # emote:nod
{asked_wolf:
    I'll be gone a day first. There's a rope I need to go and cut.
}
// They walk out once no one is watching and come back in a day with the wolf (scene 2).
~ wolf_day = today
~ act("fetch_wolf")
-> DONE

// They offered to stay, and the player asked for time to think.
=== reoffer ===
Have you thought about it? I'm not trying to rush you. They are, though.
+ [Stay. Build your lodge.]
    -> meeting_stay
+ [Not yet.]
    All right. Keep your shutters barred in the meantime. I'll be around.
    -> DONE
