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

// When this scene plays. The greet in persona.ink checks these in order.
=== function stakeout_offer_ready() ===
~ return done_count_talk and not stake_offered and not done_stakeout

=== function stakeout_night_ready() ===
~ return staking and check("late_night") and stake_last < today

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
