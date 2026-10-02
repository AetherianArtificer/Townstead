// Act 2, scene 13: The count. After the name (scene 12), the player can ask about the recitation
// they overheard (scene 11). The founder does not explain: they start again from the first article
// and stop in the same place, which still follows the pull of the blood. This arms the slip: at the
// next oath they swear someone in, their invocation slips into her wording (the hunter_oath ritual's
// line variant), which is M1 (slip.ink).

VAR done_count_talk = false
VAR slip_oaths = 0

=== the_count ===
~ done_count_talk = true
You heard that. # emote:ponder
It's nothing. It's a list. I say it over sometimes, so I don't lose it.
-> recite ->
- (after)
* [What comes after?]
    Something. # emote:shrug
    It'll come back. It usually comes back if I don't chase it.
    -> after
* [Whose words are those?]
    Hers.
    Not tonight. I've given you her name, that's plenty for one month.
    -> after
* [You stopped in the same place.]
    I know where I stopped.
    -> after
+ [I'll leave it.]
    ~ slip_oaths = count("founder_oaths")
    ~ act("prime_slip")
    Thank you.
    -> DONE
