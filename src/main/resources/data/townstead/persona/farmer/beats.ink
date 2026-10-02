// Story beats between the quests. Each plays once, as the day's one new thing.

// ---- A farmer's path: villagers grow into a career path through the work they do ----

=== path_scene ===
~ quest_day = today
~ done_path = true
~ temp path = career_path()
{path:
- "tiller":
    Guess what! I'm a Tiller now! Soil and rows, all day.
- "scarecrow":
    Guess what! I'm a Scarecrow now! Nothing gets near the rows while I'm out there!
- "levy":
    Guess what! I'm a Levy now! A farmer who fights, with a hoe! I didn't know you could be that!
- else:
    Guess what! I've found my way of working!
}
I didn't choose it. I just kept doing the work, and that's what I turned into.
+ [It suits you.]
    Does it? Well! Thank you! I'm going to go and do more of it!
+ [What happened to trying everything?]
    I still try everything! I'm just better at some of it now!
+ [Did you want a different one?]
    No! I'd never have picked it myself, and I like it anyway.
- -> DONE

// ---- The trader comes back: the old pull, the day a Wandering Trader is near ----

=== trader_scene ===
~ quest_day = today
~ done_trader = true
There's a trader in the village! Did you see?
Traders carry seeds from all over. What if they've got the one? The seed that changes everything!
+ [Last time a trader sold you a seed, nothing came up.]
    ~ trader_refused = true
    I know.
    I planted every field with it. I stood in the middle of them for a month, waiting.
    You're right. I'm not going to look.
+ [Go and look, then.]
    Just looking! I won't buy anything!
    Probably!
- -> DONE

// ---- A letter home: their quiet voice ----

=== letter_scene ===
~ quest_day = today
~ done_letter = true
Can you help me write something? I'm not good with letters.
It's to my family. They think the farm's doing well.
I never told them. It's been years.
+ [What do you want to say?]
+ [Why didn't you tell them?]
    They were so proud when I bought it. I didn't want to be the one who ended that.
- How would you start it?
+ ["Dear all, the farm failed."]
    That's very direct.
    It's also true. All right. "Dear all. The farm failed."
+ ["Dear all, I have news."]
    "I have news." Yes. That's a gentler way in.
+ ["Dear all, I'm happy."]
    Am I?
    I think I am. Let's start there, then.
- Then the rest. About this place, and {builder()}, and you.
They'll want to know I eat properly. I'll tell them I do. That's true now.
+ [Send it.]
    ~ letter_sent = true
    ~ contribute("affection", 3, "the_letter")
    Sent. They'll be cross that I waited so long.
    I think I'd like that, actually.
+ [Keep it for now.]
    ~ contribute("affection", 2, "the_letter")
    I'll keep it until the year's done. Then there's good news in it too.
- -> DONE
