// A werewolf in human form, on the road. Each speaks in their own personality and warms to the
// player over three meetings; the curse shows in small things they notice.

VAR villager_name = ""

=== greet ===
# label: Talk
{check("friendly"):
    -> friendly ->
- else:
    -> distant ->
}
+ [Hello!]
    {check("friendly"):
        -> hello_back ->
    - else:
        -> hello_off ->
    }
+ [Who are you?]
    {check("friendly"):{villager_name}.|Nobody.}
+ [Goodbye.]
- -> DONE

// The opening line: this one's voice, by how many times the player has met them.
=== friendly ===
{
- check("voice_bright"):
    {greet:
    - 1: {~Hello! Sorry, I'm starving. Do you know anyone who sells sausages?|Hi! Ignore the torn sleeve. It's fine. I'm fine!|Oh, hello! I've got so much energy today, I could run for miles.|Hello! New face! Lovely.}
    - 2: {~You again! Hello!|Hello again! I'm so glad.|Oh, hi! I was hoping I'd see you.|Hello again! Still starving, still fine!}
    - else: {~There you are! Hello!|Hello, friend! Race you somewhere? No? Fine.|Hi! You always turn up at the right time.|Hello! You're one of my favorite people.}
    }
- check("voice_sharp"):
    {greet:
    - 1: {~What? Don't stare.|Hello. I'm in a foul mood, so be quick.|Move. You're too close.|Yes, I know my hands are scratched. Mind your own business.}
    - 2: {~You again.|Oh. You. Hello.|Still poking about?|Hello. Not now, I've got a headache.}
    - else: {~You. Fine. Hello.|Hello. You're all right, I suppose.|Hello. I'm in a better mood. Don't ruin it.|Oh, it's you. Good.}
    }
- check("voice_nervous"):
    {greet:
    - 1: {~Oh! Sorry, I didn't hear you.|Hello. Please don't get too close. I'm not well.|Hello. Sorry, I can't sit still today.|Hello. Is it nearly dark? I lose track.}
    - 2: {~Oh, it's you. Hello.|Hello again. I'm sorry if I was strange last time.|Hello again. You're still all right? Good.|Hello. I keep waking up in odd places. Never mind.}
    - else: {~Hello. It helps, seeing a friendly face.|Oh, good, it's you.|Hello. Promise me you'll be careful after dark.|Hello, friend. Stay a minute?}
    }
- check("voice_quiet"):
    {greet:
    - 1: {~Hello.|Hm. Hello.|Hello. I'm fine. Just tired.|Oh. Hello.}
    - 2: {~Hello again.|Ah. You.|Hello. I remember you.|Hello again. Still well?}
    - else: {~Hello, friend.|Ah, good. You.|Hello. Sit a while, if you like.|Hello. It's easier, with you about.}
    }
- check("voice_flirty"):
    {greet:
    - 1: {~Well, hello. You're a sight.|Hello there. Sorry, I'd stay, but I'm in a state.|Oh, hello. Where did you come from?|Hello. You've got kind eyes.}
    - 2: {~Back again? I don't mind.|Hello again. I was hoping.|Oh, it's you. Good.|Hello again. You look well.}
    - else: {~There you are. I missed you.|Hello, you. Walk with me?|Hello. You're good for my nerves.|Oh, hello. You found me again.}
    }
- check("voice_odd"):
    {greet:
    - 1: {~Hello. Do dogs bark at you? They bark at me.|Hello. I woke up in a tree yesterday. Have you ever?|Hello. Sorry, I keep sniffing. Ignore it.|Hello. What day is it? Never mind, it doesn't matter.}
    - 2: {~Hello again. No trees today.|Oh, you. Hello. The dogs still bark.|Hello again. Did a dog bark at you? Just me, then.|Hello again. I've been eating a lot of carrots. It isn't helping.}
    - else: {~Hello, friend. You don't bark. I appreciate that.|Oh, good. You. Hello.|Hello. I've decided you're safe.|Hello. If you hear howling, it's probably not me.}
    }
- else:
    {greet:
    - 1: {~Hello. Sorry, I'm not much company today.|Hello. Don't mind the scratches, I had a bad night.|Oh, hello. Sorry, I'm a bit of a mess.|Hello. You haven't got any meat on you, have you? No? Never mind.}
    - 2: {~Hello again. You're kind to stop.|Oh, it's you. Hello.|Hello again. I'm feeling better than last time.|Hello again. Did you sleep? I didn't.}
    - else: {~Hello, friend. It's good to see you.|Oh, good, it's you.|Hello. Sit with me a minute? It helps.|Hello. If I'm ever not myself, keep walking. Promise?}
    }
}
->->

// Brushed off, in this one's voice.
=== distant ===
{
- check("voice_bright"): {~Sorry, sorry, can't!|Not now!|Another time, all right?|I'm in a rush!}
- check("voice_sharp"): {~Back off.|Don't stare.|Leave me alone.|What?}
- check("voice_nervous"): {~Please don't come closer.|I'm not well. Go.|Not now, please.|Stay back. For your sake.}
- check("voice_quiet"): {~No.|Not now.|Go on.|Hm.}
- check("voice_flirty"): {~Not today.|Some other time.|Shame. Go on.|Later, maybe.}
- check("voice_odd"): {~Not now. I'm smelling something.|Go away. Kindly.|No. Sorry. No.|You're standing in my spot.}
- else: {~Please, just leave me be.|Not now.|I'm not fit for company.|Sorry. Go on.}
}
->->

// Hello, answered in their own voice.
=== hello_back ===
{
- check("voice_bright"): Hi! See you around!
- check("voice_sharp"): Yes, hello. Goodbye.
- check("voice_nervous"): Oh. Hello. Sorry, I have to go.
- check("voice_quiet"): Mm. Hello.
- check("voice_flirty"): Hello yourself.
- check("voice_odd"): Hello. Again. That's twice.
- else: Hello. Take care, now.
}
->->

=== hello_off ===
{
- check("voice_bright"): Hi! Bye!
- check("voice_sharp"): Yes. Go.
- check("voice_nervous"): Please, just go.
- check("voice_quiet"): Mm.
- check("voice_flirty"): Not now.
- check("voice_odd"): Hello. Goodbye. Both.
- else: Hello. Goodbye.
}
->->
