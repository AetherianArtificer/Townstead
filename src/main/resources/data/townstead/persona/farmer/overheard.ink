// Scenes the player overhears in chat, walking past the Farmer and the Builder together. Each
// plays once, and only when both are there.

=== overheard_sit ===
# overheard: once
{not here("village_builder"): -> DONE}
When did you last sit down?
I don't remember. That isn't the same as never. # who: village_builder
It's exactly the same as never!
Keep your voice down. # who: village_builder
I AM keeping it down!
-> DONE

=== overheard_wings ===
# overheard: once
{not here("village_builder") or not depth.contraption: -> DONE}
I've added wings to the planting machine.
Why does it need wings? # who: village_builder
It doesn't NEED wings.
Keep the flag. Take off the wings. # who: village_builder
-> DONE
