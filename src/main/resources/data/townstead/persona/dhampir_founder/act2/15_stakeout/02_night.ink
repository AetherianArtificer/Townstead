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
-> stakeout_verdict

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
    -> stakeout_verdict
* [So you were right.]
    I usually am. It never makes me feel any better.
    -> stakeout_verdict
+ [Let's call it.]
    -> stakeout_verdict
