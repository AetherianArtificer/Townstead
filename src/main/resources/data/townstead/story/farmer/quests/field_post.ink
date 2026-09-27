=== field_post ===
# quest: A plan for the fields
# about: Put a Field Post near the farmer's workstation, then paint which crop goes in which row. Farmers work the ground a Field Post covers.
# goal: field_post
# skip if: field_post
{check("field_post"): -> skipped}
Every morning I walk out and decide what goes where. Then I change my mind by lunch.
There's a thing called a Field Post. You set it by the fields and paint the rows on it. Carrots here, wheat there.
Then I'd just do what it says. I'd like that a lot.
+ [I'll put one up.]
    Near my workstation, if you can. That's where I start the day. # emote:nod
    -> DONE
+ [Why can't you decide?]
    I can. I decide wrong. Last year I put the melons where the pumpkins wanted to go.
    They sulked. The melons did. The pumpkins were fine about it.
    -> DONE

= waiting
Still deciding things by hand out there. It's going about how you'd expect.
-> DONE

= done
<happy>I saw the post go up.</happy> I've been reading it all morning.
Three rows in and I haven't argued with myself once.
-> next_job

= skipped
Oh. There's already a post out there. I've been farming around it like it was a tree.
Right. I'll read it, then. # emote:laugh
-> next_job

= next_job
Now I'd like to see the plan actually grow something. Will you check back on me?
+ [I will.]
    -> first_harvest
+ [Later.]
    Sure. The rows aren't going anywhere. That's sort of their whole thing.
    -> DONE
