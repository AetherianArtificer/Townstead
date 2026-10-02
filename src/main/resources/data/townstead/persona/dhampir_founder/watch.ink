// Act 1, scene 8: The first watch. At night, once the lodge has sworn hunters and the night walk
// is done, the founder warns that more than one is coming, and the story brings them: several
// vampires walk in from the edge (spawn_at_edge). The lodge holds the village. While any are
// close ("raid_near") the founder is short and very awake. After, hurt or not, they go round the
// houses (make_rounds). Reveals the delight near the surface, and the care right after it.

VAR watching = false
VAR done_watch = false

=== watch_offer ===
Don't go to bed yet.
There's more than one out there tonight. I can hear them calling to each other, the way they do, like dogs that learned a few words.
We hold the edge of town. You can hold it with us, or you can get people behind their doors. Both of those are the job.
+ [I'll stand with you.]
    ~ trust(1)
    Good. Stay on my left. I'm better on my right, and I'd rather not hit you.
+ [I'll get people inside.]
    Good. Knock loud. Don't explain, just say I'm asking. They'll listen to that, probably.
- ~ watching = true
~ act("first_watch")
-> DONE

// While they are still coming.
=== watch_during ===
{~Keep your back to something. A wall, a tree, me.|Don't count them. Counting makes you slow.|I haven't felt this awake in weeks. Don't look at me like that, I know how it sounds.|Here they come. Stay where I can see you.}
+ [I'm fine.]
    Good. Stay fine.
+ [Go.]
    Going.
- -> DONE

// None left close. The watch is over.
=== watch_after ===
~ watching = false
~ done_watch = true
~ act("rounds")
{check("hurt"):
    Don't. Some of it's mine. It'll close.
- else:
    Not a scratch. That isn't bragging, I just got lucky tonight.
}
I'm going to go round the houses. Just to look. You don't have to come.
* [I'll come with you.]
    ~ trust(1)
    All right. Don't wake anybody. I only want to see them.
* [You should rest.]
    After. It won't take long. I like to see them, afterwards. I don't know why.
    ...Yes I do.
* [You were enjoying that.]
    Yes. And now I'm going to go and look in on every one of them, and that's true too.
    Both things are true. I don't know what to tell you.
    ** [That's all right.]
        ~ act("hunger_small")
        Is it? Good. I'll take your word for it.
    ** [Be careful with the first one.]
        ~ act("oath_small")
        I am. I'm always careful with it. That's the whole trick.
    --
- -> DONE
