// A thrall whose master was killed, come to the player's town for shelter (the dhampir founder's
// scene 10 brings them as a walk-in visitor). They grieve someone the town would not, and they
// know what the hunter up at the lodge thinks of them. The player decides: stay or go.
// Their own voice: quiet, practical, used to being useful, not asking for pity.

=== function menu() ===
~ return "You look like you've come a long way."

=== greet ===
# label: Talk
I have. Two valleys east, if that means anything to you.
I'm looking for somewhere to stop. That's all. A roof, and somewhere nobody's going to burn.
- (topics)
* [What happened?]
    Hunters. Not yours, I don't think. They came to the house at noon, when she was sleeping, and they burned it.
    I was out fetching water. That's the only reason I'm standing here. I think about that a great deal.
    -> topics
* [Who was she?]
    My mistress. You'd call her a vampire, and you wouldn't be wrong.
    She was kind to me. I know how that sounds to people like you. It's still true.
    -> topics
* [What would you do if you stayed?]
    Work. Sweep, cook, carry things. I'm good at being useful. It's the only thing I was ever allowed to be.
    -> topics
* [The hunter at the lodge wants you gone.]
    I know. I saw their face. # emote:nod
    They're not wrong about everything. They're wrong about her.
    -> topics
+ [You can stay.]
    ~ act("stay")
    ...Thank you. I won't be any trouble. I'm very good at not being any trouble.
    -> DONE
+ [You should move on.]
    ~ act("leave")
    All right. # emote:nod
    I'll be gone by dark. I know how to leave a place.
    -> DONE
+ [Let me think about it.]
    Of course. I'll wait. I'm good at that, too.
    -> DONE
