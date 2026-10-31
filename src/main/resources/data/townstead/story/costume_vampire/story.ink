// A vampire abroad. Each speaks in their own personality and warms to the player over
// three meetings; what they are shows in small things they notice.

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
    {check("friendly"):{villager_name}.|Does it matter?}
+ [Goodbye.]
- -> DONE

// The opening line: this one's voice, by how many times the player has met them.
=== friendly ===
{
- check("voice_bright"):
    {greet:
    - 1: {~Hello! Oh, a new face. How lovely.|Hi! Sorry, can't stop, I'm late!|Hello! I love meeting people. I don't get to, much.|Oh, hello! Have you ever stayed up to watch the stars? You should.}
    - 2: {~You again! Hello!|Hello again! I've been hoping.|Oh, hi! I told myself I'd see you again.|Hello again! You look well!}
    - else: {~There you are! Hello, friend!|Hello! I'm so glad it's you.|Hi! You're my favorite person to bump into.|Hello, friend! Walk with me a bit?}
    }
- check("voice_sharp"):
    {greet:
    - 1: {~What? Oh. You.|Hello. I'm busy, so make it quick.|If you're here to stare, go and stare somewhere else.|Hello. Yes. What?}
    - 2: {~You again.|Hello. I suppose you want something.|Oh. You. Hello.|Still following me about?}
    - else: {~You. Fine. Hello.|Hello. I don't mind you, for some reason.|Hello. You're the only one who doesn't flinch.|Hello. Don't tell anyone I'm being nice.}
    }
- check("voice_nervous"):
    {greet:
    - 1: {~Oh! Hello. Sorry, I thought you were a hunter.|Hello. Please don't scream.|Hello. Are there hunters about? Have you seen any?|Sorry, I startle easily these days.}
    - 2: {~Oh, it's you. Thank goodness.|Hello again. You didn't tell anyone about me, did you?|Hello again. Sorry, I'm still jumpy.|Hello. You came back. I didn't think you would.}
    - else: {~Hello. It's good to see someone who isn't afraid of me.|Oh, good. It's you.|Hello. Stay a minute? I feel safer.|Hello, friend. Promise you'll be careful.}
    }
- check("voice_quiet"):
    {greet:
    - 1: {~Hello.|Hm. Hello.|Hello. Don't mind me.|Oh. Hello. No hurry.}
    - 2: {~Hello again.|Ah. You.|Hello. I remember you.|Hello again. Still well?}
    - else: {~Hello, friend.|Ah, good. You.|Hello. Sit a while, if you like.|Hello. It's peaceful, with you here.}
    }
- check("voice_flirty"):
    {greet:
    - 1: {~Hello. You're warmer than most.|Oh, hello. Where have you been hiding?|Hello there. Sorry, I really must go. Pity.}
    - 2: {~Back again? I'm flattered.|Hello again. I was thinking about you.|Oh, it's you. Good.|Hello again. You've got color in your cheeks.}
    - else: {~There you are. I've been waiting.|Hello, you. Walk with me?|Hello. You're the best part of my week.|Oh, hello. I was hoping you'd find me.}
    }
- check("voice_odd"):
    {greet:
    - 1: {~Hello. Do you have a mirror? I want to check something.|Hello. Have you ever tried garlic? What's it like?|Hello. I used to like apples. I can't remember why.|Hello. Sorry, I'm listening to your heartbeat. It's very loud.}
    - 2: {~Hello again. Still loud.|Oh, you. Hello. I've been thinking about apples.|Hello again. Did you bring a mirror? No? Never mind.|Hello again. You look different. Taller? No.}
    - else: {~Hello, friend. Your heart's slower today. That's nice.|Oh, good. You. Hello.|Hello. I've decided I like you. I'm still deciding why.|Hello. If you ever find out what garlic tastes like, tell me.}
    }
- else:
    {greet:
    - 1: {~Oh. Hello. I wasn't expecting anyone.|Hello. Forgive the cold hands.|Hello. You're not afraid of me, are you? Good.|Hello. Sorry, I'm on my way somewhere.}
    - 2: {~Hello again. You came back.|Oh, it's you. I remember your face.|Hello again. Still keeping late hours?|Hello. I hoped I'd see you.}
    - else: {~There you are. Hello, friend.|Hello. Walk with me, if you like. I don't get much company.|Oh, good, it's you.|Hello. You always look so warm.}
    }
}
->->

// Brushed off, in this one's voice.
=== distant ===
{
- check("voice_bright"): {~Sorry! Can't stop!|Not now!|Another time, all right?|Sorry, sorry!}
- check("voice_sharp"): {~Go away.|You're too close.|I'm not hungry. Leave.|What?}
- check("voice_nervous"): {~Please, just go.|Don't. Not now.|Are you a hunter? Then go.|Leave me be.}
- check("voice_quiet"): {~No.|Not now.|Go on.|Hm.}
- check("voice_flirty"): {~Not now, darling.|Some other time.|Shame. Go on.|You'll have to wait.}
- check("voice_odd"): {~Not now. I'm counting.|Your heart's too loud. Go.|No. Sorry.|Come back when you're quieter.}
- else: {~Not now, please.|I'd rather be alone.|Please, keep walking.|Another time.}
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
