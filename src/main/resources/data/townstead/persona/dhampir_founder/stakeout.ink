// Act 2, scene 15: The decent neighbor (the stakeout). A vampire lives in town and has never bitten
// anyone who did not agree to it ("never_bit"), many days after scene 9. With nobody like that in
// town, a lone vampire walks in asking to settle (a visitor, role "settler"). The founder asks the
// player to watch them for a few nights. What the nights show follows the vampire's real record:
// one who keeps to animals shows nothing; one who has fed on a person who agreed is "There it is",
// and the player can argue consent (it sets up scene 16). The choice: make them say it (Oath), leave
// it unsaid, or agree "not yet" (Hunger). A visitor who is spared stays and becomes a resident.

VAR stake_target = ""
VAR stake_offered = false
VAR staking = false
VAR stake_visitor = false
VAR stake_nights = 0
VAR stake_last = -1
VAR done_stakeout = false
VAR not_them = false
VAR argued_consent = false

=== stakeout_offer ===
~ stake_offered = true
{neighbor != "" and is(neighbor, "never_bit"):
    ~ stake_target = neighbor
    -> resident
}
-> visitor

= resident
{stake_target} hasn't bitten anybody. Nobody who didn't want it, anyway. I've asked around, and I've looked, and I've looked again.
That bothers me more than if they had. I know what to do with one that bites.
I want to watch them. A few nights, you and me, from somewhere they won't see us. Will you?
-> ask

= visitor
~ stake_visitor = true
~ act("settler_arrives")
There's one of them coming into town. On their own, in the open, like they've got nothing to hide.
They'll be asking to settle. I'd put emeralds on it. Before anybody says yes, I want to watch them. A few nights, you and me.
-> ask

= ask
+ [All right. A few nights.]
    ~ staking = true
    Good. Come and find me after dark. Bring something warm, it's a long time to sit still.
    -> DONE
+ [Can't you just leave them be?]
    I will, if there's nothing to see. That's the deal I'm making with myself. Help me keep it.
    -> ask
+ [Not now.]
    ~ stake_offered = false
    It'll keep. They're not going anywhere. That's rather the point.
    -> DONE

// A night of watching. The verdict comes once there has been enough to see.
=== stakeout_night ===
~ stake_nights++
~ stake_last = today
{stake_visitor:
    ~ stake_target = who("visitor:settler")
}
{stake_target == "":
    -> gone
}
{is(stake_target, "fed_on_person") and stake_nights >= 2:
    -> there_it_is
}
{stake_nights >= 3:
    -> nothing
}
{stake_nights == 1:
    Keep your voice down. We're watching {stake_target}, that's all. Just watching.
- else:
    Another night of this. My knees are going to file a complaint.
}
{~Nothing yet.|Still nothing. Patience. I'm told I have some.|Nothing. I'll give it one more.}
-> DONE

= gone
They've gone. Packed up and gone, I suppose, or they never meant to stay.
That's an answer too. Not the one I wanted. I'm not sure which one I wanted.
~ staking = false
~ done_stakeout = true
-> DONE

// Three nights, and nobody bitten.
= nothing
~ staking = false
~ done_stakeout = true
Three nights. Nothing. Whatever they live on, it isn't anybody here.
I was sure. I'm still sure, somewhere. I just can't find where I put it.
-> verdict

// They have fed on someone who agreed to it.
= there_it_is
~ staking = false
~ done_stakeout = true
There it is. # emote:ponder
They've had their teeth in somebody here. Somebody who said yes, it turns out, which I'm told makes it fine.
-> consent

= consent
* [They agreed. That matters.]
    ~ argued_consent = true
    Does it? I've met people who'd agree to anything once one of those had looked at them long enough.
    ...I don't know. I don't know what it matters. Ask me again when I've slept.
    -> verdict
* [So you were right.]
    I usually am. It never makes me feel any better.
    -> verdict
+ [Let's call it.]
    -> verdict

= verdict
* [Say it. They're not dangerous.]
    ~ act("oath_small")
    ~ trust(1)
    ~ not_them = true
    You want me to say it out loud. # emote:shake_head
    ...Not them. All right? Not them. I've said it.
    -> settle
* [You don't have to say anything.]
    Thanks. I'd rather not, tonight. I'll think it, though. Don't tell anyone I thought it.
    -> settle
* [Not yet, you mean.]
    ~ act("hunger_small")
    Not yet. That's it exactly. Not yet.
    {stake_visitor:
        ~ act("settler_leave")
    }
    -> DONE

= settle
{stake_visitor:
    ~ act("settler_stay")
    They can stay. I'll tell them myself, and I'll try to make it sound like good news.
}
-> DONE

// Between-quest line once the founder has said it.
=== function not_them_line() ===
{not_them and stake_target != "":
    ~ return "If " + stake_target + " nods at me, I've decided I'm going to nod back. That's as far as I've got."
}
~ return ""
