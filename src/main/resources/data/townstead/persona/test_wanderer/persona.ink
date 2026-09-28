// Test content for the Persona system. It has no "arrives", so it only comes through
// /townstead persona spawn townstead:test_wanderer. Remove before release, with the farmer story.

=== greet ===
# label: Just passing through?
{~Don't mind me. I'm only here to be tested.|Still here. Still being tested.}
+ [Give me something to do.]
    -> errand
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
