VAR bone_asked = false
VAR done_bone = false

// When this beat plays. wolf_line/00_wolf_line.ink checks these in order.
=== function bone_ask_ready() ===
~ return done_collar and not bone_asked

// Beat 8. A bone, from the butcher when there is one.
=== bone_ask ===
# quest: A bone for the wolf
# about: Bring the hunter a bone. A butcher might have one.
# goal: bone_carried
~ bone_asked = true
{who("butcher") != "":
    Would you ask {who("butcher")} for a bone? A proper one, something with a bit of work in it. I'd go myself but I'd have to explain who it's for.
- else:
    If you find a bone anywhere, a proper one, bring it to me. Don't ask what for.
}
-> DONE

= done
~ done_bone = true
~ act("take_bone")
Give it here.
I'll leave it where it'll find it later. When nobody's looking. I'm not going to stand there watching it eat, I'm not that far gone.
-> DONE
