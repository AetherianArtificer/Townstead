// Test content for the Persona system. It has no "arrives", so it only comes through
// /townstead persona spawn townstead:test_wanderer. Remove before release, with the farmer story.

=== greet ===
# label: Just passing through?
{demeanor():
- "stern": You again.
- "guarded": Oh. It's you.
- "jovial": There you are! I was hoping you'd come by.
- else: {~Don't mind me. I'm only here to be tested.|Still here. Still being tested.}
}
{mod("farmersdelight"): Someone here has a cooking pot. I can smell it.}
+ [Give me something to do.]
    -> errand
+ [Send me somewhere.]
    -> walk
+ [Bye.]
    -> DONE

=== errand ===
# quest: A test errand
# about: A small quest for trying the Persona system.
# goal: two_farmers
# reward: bread
Get two farmers working and I'll give you some bread. It's a test. The bread is real.
-> DONE

= done
Two farmers. Here's the bread.
-> DONE

=== walk ===
# quest: A test walk
# about: Tests the ruin finder. Follow the map to the marked spot.
# goal: reach_ruin
~ act("find_ruin")
Here, take this map. There's something out that way. Go and look.
-> DONE
