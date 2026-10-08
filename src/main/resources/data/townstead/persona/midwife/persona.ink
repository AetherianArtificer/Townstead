// The midwife. She trained under the founder's mother, lived in her household, delivered the
// founder and helped raise them. She arrives after the founder slips into the old oath (M1), and
// reaches them only through the player: the bread (seeds in the crust), and the story of the night
// the founder was born, which she leaves to the player to carry. Three visits, one story; this
// file is visit 1. The whole design is in docs/design/dhampir_founder.md, "The midwife".
//
// Voice: sympathetic. The tired nurse who has seen a thousand births and her share of deaths. Her
// humor is fond or at her own expense, never at anyone. She looks after the person in front of her
// without being asked. Her guilt shows as gentleness: she deflects by asking about you.

VAR given_name = ""
VAR family_name = ""
VAR today = 0
// A fresh 1 to 100 each time the conversation opens (set by the game).
VAR chance = 0

VAR met = 0
VAR bread_given = false
VAR told_birth = false
VAR asked_night = false
VAR told_night = false
VAR asked_bring = false
VAR confessed = false
VAR forgiven = false

=== function menu() ===
{met == 0:
    ~ return "You're not from here."
}
~ return "Got a minute?"

// What she says when the player walks up, in place of MCA's greeting.
=== function greeting() ===
{met == 0:
    ~ return "Oh, hello. I don't think we've met."
}
~ temp pick = chance % 3 + 1
{
- pick == 1:
    ~ return "Hello, dear."
- pick == 2:
    ~ return "There you are."
}
~ return "Oh, it's you. Good."

// What she calls the player over with when she has something for them ("" for nothing).
=== function calling() ===
{
- asked_bring and not confessed and here("dhampir_founder"):
    ~ return "Oh. You've brought them. Come here, both of you."
- check("after_midpoint") and told_night and not asked_bring:
    ~ return "Could I trouble you for a moment, dear?"
- check("bread_delivered") and not told_birth:
    ~ return "Did they eat it? Come and tell me."
- told_birth and check("wounded_seen") and not told_night:
    ~ return "Have you a minute? Come and sit with me."
}
~ return ""

=== greet ===
# label: Talk
{met == 0:
    -> meeting
}
{asked_bring and not confessed and here("dhampir_founder"):
    -> confession
}
{check("after_midpoint") and told_night and not asked_bring:
    -> ask_bring
}
-> talk

=== meeting ===
~ met = 2
Hello. Sit down, if you've got a minute. My feet have walked two valleys today and they'd like the company.
- (topics)
* [Who are you?]
    {given_name}. I deliver babies. I've been at it so long I've started delivering the babies of babies I delivered, which makes me feel about a hundred.
    I live two valleys that way. I go wherever I'm sent for.
    -> topics
* [What brings you here?]
    I had a reason to come this way. Up at your lodge. I haven't quite got to it yet.
    ** [The hunter?]
        Mm. # emote:nod
        Tell me something. Do they eat? Properly, I mean, sitting down, off a plate?
        They never would, as a little one. You had to trick them into it.
    ** [What reason?]
        An old one. It'll keep a little longer. Most things do, at my age.
    --
    -> topics
+ [I should go.]
    -> bread

= bread
Before you go. Would you do something for me?
Take them this. It's only bread, but it's the kind they used to like. Don't say it's from me. Not yet.
~ bread_given = true
~ act("give_bread")
~ act("bread_carried")
+ [Why not take it yourself?]
    I will. I just want them to have eaten something first. People take things better on a full stomach. It's the first thing you learn.
