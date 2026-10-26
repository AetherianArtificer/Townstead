// A hunter on the road. Each speaks in their own personality and warms to the player over
// three meetings; the hunting shows in small things they carry and notice.

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
    {check("friendly"):{villager_name}.|No one you need to know.}
+ [Goodbye.]
- -> DONE

// The opening line: this one's voice, by how many times the player has met them.
=== friendly ===
{
- check("voice_bright"):
    {greet:
    - 1: {~Hello! New face! Sorry, I'd love to stay, but I'm late already.|Oh, hi! Have you ever fired a crossbow? It's the best feeling. Anyway, I have to go.|Hello! Don't mind the smell, it's garlic. You get used to it.|Hi! Lovely to meet you. Can't stop, though!|Oh, hello! Which way's quickest out of here? Never mind, I'll find it.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~You again! Brilliant. How are you?|Hello again! I was hoping I'd bump into you.|Oh, hi! Twice now. That's practically friends.|Hello again! I'd stay and chat, I really would.|Hello again. Can't talk long.}
    - else: {~There you are! Hello!|Hello, friend! Busy as ever, but never too busy for you.|Oh, good, it's you. You always cheer me up.|Hi! I got new bolts. They whistle when they fly. Isn't that great?|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- check("voice_sharp"):
    {greet:
    - 1: {~What? Oh. Hello. I'm busy.|Don't touch the stakes. They're sharp, and they're mine.|If you're selling something, I'm not buying.|Hello. Move, would you? You're in my way.|Yes, yes, hello. Was there something?|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~You again. What is it now?|Hello. Still not buying anything.|Oh. You. Fine. Hello.|If anyone asks, I paid for these bolts.|Hello again. Can't talk long.}
    - else: {~Oh, it's you. You're not so bad, I suppose.|Hello. Don't make a fuss, I'm in a good mood for once.|Hello. Keep out of trouble. It costs me bolts.|You again. All right. Hello.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- check("voice_nervous"):
    {greet:
    - 1: {~Oh! Sorry, you made me jump. Hello.|Hello. Sorry, I haven't slept. Every noise sounds like teeth.|Hello. Are you all right out here on your own?|Hello. Sorry, I'm a bit on edge.|Oh. Hello. I thought you were one of them for a second.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~Oh, it's you. Thank goodness. Hello.|Hello again. I've been checking every shadow. Ignore me.|Hello again. You're still all right. Good.|Hello again. Sorry, I'm still jumpy.|Hello again. Can't talk long.}
    - else: {~Hello. It helps, seeing a face I know.|Oh, good, it's you. I can breathe again.|Hello. Promise me you'll be careful.|Hello, friend. Stay a minute? It's easier with someone here.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- check("voice_quiet"):
    {greet:
    - 1: {~Hello.|Hm? Oh. Hello.|Hello. I'll be on my way soon.|Hello. No hurry, wherever you're going.|Oh, hello. Take your time, I'm not in a rush.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~Hello again.|Oh, it's you. Hello.|Hello again. Still keeping well?|Hello. I remember you.|Hello again. Can't talk long.}
    - else: {~Hello, friend.|Ah, it's you. Good.|Hello. Sit a while, if you like.|Hello. I've some bread, if you're hungry.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- check("voice_flirty"):
    {greet:
    - 1: {~Well, hello. I'd stop, but I'd never leave.|Oh, hello. You picked a good day to cross my path.|Hello there. Sorry, duty calls. Shame.|Hello. If I'm late, I'll tell them it was your fault.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~Back again? I'm starting to think it's on purpose.|Hello again. I was hoping.|Oh, it's you. Good. I was getting bored.|Hello again. You look well. Very well.|Hello again. Can't talk long.}
    - else: {~There you are. I've been looking out for you.|Hello, you. Walk with me a while?|Hello. You're my favorite part of the night.|Oh, hello. I'd say I was busy, but not for you.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- check("voice_odd"):
    {greet:
    - 1: {~Hello. Do you know if garlic goes off? Mine's gone soft.|Oh, hello. Seven stakes. I count them when someone startles me. Hello.|Hello. Sorry, I'm listening for something.|Hello. Have you ever seen a bat land? Me neither.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~Hello again. I asked about the garlic. Nobody knows.|Oh, you. Hello. I've been practicing holding my breath.|Hello again. Still listening. Still nothing.|Hello again. You look different. Did you do something? No? Hm.|Hello again. Can't talk long.}
    - else: {~Hello, friend. I've decided you're not one of them. I checked.|Oh, good. You. Hello.|Hello. I've been thinking about mirrors. Never mind.|Hello. If you ever find a vampire that likes garlic, tell me first.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
- else:
    {greet:
    - 1: {~Oh, hello! Sorry, I can't stop, I'm meant to be somewhere.|Hello there. Mind the mud on me, I've been crawling through hedges all morning.|Hello! If you see anyone looking poorly, pale, off their food, send them my way, would you?|Oh, hello. I'd shake your hand, but mine smells of garlic.|Hello. Sorry, I'm running late, but it's nice to meet you.|{check("in_camp"):Oh. Hello. Sorry, I can't stop, the others are waiting on me.|Sorry, I can't stop long.}}
    - 2: {~Hello again! Did you get where you were going?|Oh, it's you. I was hoping I'd see you again.|Hello again. Still garlic hands, so no handshake, sorry.|Hello again. You're looking well. That's good.|Hello again. Can't talk long.}
    - else: {~There you are. I was starting to worry.|Hello, friend. Sit with me a minute, I've been on my feet all night.|I've got more garlic than I can carry. You're welcome to some, if you ever need it.|Oh, good, it's you. Hello.|{check("night"):You're out late. Get yourself inside soon.|Mind how you go.}}
    }
}
->->

// Brushed off, in this one's voice.
=== distant ===
{
- check("voice_bright"): {~Oh! Sorry, can't stop!|Not now, I'm in a hurry!|Sorry, sorry, busy!|Another time, all right?}
- check("voice_sharp"): {~What do you want?|Go away.|I don't have time for you.|Keep walking.}
- check("voice_nervous"): {~Please don't come any closer.|Sorry, I can't, not now.|Who are you? No, don't tell me. Just go.|I'd rather be on my own.}
- check("voice_quiet"): {~Not now.|Hm.|Go on.|I'd rather not talk.}
- check("voice_flirty"): {~Not today, sorry.|Ask me another time. Maybe.|I'm working, sweetheart.|Shame. Some other time.}
- check("voice_odd"): {~Not now. I'm listening.|You're standing where I was going to stand.|Go away, please. Politely.|No. Sorry. No.}
- else: {~Sorry, not now.|I'm working. Another time, maybe.|Best keep moving.|Can't help you, sorry.}
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