+ [I'll take it.]
    Thank you. You're kind. I can always tell.
- -> DONE

=== talk ===
{~Sit, if you like. There's room.|Oh, it's you. Good. Stop me reorganizing this bag again, would you, it's the third time today.|Hello, dear. You look like you could use a chair.}
- (topics)
* {check("bread_delivered") and not told_birth} [What was the night they were born like?]
    -> birth
* {told_birth and not asked_night} [Where were you the night their mother died?]
    ~ asked_night = true
    At home. It was raining, coming in under the door. # emote:ponder
    Do you mind if we don't, tonight? Tell me about you instead. Have you got people here?
    ** [Some.]
        Good. Hold on to them. That's all the advice I've got that's worth anything.
    ** [Not really.]
        Then you've got me, for as long as I'm here. That isn't much, but it's warm.
    --
    -> topics
* {told_birth and check("wounded_seen") and not told_night} [Tell me about the night she died. Please.]
    -> night
* {check("asked_cure")} [Is there a way back from the bite?]
    -> cure
+ {not bread_given} [Is there anything I can do for you?]
    -> meeting.bread
+ [Any advice?]
    Warm milk before bed, and don't argue with anybody after dark, it keeps you up.
    That's the whole of my medicine, most days. The rest is just sitting with people.
    -> topics
+ [I'll let you rest.]
    Thank you, dear. Come back.
    -> DONE

// Visit 1: the night the founder was born. Told to the player, who decides whether to carry it.
= birth
~ told_birth = true
Long. Their mother held on so tight I lost the feeling in two fingers.
And there was a man there. I've never told anybody about him.
Tall. Cold hands. A coat worth more than my house. He held her other hand all night and never once let go.
When the baby came, he held it so carefully. Like he'd never held anything he was afraid of breaking before.
Then he gave it back, and he was gone before sunrise.
~ act("heard_birth")
I don't know if I should tell the hunter. I don't want to take anything away from them.
You know them better than I do now. You decide.
* [I'll tell them.]
    All right. Be gentle about it. They won't be, so somebody should.
* [Some things are better left.]
    Maybe. I've thought that for a long time. I'm not sure it was ever true.
- -> topics

// The fledgling's cure (scene 17): she knows the way back. Vampirism's rule, on the vampire state.
= cure
There is. It isn't kind, and it isn't quick, but there is.
Weaken them first. A potion of weakness, the kind the clerics brew. Then a golden apple, while they're weak, and they have to eat all of it.
And then you sit with them. It's a long night, and they'll want somebody there who isn't afraid of them.
* [The hunter didn't know.]
    No. They wouldn't. They were always so sure, even as a little thing. Sure about the dark, sure about bedtime, sure about carrots.
    Being sure is very restful. It just isn't always right.
* [Thank you.]
    Go on. Before you lose your nerve. I've lost mine enough times for both of us.
- -> topics

// Visit 2: the night of the death. True, but not all of it. Then the book she has kept.
= night
~ told_night = true
...All right. You've earned it, I think. You've been good to them.
It was raining. Late. I heard someone in the lane, and I went to the shutter.
It was her. Walking fast, the way you walk when you're trying not to run.
And someone behind her. A step behind, the way servants walk. Pale. Quiet. Not him. I'd have known him anywhere.
I found her in the morning. # emote:ponder
- (book)
I've had something of hers a long time. Too long. I should have given it back years ago.
~ act("give_book")
It was hers. Her rules, every one, in her own hand. Take it to them.
* [Why didn't you give it to them before?]
    Because then I'd have had to tell them how I came to have it. # emote:shake_head
    Not tonight. Please.
* [I'll take it to them.]
    Thank you. Be gentle. They won't be, so somebody should.
- -> topics

// Visit 3, after the midpoint: she asks the player to bring the hunter to her.
=== ask_bring ===
~ asked_bring = true
I have to tell them something. Not you. Them.
Will you bring them here? I can't walk up to that lodge. I've started up there twice and turned round both times.
* [I'll bring them.]
    Thank you. Soon, please. Before I lose my nerve again. I've lost it for twenty years.
* [What is it?]
    It's theirs to hear first. You'll hear it too. I'd like you there, if you don't mind. I'm braver with somebody watching.
- -> DONE

// The confession, with the hunter here. The pivot's rehearsal: what do they do with somebody who
// failed their mother out of fear?
=== confession ===
~ confessed = true
You came. # emote:ponder
She asked me to. # who: dhampir_founder
I've something to tell you, and I'm going to tell it badly, so let me get through it.
She knocked. That night. I knew her knock. She always did two and then one, like she was sorry for the third.
There was something in the lane behind her. I heard it. I had my hand on the bar, and I didn't lift it.
I told myself for twenty years it was the rain. It wasn't the rain.
... # who: dhampir_founder
In the morning her book was on my step. She'd left it there. She knew I wasn't going to open the door, and she left me her book anyway.
She wrote us a rule. The first one I ever learned. Turn no one away. I've read it out to every girl I've trained since. I read it to them so they'll be better than I was.
Say something. Please. Anything.
* [She was afraid.]
    -> forgive
* [She left her to die.]
    -> cast_out
* [It isn't my place to say.]
    {check("founder_hungry"):
        -> cast_out
    }
    -> forgive

= forgive
~ forgiven = true
~ act("forgiven")
You kept the door shut because you were afraid of what was outside it. # who: dhampir_founder
That's all I've done my whole life. I just called it something better. # who: dhampir_founder
...Come up to the lodge. There's a fire. Sit by it, some nights. # who: dhampir_founder
Oh. # emote:ponder
Oh, I'd like that. I'd like that very much.
-> DONE

= cast_out
~ act("cast_out")
You heard her and you left her there. # who: dhampir_founder
Go home. Two valleys, wasn't it. Go home, and don't come up to the lodge. # who: dhampir_founder
...Yes. That's fair. That's more than fair.
I'll go when the roads are good. I'm sorry. I've been sorry every day.
-> DONE
